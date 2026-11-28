package com.ruoyi.education.course.api;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.ruoyi.common.exception.ServiceException;

/**
 * 管理员组织范围内的教学班/绑定/学生数快照。
 *
 * <p>该结果只供第六组管理员总览适配器使用。组织范围必须来自第一组
 * 请求线程捕获的 {@code AdminOrganizationScope}，不能由客户端指定。</p>
 *
 * <p>OK 时 {@code rangeCount} 等于 {@code ranges.size()}，且
 * {@code studentCount} 为各行学生数之和（未按教学班去重）；没有绑定或没有学生的
 * 范围也保留在列表中，学生数可以是真实 0。NO_DATA/UNAVAILABLE 时两个计数均为
 * null，不能当作 0。</p>
 *
 * @param status       范围查询状态
 * @param rangeCount   范围条数；非 OK 时为 null
 * @param studentCount 各行学生数合计；非 OK 时为 null
 * @param ranges       范围明细，非 OK 时为空
 * @param asOf         查询时间，UTC
 * @param reason       UNAVAILABLE 的简短原因；其他状态为 null
 */
public record EduAdminOrganizationScopeResult(
        EduAdminOrganizationScopeStatus status,
        Integer rangeCount,
        Long studentCount,
        List<EduAdminOrganizationClassRange> ranges,
        Instant asOf,
        String reason)
{
    /**
     * 校验状态与计数组合。
     *
     * @throws ServiceException 字段组合不合法
     */
    public EduAdminOrganizationScopeResult
    {
        if (status == null)
        {
            throw new ServiceException("范围查询状态不能为空");
        }
        if (asOf == null)
        {
            throw new ServiceException("范围查询 asOf 不能为空");
        }
        if (ranges == null || ranges.stream().anyMatch(Objects::isNull))
        {
            throw new ServiceException("范围列表不能为 null 或包含 null");
        }
        if (status == EduAdminOrganizationScopeStatus.OK)
        {
            if (ranges.isEmpty() || rangeCount == null || rangeCount != ranges.size()
                    || studentCount == null || studentCount < 0L || reason != null)
            {
                throw new ServiceException("OK 状态必须有范围、条数、学生合计且无原因");
            }
        }
        else if (!ranges.isEmpty() || rangeCount != null || studentCount != null
                || (status == EduAdminOrganizationScopeStatus.NO_DATA && reason != null))
        {
            throw new ServiceException("NO_DATA/UNAVAILABLE 不得返回范围、计数或原因");
        }
        else if (status == EduAdminOrganizationScopeStatus.UNAVAILABLE
                && (reason == null || reason.isBlank()))
        {
            throw new ServiceException("UNAVAILABLE 状态必须提供原因");
        }
        ranges = List.copyOf(ranges);
    }

    /**
     * @param ranges       非空范围
     * @param studentCount 各范围学生数合计
     * @param asOf         查询时间
     * @return OK 结果
     */
    public static EduAdminOrganizationScopeResult ok(
            List<EduAdminOrganizationClassRange> ranges, long studentCount, Instant asOf)
    {
        return new EduAdminOrganizationScopeResult(EduAdminOrganizationScopeStatus.OK,
                ranges.size(), studentCount, ranges, asOf, null);
    }

    /**
     * @param asOf 查询时间
     * @return 授权组织内确实没有范围
     */
    public static EduAdminOrganizationScopeResult noData(Instant asOf)
    {
        return new EduAdminOrganizationScopeResult(EduAdminOrganizationScopeStatus.NO_DATA,
                null, null, List.of(), asOf, null);
    }

    /**
     * @param asOf   查询时间
     * @param reason 简短原因，不包含 SQL 或凭据
     * @return 查询失败
     */
    public static EduAdminOrganizationScopeResult unavailable(Instant asOf, String reason)
    {
        return new EduAdminOrganizationScopeResult(EduAdminOrganizationScopeStatus.UNAVAILABLE,
                null, null, List.of(), asOf, reason);
    }
}
