package com.ruoyi.education.course.service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
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
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.exception.AnnouncementAccessDeniedException;
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.AnnouncementNotFoundException;
import com.ruoyi.education.course.exception.AnnouncementPersistenceException;
import com.ruoyi.education.course.exception.AnnouncementValidationException;
import com.ruoyi.education.course.mapper.CourseAnnouncementMapper;
import com.ruoyi.education.course.policy.AnnouncementStatePolicy;
import com.ruoyi.education.course.support.DatabaseTimePrecision;

/**
 * 学生公告只读服务。
 *
 * <p>学生身份只来自认证上下文；每次请求只取一次服务端 UTC 时间，
 * 并用同一时刻完成成员授权和公告可见性判断。</p>
 */
@Service
public class StudentAnnouncementService
{
    private final CourseAnnouncementMapper announcementMapper;

    private final StudentAnnouncementAuthorizationPort authorizationPort;

    private final Clock clock;

    @Autowired
    public StudentAnnouncementService(CourseAnnouncementMapper announcementMapper,
            ObjectProvider<StudentAnnouncementAuthorizationPort> authorizationPortProvider)
    {
        this(announcementMapper, authorizationPortProvider.getIfAvailable(), Clock.systemUTC());
    }

    StudentAnnouncementService(CourseAnnouncementMapper announcementMapper,
            StudentAnnouncementAuthorizationPort authorizationPort, Clock clock)
    {
        this.announcementMapper = Objects.requireNonNull(announcementMapper, "announcementMapper");
        this.authorizationPort = authorizationPort;
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    @Transactional(readOnly = true)
    public CourseAnnouncementPage list(Long bindingId, int pageNum, int pageSize)
    {
        validatePage(pageNum, pageSize);
        Long userId = currentUserId();
        LocalDateTime at = now();

        List<Long> bindingIds;
        if (bindingId == null)
        {
            List<StudentAnnouncementAccess> accesses = requireActiveStudentAccesses(userId, at);
            if (accesses.isEmpty())
            {
                return new CourseAnnouncementPage(0, List.of());
            }

            LinkedHashSet<Long> activeBindingIds = new LinkedHashSet<>();
            for (StudentAnnouncementAccess access : accesses)
            {
                if (access.authority().active())
                {
                    activeBindingIds.add(access.bindingId());
                }
            }
            if (activeBindingIds.isEmpty())
            {
                return new CourseAnnouncementPage(0, List.of());
            }
            bindingIds = List.copyOf(activeBindingIds);
        }
        else
        {
            validateBindingId(bindingId);
            StudentAnnouncementAccess access = requireStudentAccess(bindingId, userId, at);
            if (!access.authority().active())
            {
                throw new AnnouncementAccessDeniedException();
            }
            bindingIds = List.of(bindingId);
        }

        long total = countVisibleByBindingIds(bindingIds, at);
        if (total == 0)
        {
            return new CourseAnnouncementPage(0, List.of());
        }
        long offset = ((long) pageNum - 1) * pageSize;
        return new CourseAnnouncementPage(total,
                selectVisibleByBindingIdsPage(bindingIds, at, offset, pageSize));
    }

    @Transactional(readOnly = true)
    public CourseAnnouncement get(Long announcementId)
    {
        validateAnnouncementId(announcementId);
        Long userId = currentUserId();
        LocalDateTime at = now();

        CourseAnnouncement announcement = selectVisibleById(announcementId, at);
        if (announcement == null)
        {
            throw new AnnouncementNotFoundException();
        }

        StudentAnnouncementAccess access = requireStudentAccess(announcement.getBindingId(), userId, at);
        if (!access.authority().active()
                || !AnnouncementStatePolicy.canStudentRead(
                        announcement.getStatus(), announcement.getOpensAt(), announcement.getClosesAt(), at))
        {
            throw new AnnouncementNotFoundException();
        }
        return announcement;
    }

    private List<StudentAnnouncementAccess> requireActiveStudentAccesses(Long userId, LocalDateTime at)
    {
        requireAuthorizationPort();
        List<StudentAnnouncementAccess> accesses;
        try
        {
            accesses = authorizationPort.listActiveBindings(userId, at);
        }
        catch (RuntimeException e)
        {
            throw new AnnouncementAuthorizationUnavailableException("学生教学班授权查询失败", e);
        }
        if (accesses == null)
        {
            throw new AnnouncementAuthorizationUnavailableException("学生教学班授权结果不完整");
        }
        for (StudentAnnouncementAccess access : accesses)
        {
            validateStudentAccess(access);
        }
        return accesses;
    }

    private StudentAnnouncementAccess requireStudentAccess(Long bindingId, Long userId, LocalDateTime at)
    {
        requireAuthorizationPort();
        StudentAnnouncementAccess access;
        try
        {
            access = authorizationPort.resolve(bindingId, userId, at);
        }
        catch (RuntimeException e)
        {
            throw new AnnouncementAuthorizationUnavailableException("学生教学班授权查询失败", e);
        }
        validateStudentAccess(access);
        if (!bindingId.equals(access.bindingId()))
        {
            throw new AnnouncementAuthorizationUnavailableException("学生教学班授权结果不完整");
        }
        return access;
    }

    private void requireAuthorizationPort()
    {
        if (authorizationPort == null)
        {
            throw new AnnouncementAuthorizationUnavailableException("学生教学班授权服务尚未接入");
        }
    }

    private void validateStudentAccess(StudentAnnouncementAccess access)
    {
        if (access == null
                || access.bindingId() == null
                || access.bindingId() <= 0
                || access.teachingClassId() == null
                || access.teachingClassId() <= 0
                || access.authority() == null)
        {
            throw new AnnouncementAuthorizationUnavailableException("学生教学班授权结果不完整");
        }
    }

    private long countVisibleByBindingIds(List<Long> bindingIds, LocalDateTime at)
    {
        try
        {
            return announcementMapper.countVisibleByBindingIds(bindingIds, at);
        }
        catch (DataAccessException e)
        {
            throw persistenceFailure("学生公告列表统计失败", e);
        }
    }

    private List<CourseAnnouncement> selectVisibleByBindingIdsPage(List<Long> bindingIds,
            LocalDateTime at, long offset, int limit)
    {
        try
        {
            return announcementMapper.selectVisibleByBindingIdsPage(bindingIds, at, offset, limit);
        }
        catch (DataAccessException e)
        {
            throw persistenceFailure("学生公告列表读取失败", e);
        }
    }

    private CourseAnnouncement selectVisibleById(Long announcementId, LocalDateTime at)
    {
        try
        {
            return announcementMapper.selectVisibleById(announcementId, at);
        }
        catch (DataAccessException e)
        {
            throw persistenceFailure("学生公告读取失败", e);
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

    private void validateBindingId(Long bindingId)
    {
        if (bindingId == null || bindingId <= 0)
        {
            throw new AnnouncementValidationException("bindingId 必须是正整数");
        }
    }

    private void validateAnnouncementId(Long announcementId)
    {
        if (announcementId == null || announcementId <= 0)
        {
            throw new AnnouncementValidationException("announcementId 必须是正整数");
        }
    }

    private void validatePage(int pageNum, int pageSize)
    {
        if (pageNum < 1)
        {
            throw new AnnouncementValidationException("pageNum 不能小于 1");
        }
        if (pageSize < 1 || pageSize > 100)
        {
            throw new AnnouncementValidationException("pageSize 必须在 1 到 100 之间");
        }
    }

    private AnnouncementPersistenceException persistenceFailure(String message, Throwable cause)
    {
        return new AnnouncementPersistenceException(message, cause);
    }
}
