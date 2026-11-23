package com.ruoyi.education.course.api;

import java.time.Instant;

/**
 * 学生在某个课程—教学班绑定下、指定时点 at 的成员段与可见性快照。
 *
 * <p>数据来源：edu_course_class_binding（published_at/ended_at）、edu_teaching_class.status、
 * edu_class_student（joined_at/left_at）。每次调用现查库，不缓存；快照只用于当次判定，
 * 不能当作永久授权。</p>
 *
 * <p>时间口径（半开区间，均为 UTC、毫秒）：
 * <ul>
 *   <li>bindingActive：published_at &lt;= at 且（ended_at 为空或 &gt; at）；</li>
 *   <li>active：bindingActive 且 joined_at &lt;= at 且（left_at 为空或 &gt; at）；</li>
 *   <li>classOpen：教学班 status 的<b>当前库值</b>为 ACTIVE。status 没有生效/失效时间列，
 *       不做历史回溯，与 at 无关。</li>
 * </ul></p>
 *
 * @param bindingId       绑定记录 ID（edu_course_class_binding.id）
 * @param teachingClassId 教学班 ID
 * @param courseSpaceId   课程空间 ID
 * @param userId          学生账号 ID（sys_user.user_id）
 * @param bindingActive   绑定在 at 是否有效
 * @param classOpen       教学班当前状态是否为 ACTIVE（当前库值，与 at 无关）
 * @param active          学生在该绑定下 at 时点是否有效在班
 * @param joinedAt        命中段入班时间（UTC、毫秒）；非有效在班为 null
 * @param leftAt          命中段退班时间（UTC、毫秒）；在班或非有效为 null
 */
public record EduStudentBindingAccess(
        Long bindingId,
        Long teachingClassId,
        Long courseSpaceId,
        Long userId,
        boolean bindingActive,
        boolean classOpen,
        boolean active,
        Instant joinedAt,
        Instant leftAt) {
}
