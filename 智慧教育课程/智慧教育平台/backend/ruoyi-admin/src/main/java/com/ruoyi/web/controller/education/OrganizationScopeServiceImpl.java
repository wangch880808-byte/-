package com.ruoyi.web.controller.education;

import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.domain.model.OrganizationScope;
import com.ruoyi.common.core.service.OrganizationScopeService;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import org.springframework.stereotype.Service;

/**
 * 复用 {@link EduSchoolScope} 的唯一权限规则，向业务模块暴露组织范围。
 */
@Service
public class OrganizationScopeServiceImpl implements OrganizationScopeService {
    private static final String DEFAULT_PERMISSION = "education:overview:admin";

    private final EduSchoolScope scope;

    public OrganizationScopeServiceImpl(EduSchoolScope scope) {
        this.scope = scope;
    }

    @Override
    public OrganizationScope currentScope(String permission) {
        String effectivePermission = StringUtils.isBlank(permission) ? DEFAULT_PERMISSION : permission;
        List<SysDept> visible = scope.visibleDepartments(effectivePermission);
        List<SysDept> schools = scope.schools(visible);
        LoginUser loginUser = loginUser();
        Long deptId = loginUser == null || loginUser.getUser() == null ? null : loginUser.getUser().getDeptId();
        boolean allSchools = isAllScope(loginUser);

        OrganizationScope result = new OrganizationScope();
        result.setPermission(effectivePermission);
        result.setDeptId(deptId);
        result.setSchoolId(scope.schoolIdOf(deptId));
        result.setAllSchools(allSchools);
        result.setScopeType(resolveScopeType(allSchools, visible, loginUser));
        result.setSchoolIds(schools.stream()
                .map(SysDept::getDeptId)
                .filter(Objects::nonNull)
                .toList());
        result.setOrganizationIds(visible.stream()
                .map(SysDept::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList());
        return result;
    }

    @Override
    public Long schoolIdOf(Long deptId) {
        return scope.schoolIdOf(deptId);
    }

    private LoginUser loginUser() {
        try {
            return SecurityUtils.getLoginUser();
        } catch (RuntimeException ignored) {
            return null;
        }
    }

    private boolean isAllScope(LoginUser loginUser) {
        if (loginUser == null || loginUser.getUser() == null) {
            return false;
        }
        if (SecurityUtils.isAdmin(loginUser.getUserId())) {
            return true;
        }
        List<SysRole> roles = loginUser.getUser().getRoles();
        return roles != null && roles.stream()
                .anyMatch(role -> role.isAdmin() || Constants.Dept.DATA_SCOPE_ALL.equals(role.getDataScope()));
    }

    private String resolveScopeType(boolean allSchools, List<SysDept> visible, LoginUser loginUser) {
        if (allSchools) {
            return OrganizationScope.TYPE_ALL;
        }
        if (visible == null || visible.isEmpty()) {
            return OrganizationScope.TYPE_NONE;
        }
        List<SysRole> roles = loginUser == null || loginUser.getUser() == null
                ? null : loginUser.getUser().getRoles();
        if (roles == null || roles.isEmpty()) {
            return OrganizationScope.TYPE_NONE;
        }
        Set<String> dataScopes = roles.stream()
                .map(SysRole::getDataScope)
                .filter(StringUtils::isNotEmpty)
                .collect(Collectors.toSet());
        if (dataScopes.size() == 1) {
            String dataScope = dataScopes.iterator().next();
            if (Constants.Dept.DATA_SCOPE_DEPT.equals(dataScope)) {
                return OrganizationScope.TYPE_DEPT;
            }
            if (Constants.Dept.DATA_SCOPE_DEPT_AND_CHILD.equals(dataScope)) {
                return OrganizationScope.TYPE_DEPT_AND_CHILD;
            }
            if (Constants.Dept.DATA_SCOPE_SELF.equals(dataScope)) {
                return OrganizationScope.TYPE_SELF;
            }
        }
        return OrganizationScope.TYPE_CUSTOM;
    }
}
