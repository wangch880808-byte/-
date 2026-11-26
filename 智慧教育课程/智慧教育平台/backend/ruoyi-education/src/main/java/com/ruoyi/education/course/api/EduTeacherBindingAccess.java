package com.ruoyi.education.course.api;

import java.time.Instant;

/**
 * 教师在某个课程—教学班绑定下、指定时点 at 的任课与发布权限快照（教师公告等消费方使用）。
 *
 * <p>数据来源：edu_course_class_binding（published_at/ended_at）、edu_teaching_class（status/name/code，
 * 均为当前库值）、edu_class_teacher（valid_from/valid_to/can_publish）、
 * edu_course_teacher（课程团队 fallback，joined_at/ended_at）、v_identity_user（用户标识）。
 * 每次调用现查库，不缓存；快照只用于当次判定，不能当作永久授权。</p>
 *
 * <p>时间口径（半开区间，均为 UTC、毫秒）：
 * <ul>
 *   <li>bindingActive：published_at &lt;= at 且（ended_at 为空或 &gt; at）；</li>
 *   <li>currentTeacher：任课授权段覆盖 at —— 班级任课段（valid_from &lt;= at 且 valid_to 为空或 &gt; at）
 *       或课程团队段（joined_at &lt;= at 且 ended_at 为空或 &gt; at，且绑定覆盖 at、
 *       课程当前未删除/未归档、账号当前持有教师角色）；与 bindingActive 相互独立，
 *       与 {@link EduCourseAccessService#membership} 的现口径一致；</li>
 *   <li>classOpen：教学班 status 的<b>当前库值</b>为 ACTIVE。status 没有生效/失效时间列，
 *       不做历史回溯，与 at 无关；</li>
 *   <li>canPublish：发布权限位没有历史版本，取覆盖 at 的那条任课记录的当前存储值。
 *       命中班级任课段时为该段的 can_publish；命中课程团队 fallback 时恒为 false；
 *       非任课教师为 null。</li>
 * </ul></p>
 *
 * @param bindingId         绑定记录 ID（edu_course_class_binding.id）
 * @param teachingClassId   教学班 ID
 * @param teachingClassName 教学班名称（当前库值）
 * @param teachingClassCode 教学班编码（当前库值）
 * @param courseSpaceId     课程空间 ID
 * @param bindingPublishedAt 绑定发布时间（UTC、毫秒）
 * @param bindingEndedAt    绑定结束时间（UTC、毫秒）；未结束为 null
 * @param userId            账号 ID（sys_user.user_id）
 * @param userName          登录名（v_identity_user）
 * @param nickName          姓名（v_identity_user）
 * @param bindingActive     绑定在 at 是否有效
 * @param classOpen         教学班当前状态是否为 ACTIVE（当前库值，与 at 无关）
 * @param currentTeacher    用户在 at 是否为本绑定对应教学班的有效任课教师
 * @param teacherRole       命中任课记录的角色（LEAD / CO_TEACHER）；非任课教师为 null
 * @param canPublish        是否可发布（见类注释口径）；非任课教师为 null
 */
public record EduTeacherBindingAccess(
        Long bindingId,
        Long teachingClassId,
        String teachingClassName,
        String teachingClassCode,
        Long courseSpaceId,
        Instant bindingPublishedAt,
        Instant bindingEndedAt,
        Long userId,
        String userName,
        String nickName,
        boolean bindingActive,
        boolean classOpen,
        boolean currentTeacher,
        String teacherRole,
        Boolean canPublish) {
}
