package com.ruoyi.education.course.api;

/**
 * 课程空间快照。
 *
 * @param courseSpaceId  课程空间 ID
 * @param title          课程名称
 * @param ownerUserId    课程负责人（建课教师）
 * @param organizationId 所属组织
 * @param status         DRAFT / ACTIVE / ARCHIVED，只有 ACTIVE 对学生可见
 */
public record EduCourseRef(
        Long courseSpaceId,
        String title,
        Long ownerUserId,
        Long organizationId,
        String status) {

    public boolean published() {
        return "ACTIVE".equals(status);
    }
}
