package com.ruoyi.education.course.authorization;

import java.util.Objects;
import com.ruoyi.education.course.domain.TeacherAnnouncementAuthority;

/**
 * 教师在指定课程—教学班绑定下的公告授权快照。
 *
 * <p>该对象由教学班授权适配层在服务端当前时间下解析，公告模块不直接
 * 查询任课、成员或绑定的私有表。复用 {@link TeacherAnnouncementAuthority}
 * 承载当前任课、绑定、教学班和发布权限。</p>
 */
public record TeacherAnnouncementAccess(
        Long bindingId,
        Long teachingClassId,
        TeacherAnnouncementAuthority authority)
{
    public TeacherAnnouncementAccess
    {
        Objects.requireNonNull(authority, "authority");
    }
}
