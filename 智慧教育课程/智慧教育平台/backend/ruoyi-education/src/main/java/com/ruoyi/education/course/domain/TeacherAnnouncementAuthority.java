package com.ruoyi.education.course.domain;

/**
 * 教师对某个教学班公告的授权快照。
 *
 * <p>由教学班授权服务在服务端当前时间下解析，不在本模块内直接查询
 * 其他领域的成员或任课表。</p>
 *
 * @param currentTeacher    当前是否为该教学班有效任课教师
 * @param canPublish        当前是否具备发布权限
 * @param bindingActive     课程—教学班绑定是否未结束
 * @param teachingClassActive 教学班是否处于 ACTIVE 状态
 */
public record TeacherAnnouncementAuthority(
        boolean currentTeacher,
        boolean canPublish,
        boolean bindingActive,
        boolean teachingClassActive)
{
}
