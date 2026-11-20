package com.ruoyi.education.course.api;

/**
 * 教学班有效任课教师快照（valid_to IS NULL）。
 *
 * <p>用于课程详情页展示教师团队、资源发布前的权限核对。跨领域不直接读
 * {@code edu_class_teacher}，统一通过本契约取得。</p>
 *
 * @param classId     教学班 ID
 * @param id          任课记录 ID（edu_class_teacher.id）
 * @param userId      教师账号 ID（sys_user.user_id）
 * @param userName    登录名
 * @param nickName    姓名
 * @param teacherRole LEAD / ASSIST / GUEST
 * @param canPublish  是否可发布资源/任务
 * @param canGrade    是否可批改与确认成绩
 * @param canExport   是否可导出
 * @param validTo     授权截止日期 yyyy-MM-dd，长期有效为 null
 */
public record EduTeacherRef(
        Long classId,
        Long id,
        Long userId,
        String userName,
        String nickName,
        String teacherRole,
        boolean canPublish,
        boolean canGrade,
        boolean canExport,
        String validTo) {
}
