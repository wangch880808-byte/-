package com.ruoyi.education.overview.security;

/**
 * 总览可信身份与组织范围提供者。
 *
 * <p>控制器通过本接口从服务端认证上下文取用户/组织，不接受请求参数传入的
 * userId/studentId。测试可替换为固定身份；生产实现见
 * {@link SecurityOverviewIdentityProvider}。</p>
 */
public interface OverviewIdentityProvider
{
    /**
     * 返回当前登录用户 ID。
     *
     * @return 当前用户 ID
     */
    Long currentUserId();

    /**
     * 返回当前管理员获授权的组织 ID。
     *
     * @return 组织 ID；未配置时为 null
     */
    Long currentOrganizationId();
}
