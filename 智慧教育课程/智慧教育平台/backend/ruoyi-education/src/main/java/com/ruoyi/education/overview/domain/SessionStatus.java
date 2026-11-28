package com.ruoyi.education.overview.domain;

import java.time.Instant;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 课堂会话聚合状态。
 *
 * <p>{@code status} 沿用课堂领域的状态字符串（PLANNED/RUNNING/PAUSED/ENDED），
 * 本模块不复制其枚举，避免跨组耦合。适配器负责把领域状态映射为稳定字符串。</p>
 *
 * @param bindingId   课程空间与教学班绑定 ID
 * @param sessionId   课堂会话 ID
 * @param lessonId    课次 ID，可为 null
 * @param status      会话状态
 * @param startedAt   开始时间，可为 null
 * @param lessonTitle 课次标题，可为 null
 */
public record SessionStatus(
        Long bindingId,
        Long sessionId,
        Long lessonId,
        String status,
        Instant startedAt,
        String lessonTitle)
{
    /**
     * 校验必要标识与状态。
     *
     * @throws OverviewValidationException 绑定/会话 ID 或状态为空
     */
    public SessionStatus
    {
        if (bindingId == null)
        {
            throw new OverviewValidationException("bindingId 不能为空");
        }
        if (sessionId == null)
        {
            throw new OverviewValidationException("sessionId 不能为空");
        }
        if (status == null || status.isBlank())
        {
            throw new OverviewValidationException("status 不能为空");
        }
    }
}
