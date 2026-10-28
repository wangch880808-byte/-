package com.ruoyi.education.course.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Objects;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.course.authorization.StudentAnnouncementAccess;
import com.ruoyi.education.course.authorization.StudentAnnouncementAuthorizationPort;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.domain.StudentNotice;
import com.ruoyi.education.course.exception.NoticeAccessDeniedException;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.NoticeConcurrentUpdateException;
import com.ruoyi.education.course.exception.NoticeNotFoundException;
import com.ruoyi.education.course.exception.NoticePersistenceException;
import com.ruoyi.education.course.exception.NoticeValidationException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;
import com.ruoyi.education.course.support.DatabaseTimePrecision;

/**
 * 学生通知只读与已读服务。
 *
 * <p>学生身份只来自认证上下文；每次请求只取一次服务端 UTC 时间，
 * 并用同一时刻完成成员授权、通知可见性和已读写入。通知只是提醒，
 * 不是来源业务详情接口的访问凭证。</p>
 */
@Service
public class StudentNoticeService
{
    private final StudentNoticeMapper noticeMapper;

    private final StudentAnnouncementAuthorizationPort authorizationPort;

    private final Clock clock;

    @Autowired
    public StudentNoticeService(StudentNoticeMapper noticeMapper,
            ObjectProvider<StudentAnnouncementAuthorizationPort> authorizationPortProvider)
    {
        this(noticeMapper, authorizationPortProvider.getIfAvailable(), Clock.systemUTC());
    }

    StudentNoticeService(StudentNoticeMapper noticeMapper,
            StudentAnnouncementAuthorizationPort authorizationPort, Clock clock)
    {
        this.noticeMapper = Objects.requireNonNull(noticeMapper, "noticeMapper");
        this.authorizationPort = authorizationPort;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public StudentNoticePage list(Long bindingId, NoticeKind kind, boolean unreadOnly,
            int pageNum, int pageSize)
    {
        validatePage(pageNum, pageSize);
        Long userId = currentUserId();
        LocalDateTime at = now();

        List<NoticeBindingScope> scopes = resolveScopesForQuery(bindingId, userId, at);
        if (scopes.isEmpty())
        {
            return new StudentNoticePage(0, List.of());
        }

        StudentNoticeQuery query = query(userId, at, scopes, kind, unreadOnly,
                ((long) pageNum - 1) * pageSize, pageSize);
        long total = countVisible(query);
        if (total == 0)
        {
            return new StudentNoticePage(0, List.of());
        }
        return new StudentNoticePage(total, selectVisiblePage(query));
    }

    @Transactional(readOnly = true)
    public StudentNotice get(Long noticeId)
    {
        validateNoticeId(noticeId);
        Long userId = currentUserId();
        LocalDateTime at = now();

        StudentNotice owned = selectByIdAndRecipient(noticeId, userId);
        if (owned == null || owned.getBindingId() == null)
        {
            throw new NoticeNotFoundException();
        }
        NoticeBindingScope scope = resolveVisibleScope(owned.getBindingId(), userId, at);
        if (scope == null)
        {
            throw new NoticeNotFoundException();
        }

        StudentNotice notice = selectVisibleById(noticeId,
                query(userId, at, List.of(scope), null, false, 0, 0));
        if (notice == null)
        {
            throw new NoticeNotFoundException();
        }
        return notice;
    }

    @Transactional(readOnly = true)
    public StudentNoticeUnreadCountResult unreadCount(Long bindingId)
    {
        Long userId = currentUserId();
        LocalDateTime at = now();

        List<NoticeBindingScope> scopes = resolveScopesForQuery(bindingId, userId, at);
        if (scopes.isEmpty())
        {
            return new StudentNoticeUnreadCountResult(0, at);
        }
        return new StudentNoticeUnreadCountResult(
                countVisible(query(userId, at, scopes, null, true, 0, 0)), at);
    }

    @Transactional(rollbackFor = Exception.class)
    public StudentNoticeReadResult markRead(Long noticeId)
    {
        validateNoticeId(noticeId);
        Long userId = currentUserId();
        LocalDateTime at = now();

        StudentNotice owned = selectByIdAndRecipient(noticeId, userId);
        if (owned == null || owned.getBindingId() == null)
        {
            throw new NoticeNotFoundException();
        }
        NoticeBindingScope scope = resolveVisibleScope(owned.getBindingId(), userId, at);
        if (scope == null)
        {
            throw new NoticeNotFoundException();
        }

        StudentNoticeQuery query = query(userId, at, List.of(scope), null, false, 0, 0);
        StudentNotice visible = selectVisibleById(noticeId, query);
        if (visible == null)
        {
            throw new NoticeNotFoundException();
        }
        if (visible.getReadAt() != null)
        {
            return new StudentNoticeReadResult(noticeId, visible.getReadAt(), true);
        }

        int rows = updateMarkRead(noticeId, at, query);
        if (rows == 1)
        {
            return new StudentNoticeReadResult(noticeId, at, false);
        }

        StudentNotice current = selectVisibleByIdForUpdate(noticeId, query);
        if (current == null)
        {
            throw new NoticeNotFoundException();
        }
        if (current.getReadAt() != null)
        {
            return new StudentNoticeReadResult(noticeId, current.getReadAt(), true);
        }
        throw new NoticeConcurrentUpdateException();
    }

    @Transactional(rollbackFor = Exception.class)
    public StudentNoticeReadAllResult markAllRead()
    {
        Long userId = currentUserId();
        LocalDateTime at = now();

        List<NoticeBindingScope> scopes = resolveActiveScopes(userId, at);
        if (scopes.isEmpty())
        {
            return new StudentNoticeReadAllResult(0, 0, at);
        }

        StudentNoticeQuery query = query(userId, at, scopes, null, true, 0, 0);
        int markedCount = updateMarkAllRead(at, query);
        long unreadCount = countVisibleForUpdate(query);
        return new StudentNoticeReadAllResult(markedCount, unreadCount, at);
    }

    private List<NoticeBindingScope> resolveScopesForQuery(Long bindingId, Long userId, LocalDateTime at)
    {
        if (bindingId != null)
        {
            StudentAnnouncementAccess access = resolveAccess(bindingId, userId, at);
            if (!access.authority().active())
            {
                throw new NoticeAccessDeniedException();
            }
            requireMembershipWindow(access);
            return List.of(scopeOf(access));
        }
        return resolveActiveScopes(userId, at);
    }

    private List<NoticeBindingScope> resolveActiveScopes(Long userId, LocalDateTime at)
    {
        requirePort();
        List<StudentAnnouncementAccess> accesses;
        try
        {
            accesses = authorizationPort.listActiveBindings(userId, at);
        }
        catch (RuntimeException e)
        {
            throw new NoticeAuthorizationUnavailableException("学生通知授权查询失败", e);
        }
        if (accesses == null)
        {
            throw new NoticeAuthorizationUnavailableException("学生通知授权结果不完整");
        }

        LinkedHashMap<Long, NoticeBindingScope> scopes = new LinkedHashMap<>();
        for (StudentAnnouncementAccess access : accesses)
        {
            validateAccessShape(access);
            if (!access.authority().active())
            {
                continue;
            }
            requireMembershipWindow(access);
            NoticeBindingScope scope = scopeOf(access);
            NoticeBindingScope existing = scopes.putIfAbsent(scope.getBindingId(), scope);
            if (existing != null && !existing.equals(scope))
            {
                throw new NoticeAuthorizationUnavailableException("学生通知授权结果不一致");
            }
        }
        return List.copyOf(scopes.values());
    }

    private NoticeBindingScope resolveVisibleScope(Long bindingId, Long userId, LocalDateTime at)
    {
        StudentAnnouncementAccess access = resolveAccess(bindingId, userId, at);
        if (!access.authority().active())
        {
            return null;
        }
        requireMembershipWindow(access);
        return scopeOf(access);
    }

    private StudentAnnouncementAccess resolveAccess(Long bindingId, Long userId, LocalDateTime at)
    {
        requirePort();
        StudentAnnouncementAccess access;
        try
        {
            access = authorizationPort.resolve(bindingId, userId, at);
        }
        catch (RuntimeException e)
        {
            throw new NoticeAuthorizationUnavailableException("学生通知授权查询失败", e);
        }
        validateAccessShape(access);
        if (!bindingId.equals(access.bindingId()))
        {
            throw new NoticeAuthorizationUnavailableException("学生通知授权结果不完整");
        }
        return access;
    }

    private void validateAccessShape(StudentAnnouncementAccess access)
    {
        if (access == null
                || access.bindingId() == null || access.bindingId() <= 0
                || access.teachingClassId() == null || access.teachingClassId() <= 0
                || access.authority() == null)
        {
            throw new NoticeAuthorizationUnavailableException("学生通知授权结果不完整");
        }
    }

    /**
     * 只有有效成员才需要入班区间；明确非成员先按 403/404/跳过处理，
     * 不能因为非成员快照没有 joinedAt 而把明确拒绝误报成 503。
     */
    private void requireMembershipWindow(StudentAnnouncementAccess access)
    {
        if (access.joinedAt() == null)
        {
            throw new NoticeAuthorizationUnavailableException("学生通知授权结果不完整");
        }
        if (access.leftAt() != null && !access.leftAt().isAfter(access.joinedAt()))
        {
            throw new NoticeAuthorizationUnavailableException("学生通知授权结果不完整");
        }
    }

    private NoticeBindingScope scopeOf(StudentAnnouncementAccess access)
    {
        return new NoticeBindingScope(access.bindingId(),
                DatabaseTimePrecision.toMillisecondPrecision(access.joinedAt()),
                DatabaseTimePrecision.toMillisecondPrecision(access.leftAt()));
    }

    private void requirePort()
    {
        if (authorizationPort == null)
        {
            throw new NoticeAuthorizationUnavailableException("学生通知授权服务尚未接入");
        }
    }

    private StudentNoticeQuery query(Long userId, LocalDateTime at, List<NoticeBindingScope> scopes,
            NoticeKind kind, boolean unreadOnly, long offset, int limit)
    {
        StudentNoticeQuery query = new StudentNoticeQuery();
        query.setRecipientUserId(userId);
        query.setAt(at);
        query.setBindings(scopes);
        query.setKind(kind);
        query.setUnreadOnly(unreadOnly);
        query.setOffset(offset);
        query.setLimit(limit);
        return query;
    }

    private long countVisible(StudentNoticeQuery query)
    {
        try
        {
            return noticeMapper.countVisible(query);
        }
        catch (DataAccessException e)
        {
            throw new NoticePersistenceException("学生通知统计失败", e);
        }
    }

    /**
     * 全部已读后的剩余未读统计必须使用当前读。MySQL REPEATABLE READ 下
     * 普通 COUNT 会复用授权适配器在同一事务里更早建立的快照，可能把其他
     * 事务已提交的已读记录继续算作未读。
     */
    private long countVisibleForUpdate(StudentNoticeQuery query)
    {
        try
        {
            return noticeMapper.selectVisibleUnreadIdsForUpdate(query).size();
        }
        catch (DataAccessException e)
        {
            throw new NoticePersistenceException("学生通知统计失败", e);
        }
    }

    private List<StudentNotice> selectVisiblePage(StudentNoticeQuery query)
    {
        try
        {
            return noticeMapper.selectVisiblePage(query);
        }
        catch (DataAccessException e)
        {
            throw new NoticePersistenceException("学生通知列表读取失败", e);
        }
    }

    private StudentNotice selectByIdAndRecipient(Long noticeId, Long userId)
    {
        try
        {
            return noticeMapper.selectByIdAndRecipient(noticeId, userId);
        }
        catch (DataAccessException e)
        {
            throw new NoticePersistenceException("学生通知读取失败", e);
        }
    }

    private StudentNotice selectVisibleById(Long noticeId, StudentNoticeQuery query)
    {
        try
        {
            return noticeMapper.selectVisibleById(noticeId, query);
        }
        catch (DataAccessException e)
        {
            throw new NoticePersistenceException("学生通知读取失败", e);
        }
    }

    private StudentNotice selectVisibleByIdForUpdate(Long noticeId, StudentNoticeQuery query)
    {
        try
        {
            return noticeMapper.selectVisibleByIdForUpdate(noticeId, query);
        }
        catch (DataAccessException e)
        {
            throw new NoticePersistenceException("学生通知状态读取失败", e);
        }
    }

    private int updateMarkRead(Long noticeId, LocalDateTime readAt, StudentNoticeQuery query)
    {
        try
        {
            return noticeMapper.markRead(noticeId, readAt, query);
        }
        catch (DataAccessException e)
        {
            throw new NoticePersistenceException("学生通知标记已读失败", e);
        }
    }

    private int updateMarkAllRead(LocalDateTime operatedAt, StudentNoticeQuery query)
    {
        try
        {
            return noticeMapper.markAllRead(operatedAt, query);
        }
        catch (DataAccessException e)
        {
            throw new NoticePersistenceException("学生通知全部标记已读失败", e);
        }
    }

    private Long currentUserId()
    {
        Long userId = SecurityUtils.getUserId();
        if (userId == null)
        {
            throw new ServiceException("获取用户ID异常", HttpStatus.UNAUTHORIZED);
        }
        return userId;
    }

    private LocalDateTime now()
    {
        return DatabaseTimePrecision.toMillisecondPrecision(LocalDateTime.now(clock));
    }

    private void validateNoticeId(Long noticeId)
    {
        if (noticeId == null || noticeId <= 0)
        {
            throw new NoticeValidationException("noticeId 必须是正整数");
        }
    }

    private void validatePage(int pageNum, int pageSize)
    {
        if (pageNum < 1)
        {
            throw new NoticeValidationException("pageNum 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100)
        {
            throw new NoticeValidationException("pageSize 必须在 1 到 100 之间");
        }
    }
}
