package com.ruoyi.common.core.service;

import com.ruoyi.common.core.domain.model.OrganizationScope;

/**
 * 业务层可复用的组织范围服务。
 *
 * <p>该接口放在 {@code ruoyi-common}，业务模块只依赖接口，不直接访问
 * {@code ruoyi-admin} 或 {@code ruoyi-framework} 的内部实现。权限规则只在实现层维护一份。</p>
 */
public interface OrganizationScopeService {
    /**
     * 返回当前登录账号在指定权限下可访问的学校/组织范围。
     *
     * <p>建议管理员教学指标传入 {@code education:overview:admin}。实现会按该权限应用
     * 当前账号角色的若依数据权限。</p>
     *
     * @param permission 权限串，例如 {@code education:overview:admin}
     * @return 组织范围；不会返回 {@code null}
     */
    OrganizationScope currentScope(String permission);

    /**
     * 把任意部门 ID 映射到所属学校根部门 ID。
     *
     * @param deptId 部门 ID，可为空
     * @return 学校根部门 ID；无法归属学校时返回 {@code null}
     */
    Long schoolIdOf(Long deptId);
}
