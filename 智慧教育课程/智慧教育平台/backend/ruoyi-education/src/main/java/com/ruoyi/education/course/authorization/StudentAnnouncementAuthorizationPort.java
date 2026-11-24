package com.ruoyi.education.course.authorization;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生公告授权适配端口。
 *
 * <p>实现方必须使用服务端时间校验学生角色/成员关系、绑定和教学班状态。
 * 返回 null、抛异常、返回与请求 bindingId 不一致或字段不完整的结果时，
 * 公告/通知服务按授权不可用或拒绝处理，不能放行。</p>
 *
 * <p>时间语义：读取/已读授权使用服务端当前 UTC 时间 {@code now}；
 * 按教学班投递的上游事件使用事件发生时间 {@code occurredAt}；
 * 实现方返回覆盖该 {@code at} 的成员段，并与 {@code [joinedAt, leftAt)}
 * 半开区间比较。教学班 status 没有生效/失效时间列，仍返回当前库值，
 * 不按历史时点回溯。</p>
 *
 * <p>TODO 生产装配与真实联调待确认：本分支已提供基于 #39
 * `EduMembershipTimelineService` 的候选适配器
 * {@code MembershipTimelineStudentAnnouncementAuthorizationAdapter} 及隔离测试。
 * 适配器通过 {@code ObjectProvider} 可选注入来源：缺源时应用上下文可创建，
 * 业务调用仍按授权不可用失败关闭。解除条件为 #39 合并、真实数据库/HTTP/
 * 角色/审计联调完成。公告 fan-out 在缺失适配器时仍按 503 失败关闭。</p>
 */
public interface StudentAnnouncementAuthorizationPort
{
    /**
     * 解析指定绑定下的学生权限。
     *
     * @param bindingId 课程—教学班绑定 ID，必须为正数
     * @param userId 学生用户 ID，必须为正数
     * @param at 读取/已读使用服务端当前 UTC 时间；按班投递的上游事件使用
     *        occurredAt；实现方返回覆盖该时点的成员段
     * @return 绑定、教学班和成员权限快照；无权限时返回非活动快照，
     *         无法判定时返回 null；返回用户必须与请求 userId 一致
     * @throws RuntimeException 上游授权查询失败时由实现方抛出，调用方映射为 503
     */
    StudentAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at);

    /**
     * 返回学生当前所有有效班级绑定；用于未传 bindingId 的合并列表。
     *
     * @param userId 学生用户 ID，必须为正数
     * @param at 读取/已读使用服务端当前 UTC 时间；实现方返回覆盖该时点的成员段
     * @return 当前有效绑定快照；返回用户必须与请求 userId 一致；
     *         无绑定时返回空列表，无法判定时返回 null
     * @throws RuntimeException 上游授权查询失败时由实现方抛出，调用方映射为 503
     */
    List<StudentAnnouncementAccess> listActiveBindings(Long userId, LocalDateTime at);

    /**
     * 返回指定绑定下当前有效学生成员及入班区间，用于公告发布 fan-out。
     *
     * <p>实现方必须使用服务端时间校验成员、绑定和教学班状态；按 {@code at}
     * 返回覆盖该时点的成员段，用于公告发布 fan-out。只返回该时点有效学生；
     * 教学班状态仍是当前库值，不随 at 回溯。返回 null、抛异常、跨绑定、
     * 用户/成员段不一致或字段不完整时，调用方按 503 失败关闭，不得放行或
     * 伪造成员。</p>
     *
     * @param bindingId 课程—教学班绑定 ID，必须为正数
     * @param at 读取/已读使用服务端当前 UTC 时间；按班投递的上游事件使用
     *        occurredAt；用于判断 joinedAt/leftAt、成员段和绑定状态
     * @return 覆盖该时点的有效学生成员列表；无成员时返回空列表，
     *         无法判定时返回 null
     * @throws RuntimeException 上游授权查询失败时由实现方抛出，调用方映射为 503
     * TODO 候选适配器已基于 #39 时点合同提供并隔离测试；解除条件：
     * #39 合并、真实数据库/HTTP/角色/审计联调完成。
     */
    List<StudentAnnouncementMember> listActiveStudents(Long bindingId, LocalDateTime at);
}
