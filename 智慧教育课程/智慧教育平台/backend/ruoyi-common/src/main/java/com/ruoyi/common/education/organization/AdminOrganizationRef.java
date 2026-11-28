package com.ruoyi.common.education.organization;

import com.ruoyi.common.exception.ServiceException;

/**
 * 当前管理员获授权的学校组织快照。
 *
 * <p>只包含学校节点，不包含学院、专业或班级节点。调用方不得把学院可见
 * 推断成整校授权；如果授权数据只覆盖学院，本范围应为 NO_DATA，而不是
 * 返回学院所属学校。</p>
 *
 * @param deptId   学校部门 ID
 * @param parentId 父部门 ID；学校根节点为 0
 * @param deptName 学校名称
 */
public record AdminOrganizationRef(Long deptId, Long parentId, String deptName)
{
    /**
     * 校验学校节点字段。
     *
     * @throws ServiceException ID 为空或名称缺失
     */
    public AdminOrganizationRef
    {
        if (deptId == null || deptId <= 0L)
        {
            throw new ServiceException("学校 deptId 必须为正整数");
        }
        if (parentId == null || parentId < 0L)
        {
            throw new ServiceException("学校 parentId 不能为空或负数");
        }
        if (deptName == null || deptName.isBlank())
        {
            throw new ServiceException("学校名称不能为空");
        }
    }
}
