package com.ruoyi.web.controller.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import com.ruoyi.common.constant.Constants;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.domain.model.OrganizationScope;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

class OrganizationScopeServiceImplTest {
    private final EduSchoolScope schoolScope = mock(EduSchoolScope.class);
    private final OrganizationScopeServiceImpl service = new OrganizationScopeServiceImpl(schoolScope);

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void returnsNoneWhenNoOrganizationVisible() {
        authenticate(2L, 11L, role(2L, Constants.Dept.DATA_SCOPE_CUSTOM));
        when(schoolScope.visibleDepartments("education:overview:admin")).thenReturn(List.of());
        when(schoolScope.schools(anyList())).thenReturn(List.of());
        when(schoolScope.schoolIdOf(11L)).thenReturn(1L);

        OrganizationScope scope = service.currentScope("education:overview:admin");

        assertEquals(OrganizationScope.TYPE_NONE, scope.getScopeType());
        assertFalse(scope.isAllSchools());
        assertTrue(scope.getSchoolIds().isEmpty());
        assertTrue(scope.getOrganizationIds().isEmpty());
    }

    @Test
    void returnsAllForSuperAdmin() {
        authenticate(1L, 1L, role(1L, Constants.Dept.DATA_SCOPE_ALL));
        SysDept school = dept(1L, 0L, "0");
        when(schoolScope.visibleDepartments("education:overview:admin")).thenReturn(List.of(school));
        when(schoolScope.schools(anyList())).thenReturn(List.of(school));
        when(schoolScope.schoolIdOf(1L)).thenReturn(1L);

        OrganizationScope scope = service.currentScope("education:overview:admin");

        assertEquals(OrganizationScope.TYPE_ALL, scope.getScopeType());
        assertTrue(scope.isAllSchools());
        assertEquals(List.of(1L), scope.getSchoolIds());
        assertEquals(List.of(1L), scope.getOrganizationIds());
    }

    @Test
    void returnsOrganizationIdsForNormalAdmin() {
        authenticate(2L, 11L, role(2L, Constants.Dept.DATA_SCOPE_DEPT));
        SysDept school = dept(1L, 0L, "0");
        SysDept college = dept(11L, 1L, "0,1");
        when(schoolScope.visibleDepartments("education:overview:admin")).thenReturn(List.of(school, college));
        when(schoolScope.schools(anyList())).thenReturn(List.of(school));
        when(schoolScope.schoolIdOf(11L)).thenReturn(1L);

        OrganizationScope scope = service.currentScope("education:overview:admin");

        assertEquals(OrganizationScope.TYPE_DEPT, scope.getScopeType());
        assertFalse(scope.isAllSchools());
        assertEquals(Long.valueOf(1L), scope.getSchoolId());
        assertEquals(List.of(1L), scope.getSchoolIds());
        assertEquals(List.of(1L, 11L), scope.getOrganizationIds());
    }

    private void authenticate(Long userId, Long deptId, SysRole role) {
        SysUser user = new SysUser();
        user.setUserId(userId);
        user.setDeptId(deptId);
        user.setRoles(List.of(role));
        LoginUser loginUser = new LoginUser(userId, deptId, user, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, null));
    }

    private SysRole role(Long roleId, String dataScope) {
        SysRole role = new SysRole();
        role.setRoleId(roleId);
        role.setDataScope(dataScope);
        role.setStatus("0");
        role.setPermissions(Set.of("education:overview:admin"));
        return role;
    }

    private SysDept dept(Long deptId, Long parentId, String ancestors) {
        SysDept dept = new SysDept();
        dept.setDeptId(deptId);
        dept.setParentId(parentId);
        dept.setAncestors(ancestors);
        dept.setDelFlag("0");
        return dept;
    }
}
