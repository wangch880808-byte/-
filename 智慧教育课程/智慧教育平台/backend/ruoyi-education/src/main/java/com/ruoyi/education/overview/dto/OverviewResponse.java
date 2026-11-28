package com.ruoyi.education.overview.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 三端总览聚合响应。
 *
 * <p>{@code items} 不允许为空：没有数据时必须显式返回 NO_DATA 或
 * UNAVAILABLE 项，不能用空数组冒充。列表做不可变拷贝，避免调用方修改。</p>
 *
 * @param asOf   服务端计算截止时间，UTC
 * @param window 本次聚合使用的时间窗
 * @param items  聚合项，至少一项
 */
public record OverviewResponse(Instant asOf, OverviewWindow window, List<OverviewItem> items)
{
    /**
     * 校验响应并复制列表。
     *
     * @throws OverviewValidationException asOf/window 为空、items 为空或含 null
     */
    public OverviewResponse
    {
        if (asOf == null)
        {
            throw new OverviewValidationException("asOf 不能为空");
        }
        if (window == null)
        {
            throw new OverviewValidationException("window 不能为空");
        }
        if (items == null || items.isEmpty())
        {
            throw new OverviewValidationException("items 不能为空；无数据必须用 NO_DATA/UNAVAILABLE 项表达");
        }
        if (items.stream().anyMatch(Objects::isNull))
        {
            throw new OverviewValidationException("items 不能包含 null");
        }
        items = List.copyOf(items);
    }

    /**
     * 创建聚合响应。
     *
     * @param asOf   服务端计算截止时间
     * @param window 时间窗
     * @param items  聚合项，至少一项
     * @return 校验后的响应
     * @throws OverviewValidationException 参数非法
     */
    public static OverviewResponse of(Instant asOf, OverviewWindow window,
            List<OverviewItem> items)
    {
        return new OverviewResponse(asOf, window, items);
    }
}
