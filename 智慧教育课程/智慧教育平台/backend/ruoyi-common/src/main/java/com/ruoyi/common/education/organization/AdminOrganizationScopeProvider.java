package com.ruoyi.common.education.organization;

/**
 * 第一组提供的管理员组织范围公共只读契约。
 *
 * <p>接口按当前登录人和 {@code education:overview:admin} 权限计算“明确授权
 * 的学校节点”，不读取客户端传入的 userId、deptId 或 organizationId，也不把
 * 学院/专业可见推断为整校授权。</p>
 *
 * <h2>线程要求</h2>
 * <p>{@link #captureCurrent()} 必须在请求线程调用：若依部门数据权限依赖
 * {@code PermissionContextHolder}，而它存放在 {@code RequestContextHolder} 中，
 * 不会自动传播到总览的异步源线程。捕获得到的 {@link AdminOrganizationScope}
 * 是纯快照，可安全传给异步线程。若在异步线程调用，来源应返回
 * {@code UNAVAILABLE}，不得返回空范围或全量。</p>
 *
 * <h2>错误语义</h2>
 * <ul>
 *   <li>当前账号无 {@code education:overview:admin} 权限或无可信登录身份：
 *       {@link com.ruoyi.common.exception.ForbiddenException}；</li>
 *   <li>授权成功但没有任何明确学校范围：{@code NO_DATA}；</li>
 *   <li>部门授权来源或数据库查询失败：{@code UNAVAILABLE}。</li>
 * </ul>
 */
public interface AdminOrganizationScopeProvider
{
    /**
     * 管理员总览权限串。
     */
    String ADMIN_OVERVIEW_PERMISSION = "education:overview:admin";

    /**
     * 在请求线程捕获当前管理员可见的学校范围。
     *
     * @return 不可变范围快照
     * @throws com.ruoyi.common.exception.ForbiddenException 无权限或无可信身份
     */
    AdminOrganizationScope captureCurrent();
}
