package com.ruoyi.education.course.api;

import java.time.Instant;

/**
 * 绑定下在指定时点 at 在班的单个学生成员段。
 *
 * <p>memberId 是入班记录 ID（edu_class_student.id），同一学生多次入班会有多条历史记录，
 * 每条记录对应一个 joinedAt/leftAt 段；本快照始终返回覆盖 at 的那一段（正常至多一段）。</p>
 *
 * @param memberId        入班记录 ID（edu_class_student.id）
 * @param teachingClassId 教学班 ID
 * @param userId          学生账号 ID（sys_user.user_id）
 * @param userName        学号 / 登录名
 * @param nickName        姓名
 * @param joinedAt        入班时间（UTC、毫秒）
 * @param leftAt          退班时间（UTC、毫秒）；在班为 null
 */
public record EduStudentMemberSegment(
        Long memberId,
        Long teachingClassId,
        Long userId,
        String userName,
        String nickName,
        Instant joinedAt,
        Instant leftAt) {
}
