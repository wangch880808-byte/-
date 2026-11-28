package com.ruoyi.education.overview.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

/**
 * 聚合服务测试：正常、三态、部分失败、超时、并发、越权和缺适配器。
 *
 * <p>所有端口均为 Mockito 模拟，不代表真实联调；测试只验证服务编排和
 * 失败语义，不宣称生产适配器已存在。</p>
 */
class OverviewQueryServiceTest
{
    private static final Instant AS_OF = Instant.parse("2026-10-30T06:00:00Z");

    private static final OverviewWindow WINDOW = OverviewWindow.of(
            Instant.parse("2026-10-30T00:00:00Z"),
            Instant.parse("2026-10-31T00:00:00Z"));

    private ExecutorService sourceExecutor;

    private ExecutorService aggregationExecutor;

    private OverviewSourceGuard guard;

    private Clock clock;

    @BeforeEach
    void setUp()
    {
        sourceExecutor = Executors.newFixedThreadPool(4);
        aggregationExecutor = Executors.newFixedThreadPool(4);
        guard = new OverviewSourceGuard(sourceExecutor, Duration.ofMillis(300));
        clock = Clock.fixed(AS_OF, ZoneOffset.UTC);
    }

    @AfterEach
    void tearDown()
    {
        sourceExecutor.shutdownNow();
        aggregationExecutor.shutdownNow();
    }

    @Test
    void teacherOverviewAggregatesOkItems()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(100L, List.of(session("RUNNING"))));
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 2L, 1L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 4L, AS_OF)));

        OverviewResponse response = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort).teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.class.count", OverviewStatus.OK, 1L);
        assertItem(response, "teacher.student.count", OverviewStatus.OK, 3L);
        assertItem(response, "teacher.session.running", OverviewStatus.OK, 1L);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.OK, 2L);
        assertItem(response, "teacher.grade.pending", OverviewStatus.OK, 3L);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 4L);
    }

    @Test
    void studentTodayTodosAggregatesOkItems()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findStudentScopes(any()))
                .thenReturn(Map.of(9L, List.of(scope(12L, 101L, 30L))));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(101L, List.of(session(101L, "PAUSED"))));
        when(assignmentPort.findStudentTodos(any(), any()))
                .thenReturn(Map.of(9L, List.of(assignment(101L, 1L, 0L))));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(9L, new NoticeSummary(9L, 2L, AS_OF)));

        OverviewResponse response = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort).studentTodayTodos(9L, WINDOW);

        assertItem(response, "student.class.count", OverviewStatus.OK, 1L);
        assertItem(response, "student.session.active", OverviewStatus.OK, 1L);
        assertItem(response, "student.assignment.due", OverviewStatus.OK, 1L);
        assertItem(response, "student.notice.unread", OverviewStatus.OK, 2L);
    }

    @Test
    void adminOverviewAggregatesOkItems()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findOrganizationScopes(1L))
                .thenReturn(List.of(scope(13L, 102L, 40L)));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(102L, List.of(session(102L, "RUNNING"))));
        when(assignmentPort.findBindingTodos(any(), any()))
                .thenReturn(Map.of(102L, List.of(assignment(102L, 10L, 4L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(102L, new GradeTodo(102L, 400L, 5L, 1L)));

        OverviewResponse response = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort).adminOverview(1L, WINDOW);

        assertItem(response, "admin.class.count", OverviewStatus.OK, 1L);
        assertItem(response, "admin.student.count", OverviewStatus.OK, 40L);
        assertItem(response, "admin.session.running", OverviewStatus.OK, 1L);
        assertItem(response, "admin.assignment.pending", OverviewStatus.OK, 6L);
        assertItem(response, "admin.grade.pending", OverviewStatus.OK, 6L);
    }

    @Test
    void partialSourceFailureOnlyMarksThatItemUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any()))
                .thenThrow(new IllegalStateException("classroom down"));
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 2L, 1L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 4L, AS_OF)));

        OverviewResponse response = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort).teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.session.running", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.OK, 2L);
        assertItem(response, "teacher.grade.pending", OverviewStatus.OK, 3L);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 4L);
    }

    @Test
    void sourceTimeoutOnlyMarksThatItemUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any())).thenAnswer(invocation ->
        {
            Thread.sleep(300L);
            return Map.of(100L, List.of(session("RUNNING")));
        });
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 2L, 1L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 4L, AS_OF)));

        OverviewSourceGuard shortGuard = new OverviewSourceGuard(sourceExecutor,
                Duration.ofMillis(30));
        OverviewQueryService service = new OverviewQueryService(coursePort, sessionPort,
                assignmentPort, gradePort, noticePort, shortGuard, aggregationExecutor,
                Duration.ofMillis(800), clock);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.session.running", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 4L);
    }

    @Test
    void emptyListsAreNoDataButExplicitZeroCountsAreOk()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any())).thenReturn(Map.of(100L, List.of()));
        when(assignmentPort.findTeacherTodos(any(), any())).thenReturn(Map.of(7L, List.of()));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 0L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));

        OverviewResponse response = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort).teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.session.running", OverviewStatus.NO_DATA, null);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.NO_DATA, null);
        assertItem(response, "teacher.grade.pending", OverviewStatus.OK, 0L);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 0L);
    }

    @Test
    void missingAdaptersReturnUnavailableNotZero()
    {
        OverviewQueryService service = new OverviewQueryService(null, null, null, null,
                null, guard, aggregationExecutor, Duration.ofMillis(800), clock);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertTrue(response.items().stream()
                .allMatch(item -> item.status() == OverviewStatus.UNAVAILABLE));
        assertTrue(response.items().stream().noneMatch(item -> item.value() != null));
    }

    @Test
    void emptyScopeReturnsNoDataAndNoticeCanStillBeOkZero()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any())).thenReturn(Map.of(7L, List.of()));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));

        OverviewResponse response = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort).teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.class.count", OverviewStatus.NO_DATA, null);
        assertItem(response, "teacher.session.running", OverviewStatus.NO_DATA, null);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 0L);
        verifyNoInteractions(sessionPort, assignmentPort, gradePort);
    }

    @Test
    void scopeWithoutBindingDoesNotCallBatchPorts()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any())).thenReturn(Map.of(7L, List.of(
                new CourseScope(11L, null, null, 1L, "未绑定班", "课程", 3L))));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));

        OverviewResponse response = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort).teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.class.count", OverviewStatus.OK, 1L);
        assertItem(response, "teacher.session.running", OverviewStatus.NO_DATA, null);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.NO_DATA, null);
        assertItem(response, "teacher.grade.pending", OverviewStatus.NO_DATA, null);
        verifyNoInteractions(sessionPort, assignmentPort, gradePort);
    }

    @Test
    void parallelSourcesAreInvokedConcurrently()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        CyclicBarrier barrier = new CyclicBarrier(4);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any())).thenAnswer(invocation ->
        {
            barrier.await(2, TimeUnit.SECONDS);
            return Map.of(100L, List.of(session("RUNNING")));
        });
        when(assignmentPort.findTeacherTodos(any(), any())).thenAnswer(invocation ->
        {
            barrier.await(2, TimeUnit.SECONDS);
            return Map.of(7L, List.of(assignment(3L, 1L)));
        });
        when(gradePort.findPendingGrades(any())).thenAnswer(invocation ->
        {
            barrier.await(2, TimeUnit.SECONDS);
            return Map.of(100L, new GradeTodo(100L, 300L, 2L, 1L));
        });
        when(noticePort.findUnreadSummaries(any())).thenAnswer(invocation ->
        {
            barrier.await(2, TimeUnit.SECONDS);
            return Map.of(7L, new NoticeSummary(7L, 4L, AS_OF));
        });

        OverviewSourceGuard longGuard = new OverviewSourceGuard(sourceExecutor,
                Duration.ofSeconds(2));
        OverviewQueryService service = new OverviewQueryService(coursePort, sessionPort,
                assignmentPort, gradePort, noticePort, longGuard, aggregationExecutor,
                Duration.ofSeconds(3), clock);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.session.running", OverviewStatus.OK, 1L);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.OK, 2L);
        assertItem(response, "teacher.grade.pending", OverviewStatus.OK, 3L);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 4L);
    }

    @Test
    void adminTodosNeverFabricatesSource()
    {
        OverviewQueryService service = new OverviewQueryService(null, null, null, null,
                null, guard, aggregationExecutor, Duration.ofMillis(800), clock);

        OverviewResponse response = service.adminTodos(1L, WINDOW);

        assertEquals(1, response.items().size());
        assertEquals(OverviewStatus.NO_DATA, response.items().get(0).status());
        assertEquals("pending_product_confirmation", response.items().get(0).source());
        assertNull(response.items().get(0).value());
    }

    @Test
    void rejectsMissingIdentityAndUnauthorizedClass()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        OverviewQueryService service = service(coursePort, mock(SessionStatusPort.class),
                mock(AssignmentTodoPort.class), mock(GradeTodoPort.class),
                mock(NoticeSummaryPort.class));

        assertThrows(OverviewAccessDeniedException.class,
                () -> service.teacherOverview(null, null, WINDOW));
        assertThrows(OverviewAccessDeniedException.class,
                () -> service.teacherOverview(7L, 99L, WINDOW));
        assertThrows(OverviewAccessDeniedException.class,
                () -> service.studentTodayTodos(null, WINDOW));
        assertThrows(OverviewAccessDeniedException.class,
                () -> service.adminOverview(null, WINDOW));
        assertThrows(OverviewAccessDeniedException.class,
                () -> service.adminTodos(null, WINDOW));
        assertThrows(OverviewValidationException.class,
                () -> service.adminTodos(1L, null));
    }

    @Test
    void teacherClassIdFiltersAssignmentTodosBySelectedBinding()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any())).thenReturn(Map.of(7L, List.of(
                scope(11L, 100L, 3L), scope(12L, 101L, 2L))));
        when(sessionPort.findSessions(any(), any())).thenReturn(Map.of(
                100L, List.of(session(100L, "RUNNING")),
                101L, List.of(session(101L, "RUNNING"))));
        when(assignmentPort.findTeacherTodos(any(), any())).thenReturn(Map.of(7L, List.of(
                assignment(100L, 10L, 7L), assignment(101L, 20L, 19L))));
        when(gradePort.findPendingGrades(any())).thenReturn(Map.of(
                100L, new GradeTodo(100L, 300L, 1L, 0L),
                101L, new GradeTodo(101L, 301L, 2L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse classA = service.teacherOverview(7L, 11L, WINDOW);
        assertItem(classA, "teacher.class.count", OverviewStatus.OK, 1L);
        assertItem(classA, "teacher.student.count", OverviewStatus.OK, 3L);
        assertItem(classA, "teacher.assignment.pending", OverviewStatus.OK, 3L);
        assertItem(classA, "teacher.grade.pending", OverviewStatus.OK, 1L);

        OverviewResponse classB = service.teacherOverview(7L, 12L, WINDOW);
        assertItem(classB, "teacher.class.count", OverviewStatus.OK, 1L);
        assertItem(classB, "teacher.student.count", OverviewStatus.OK, 2L);
        assertItem(classB, "teacher.assignment.pending", OverviewStatus.OK, 1L);
        assertItem(classB, "teacher.grade.pending", OverviewStatus.OK, 2L);

        OverviewResponse allAuthorized = service.teacherOverview(7L, null, WINDOW);
        assertItem(allAuthorized, "teacher.class.count", OverviewStatus.OK, 2L);
        assertItem(allAuthorized, "teacher.student.count", OverviewStatus.OK, 5L);
        assertItem(allAuthorized, "teacher.assignment.pending", OverviewStatus.OK, 4L);
        assertItem(allAuthorized, "teacher.grade.pending", OverviewStatus.OK, 3L);
    }

    @Test
    void teacherClassIdWithNoIncludedTodosReturnsNoData()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any())).thenReturn(Map.of(7L, List.of(
                scope(11L, 100L, 3L), scope(12L, 101L, 2L))));
        when(sessionPort.findSessions(any(), any())).thenReturn(Map.of(
                100L, List.of(session(100L, "RUNNING")),
                101L, List.of(session(101L, "RUNNING"))));
        when(assignmentPort.findTeacherTodos(any(), any())).thenReturn(Map.of(7L,
                List.of(assignment(101L, 5L, 1L))));
        when(gradePort.findPendingGrades(any())).thenReturn(Map.of(
                100L, new GradeTodo(100L, 300L, 1L, 0L),
                101L, new GradeTodo(101L, 301L, 1L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.teacherOverview(7L, 11L, WINDOW);

        assertItem(response, "teacher.class.count", OverviewStatus.OK, 1L);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.NO_DATA, null);
    }

    @Test
    void teacherAssignmentOutsideAuthorizedBindingsIsUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(100L, List.of(session(100L, "RUNNING"))));
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(999L, 3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 0L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.assignment.pending", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 0L);
    }

    @Test
    void studentAssignmentOutsideAuthorizedBindingsIsUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findStudentScopes(any()))
                .thenReturn(Map.of(9L, List.of(scope(12L, 101L, 30L))));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(101L, List.of(session(101L, "PAUSED"))));
        when(assignmentPort.findStudentTodos(any(), any()))
                .thenReturn(Map.of(9L, List.of(assignment(999L, 1L, 0L))));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(9L, new NoticeSummary(9L, 1L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.studentTodayTodos(9L, WINDOW);

        assertItem(response, "student.assignment.due", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "student.notice.unread", OverviewStatus.OK, 1L);
    }

    @Test
    void nonPositiveClassIdIsRejectedBeforeCoursePort()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        assertThrows(OverviewValidationException.class,
                () -> service.teacherOverview(7L, 0L, WINDOW));
        assertThrows(OverviewValidationException.class,
                () -> service.teacherOverview(7L, -1L, WINDOW));
        verifyNoInteractions(coursePort, sessionPort, assignmentPort, gradePort, noticePort);
    }

    @Test
    void nullCourseScopeElementMakesDependentItemsUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, Arrays.asList(scope(11L, 100L, 3L), null)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.class.count", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.student.count", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.session.running", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 0L);
        verifyNoInteractions(sessionPort, assignmentPort, gradePort);
    }

    @Test
    void nullSessionElementOrBindingMismatchMakesSessionUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any())).thenReturn(Map.of(100L,
                Arrays.asList(session(100L, "RUNNING"), null)));
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(100L, 3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 0L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.session.running", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.OK, 2L);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 0L);

        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(100L, List.of(session(101L, "RUNNING"))));
        OverviewResponse mismatch = service.teacherOverview(7L, null, WINDOW);
        assertItem(mismatch, "teacher.session.running", OverviewStatus.UNAVAILABLE, null);
    }

    @Test
    void nullAssignmentElementOrBindingMismatchMakesAssignmentUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(100L, List.of(session(100L, "RUNNING"))));
        when(assignmentPort.findTeacherTodos(any(), any())).thenReturn(Map.of(7L,
                Arrays.asList(assignment(100L, 3L, 1L), null)));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 0L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.assignment.pending", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.session.running", OverviewStatus.OK, 1L);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 0L);
    }

    @Test
    void gradeBindingMismatchMakesGradeUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(100L, List.of(session(100L, "RUNNING"))));
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(100L, 3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(101L, 300L, 1L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.grade.pending", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.session.running", OverviewStatus.OK, 1L);
    }

    @Test
    void adminAssignmentBindingMismatchMakesAssignmentUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findOrganizationScopes(1L))
                .thenReturn(List.of(scope(13L, 102L, 40L)));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(102L, List.of(session(102L, "RUNNING"))));
        when(assignmentPort.findBindingTodos(any(), any()))
                .thenReturn(Map.of(102L, List.of(assignment(101L, 5L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(102L, new GradeTodo(102L, 400L, 0L, 0L)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.adminOverview(1L, WINDOW);

        assertItem(response, "admin.assignment.pending", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "admin.session.running", OverviewStatus.OK, 1L);
    }

    @Test
    void missingSessionMapKeyMakesSessionUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any())).thenReturn(Map.of());
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(100L, 3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 0L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.session.running", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.OK, 2L);
    }

    @Test
    void nullSessionMapValueMakesSessionUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Collections.singletonMap(100L, null));
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(100L, 3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 0L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.session.running", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 0L);
    }

    @Test
    void noticeRecipientMismatchMakesNoticeUnavailable()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(100L, List.of(session(100L, "RUNNING"))));
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(100L, 3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 0L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(8L, 0L, AS_OF)));
        OverviewQueryService service = service(coursePort, sessionPort, assignmentPort,
                gradePort, noticePort);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.notice.unread", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.OK, 2L);
    }

    @Test
    void budgetExpiredAfterCourseSkipsNewSources()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        AtomicLong nanoClock = new AtomicLong(0L);
        when(coursePort.findTeacherScopes(any())).thenAnswer(invocation ->
        {
            nanoClock.addAndGet(200_000_000L);
            return Map.of(7L, List.of(scope(11L, 100L, 3L)));
        });
        OverviewQueryService service = new OverviewQueryService(coursePort, sessionPort,
                assignmentPort, gradePort, noticePort, guard, aggregationExecutor,
                Duration.ofMillis(100), clock, nanoClock::get);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.class.count", OverviewStatus.OK, 1L);
        assertItem(response, "teacher.student.count", OverviewStatus.OK, 3L);
        assertItem(response, "teacher.session.running", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.grade.pending", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.notice.unread", OverviewStatus.UNAVAILABLE, null);
        verifyNoInteractions(sessionPort, assignmentPort, gradePort, noticePort);
    }

    @Test
    void budgetExpiredDuringSourceSubmissionSkipsLaterSources()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        AtomicLong nanoClock = new AtomicLong(0L);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any())).thenAnswer(invocation ->
        {
            // 本来源在预算耗尽前完成；完成后预算已到 1000ms 上限。
            nanoClock.addAndGet(1_000_000_000L);
            return Map.of(100L, List.of(session(100L, "RUNNING")));
        });
        Executor direct = Runnable::run;
        OverviewSourceGuard directGuard = new OverviewSourceGuard(direct,
                Duration.ofMillis(500));
        OverviewQueryService service = new OverviewQueryService(coursePort, sessionPort,
                assignmentPort, gradePort, noticePort, directGuard, direct,
                Duration.ofMillis(1000), clock, nanoClock::get);

        OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

        assertItem(response, "teacher.session.running", OverviewStatus.OK, 1L);
        assertItem(response, "teacher.assignment.pending", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.grade.pending", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.notice.unread", OverviewStatus.UNAVAILABLE, null);
        verifyNoInteractions(assignmentPort, gradePort, noticePort);
    }

    @Test
    void courseConsumptionLeavesOnlyRemainingBudgetForParallelSources()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        AtomicLong nanoClock = new AtomicLong(0L);
        CountDownLatch blocker = new CountDownLatch(1);
        when(coursePort.findTeacherScopes(any())).thenAnswer(invocation ->
        {
            // 消耗 900ms 假预算，后续并行来源只剩 100ms。
            nanoClock.addAndGet(900_000_000L);
            return Map.of(7L, List.of(scope(11L, 100L, 3L)));
        });
        when(sessionPort.findSessions(any(), any())).thenAnswer(invocation ->
        {
            try
            {
                blocker.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            return Map.of(100L, List.of(session(100L, "RUNNING")));
        });
        when(assignmentPort.findTeacherTodos(any(), any()))
                .thenReturn(Map.of(7L, List.of(assignment(100L, 3L, 1L))));
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 0L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewSourceGuard longSourceGuard = new OverviewSourceGuard(sourceExecutor,
                Duration.ofSeconds(2));
        OverviewQueryService service = new OverviewQueryService(coursePort, sessionPort,
                assignmentPort, gradePort, noticePort, longSourceGuard, aggregationExecutor,
                Duration.ofMillis(1000), clock, nanoClock::get);

        long startedAt = System.nanoTime();
        OverviewResponse response;
        try
        {
            response = service.teacherOverview(7L, null, WINDOW);
        }
        finally
        {
            blocker.countDown();
        }
        long elapsedNanos = System.nanoTime() - startedAt;

        assertItem(response, "teacher.session.running", OverviewStatus.UNAVAILABLE, null);
        assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 0L);
        // 旧实现会在并行阶段再获得 1000ms；新实现只能使用约 100ms 剩余预算。
        assertTrue(elapsedNanos < 700_000_000L);
    }

    @Test
    void overallBudgetTimeoutPreservesCompletedSources()
    {
        CourseScopePort coursePort = mock(CourseScopePort.class);
        SessionStatusPort sessionPort = mock(SessionStatusPort.class);
        AssignmentTodoPort assignmentPort = mock(AssignmentTodoPort.class);
        GradeTodoPort gradePort = mock(GradeTodoPort.class);
        NoticeSummaryPort noticePort = mock(NoticeSummaryPort.class);
        CountDownLatch blocker = new CountDownLatch(1);
        when(coursePort.findTeacherScopes(any()))
                .thenReturn(Map.of(7L, List.of(scope(11L, 100L, 3L))));
        when(sessionPort.findSessions(any(), any()))
                .thenReturn(Map.of(100L, List.of(session(100L, "RUNNING"))));
        when(assignmentPort.findTeacherTodos(any(), any())).thenAnswer(invocation ->
        {
            try
            {
                blocker.await(5, TimeUnit.SECONDS);
            }
            catch (InterruptedException e)
            {
                Thread.currentThread().interrupt();
            }
            return Map.of(7L, List.of(assignment(100L, 3L, 1L)));
        });
        when(gradePort.findPendingGrades(any()))
                .thenReturn(Map.of(100L, new GradeTodo(100L, 300L, 0L, 0L)));
        when(noticePort.findUnreadSummaries(any()))
                .thenReturn(Map.of(7L, new NoticeSummary(7L, 0L, AS_OF)));
        OverviewQueryService service = new OverviewQueryService(coursePort, sessionPort,
                assignmentPort, gradePort, noticePort, guard, aggregationExecutor,
                Duration.ofMillis(100), clock);

        try
        {
            OverviewResponse response = service.teacherOverview(7L, null, WINDOW);

            assertItem(response, "teacher.session.running", OverviewStatus.OK, 1L);
            assertItem(response, "teacher.grade.pending", OverviewStatus.OK, 0L);
            assertItem(response, "teacher.notice.unread", OverviewStatus.OK, 0L);
            assertItem(response, "teacher.assignment.pending", OverviewStatus.UNAVAILABLE,
                    null);
        }
        finally
        {
            blocker.countDown();
        }
    }

    private OverviewQueryService service(CourseScopePort coursePort,
            SessionStatusPort sessionPort, AssignmentTodoPort assignmentPort,
            GradeTodoPort gradePort, NoticeSummaryPort noticePort)
    {
        return new OverviewQueryService(coursePort, sessionPort, assignmentPort, gradePort,
                noticePort, guard, aggregationExecutor, Duration.ofMillis(800), clock);
    }

    private CourseScope scope(Long classId, Long bindingId, Long studentCount)
    {
        return new CourseScope(classId, bindingId, 200L, 1L, "教学班", "课程", studentCount);
    }

    private SessionStatus session(String status)
    {
        return session(100L, status);
    }

    private SessionStatus session(Long bindingId, String status)
    {
        return new SessionStatus(bindingId, 500L, 900L, status, AS_OF, "第 1 课");
    }

    private AssignmentTodo assignment(long target, long submitted)
    {
        return assignment(100L, target, submitted);
    }

    private AssignmentTodo assignment(Long bindingId, long target, long submitted)
    {
        return new AssignmentTodo(bindingId, 300L, "作业", "HOMEWORK", AS_OF,
                AS_OF.plusSeconds(3600), target, submitted);
    }

    private void assertItem(OverviewResponse response, String metricCode,
            OverviewStatus status, Long expectedValue)
    {
        OverviewItem item = response.items().stream()
                .filter(candidate -> metricCode.equals(candidate.metricCode()))
                .findFirst()
                .orElseThrow();
        assertEquals(status, item.status(), metricCode);
        if (expectedValue == null)
        {
            assertNull(item.value(), metricCode);
        }
        else
        {
            assertEquals(0, item.value().compareTo(java.math.BigDecimal.valueOf(expectedValue)),
                    metricCode);
        }
    }
}
