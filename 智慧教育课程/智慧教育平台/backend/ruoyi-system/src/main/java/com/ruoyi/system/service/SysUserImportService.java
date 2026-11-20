package com.ruoyi.system.service;

import java.util.List;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.bean.BeanValidators;
import com.ruoyi.system.domain.SysUserImportRow;

@Service
public class SysUserImportService
{
    private final ISysUserService userService;
    private final ISysDeptService deptService;
    private final ISysRoleService roleService;
    private final Validator validator;

    public SysUserImportService(ISysUserService userService, ISysDeptService deptService,
            ISysRoleService roleService, Validator validator)
    {
        this.userService = userService;
        this.deptService = deptService;
        this.roleService = roleService;
        this.validator = validator;
    }

    @Transactional(rollbackFor = Exception.class)
    public String importUsers(List<SysUserImportRow> rows, String operator)
    {
        if (rows.isEmpty()) throw invalid("导入用户数据不能为空");
        if (rows.size() > 1000) throw invalid("每次最多导入 1000 个用户");

        SysDept deptFilter = new SysDept();
        deptFilter.setStatus(UserConstants.DEPT_NORMAL);
        List<SysDept> departments = deptService.selectDeptList(deptFilter);
        SysRole roleFilter = new SysRole();
        roleFilter.setStatus(UserConstants.ROLE_NORMAL);
        boolean admin = SecurityUtils.isAdmin();
        List<SysRole> roles = roleService.selectRoleList(roleFilter).stream()
                .filter(role -> admin || (!role.isAdmin() && !"admin".equalsIgnoreCase(role.getRoleKey())))
                .toList();

        int position = 0;
        for (SysUserImportRow row : rows)
        {
            position++;
            try
            {
                SysUser user = toUser(row, departments, roles);
                BeanValidators.validateWithException(validator, user);
                deptService.checkDeptDataScope(user.getDeptId());
                roleService.checkRoleDataScope(user.getRoleIds());
                if (!userService.checkUserNameUnique(user)) throw invalid("账号已存在");
                if (!user.getPhonenumber().isEmpty() && !userService.checkPhoneUnique(user))
                    throw invalid("手机号已存在");
                if (!user.getEmail().isEmpty() && !userService.checkEmailUnique(user))
                    throw invalid("邮箱已存在");
                user.setCreateBy(operator);
                user.setPassword(SecurityUtils.encryptPassword(user.getPassword()));
                if (userService.insertUser(user) <= 0) throw invalid("账号创建失败");
            }
            catch (ServiceException | ConstraintViolationException error)
            {
                throw invalid("第 " + position + " 条数据：" + error.getMessage());
            }
        }
        return "成功导入 " + rows.size() + " 个用户";
    }

    private SysUser toUser(SysUserImportRow row, List<SysDept> departments, List<SysRole> roles)
    {
        SysUser user = new SysUser();
        user.setUserName(StringUtils.trimToEmpty(row.getUserName()));
        user.setNickName(StringUtils.trimToEmpty(row.getNickName()));
        user.setPhonenumber(StringUtils.trimToEmpty(row.getPhonenumber()));
        user.setEmail(StringUtils.trimToEmpty(row.getEmail()));
        user.setRemark(StringUtils.trimToEmpty(row.getRemark()));
        if (user.getUserName().length() < UserConstants.USERNAME_MIN_LENGTH) throw invalid("账号至少 2 个字符");
        if (user.getNickName().isEmpty()) throw invalid("请填写昵称");
        if (!user.getPhonenumber().isEmpty() && !user.getPhonenumber().matches("1[3-9]\\d{9}"))
            throw invalid("手机号格式不正确");
        if (user.getRemark().length() > 500) throw invalid("备注不能超过 500 个字符");

        String password = row.getPassword();
        if (password == null || password.isBlank() || password.length() < UserConstants.PASSWORD_MIN_LENGTH
                || password.length() > UserConstants.PASSWORD_MAX_LENGTH)
            throw invalid("初始密码需为 5-20 个字符");
        user.setPassword(password);
        user.setDeptId(resolveDepartment(row.getDept(), departments));
        user.setRoleIds(new Long[] { resolveRole(row.getRoles(), roles) });
        user.setSex(switch (StringUtils.trimToEmpty(row.getSex()))
        {
            case "", "0", "男" -> "0";
            case "1", "女" -> "1";
            case "2", "未知" -> "2";
            default -> throw invalid("性别请填写男、女或未知");
        });
        user.setStatus(switch (StringUtils.trimToEmpty(row.getStatus()))
        {
            case "", "0", "正常" -> UserConstants.NORMAL;
            case "1", "停用" -> UserConstants.USER_DISABLE;
            default -> throw invalid("状态请填写正常或停用");
        });
        return user;
    }

    private Long resolveDepartment(String value, List<SysDept> departments)
    {
        String name = StringUtils.trimToEmpty(value);
        if (name.isEmpty()) return null;
        List<SysDept> matches = departments.stream()
                .filter(dept -> name.equals(dept.getDeptName()) || name.equals(dept.getDeptId().toString()))
                .toList();
        if (matches.isEmpty()) throw invalid("部门不存在、已停用或超出可管理范围");
        if (matches.size() > 1) throw invalid("部门名称重复，请填写部门编号");
        return matches.get(0).getDeptId();
    }

    private Long resolveRole(String value, List<SysRole> roles)
    {
        String roleText = StringUtils.trimToEmpty(value);
        if (roleText.isEmpty()) throw invalid("请填写角色");
        List<SysRole> matches = roles.stream()
                .filter(role -> roleText.equals(role.getRoleName()) || roleText.equals(role.getRoleKey()))
                .toList();
        if (matches.size() != 1) throw invalid("每条新增用户只能填写一个可分配的角色名称或权限字符");
        return matches.get(0).getRoleId();
    }

    private ServiceException invalid(String message)
    {
        return new ServiceException(message, 400);
    }
}
