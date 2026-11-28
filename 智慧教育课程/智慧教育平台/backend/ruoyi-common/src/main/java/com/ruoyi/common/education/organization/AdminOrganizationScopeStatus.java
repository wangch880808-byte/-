package com.ruoyi.common.education.organization;

/**
 * 管理员组织范围来源状态。
 *
 * <p>OK 表示按当前登录人和权限查到了明确的学校范围；NO_DATA 表示授权成功但
 * 范围确实为空；UNAVAILABLE 表示授权来源或数据库查询失败。三者不能互相
 * 代替，尤其不能把 UNAVAILABLE 降级成 0。</p>
 */
public enum AdminOrganizationScopeStatus
{
    OK,
    NO_DATA,
    UNAVAILABLE
}
