package com.ruoyi.common.education.organization;

import java.time.Instant;
import java.util.List;
import java.util.Objects;
import com.ruoyi.common.exception.ServiceException;

/**
 * 管理员组织范围快照。
 *
 * <p>该对象应在请求线程捕获，之后可安全传给异步线程；范围本身不再依赖
 * {@code RequestContextHolder} 或 {@code PermissionContextHolder}。调用方必须
 * 在授权范围内查询业务数据，不能把空范围当成全量，也不能把 UNAVAILABLE
 * 当成 NO_DATA。</p>
 *
 * @param status        来源状态
 * @param userId        捕获时的当前用户；无可信登录身份时可为 null
 * @param organizations 仅学校节点的授权范围；非 OK 时为空
 * @param asOf          捕获时间，UTC
 * @param reason        UNAVAILABLE 时的简短原因；其他状态为 null
 */
public record AdminOrganizationScope(
        AdminOrganizationScopeStatus status,
        Long userId,
        List<AdminOrganizationRef> organizations,
        Instant asOf,
        String reason)
{
    /**
     * 校验状态、用户、范围和原因的组合。
     *
     * @throws ServiceException 字段组合不合法
     */
    public AdminOrganizationScope
    {
        if (status == null)
        {
            throw new ServiceException("组织范围状态不能为空");
        }
        if (asOf == null)
        {
            throw new ServiceException("组织范围 asOf 不能为空");
        }
        if (organizations == null || organizations.stream().anyMatch(Objects::isNull))
        {
            throw new ServiceException("组织范围列表不能为 null 或包含 null");
        }
        if (status == AdminOrganizationScopeStatus.OK)
        {
            if (userId == null || organizations.isEmpty() || reason != null)
            {
                throw new ServiceException("OK 状态必须有用户和至少一个学校范围");
            }
        }
        else if (!organizations.isEmpty())
        {
            throw new ServiceException("NO_DATA/UNAVAILABLE 不得返回学校范围");
        }
        else if (status == AdminOrganizationScopeStatus.NO_DATA && userId == null)
        {
            throw new ServiceException("NO_DATA 状态必须有用户");
        }
        else if (status == AdminOrganizationScopeStatus.UNAVAILABLE
                && (reason == null || reason.isBlank()))
        {
            throw new ServiceException("UNAVAILABLE 状态必须提供原因");
        }
        organizations = List.copyOf(organizations);
    }

    /**
     * @param userId        当前用户
     * @param organizations 明确授权的学校范围
     * @param asOf          捕获时间
     * @return OK 快照
     */
    public static AdminOrganizationScope ok(Long userId, List<AdminOrganizationRef> organizations,
            Instant asOf)
    {
        return new AdminOrganizationScope(AdminOrganizationScopeStatus.OK, userId,
                organizations, asOf, null);
    }

    /**
     * @param userId 当前用户
     * @param asOf   捕获时间
     * @return 授权成功但没有学校范围
     */
    public static AdminOrganizationScope noData(Long userId, Instant asOf)
    {
        return new AdminOrganizationScope(AdminOrganizationScopeStatus.NO_DATA, userId,
                List.of(), asOf, null);
    }

    /**
     * @param userId 当前用户；无登录身份时可为 null
     * @param asOf   捕获时间
     * @param reason 简短原因，不包含 SQL 或凭据
     * @return 来源不可用
     */
    public static AdminOrganizationScope unavailable(Long userId, Instant asOf, String reason)
    {
        return new AdminOrganizationScope(AdminOrganizationScopeStatus.UNAVAILABLE, userId,
                List.of(), asOf, reason);
    }

    /**
     * @return 学校部门 ID 列表；非 OK 时为空
     */
    public List<Long> organizationIds()
    {
        return organizations.stream().map(AdminOrganizationRef::deptId).toList();
    }
}
