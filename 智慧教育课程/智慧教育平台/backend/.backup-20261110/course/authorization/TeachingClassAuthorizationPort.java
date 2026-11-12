package com.ruoyi.education.course.authorization;

import java.time.LocalDateTime;

/**
 * 教学班授权适配端口。
 *
 * <p>实现方必须在服务端校验绑定、教学班、任课有效期和发布权限。
 * 返回 null、抛异常或返回与请求 bindingId 不一致的结果时，公告服务
 * 按授权不可用或拒绝处理，不能放行。</p>
 */
public interface TeachingClassAuthorizationPort
{
    TeacherAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at);
}
