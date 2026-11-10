package com.ruoyi.education.course.authorization;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 学生公告授权适配端口。
 *
 * <p>实现方必须使用服务端时间校验学生角色/成员关系、绑定和教学班状态。
 * 返回 null、抛异常、返回与请求 bindingId 不一致或字段不完整的结果时，
 * 公告服务按授权不可用或拒绝处理，不能放行。</p>
 */
public interface StudentAnnouncementAuthorizationPort
{
    /**
     * 解析指定绑定下的学生权限。
     */
    StudentAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at);

    /**
     * 返回学生当前所有有效班级绑定；用于未传 bindingId 的合并列表。
     * 返回 null 表示授权结果不可用。
     */
    List<StudentAnnouncementAccess> listActiveBindings(Long userId, LocalDateTime at);
}
