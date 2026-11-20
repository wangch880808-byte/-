package com.ruoyi.education.overview.support;

import com.ruoyi.education.overview.dto.OverviewStatus;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 单个聚合数据源的查询结果。
 *
 * <p>这是内部结果对象，不直接序列化给前端；调用方按状态映射为
 * {@code OverviewItem}。OK 必须有值；NO_DATA/UNAVAILABLE 必须没有值，
 * 从类型层面阻止“无数据写 0”。</p>
 *
 * @param source        来源领域
 * @param status        三态状态
 * @param value         数据源结果；仅 OK 时非 null
 * @param failureReason 失败原因，仅 UNAVAILABLE 时非空；只写日志，不返回前端
 * @param <T>           数据源结果类型
 */
public record OverviewSourceResult<T>(
        String source,
        OverviewStatus status,
        T value,
        String failureReason)
{
    /**
     * 校验来源、状态与值组合。
     *
     * @throws OverviewValidationException 字段为空或状态与值组合非法
     */
    public OverviewSourceResult
    {
        if (source == null || source.isBlank())
        {
            throw new OverviewValidationException("source 不能为空");
        }
        if (status == null)
        {
            throw new OverviewValidationException("status 不能为空");
        }
        if (status == OverviewStatus.OK && value == null)
        {
            throw new OverviewValidationException("OK 状态必须提供 value");
        }
        if (status != OverviewStatus.OK && value != null)
        {
            throw new OverviewValidationException("NO_DATA/UNAVAILABLE 状态禁止提供 value");
        }
        if (status == OverviewStatus.UNAVAILABLE
                && (failureReason == null || failureReason.isBlank()))
        {
            throw new OverviewValidationException("UNAVAILABLE 状态必须提供失败原因");
        }
        if (status != OverviewStatus.UNAVAILABLE && failureReason != null)
        {
            throw new OverviewValidationException("仅 UNAVAILABLE 状态允许失败原因");
        }
    }

    /**
     * 创建成功结果。
     *
     * @param source 来源领域
     * @param value  非 null 结果
     * @param <T>    结果类型
     * @return OK 结果
     * @throws OverviewValidationException 参数非法
     */
    public static <T> OverviewSourceResult<T> ok(String source, T value)
    {
        return new OverviewSourceResult<>(source, OverviewStatus.OK, value, null);
    }

    /**
     * 创建无数据结果。
     *
     * @param source 来源领域
     * @param <T>    结果类型
     * @return NO_DATA 结果，value 固定为 null
     * @throws OverviewValidationException 来源为空
     */
    public static <T> OverviewSourceResult<T> noData(String source)
    {
        return new OverviewSourceResult<>(source, OverviewStatus.NO_DATA, null, null);
    }

    /**
     * 创建源不可用结果。
     *
     * @param source 来源领域
     * @param reason 失败原因，只用于服务端日志
     * @param <T>    结果类型
     * @return UNAVAILABLE 结果，value 固定为 null
     * @throws OverviewValidationException 来源或原因为空
     */
    public static <T> OverviewSourceResult<T> unavailable(String source, String reason)
    {
        return new OverviewSourceResult<>(source, OverviewStatus.UNAVAILABLE, null, reason);
    }
}
