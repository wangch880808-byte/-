package com.ruoyi.education.overview.dto;

/**
 * 总览聚合项状态。
 *
 * <p>三态语义是契约核心：无数据与源不可用都不能伪装成数值 0。
 * 状态与 {@code value} 的组合由 {@link OverviewItem} 强制校验。</p>
 */
public enum OverviewStatus
{
    /**
     * 数据源可用且返回有效结果；{@code value} 可以为 0，表示真实测量值。
     */
    OK,

    /**
     * 数据源可用，但没有证据、样本或分母；{@code value} 必须为 null。
     */
    NO_DATA,

    /**
     * 适配器缺失、超时、异常或契约违约；{@code value} 必须为 null。
     */
    UNAVAILABLE
}
