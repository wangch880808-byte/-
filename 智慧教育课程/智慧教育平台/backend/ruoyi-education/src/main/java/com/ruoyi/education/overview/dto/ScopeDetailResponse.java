package com.ruoyi.education.overview.dto;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 教师或学生授权范围明细响应。
 *
 * <p>该响应只用于解释总览中的范围指标，不替代课程或教学班业务页面。
 * {@code classCount} 采用与 {@code *.class.count} 相同的范围条数口径，
 * {@code studentCount} 采用与教师学生数相同的逐范围求和口径；任一范围缺少
 * 学生数时为 null，不能少算成真实总数。</p>
 *
 * <p>{@code status} 为 {@code OK} 时必须返回非空范围列表和与之相等的
 * {@code classCount}；{@code NO_DATA}/{@code UNAVAILABLE} 必须返回空列表且
 * 两个计数字段均为 null。空范围不能伪装成 0。</p>
 *
 * @param asOf         服务端计算截止时间，UTC
 * @param status       范围来源状态
 * @param classCount   范围条数，与总览班级数同口径；非 OK 为 null
 * @param studentCount 各范围学生数合计；学生端点或任一范围缺数时为 null
 * @param ranges       范围明细，不能为 null
 */
public record ScopeDetailResponse(
        Instant asOf,
        OverviewStatus status,
        Long classCount,
        Long studentCount,
        List<ScopeRangeResponse> ranges)
{
    /**
     * 校验状态、计数和明细组合。
     *
     * @throws OverviewValidationException 字段为空或状态与计数组合非法
     */
    public ScopeDetailResponse
    {
        if (asOf == null)
        {
            throw new OverviewValidationException("asOf 不能为空");
        }
        if (status == null)
        {
            throw new OverviewValidationException("status 不能为空");
        }
        if (ranges == null || ranges.stream().anyMatch(Objects::isNull))
        {
            throw new OverviewValidationException("ranges 不能为 null 或包含 null");
        }
        if (classCount != null && classCount < 0)
        {
            throw new OverviewValidationException("classCount 不能为负数");
        }
        if (studentCount != null && studentCount < 0)
        {
            throw new OverviewValidationException("studentCount 不能为负数");
        }
        if (status == OverviewStatus.OK)
        {
            if (classCount == null || classCount != ranges.size())
            {
                throw new OverviewValidationException("OK 状态 classCount 必须等于范围条数");
            }
        }
        else if (classCount != null || studentCount != null)
        {
            throw new OverviewValidationException("NO_DATA/UNAVAILABLE 状态禁止提供计数");
        }
        ranges = List.copyOf(ranges);
    }
}
