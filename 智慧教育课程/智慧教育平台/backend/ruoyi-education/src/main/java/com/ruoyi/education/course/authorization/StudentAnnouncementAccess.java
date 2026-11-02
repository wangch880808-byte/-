package com.ruoyi.education.course.authorization;

import java.util.Objects;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;

/**
 * 学生在指定课程—教学班绑定下的公告只读授权快照。
 */
public record StudentAnnouncementAccess(
        Long bindingId,
        Long teachingClassId,
        StudentAnnouncementAuthority authority)
{
    public StudentAnnouncementAccess
    {
        Objects.requireNonNull(authority, "authority");
    }
}
