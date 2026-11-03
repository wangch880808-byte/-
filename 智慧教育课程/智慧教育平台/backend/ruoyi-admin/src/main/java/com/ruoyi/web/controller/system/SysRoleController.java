package com.ruoyi.web.controller.system;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysMenu;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.framework.web.service.SysPermissionService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.domain.SysUserRole;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysMenuService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 角色信息
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/role")
public class SysRoleController extends BaseController
{
    @Autowired
    private ISysRoleService roleService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SysPermissionService permissionService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private ISysMenuService menuService;

    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/list")
    public TableDataInfo list(SysRole role)
    {
        startPage();
        List<SysRole> list = roleService.selectRoleList(role);
        return getDataTable(list);
    }

    @Log(title = "角色管理", businessType = BusinessType.EXPORT)
    @PreAuthorize("@ss.hasPermi('system:role:export')")
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysRole role)
    {
        List<SysRole> list = roleService.selectRoleList(role);
        ExcelUtil<SysRole> util = new ExcelUtil<SysRole>(SysRole.class);
        util.exportExcel(response, list, "角色数据");
    }

    /**
     * 根据角色编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping(value = "/{roleId}")
    public AjaxResult getInfo(@PathVariable Long roleId)
    {
        roleService.checkRoleDataScope(roleId);
        return success(roleService.selectRoleById(roleId));
    }

    /**
     * 新增角色
     */
    @PreAuthorize("@ss.hasPermi('system:role:add')")
    @Log(title = "角色管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysRole role)
    {
        checkNotAdminRoleKey(role);
        checkMenuSubset(role);
        if (!roleService.checkRoleNameUnique(role))
        {
            return error("新增角色'" + role.getRoleName() + "'失败，角色名称已存在");
        }
        else if (!roleService.checkRoleKeyUnique(role))
        {
            return error("新增角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setCreateBy(getUsername());
        return toAjax(roleService.insertRole(role));

    }

    /**
     * 修改保存角色
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysRole role)
    {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        checkNotAdminRoleKey(role);
        checkMenuSubset(role);
        if (!roleService.checkRoleNameUnique(role))
        {
            return error("修改角色'" + role.getRoleName() + "'失败，角色名称已存在");
        }
        else if (!roleService.checkRoleKeyUnique(role))
        {
            return error("修改角色'" + role.getRoleName() + "'失败，角色权限已存在");
        }
        role.setUpdateBy(getUsername());
        
        if (roleService.updateRole(role) > 0)
        {
            // 刷新所有持有该角色的在线用户权限
            tokenService.refreshPermissionByRoleId(role.getRoleId(), permissionService);
            return success();
        }
        return error("修改角色'" + role.getRoleName() + "'失败，请联系管理员");
    }

    /**
     * 修改保存数据权限
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/dataScope")
    public AjaxResult dataScope(@RequestBody SysRole role)
    {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        checkNotAdminRoleKey(role);
        int rows = roleService.authDataScope(role);
        if (rows > 0) tokenService.invalidateRoleSessions(role.getRoleId());
        return toAjax(rows);
    }

    /**
     * 状态修改
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.UPDATE)
    @PutMapping("/changeStatus")
    public AjaxResult changeStatus(@RequestBody SysRole role)
    {
        roleService.checkRoleAllowed(role);
        roleService.checkRoleDataScope(role.getRoleId());
        checkNotAdminRoleKey(role);
        role.setUpdateBy(getUsername());
        int rows = roleService.updateRoleStatus(role);
        if (rows > 0)
        {
            // 角色停用/恢复后，刷新所有持有该角色的在线用户权限
            tokenService.refreshPermissionByRoleId(role.getRoleId(), permissionService);
        }
        return toAjax(rows);
    }

    /**
     * 删除角色
     */
    @PreAuthorize("@ss.hasPermi('system:role:remove')")
    @Log(title = "角色管理", businessType = BusinessType.DELETE)
    @DeleteMapping("/{roleIds}")
    public AjaxResult remove(@PathVariable Long[] roleIds)
    {
        return toAjax(roleService.deleteRoleByIds(roleIds));
    }

    /**
     * 获取角色选择框列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping("/optionselect")
    public AjaxResult optionselect()
    {
        return success(roleService.selectRoleAll());
    }

    /**
     * 查询已分配用户角色列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/authUser/allocatedList")
    public TableDataInfo allocatedList(SysUser user)
    {
        startPage();
        List<SysUser> list = userService.selectAllocatedList(user);
        return getDataTable(list);
    }

    /**
     * 查询未分配用户角色列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:list')")
    @GetMapping("/authUser/unallocatedList")
    public TableDataInfo unallocatedList(SysUser user)
    {
        startPage();
        List<SysUser> list = userService.selectUnallocatedList(user);
        return getDataTable(list);
    }

    /**
     * 取消授权用户
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancel")
    public AjaxResult cancelAuthUser(@RequestBody SysUserRole userRole)
    {
        checkRoleAssignment(userRole.getRoleId(), new Long[] { userRole.getUserId() });
        int rows = roleService.deleteAuthUser(userRole);
        // 授权变更后旧令牌立即失效
        if (rows > 0)
        {
            tokenService.deleteByUserId(userRole.getUserId());
        }
        return toAjax(rows);
    }

    /**
     * 批量取消授权用户
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/cancelAll")
    public AjaxResult cancelAuthUserAll(Long roleId, Long[] userIds)
    {
        checkRoleAssignment(roleId, userIds);
        int rows = roleService.deleteAuthUsers(roleId, userIds);
        // 授权变更后旧令牌立即失效
        if (rows > 0)
        {
            for (Long userId : userIds)
            {
                tokenService.deleteByUserId(userId);
            }
        }
        return toAjax(rows);
    }

    /**
     * 批量选择用户授权
     */
    @PreAuthorize("@ss.hasPermi('system:role:edit')")
    @Log(title = "角色管理", businessType = BusinessType.GRANT)
    @PutMapping("/authUser/selectAll")
    public AjaxResult selectAuthUserAll(Long roleId, Long[] userIds)
    {
        checkRoleAssignment(roleId, userIds);
        int rows = roleService.insertAuthUsers(roleId, userIds);
        // 授权变更后旧令牌立即失效，重新登录后按新角色生效
        if (rows > 0)
        {
            for (Long userId : userIds)
            {
                tokenService.deleteByUserId(userId);
            }
        }
        return toAjax(rows);
    }

    /**
     * 获取对应角色部门树列表
     */
    @PreAuthorize("@ss.hasPermi('system:role:query')")
    @GetMapping(value = "/deptTree/{roleId}")
    public AjaxResult deptTree(@PathVariable("roleId") Long roleId)
    {
        roleService.checkRoleDataScope(roleId);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("checkedKeys", deptService.selectDeptListByRoleId(roleId));
        ajax.put("depts", deptService.selectDeptTreeList(new SysDept()));
        return ajax;
    }

    /**
     * 提权防护：非超管不得新建或操作 role_key=admin 的超级管理员角色
     */
    private void checkRoleAssignment(Long roleId, Long[] userIds)
    {
        if (roleId == null || userIds == null || userIds.length == 0)
        {
            throw new ServiceException("请选择角色和用户");
        }
        roleService.checkRoleDataScope(roleId);
        SysRole role = roleService.selectRoleById(roleId);
        if (role == null) throw new ServiceException("角色不存在");
        checkNotAdminRoleKey(role);
        for (Long userId : userIds)
        {
            if (userId == null) throw new ServiceException("用户编号不能为空");
            userService.checkUserDataScope(userId);
            if (!SecurityUtils.isAdmin())
            {
                SysUser user = new SysUser();
                user.setUserId(userId);
                userService.checkUserAllowed(user);
                if (userId.equals(SecurityUtils.getUserId()))
                {
                    throw new com.ruoyi.common.exception.ForbiddenException("不允许修改自己的角色");
                }
            }
        }
    }

    private void checkNotAdminRoleKey(SysRole role)
    {
        if (SecurityUtils.isAdmin())
        {
            return;
        }
        if (StringUtils.isNotNull(role.getRoleId()))
        {
            SysRole dbRole = roleService.selectRoleById(role.getRoleId());
            if (dbRole != null && Constants.SUPER_ADMIN.equals(dbRole.getRoleKey()))
            {
                throw new com.ruoyi.common.exception.ForbiddenException("不允许操作超级管理员角色");
            }
        }
        if (Constants.SUPER_ADMIN.equals(role.getRoleKey()))
        {
            throw new com.ruoyi.common.exception.ForbiddenException("不允许创建超级管理员角色");
        }
    }

    /**
     * 提权防护：非超管新增/编辑角色时，授予的菜单必须是本人拥有菜单的子集
     */
    private void checkMenuSubset(SysRole role)
    {
        if (SecurityUtils.isAdmin() || StringUtils.isEmpty(role.getMenuIds()))
        {
            return;
        }
        Set<Long> ownedMenuIds = menuService.selectMenuList(SecurityUtils.getUserId()).stream()
                .map(SysMenu::getMenuId).collect(Collectors.toSet());
        for (Long menuId : role.getMenuIds())
        {
            if (!ownedMenuIds.contains(menuId))
            {
                throw new com.ruoyi.common.exception.ForbiddenException("无权授予超出自身范围的菜单权限");
            }
        }
    }
}
