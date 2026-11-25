package com.ruoyi.education.course.api;

/**
 * 当前登录人在某个教学班中的身份与权限位。
 *
 * <p>数据来源是 edu_class_teacher（valid_from 已到、valid_to 为空或未到）与 edu_class_student
 * （joined_at 已到、left_at 为空或未到），判定均为半开区间 [start, end)，
 * 每次请求现查，不做缓存；撤权、退班后下一次请求立即失效。</p>
 *
 * @param classId    教学班 ID
 * @param userId     当前登录人 ID
 * @param teacher    是否为有效任课教师
 * @param student    是否为有效在班学生
 * @param teacherRole LEAD / ASSIST / GUEST，非教师为 null
 * @param canPublish 是否可发布资源/任务
 * @param canGrade   是否可批改与确认成绩
 * @param canExport  是否可导出
 * @param validTo    授权截止日期 yyyy-MM-dd，长期有效为 null
 */
public record EduClassMembership(
        Long classId,
        Long userId,
        boolean teacher,
        boolean student,
        String teacherRole,
        boolean canPublish,
        boolean canGrade,
        boolean canExport,
        String validTo) {
}
