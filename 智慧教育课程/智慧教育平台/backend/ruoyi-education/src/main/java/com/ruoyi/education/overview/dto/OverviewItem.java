package com.ruoyi.education.overview.dto;

import java.math.BigDecimal;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 总览聚合项。
 *
 * <p>状态与值必须一致：{@code OK} 必须有值；{@code NO_DATA} 和
 * {@code UNAVAILABLE} 必须没有值。该不变式用于阻止“无数据写 0”或
 * “源失败写 0”的降级。</p>
 *
 * @param metricCode 稳定指标编码，不能为空
 * @param value      指标值；{@code OK} 时非 null，其余状态必须为 null
 * @param unit       单位，如 COUNT/PERCENT/SCORE
 * @param status     三态状态
 * @param source     来源领域，如 course/classroom/assignment/grade/notice
 * @param sampleCount 参与计算的样本数；不适用时为 null
 * @param actionPath 前端下钻相对路径；无下钻时为 null
 */
public record OverviewItem(
        String metricCode,
        BigDecimal value,
        String unit,
        OverviewStatus status,
        String source,
        Long sampleCount,
        String actionPath)
{
    /**
     * 校验聚合项字段与状态组合。
     *
     * @throws OverviewValidationException 字段为空或状态与值组合非法
     */
    public OverviewItem
    {
        if (metricCode == null || metricCode.isBlank())
        {
            throw new OverviewValidationException("metricCode 不能为空");
        }
        if (unit == null || unit.isBlank())
        {
            throw new OverviewValidationException("unit 不能为空");
        }
        if (status == null)
        {
            throw new OverviewValidationException("status 不能为空");
        }
        if (source == null || source.isBlank())
        {
            throw new OverviewValidationException("source 不能为空");
        }
        if (sampleCount != null && sampleCount < 0)
        {
            throw new OverviewValidationException("sampleCount 不能为负数");
        }
        if (status == OverviewStatus.OK && value == null)
        {
            throw new OverviewValidationException("OK 状态必须提供 value");
        }
        if (status != OverviewStatus.OK && value != null)
        {
            throw new OverviewValidationException("NO_DATA/UNAVAILABLE 状态禁止提供 value");
        }
    }

    /**
     * 创建有数据的聚合项。
     *
     * @param metricCode 指标编码
     * @param value      真实值，可以为 0
     * @param unit       单位
     * @param source     来源领域
     * @param sampleCount 样本数，可为 null
     * @param actionPath 下钻路径，可为 null
     * @return OK 状态聚合项
     * @throws OverviewValidationException 字段或状态组合非法
     */
    public static OverviewItem ok(String metricCode, BigDecimal value, String unit,
            String source, Long sampleCount, String actionPath)
    {
        return new OverviewItem(metricCode, value, unit, OverviewStatus.OK,
                source, sampleCount, actionPath);
    }

    /**
     * 创建无数据聚合项。
     *
     * @param metricCode 指标编码
     * @param unit       单位
     * @param source     来源领域
     * @param actionPath 下钻路径，可为 null
     * @return NO_DATA 状态聚合项，value 固定为 null
     * @throws OverviewValidationException 字段非法
     */
    public static OverviewItem noData(String metricCode, String unit, String source,
            String actionPath)
    {
        return new OverviewItem(metricCode, null, unit, OverviewStatus.NO_DATA,
                source, null, actionPath);
    }

    /**
     * 创建源不可用聚合项。
     *
     * @param metricCode 指标编码
     * @param unit       单位
     * @param source     来源领域
     * @param actionPath 下钻路径，可为 null
     * @return UNAVAILABLE 状态聚合项，value 固定为 null
     * @throws OverviewValidationException 字段非法
     */
    public static OverviewItem unavailable(String metricCode, String unit, String source,
            String actionPath)
    {
        return new OverviewItem(metricCode, null, unit, OverviewStatus.UNAVAILABLE,
                source, null, actionPath);
    }
}
