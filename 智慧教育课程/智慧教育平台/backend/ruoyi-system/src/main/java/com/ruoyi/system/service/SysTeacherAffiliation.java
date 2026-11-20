package com.ruoyi.system.service;

import java.util.Arrays;
import java.util.Objects;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.SysDeptType;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.mapper.SysUserMapper;

/** 校验教师账号的组织归属。任课关系独立于账号所在学院。 */
@Service
public class SysTeacherAffiliation
{
    private final SysDeptMapper departments;
    private final SysRoleMapper roles;
    private final SysUserMapper users;

    public SysTeacherAffiliation(SysDeptMapper departments, SysRoleMapper roles, SysUserMapper users)
    {
        this.departments = departments;
        this.roles = roles;
        this.users = users;
    }

    public void checkUserEdit(SysUser user)
    {
        if (!includesTeacher(user.getRoleIds())) return;
        SysUser current = users.selectUserById(user.getUserId());
        // 若依把 0 当作不修改部门。
        Long departmentId = Long.valueOf(0).equals(user.getDeptId()) && current != null
                ? current.getDeptId() : user.getDeptId();
        if (current != null && Objects.equals(departmentId, current.getDeptId()) && alreadyTeacher(current)) return;
        requireCollege(departmentId);
    }

    public void checkRoleGrant(Long userId, Long[] roleIds)
    {
        if (!includesTeacher(roleIds)) return;
        SysUser current = users.selectUserById(userId);
        if (current != null && alreadyTeacher(current)) return;
        requireCollege(current == null ? null : current.getDeptId());
    }

    private boolean includesTeacher(Long[] roleIds)
    {
        return roleIds != null && Arrays.stream(roleIds)
                .filter(Objects::nonNull)
                .map(roles::selectRoleById)
                .anyMatch(SysTeacherAffiliation::isTeacherRole);
    }

    private static boolean alreadyTeacher(SysUser user)
    {
        return user.getRoles() != null && user.getRoles().stream().anyMatch(SysTeacherAffiliation::isTeacherRole);
    }

    private static boolean isTeacherRole(SysRole role)
    {
        return role != null && "teacher".equals(role.getRoleKey()) && !"2".equals(role.getDelFlag());
    }

    public void requireCollege(Long deptId)
    {
        SysDept dept = deptId == null ? null : departments.selectDeptById(deptId);
        SysDept school = enabled(dept) && dept.getParentId() != null && dept.getParentId() != 0
                ? departments.selectDeptById(dept.getParentId()) : null;
        if (!enabled(school) || !Long.valueOf(0).equals(school.getParentId())
                || SysDeptType.effectiveType(school, null) != SysDeptType.SCHOOL
                || SysDeptType.effectiveType(dept, school) != SysDeptType.COLLEGE)
        {
            throw new ServiceException("教师账号的部门须选择已开启的学院，请先调整所属学院", 400);
        }
    }

    private static boolean enabled(SysDept dept)
    {
        return dept != null && "0".equals(dept.getDelFlag()) && UserConstants.DEPT_NORMAL.equals(dept.getStatus());
    }
}
