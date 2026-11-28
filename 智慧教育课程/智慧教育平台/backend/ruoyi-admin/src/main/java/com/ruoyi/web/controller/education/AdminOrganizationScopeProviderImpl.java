package com.ruoyi.web.controller.education;

import java.time.Instant;
import java.util.List;
import org.springframework.stereotype.Component;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.education.organization.AdminOrganizationRef;
import com.ruoyi.common.education.organization.AdminOrganizationScope;
import com.ruoyi.common.education.organization.AdminOrganizationScopeProvider;
import com.ruoyi.common.exception.ForbiddenException;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 第一组管理员组织范围公共契约的默认实现。
 *
 * <p>只在请求线程读取当前登录身份和部门数据权限；返回快照后，异步总览不再
 * 依赖 {@code PermissionContextHolder}。部门范围只保留权限直接覆盖的学校
 * 根节点，不把学院可见回溯成整校授权。</p>
 */
@Component
public class AdminOrganizationScopeProviderImpl implements AdminOrganizationScopeProvider
{
    private final EduSchoolScope schoolScope;

    /**
     * 创建提供者。
     *
     * @param schoolScope 第一组组织范围查询
     */
    public AdminOrganizationScopeProviderImpl(EduSchoolScope schoolScope)
    {
        this.schoolScope = schoolScope;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public AdminOrganizationScope captureCurrent()
    {
        Instant asOf = Instant.now();
        LoginUser loginUser;
        try
        {
            loginUser = SecurityUtils.getLoginUser();
        }
        catch (RuntimeException exception)
        {
            throw new ForbiddenException("缺少可信登录身份");
        }
        if (loginUser == null || loginUser.getUserId() == null)
        {
            throw new ForbiddenException("缺少可信登录身份");
        }
        Long userId = loginUser.getUserId();
        try
        {
            if (!SecurityUtils.hasPermi(ADMIN_OVERVIEW_PERMISSION))
            {
                throw new ForbiddenException("没有教育总览权限");
            }
        }
        catch (ForbiddenException exception)
        {
            throw exception;
        }
        catch (RuntimeException exception)
        {
            throw new ForbiddenException("缺少可信登录身份或权限");
        }

        try
        {
            List<SysDept> schools = schoolScope.authorizedSchools(ADMIN_OVERVIEW_PERMISSION);
            if (schools.isEmpty())
            {
                return AdminOrganizationScope.noData(userId, asOf);
            }
            List<AdminOrganizationRef> organizations = schools.stream()
                    .map(school -> new AdminOrganizationRef(school.getDeptId(),
                            school.getParentId(), school.getDeptName()))
                    .toList();
            return AdminOrganizationScope.ok(userId, organizations, asOf);
        }
        catch (ForbiddenException exception)
        {
            throw exception;
        }
        catch (RuntimeException exception)
        {
            // 含异步线程缺少 RequestContextHolder、部门来源异常或数据库失败；
            // 不把异常文本（可能含 SQL）返回给总览。
            return AdminOrganizationScope.unavailable(userId, asOf,
                    "管理员组织范围来源暂不可用");
        }
    }
}
