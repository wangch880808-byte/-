package com.ruoyi.education.overview.security;

import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 基于若依安全上下文的身份提供者。
 *
 * <p>用户 ID 来自 {@link SecurityUtils#getUserId()}，组织范围来自
 * {@link SecurityUtils#getDeptId()}；不读取客户端请求参数。</p>
 *
 * <p>TODO：第 1 组配置 `education:overview:*` 菜单/角色权限后，补充真实登录、
 * 角色拦截和审计联调；解除条件为权限数据落库且真实 HTTP 登录可用。</p>
 */
@Component
public class SecurityOverviewIdentityProvider implements OverviewIdentityProvider
{
    /**
     * 返回当前登录用户 ID。
     *
     * @return 当前用户 ID
     */
    @Override
    public Long currentUserId()
    {
        return SecurityUtils.getUserId();
    }

    /**
     * 返回当前管理员组织 ID。
     *
     * @return 组织 ID；未配置时为 null
     */
    @Override
    public Long currentOrganizationId()
    {
        return SecurityUtils.getDeptId();
    }
}
