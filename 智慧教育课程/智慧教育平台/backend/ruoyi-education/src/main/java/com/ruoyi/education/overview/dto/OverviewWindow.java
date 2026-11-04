package com.ruoyi.education.overview.dto;

import java.time.Instant;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 聚合时间窗，左闭右开 {@code [startsAt, endsAt)}。
 *
 * <p>所有时间使用 UTC；服务端确定窗口并在响应中原样返回，客户端不得自行推算
 * “今天”或“当前学期”。</p>
 *
 * @param startsAt 窗口开始时间，含
 * @param endsAt   窗口结束时间，不含，必须晚于开始时间
 */
public record OverviewWindow(Instant startsAt, Instant endsAt)
{
    /**
     * 校验时间窗。
     *
     * @throws OverviewValidationException 时间为空或结束时间不晚于开始时间
     */
    public OverviewWindow
    {
        if (startsAt == null || endsAt == null)
        {
            throw new OverviewValidationException("总览时间窗不能为空");
        }
        if (!endsAt.isAfter(startsAt))
        {
            throw new OverviewValidationException("总览时间窗结束时间必须晚于开始时间");
        }
    }

    /**
     * 创建时间窗。
     *
     * @param startsAt 窗口开始时间，含
     * @param endsAt   窗口结束时间，不含
     * @return 校验后的时间窗
     * @throws OverviewValidationException 时间窗非法
     */
    public static OverviewWindow of(Instant startsAt, Instant endsAt)
    {
        return new OverviewWindow(startsAt, endsAt);
    }
}
