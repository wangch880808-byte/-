package com.ruoyi.education.course.authorization;

import java.time.LocalDateTime;
import java.util.Objects;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;

/**
 * 教学班学生在指定课程—教学班绑定下的成员快照。
 *
 * <p>由学生授权适配层在服务端时间下解析，供公告 fan-out 判断接收人。
 * {@code userId}、{@code bindingId} 和 {@code joinedAt} 必须非空；
 * {@code leftAt} 为空表示当前未结束。调用方必须校验这些字段，
 * 不得把缺失结果当成“无入班下限”或放行。</p>
 *
 * @param userId 学生用户 ID，正整数
 * @param bindingId 课程—教学班绑定 ID，正整数
 * @param teachingClassId 教学班 ID，正整数
 * @param joinedAt 本段入班时间（UTC）
 * @param leftAt 本段退班时间（UTC，不含）；为空表示未退班
 * @param authority 当前成员、绑定和教学班状态快照
 */
public record StudentAnnouncementMember(
        Long userId,
        Long bindingId,
        Long teachingClassId,
        LocalDateTime joinedAt,
        LocalDateTime leftAt,
        StudentAnnouncementAuthority authority)
{
    public StudentAnnouncementMember
    {
        Objects.requireNonNull(authority, "authority");
    }
}
