package com.ruoyi.education.course.domain;

/**
 * 学生在某个教学班公告下的授权快照。
 *
 * <p>由学生教学班授权适配层在服务端当前时间下解析，不在本模块内
 * 直接查询成员或绑定表。</p>
 *
 * @param currentStudent      当前是否为该教学班有效学生
 * @param bindingActive       课程—教学班绑定是否未结束
 * @param teachingClassActive 教学班是否处于 ACTIVE 状态
 */
public record StudentAnnouncementAuthority(
        boolean currentStudent,
        boolean bindingActive,
        boolean teachingClassActive)
{
    public boolean active()
    {
        return currentStudent && bindingActive && teachingClassActive;
    }
}
