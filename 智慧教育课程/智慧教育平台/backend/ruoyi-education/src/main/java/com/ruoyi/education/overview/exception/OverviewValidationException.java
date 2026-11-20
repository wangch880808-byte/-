package com.ruoyi.education.overview.exception;

/**
 * 总览契约参数或状态组合非法。
 *
 * <p>用于时间窗、指标编码、状态与值组合等契约校验；不用于数据源失败。
 * 数据源失败由聚合层映射为 {@code UNAVAILABLE}，不抛本异常。</p>
 */
public class OverviewValidationException extends OverviewException
{
    private static final long serialVersionUID = 1L;

    /**
     * 创建参数校验异常。
     *
     * @param message 面向调用方的中文错误信息
     */
    public OverviewValidationException(String message)
    {
        super(message, 400);
    }
}
