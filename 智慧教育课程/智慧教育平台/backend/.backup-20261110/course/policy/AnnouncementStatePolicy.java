package com.ruoyi.education.course.policy;

import java.time.LocalDateTime;
import java.util.Objects;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.AnnouncementVisibility;
import com.ruoyi.education.course.domain.TeacherAnnouncementAuthority;

/**
 * 教学班公告的纯状态规则。
 *
 * <p>本类不查询数据库、不依赖 Spring，也不负责教学班成员或任课授权；
 * 调用方必须先在服务端解析 {@link TeacherAnnouncementAuthority}。</p>
 */
public final class AnnouncementStatePolicy
{
    private AnnouncementStatePolicy()
    {
    }

    public static boolean canCreateDraft(TeacherAnnouncementAuthority authority)
    {
        return isActiveClassTeacher(authority) && authority.canPublish();
    }

    /**
     * 仅适用于已知调用者具有 canPublish 的场景。
     * 需要“创建者可编辑自己草稿”的完整规则时使用四参数重载。
     */
    public static boolean canEditDraft(TeacherAnnouncementAuthority authority, AnnouncementStatus status)
    {
        return canCreateDraft(authority) && status == AnnouncementStatus.DRAFT;
    }

    /**
     * 编辑草稿的完整规则：具有 canPublish 的教师可编辑本班草稿，
     * 创建者即使没有 canPublish 也可编辑自己的草稿。
     */
    public static boolean canEditDraft(TeacherAnnouncementAuthority authority, AnnouncementStatus status,
            Long createdByUserId, Long currentUserId)
    {
        if (!isActiveClassTeacher(authority) || status != AnnouncementStatus.DRAFT)
        {
            return false;
        }
        return authority.canPublish()
                || (currentUserId != null && currentUserId.equals(createdByUserId));
    }

    public static boolean canPublish(TeacherAnnouncementAuthority authority, AnnouncementStatus status)
    {
        return canCreateDraft(authority) && status == AnnouncementStatus.DRAFT;
    }

    public static boolean canArchive(TeacherAnnouncementAuthority authority, AnnouncementStatus status)
    {
        return canCreateDraft(authority) && status == AnnouncementStatus.PUBLISHED;
    }

    public static boolean canReadTeacher(TeacherAnnouncementAuthority authority)
    {
        return isActiveClassTeacher(authority);
    }

    public static boolean canStudentRead(AnnouncementStatus status, LocalDateTime opensAt,
            LocalDateTime closesAt, LocalDateTime now)
    {
        return visibility(status, opensAt, closesAt, now) == AnnouncementVisibility.OPEN;
    }

    /**
     * 计算公告在给定服务端时刻的可见状态。
     *
     * <p>opens_at 为包含边界，closes_at 为不包含边界。DRAFT 和 ARCHIVED
     * 不依赖时间字段；PUBLISHED 缺少 opens_at 时按未开放处理，避免把
     * 不完整数据误判为可读。</p>
     */
    public static AnnouncementVisibility visibility(AnnouncementStatus status,
            LocalDateTime opensAt, LocalDateTime closesAt, LocalDateTime now)
    {
        Objects.requireNonNull(status, "status");
        Objects.requireNonNull(now, "now");

        return switch (status)
        {
            case DRAFT -> AnnouncementVisibility.DRAFT;
            case ARCHIVED -> AnnouncementVisibility.ARCHIVED;
            case PUBLISHED ->
            {
                if (opensAt == null || now.isBefore(opensAt))
                {
                    yield AnnouncementVisibility.NOT_YET_OPEN;
                }
                if (closesAt != null && !now.isBefore(closesAt))
                {
                    yield AnnouncementVisibility.CLOSED;
                }
                yield AnnouncementVisibility.OPEN;
            }
        };
    }

    private static boolean isActiveClassTeacher(TeacherAnnouncementAuthority authority)
    {
        return authority != null
                && authority.currentTeacher()
                && authority.bindingActive()
                && authority.teachingClassActive();
    }
}
