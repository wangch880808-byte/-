package com.ruoyi.education.overview.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.ruoyi.education.overview.domain.AssignmentTodo;
import com.ruoyi.education.overview.domain.CourseScope;
import com.ruoyi.education.overview.domain.GradeTodo;
import com.ruoyi.education.overview.domain.NoticeSummary;
import com.ruoyi.education.overview.domain.SessionStatus;
import com.ruoyi.education.overview.dto.OverviewItem;
import com.ruoyi.education.overview.dto.OverviewResponse;
import com.ruoyi.education.overview.dto.OverviewStatus;
import com.ruoyi.education.overview.dto.OverviewWindow;
import com.ruoyi.education.overview.exception.OverviewAccessDeniedException;
import com.ruoyi.education.overview.exception.OverviewValidationException;
import com.ruoyi.education.overview.port.AssignmentTodoPort;
import com.ruoyi.education.overview.port.CourseScopePort;
import com.ruoyi.education.overview.port.GradeTodoPort;
import com.ruoyi.education.overview.port.NoticeSummaryPort;
import com.ruoyi.education.overview.port.SessionStatusPort;
import com.ruoyi.education.overview.support.OverviewSourceGuard;
import com.ruoyi.education.overview.support.OverviewSourceResult;

/**
 * 三端总览聚合服务。
 *
 * <p>本服务只消费端口，不直读第 1/2/3/4/5 组业务表。教师/学生先取课程范围，
 * 再并行取课堂、任务、成绩和通知；管理员先取组织范围，再并行取课堂/任务/成绩。
 * 单个来源失败只保留对应 item 为 UNAVAILABLE，不拖垮整个响应。</p>
 *
 * <p>并发与超时：端口调用通过 {@link OverviewSourceGuard} 执行，单源有独立超时；
 * 本服务再用总预算等待并行结果。超过总预算的 future 被取消，对应 item 标记
 * UNAVAILABLE。端口实现缺失时 guard 直接返回 UNAVAILABLE，不使用假数据。</p>
 *
 * <p>线程模型：聚合线程池与 guard 的源线程池分开配置，避免聚合线程等待源任务时
 * 占满同一线程池；配置见 {@code OverviewConfiguration}。</p>
 */
public class OverviewQueryService
{
    private static final Logger log = LoggerFactory.getLogger(OverviewQueryService.class);

    private static final String SOURCE_COURSE = "course";

    private static final String SOURCE_CLASSROOM = "classroom";

    private static final String SOURCE_ASSIGNMENT = "assignment";

    private static final String SOURCE_GRADE = "grade";

    private static final String SOURCE_NOTICE = "notice";

    private static final String SOURCE_PENDING_PRODUCT = "pending_product_confirmation";

    private final CourseScopePort courseScopePort;

    private final SessionStatusPort sessionStatusPort;

    private final AssignmentTodoPort assignmentTodoPort;

    private final GradeTodoPort gradeTodoPort;

    private final NoticeSummaryPort noticeSummaryPort;

    private final OverviewSourceGuard sourceGuard;

    private final Executor aggregationExecutor;

    private final Duration overallTimeout;

    private final Clock clock;

    /**
     * 创建聚合服务。
     *
     * @param courseScopePort    课程范围端口，可为 null（表示适配器未接入）
     * @param sessionStatusPort  课堂状态端口，可为 null
     * @param assignmentTodoPort 任务待办端口，可为 null
     * @param gradeTodoPort      成绩待办端口，可为 null
     * @param noticeSummaryPort  通知汇总端口，可为 null
     * @param sourceGuard        数据源守卫，不能为 null
     * @param aggregationExecutor 并行聚合执行器，不能为 null
     * @param overallTimeout     聚合总超时，必须大于 0
     * @param clock              服务端时钟，不能为 null
     * @throws OverviewValidationException 必要参数非法
     */
    public OverviewQueryService(CourseScopePort courseScopePort,
            SessionStatusPort sessionStatusPort,
            AssignmentTodoPort assignmentTodoPort,
            GradeTodoPort gradeTodoPort,
            NoticeSummaryPort noticeSummaryPort,
            OverviewSourceGuard sourceGuard,
            Executor aggregationExecutor,
            Duration overallTimeout,
            Clock clock)
    {
        if (sourceGuard == null)
        {
            throw new OverviewValidationException("sourceGuard 不能为空");
        }
        if (aggregationExecutor == null)
        {
            throw new OverviewValidationException("aggregationExecutor 不能为空");
        }
        if (overallTimeout == null || overallTimeout.isZero() || overallTimeout.isNegative())
        {
            throw new OverviewValidationException("overallTimeout 必须大于 0");
        }
        if (clock == null)
        {
            throw new OverviewValidationException("clock 不能为空");
        }
        this.courseScopePort = courseScopePort;
        this.sessionStatusPort = sessionStatusPort;
        this.assignmentTodoPort = assignmentTodoPort;
        this.gradeTodoPort = gradeTodoPort;
        this.noticeSummaryPort = noticeSummaryPort;
        this.sourceGuard = sourceGuard;
        this.aggregationExecutor = aggregationExecutor;
        this.overallTimeout = overallTimeout;
        this.clock = clock;
    }

    /**
     * 教师教学总览。
     *
     * @param teacherUserId 当前教师用户 ID，不能为空
     * @param classId       可选教学班过滤；非本人教学班返回 403
     * @param window        时间窗，不能为空
     * @return 教师总览响应，至少包含一个 item
     * @throws OverviewAccessDeniedException 教师身份缺失或班级越权
     * @throws OverviewValidationException   时间窗为空
     */
    public OverviewResponse teacherOverview(Long teacherUserId, Long classId,
            OverviewWindow window)
    {
        requireUser(teacherUserId, "教师身份缺失");
        requireWindow(window);

        OverviewSourceResult<Map<Long, List<CourseScope>>> scopeResult = awaitSingle(
                SOURCE_COURSE, () -> courseScopePort.findTeacherScopes(List.of(teacherUserId)));
        ScopeView scopeView = userScopeView(scopeResult, teacherUserId);
        if (classId != null && scopeView.status() == OverviewStatus.OK)
        {
            List<CourseScope> filtered = scopeView.scopes().stream()
                    .filter(scope -> classId.equals(scope.teachingClassId()))
                    .toList();
            if (filtered.isEmpty())
            {
                throw new OverviewAccessDeniedException("无权查看该教学班总览");
            }
            scopeView = new ScopeView(OverviewStatus.OK, filtered);
        }

        Set<Long> bindingIds = bindingIds(scopeView.scopes());
        // 没有课程空间绑定时不调用批量端口，避免空集合被适配器误判为源失败。
        boolean hasBindings = scopeView.status() == OverviewStatus.OK
                && !bindingIds.isEmpty();
        CompletableFuture<OverviewSourceResult<Map<Long, List<SessionStatus>>>> sessionsFuture =
                hasBindings
                        ? async(SOURCE_CLASSROOM,
                                () -> sessionStatusPort.findSessions(bindingIds, window))
                        : completed(SOURCE_CLASSROOM, noDataIfOk(scopeView.status()));
        CompletableFuture<OverviewSourceResult<Map<Long, List<AssignmentTodo>>>> assignmentsFuture =
                hasBindings
                        ? async(SOURCE_ASSIGNMENT,
                                () -> assignmentTodoPort.findTeacherTodos(
                                        List.of(teacherUserId), window))
                        : completed(SOURCE_ASSIGNMENT, noDataIfOk(scopeView.status()));
        CompletableFuture<OverviewSourceResult<Map<Long, GradeTodo>>> gradesFuture =
                hasBindings
                        ? async(SOURCE_GRADE,
                                () -> gradeTodoPort.findPendingGrades(bindingIds))
                        : completed(SOURCE_GRADE, noDataIfOk(scopeView.status()));
        CompletableFuture<OverviewSourceResult<Map<Long, NoticeSummary>>> noticesFuture =
                async(SOURCE_NOTICE,
                        () -> noticeSummaryPort.findUnreadSummaries(List.of(teacherUserId)));

        awaitAll(List.of(sessionsFuture, assignmentsFuture, gradesFuture, noticesFuture));

        List<OverviewItem> items = new ArrayList<>();
        items.add(classCountItem("teacher.class.count", scopeView, "/teacher/classes"));
        items.add(studentCountItem("teacher.student.count", scopeView, "/teacher/classes"));
        items.add(sessionItem("teacher.session.running", SOURCE_CLASSROOM,
                resultNow(sessionsFuture, SOURCE_CLASSROOM), bindingIds, "/teacher/classes"));
        items.add(assignmentItem("teacher.assignment.pending", SOURCE_ASSIGNMENT,
                resultNow(assignmentsFuture, SOURCE_ASSIGNMENT), teacherUserId, true,
                "/teacher/assignments"));
        items.add(gradeItem("teacher.grade.pending", resultNow(gradesFuture, SOURCE_GRADE),
                bindingIds, "/teacher/grades"));
        items.add(noticeItem("teacher.notice.unread",
                resultNow(noticesFuture, SOURCE_NOTICE), teacherUserId, "/teacher/notices"));
        return response(window, items);
    }

    /**
     * 学生今日待办。
     *
     * @param studentUserId 当前学生用户 ID，不能为空
     * @param window        时间窗，不能为空
     * @return 学生今日待办响应，至少包含一个 item
     * @throws OverviewAccessDeniedException 学生身份缺失
     * @throws OverviewValidationException   时间窗为空
     */
    public OverviewResponse studentTodayTodos(Long studentUserId, OverviewWindow window)
    {
        requireUser(studentUserId, "学生身份缺失");
        requireWindow(window);

        OverviewSourceResult<Map<Long, List<CourseScope>>> scopeResult = awaitSingle(
                SOURCE_COURSE, () -> courseScopePort.findStudentScopes(List.of(studentUserId)));
        ScopeView scopeView = userScopeView(scopeResult, studentUserId);

        Set<Long> bindingIds = bindingIds(scopeView.scopes());
        // 没有课程空间绑定时不调用批量端口，避免空集合被适配器误判为源失败。
        boolean hasBindings = scopeView.status() == OverviewStatus.OK
                && !bindingIds.isEmpty();
        CompletableFuture<OverviewSourceResult<Map<Long, List<SessionStatus>>>> sessionsFuture =
                hasBindings
                        ? async(SOURCE_CLASSROOM,
                                () -> sessionStatusPort.findSessions(bindingIds, window))
                        : completed(SOURCE_CLASSROOM, noDataIfOk(scopeView.status()));
        CompletableFuture<OverviewSourceResult<Map<Long, List<AssignmentTodo>>>> assignmentsFuture =
                hasBindings
                        ? async(SOURCE_ASSIGNMENT,
                                () -> assignmentTodoPort.findStudentTodos(
                                        List.of(studentUserId), window))
                        : completed(SOURCE_ASSIGNMENT, noDataIfOk(scopeView.status()));
        CompletableFuture<OverviewSourceResult<Map<Long, NoticeSummary>>> noticesFuture =
                async(SOURCE_NOTICE,
                        () -> noticeSummaryPort.findUnreadSummaries(List.of(studentUserId)));

        awaitAll(List.of(sessionsFuture, assignmentsFuture, noticesFuture));

        List<OverviewItem> items = new ArrayList<>();
        items.add(classCountItem("student.class.count", scopeView, "/student/classes"));
        items.add(sessionItem("student.session.active", SOURCE_CLASSROOM,
                resultNow(sessionsFuture, SOURCE_CLASSROOM), bindingIds, "/student/todos"));
        items.add(assignmentItem("student.assignment.due", SOURCE_ASSIGNMENT,
                resultNow(assignmentsFuture, SOURCE_ASSIGNMENT), studentUserId, false,
                "/student/todos"));
        items.add(noticeItem("student.notice.unread",
                resultNow(noticesFuture, SOURCE_NOTICE), studentUserId, "/student/notices"));
        return response(window, items);
    }

    /**
     * 管理员管理总览。
     *
     * @param organizationId 当前管理员获授权组织 ID，不能为空
     * @param window         时间窗，不能为空
     * @return 管理员总览响应，至少包含一个 item
     * @throws OverviewAccessDeniedException 管理员组织范围缺失
     * @throws OverviewValidationException   时间窗为空
     */
    public OverviewResponse adminOverview(Long organizationId, OverviewWindow window)
    {
        requireOrganization(organizationId);
        requireWindow(window);

        OverviewSourceResult<List<CourseScope>> scopeResult = awaitSingle(
                SOURCE_COURSE,
                () -> courseScopePort.findOrganizationScopes(organizationId));
        ScopeView scopeView = organizationScopeView(scopeResult);

        Set<Long> bindingIds = bindingIds(scopeView.scopes());
        // 没有课程空间绑定时不调用批量端口，避免空集合被适配器误判为源失败。
        boolean hasBindings = scopeView.status() == OverviewStatus.OK
                && !bindingIds.isEmpty();
        CompletableFuture<OverviewSourceResult<Map<Long, List<SessionStatus>>>> sessionsFuture =
                hasBindings
                        ? async(SOURCE_CLASSROOM,
                                () -> sessionStatusPort.findSessions(bindingIds, window))
                        : completed(SOURCE_CLASSROOM, noDataIfOk(scopeView.status()));
        CompletableFuture<OverviewSourceResult<Map<Long, List<AssignmentTodo>>>> assignmentsFuture =
                hasBindings
                        ? async(SOURCE_ASSIGNMENT,
                                () -> assignmentTodoPort.findBindingTodos(bindingIds, window))
                        : completed(SOURCE_ASSIGNMENT, noDataIfOk(scopeView.status()));
        CompletableFuture<OverviewSourceResult<Map<Long, GradeTodo>>> gradesFuture =
                hasBindings
                        ? async(SOURCE_GRADE,
                                () -> gradeTodoPort.findPendingGrades(bindingIds))
                        : completed(SOURCE_GRADE, noDataIfOk(scopeView.status()));

        awaitAll(List.of(sessionsFuture, assignmentsFuture, gradesFuture));

        List<OverviewItem> items = new ArrayList<>();
        items.add(classCountItem("admin.class.count", scopeView, "/admin/classes"));
        items.add(studentCountItem("admin.student.count", scopeView, "/admin/classes"));
        items.add(sessionItem("admin.session.running", SOURCE_CLASSROOM,
                resultNow(sessionsFuture, SOURCE_CLASSROOM), bindingIds, "/admin/classes"));
        items.add(bindingAssignmentItem("admin.assignment.pending", SOURCE_ASSIGNMENT,
                resultNow(assignmentsFuture, SOURCE_ASSIGNMENT), bindingIds, "/admin/assignments"));
        items.add(gradeItem("admin.grade.pending", resultNow(gradesFuture, SOURCE_GRADE),
                bindingIds, "/admin/grades"));
        return response(window, items);
    }

    /**
     * 管理员待办事项。
     *
     * <p>待办来源尚未经产品确认；本方法不调用任何业务端口，也不自造指标，
     * 只返回一个 NO_DATA item。产品确认后再接来源适配器。</p>
     *
     * @param organizationId 当前管理员获授权组织 ID，不能为空
     * @param window         时间窗，不能为空
     * @return 只含 NO_DATA 项的管理员待办响应
     * @throws OverviewAccessDeniedException 管理员组织范围缺失
     * @throws OverviewValidationException   时间窗为空
     */
    public OverviewResponse adminTodos(Long organizationId, OverviewWindow window)
    {
        requireOrganization(organizationId);
        requireWindow(window);
        return response(window, List.of(OverviewItem.noData(
                "admin.todo.pending", "COUNT", SOURCE_PENDING_PRODUCT, null)));
    }

    private ScopeView userScopeView(
            OverviewSourceResult<Map<Long, List<CourseScope>>> result, Long userId)
    {
        if (result.status() != OverviewStatus.OK)
        {
            return new ScopeView(result.status(), List.of());
        }
        Map<Long, List<CourseScope>> map = result.value();
        if (map == null || !map.containsKey(userId))
        {
            // 契约要求每个请求 key 都有条目；缺 key 不能当成“没有班级”。
            return new ScopeView(OverviewStatus.UNAVAILABLE, List.of());
        }
        List<CourseScope> scopes = map.get(userId);
        if (scopes == null)
        {
            // key 存在但值为 null 是契约违约，不能当成“没有班级”。
            return new ScopeView(OverviewStatus.UNAVAILABLE, List.of());
        }
        if (scopes.isEmpty())
        {
            return new ScopeView(OverviewStatus.NO_DATA, List.of());
        }
        return new ScopeView(OverviewStatus.OK, List.copyOf(scopes));
    }

    private ScopeView organizationScopeView(OverviewSourceResult<List<CourseScope>> result)
    {
        if (result.status() != OverviewStatus.OK)
        {
            return new ScopeView(result.status(), List.of());
        }
        List<CourseScope> scopes = result.value();
        if (scopes == null)
        {
            return new ScopeView(OverviewStatus.UNAVAILABLE, List.of());
        }
        if (scopes.isEmpty())
        {
            return new ScopeView(OverviewStatus.NO_DATA, List.of());
        }
        return new ScopeView(OverviewStatus.OK, List.copyOf(scopes));
    }

    private Set<Long> bindingIds(List<CourseScope> scopes)
    {
        return scopes.stream()
                .map(CourseScope::bindingId)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    private OverviewItem classCountItem(String metricCode, ScopeView scopeView, String actionPath)
    {
        if (scopeView.status() != OverviewStatus.OK)
        {
            return statusItem(metricCode, SOURCE_COURSE, scopeView.status(), actionPath);
        }
        return okItem(metricCode, scopeView.scopes().size(), SOURCE_COURSE,
                (long) scopeView.scopes().size(), actionPath);
    }

    private OverviewItem studentCountItem(String metricCode, ScopeView scopeView,
            String actionPath)
    {
        if (scopeView.status() != OverviewStatus.OK)
        {
            return statusItem(metricCode, SOURCE_COURSE, scopeView.status(), actionPath);
        }
        long total = 0;
        for (CourseScope scope : scopeView.scopes())
        {
            if (scope.studentCount() == null)
            {
                // 任一班级缺学生数就返回 NO_DATA，避免少算后当成真实总数。
                return OverviewItem.noData(metricCode, "COUNT", SOURCE_COURSE, actionPath);
            }
            total += scope.studentCount();
        }
        return okItem(metricCode, total, SOURCE_COURSE,
                (long) scopeView.scopes().size(), actionPath);
    }

    private OverviewItem sessionItem(String metricCode, String source,
            OverviewSourceResult<Map<Long, List<SessionStatus>>> result,
            Collection<Long> bindingIds, String actionPath)
    {
        if (result.status() != OverviewStatus.OK)
        {
            return statusItem(metricCode, source, result.status(), actionPath);
        }
        Map<Long, List<SessionStatus>> map = result.value();
        if (map == null || !map.keySet().containsAll(bindingIds))
        {
            return OverviewItem.unavailable(metricCode, "COUNT", source, actionPath);
        }
        List<SessionStatus> sessions = new ArrayList<>();
        for (Long bindingId : bindingIds)
        {
            List<SessionStatus> value = map.get(bindingId);
            if (value == null)
            {
                return OverviewItem.unavailable(metricCode, "COUNT", source, actionPath);
            }
            sessions.addAll(value);
        }
        if (sessions.isEmpty())
        {
            // 契约：空 List 表示没有数据，不能写成 0。
            return OverviewItem.noData(metricCode, "COUNT", source, actionPath);
        }
        long running = sessions.stream()
                .filter(session -> "RUNNING".equals(session.status())
                        || "PAUSED".equals(session.status()))
                .count();
        return okItem(metricCode, running, source, (long) bindingIds.size(), actionPath);
    }

    private OverviewItem assignmentItem(String metricCode, String source,
            OverviewSourceResult<Map<Long, List<AssignmentTodo>>> result,
            Long userId, boolean sumPending, String actionPath)
    {
        if (result.status() != OverviewStatus.OK)
        {
            return statusItem(metricCode, source, result.status(), actionPath);
        }
        Map<Long, List<AssignmentTodo>> map = result.value();
        if (map == null || !map.containsKey(userId))
        {
            return OverviewItem.unavailable(metricCode, "COUNT", source, actionPath);
        }
        List<AssignmentTodo> todos = map.get(userId);
        if (todos == null)
        {
            return OverviewItem.unavailable(metricCode, "COUNT", source, actionPath);
        }
        if (todos.isEmpty())
        {
            // 契约：空 List 表示没有待办数据，不能写成 0。
            return OverviewItem.noData(metricCode, "COUNT", source, actionPath);
        }
        long value = sumPending
                ? todos.stream().mapToLong(AssignmentTodo::pendingCount).sum()
                : todos.size();
        return okItem(metricCode, value, source, (long) todos.size(), actionPath);
    }

    private OverviewItem bindingAssignmentItem(String metricCode, String source,
            OverviewSourceResult<Map<Long, List<AssignmentTodo>>> result,
            Collection<Long> bindingIds, String actionPath)
    {
        if (result.status() != OverviewStatus.OK)
        {
            return statusItem(metricCode, source, result.status(), actionPath);
        }
        Map<Long, List<AssignmentTodo>> map = result.value();
        if (map == null || !map.keySet().containsAll(bindingIds))
        {
            return OverviewItem.unavailable(metricCode, "COUNT", source, actionPath);
        }
        long pending = 0L;
        long recordCount = 0L;
        for (Long bindingId : bindingIds)
        {
            List<AssignmentTodo> todos = map.get(bindingId);
            if (todos == null)
            {
                return OverviewItem.unavailable(metricCode, "COUNT", source, actionPath);
            }
            pending += todos.stream().mapToLong(AssignmentTodo::pendingCount).sum();
            recordCount += todos.size();
        }
        if (recordCount == 0L)
        {
            // 契约：空 List 表示没有待办数据，不能写成 0。
            return OverviewItem.noData(metricCode, "COUNT", source, actionPath);
        }
        return okItem(metricCode, pending, source, (long) bindingIds.size(), actionPath);
    }

    private OverviewItem gradeItem(String metricCode,
            OverviewSourceResult<Map<Long, GradeTodo>> result,
            Collection<Long> bindingIds, String actionPath)
    {
        if (result.status() != OverviewStatus.OK)
        {
            return statusItem(metricCode, SOURCE_GRADE, result.status(), actionPath);
        }
        Map<Long, GradeTodo> map = result.value();
        if (map == null || !map.keySet().containsAll(bindingIds))
        {
            return OverviewItem.unavailable(metricCode, "COUNT", SOURCE_GRADE, actionPath);
        }
        long pending = 0L;
        for (Long bindingId : bindingIds)
        {
            GradeTodo todo = map.get(bindingId);
            if (todo == null)
            {
                return OverviewItem.unavailable(metricCode, "COUNT", SOURCE_GRADE,
                        actionPath);
            }
            pending += todo.pendingGradeCount() + todo.pendingPublishCount();
        }
        return okItem(metricCode, pending, SOURCE_GRADE, (long) bindingIds.size(), actionPath);
    }

    private OverviewItem noticeItem(String metricCode,
            OverviewSourceResult<Map<Long, NoticeSummary>> result,
            Long userId, String actionPath)
    {
        if (result.status() != OverviewStatus.OK)
        {
            return statusItem(metricCode, SOURCE_NOTICE, result.status(), actionPath);
        }
        Map<Long, NoticeSummary> map = result.value();
        NoticeSummary summary = map == null ? null : map.get(userId);
        if (summary == null)
        {
            return OverviewItem.unavailable(metricCode, "COUNT", SOURCE_NOTICE, actionPath);
        }
        return okItem(metricCode, summary.unreadCount(), SOURCE_NOTICE, null, actionPath);
    }

    private OverviewItem statusItem(String metricCode, String source, OverviewStatus status,
            String actionPath)
    {
        if (status == OverviewStatus.UNAVAILABLE)
        {
            return OverviewItem.unavailable(metricCode, "COUNT", source, actionPath);
        }
        return OverviewItem.noData(metricCode, "COUNT", source, actionPath);
    }

    private OverviewItem okItem(String metricCode, long value, String source, Long sampleCount,
            String actionPath)
    {
        return OverviewItem.ok(metricCode, BigDecimal.valueOf(value), "COUNT", source,
                sampleCount, actionPath);
    }

    private OverviewResponse response(OverviewWindow window, List<OverviewItem> items)
    {
        return OverviewResponse.of(clock.instant(), window, items);
    }

    private <T> OverviewSourceResult<T> awaitSingle(String source, Supplier<T> supplier)
    {
        CompletableFuture<OverviewSourceResult<T>> future = async(source, supplier);
        try
        {
            return future.get(overallTimeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            future.cancel(true);
            log.warn("总览单源聚合超时: source={}, timeoutMs={}", source,
                    overallTimeout.toMillis());
            return OverviewSourceResult.unavailable(source, "aggregation timeout");
        }
        catch (InterruptedException e)
        {
            future.cancel(true);
            Thread.currentThread().interrupt();
            log.warn("总览单源聚合被中断: source={}", source);
            return OverviewSourceResult.unavailable(source, "interrupted");
        }
        catch (ExecutionException e)
        {
            log.warn("总览单源聚合异常: source={}, cause={}", source,
                    e.getCause() == null ? e.toString() : e.getCause().toString());
            return OverviewSourceResult.unavailable(source, "aggregation failure");
        }
    }

    private void awaitAll(List<CompletableFuture<?>> futures)
    {
        try
        {
            CompletableFuture.allOf(futures.toArray(CompletableFuture[]::new))
                    .get(overallTimeout.toMillis(), TimeUnit.MILLISECONDS);
        }
        catch (TimeoutException e)
        {
            futures.forEach(future -> future.cancel(true));
            log.warn("总览并行聚合超时: timeoutMs={}", overallTimeout.toMillis());
        }
        catch (InterruptedException e)
        {
            futures.forEach(future -> future.cancel(true));
            Thread.currentThread().interrupt();
            log.warn("总览并行聚合被中断");
        }
        catch (ExecutionException e)
        {
            log.warn("总览并行聚合异常: cause={}",
                    e.getCause() == null ? e.toString() : e.getCause().toString());
        }
    }

    private <T> OverviewSourceResult<T> resultNow(
            CompletableFuture<OverviewSourceResult<T>> future, String source)
    {
        try
        {
            return future.getNow(OverviewSourceResult.unavailable(source,
                    "aggregation incomplete"));
        }
        catch (CompletionException | CancellationException e)
        {
            return OverviewSourceResult.unavailable(source, "aggregation failed");
        }
    }

    private <T> CompletableFuture<OverviewSourceResult<T>> async(String source,
            Supplier<T> supplier)
    {
        try
        {
            return CompletableFuture.supplyAsync(
                    () -> sourceGuard.query(source, supplier, value -> false),
                    aggregationExecutor);
        }
        catch (RejectedExecutionException e)
        {
            log.warn("总览聚合执行器拒绝任务: source={}", source);
            return CompletableFuture.completedFuture(
                    OverviewSourceResult.unavailable(source, "aggregation executor rejected"));
        }
    }

    private OverviewStatus noDataIfOk(OverviewStatus status)
    {
        return status == OverviewStatus.OK ? OverviewStatus.NO_DATA : status;
    }

    private <T> CompletableFuture<OverviewSourceResult<T>> completed(String source,
            OverviewStatus status)
    {
        return CompletableFuture.completedFuture(status == OverviewStatus.UNAVAILABLE
                ? OverviewSourceResult.unavailable(source, "course scope unavailable")
                : OverviewSourceResult.noData(source));
    }

    private void requireUser(Long userId, String message)
    {
        if (userId == null)
        {
            throw new OverviewAccessDeniedException(message);
        }
    }

    private void requireOrganization(Long organizationId)
    {
        if (organizationId == null)
        {
            throw new OverviewAccessDeniedException("管理员组织范围缺失");
        }
    }

    private void requireWindow(OverviewWindow window)
    {
        if (window == null)
        {
            throw new OverviewValidationException("window 不能为空");
        }
    }

    /**
     * 课程范围视图：状态与范围列表绑定，避免把 NO_DATA 当成空班级 0。
     *
     * @param status 来源状态
     * @param scopes 授权范围；非 OK 时为空列表
     */
    private record ScopeView(OverviewStatus status, List<CourseScope> scopes)
    {
    }
}
