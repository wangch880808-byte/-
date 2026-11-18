package com.ruoyi.system.service;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jakarta.validation.Validator;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.bean.BeanValidators;
import com.ruoyi.system.domain.SysRoleImportRow;

/** 先校验整批再创建普通角色；不从文件导入任何授权。 */
@Service
public class SysRoleImportService
{
    private final ISysRoleService roles;
    private final Validator validator;
    public SysRoleImportService(ISysRoleService roles, Validator validator)
    { this.roles = roles; this.validator = validator; }

    @Transactional(rollbackFor = Exception.class)
    public String importRoles(List<SysRoleImportRow> rows, String operator)
    {
        if (rows == null || rows.isEmpty()) throw new ServiceException("导入角色数据不能为空", 400);
        if (rows.size() > 1000) throw new ServiceException("每次最多导入 1000 个角色", 400);
        List<SysRole> prepared = new ArrayList<>();
        Set<String> names = new HashSet<>(), keys = new HashSet<>();
        int line = 1;
        for (SysRoleImportRow row : rows)
        {
            line++;
            try
            {
                if (row == null) throw new IllegalArgumentException("数据为空");
                SysRole role = new SysRole();
                role.setRoleName(trim(row.getRoleName())); role.setRoleKey(trim(row.getRoleKey()));
                if ("admin".equalsIgnoreCase(role.getRoleKey())) throw new IllegalArgumentException("不能导入超级管理员角色");
                String sort = trim(row.getRoleSort());
                int order = sort.isEmpty() ? 0 : new BigDecimal(sort).intValueExact();
                if (order < 0) throw new IllegalArgumentException("角色排序须为非负整数");
                role.setRoleSort(order);
                String status = trim(row.getStatus());
                if (status.isEmpty() || "0".equals(status) || "正常".equals(status)) role.setStatus("0");
                else if ("1".equals(status) || "停用".equals(status)) role.setStatus("1");
                else throw new IllegalArgumentException("角色状态须为0/正常或1/停用");
                role.setRemark(trim(row.getRemark()));
                if (role.getRemark().length() > 500) throw new IllegalArgumentException("备注不能超过500个字符");
                BeanValidators.validateWithException(validator, role);
                if (!names.add(role.getRoleName().toLowerCase(Locale.ROOT)) || !keys.add(role.getRoleKey().toLowerCase(Locale.ROOT)))
                    throw new IllegalArgumentException("文件中存在重复角色名称或权限字符");
                if (!roles.checkRoleNameUnique(role) || !roles.checkRoleKeyUnique(role))
                    throw new IllegalArgumentException("角色名称或权限字符已存在");
                role.setDataScope("5"); role.setMenuIds(new Long[0]); role.setCreateBy(operator);
                prepared.add(role);
            }
            catch (Exception error)
            {
                throw new ServiceException("第" + line + "行：" + error.getMessage(), 400);
            }
        }
        for (SysRole role : prepared)
            if (roles.insertRole(role) <= 0) throw new ServiceException("角色创建失败，本批次已回滚", 500);
        return "成功导入 " + prepared.size() + " 个角色，请在编辑角色中配置菜单权限。";
    }
    private static String trim(String value) { return value == null ? "" : value.trim(); }
}
