package com.ruoyi.education.course.api;

/**
 * 教学班有效成员快照（left_at IS NULL）。
 *
 * <p>用于生成作业目标名单、公告接收范围、签到名单。领域落库时应同时保存 classId 与 userId，
 * 并在每次读取时重新调用本契约校验，不能把该快照当成永久授权。</p>
 *
 * @param classId  教学班 ID
 * @param memberId 入班记录 ID（edu_class_student.id），同一学生多次入班会有多条历史记录
 * @param userId   学生账号 ID（sys_user.user_id）
 * @param userName 学号 / 登录名
 * @param nickName 姓名
 * @param joinedAt 入班日期 yyyy-MM-dd
 */
public record EduMemberRef(
        Long classId,
        Long memberId,
        Long userId,
        String userName,
        String nickName,
        String joinedAt) {
}
