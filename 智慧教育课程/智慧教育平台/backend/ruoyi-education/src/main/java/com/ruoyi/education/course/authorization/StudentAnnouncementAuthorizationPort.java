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
 * <p>TODO 生产学生授权适配器尚未接入；第 2 组提供绑定成员契约后解除。
 * 当前仅有接口和测试替身，公告 fan-out 在缺失适配器时按 503 失败关闭。</p>
 */
public interface StudentAnnouncementAuthorizationPort
{
    /**
     * 解析指定绑定下的学生权限。
     *
     * @param bindingId 课程—教学班绑定 ID，必须为正数
     * @param userId 学生用户 ID，必须为正数
     * @param at 服务端当前 UTC 时间；实现方必须以该时间判断有效期
     * @return 绑定、教学班和成员权限快照；无权限时返回不完整或非活动快照，
     *         无法判定时返回 null
     * @throws RuntimeException 上游授权查询失败时由实现方抛出，调用方映射为 503
     */
    StudentAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at);

    /**
     * 返回学生当前所有有效班级绑定；用于未传 bindingId 的合并列表。
     *
     * @param userId 学生用户 ID，必须为正数
     * @param at 服务端当前 UTC 时间
     * @return 当前有效绑定快照；无绑定时返回空列表，无法判定时返回 null
     * @throws RuntimeException 上游授权查询失败时由实现方抛出，调用方映射为 503
     */
    List<StudentAnnouncementAccess> listActiveBindings(Long userId, LocalDateTime at);

    /**
     * 返回指定绑定下当前有效学生成员及入班区间，用于公告发布 fan-out。
     *
     * <p>实现方必须使用服务端时间校验成员、绑定和教学班状态；只返回
     * 当前有效学生。返回 null、抛异常、跨绑定或字段不完整时，调用方按
     * 503 失败关闭，不得放行或伪造成员。</p>
     *
     * @param bindingId 课程—教学班绑定 ID，必须为正数
     * @param at 服务端当前 UTC 时间；用于判断 joinedAt/leftAt 和绑定状态
     * @return 当前有效学生成员列表；无成员时返回空列表，无法判定时返回 null
     * @throws RuntimeException 上游授权查询失败时由实现方抛出，调用方映射为 503
     * TODO 生产适配器尚未实现；解除条件：第 2 组提供绑定成员查询契约并完成联调。
     */
    List<StudentAnnouncementMember> listActiveStudents(Long bindingId, LocalDateTime at);
}
