package com.ruoyi.education.course.authorization;

import java.time.LocalDateTime;
import java.util.Objects;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;

/**
 * 学生在指定课程—教学班绑定下的公告只读授权快照。
 *
 * <p>通知服务还需要 {@code joinedAt}/{@code leftAt} 判断通知可见时间是否
 * 落在当前入班区间内；公告服务不读取这两个字段。</p>
 */
public record StudentAnnouncementAccess(
        Long bindingId,
        Long teachingClassId,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        StudentAnnouncementAuthority authority)
{
    public StudentAnnouncementAccess
    {
        Objects.requireNonNull(authority, "authority");
    }

    /**
     * 兼容仅需要绑定与权限快照的公告调用方。
     */
    public StudentAnnouncementAccess(Long bindingId, Long teachingClassId,
            StudentAnnouncementAuthority authority)
    {
        this(bindingId, teachingClassId, null, null, authority);
    }
}
