package com.ruoyi.web.controller.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.education.organization.AdminOrganizationScope;
import com.ruoyi.common.education.organization.AdminOrganizationScopeStatus;
import com.ruoyi.common.exception.ForbiddenException;
import com.ruoyi.system.service.ISysDeptService;

class AdminOrganizationScopeProviderImplTest
{
    private static final String PERMISSION = "education:overview:admin";

    private EduSchoolScope schoolScope;

    private AdminOrganizationScopeProviderImpl provider;

    @BeforeEach
    void setUp()
    {
        schoolScope = mock(EduSchoolScope.class);
        provider = new AdminOrganizationScopeProviderImpl(schoolScope);
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void capturesExplicitSchoolsForCurrentUser()
    {
        login(Set.of(PERMISSION));
        when(schoolScope.authorizedSchools(PERMISSION)).thenReturn(List.of(
                school(920000000001L, "演示学校")));

        AdminOrganizationScope scope = provider.captureCurrent();

        assertEquals(AdminOrganizationScopeStatus.OK, scope.status());
        assertEquals(7L, scope.userId());
        assertEquals(List.of(920000000001L), scope.organizationIds());
        assertNotNull(scope.asOf());
        assertNull(scope.reason());
    }

    @Test
    void authorizedButNoExplicitSchoolIsNoData()
    {
        login(Set.of(PERMISSION));
        when(schoolScope.authorizedSchools(PERMISSION)).thenReturn(List.of());

        AdminOrganizationScope scope = provider.captureCurrent();

        assertEquals(AdminOrganizationScopeStatus.NO_DATA, scope.status());
        assertEquals(List.of(), scope.organizationIds());
        assertNull(scope.reason());
    }

    @Test
    void missingPermissionIsForbidden()
    {
        login(Set.of("education:course:list"));

        assertThrows(ForbiddenException.class, () -> provider.captureCurrent());
    }

    @Test
    void missingLoginIsForbidden()
    {
        assertThrows(ForbiddenException.class, () -> provider.captureCurrent());
    }

    @Test
    void sourceFailureIsUnavailableNotNoData()
    {
        login(Set.of(PERMISSION));
        when(schoolScope.authorizedSchools(PERMISSION))
                .thenThrow(new IllegalStateException("source down"));

        AdminOrganizationScope scope = provider.captureCurrent();

        assertEquals(AdminOrganizationScopeStatus.UNAVAILABLE, scope.status());
        assertEquals(7L, scope.userId());
        assertEquals(List.of(), scope.organizationIds());
        assertNotNull(scope.reason());
        assertTrue(scope.reason().contains("暂不可用"));
    }

    @Test
    void callingFromThreadWithoutRequestAttributesIsUnavailable()
    {
        login(Set.of(PERMISSION));
        RequestContextHolder.resetRequestAttributes();
        ISysDeptService departments = mock(ISysDeptService.class);
        when(departments.selectDeptList(any())).thenReturn(List.of(school(1L, "学校")));
        AdminOrganizationScopeProviderImpl asyncProvider =
                new AdminOrganizationScopeProviderImpl(new EduSchoolScope(departments));

        AdminOrganizationScope scope = asyncProvider.captureCurrent();

        assertEquals(AdminOrganizationScopeStatus.UNAVAILABLE, scope.status());
        assertEquals(7L, scope.userId());
        assertNotNull(scope.reason());
    }

    private void login(Set<String> permissions)
    {
        SysUser user = new SysUser();
        user.setUserId(7L);
        user.setDeptId(100L);
        user.setUserName("admin");
        LoginUser loginUser = new LoginUser(7L, 100L, user, permissions);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null,
                        loginUser.getAuthorities()));
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private SysDept school(Long id, String name)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setParentId(0L);
        dept.setDeptName(name);
        dept.setDeptType("SCHOOL");
        dept.setStatus("0");
        dept.setDelFlag("0");
        return dept;
    }
}
