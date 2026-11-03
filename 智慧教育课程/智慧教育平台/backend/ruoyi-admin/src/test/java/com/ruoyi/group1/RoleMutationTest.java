package com.ruoyi.group1;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysRoleService;
import com.ruoyi.web.controller.system.SysRoleController;
import static org.mockito.Mockito.*;

class RoleMutationTest
{
    private final SysRoleController controller = new SysRoleController();
    private final ISysRoleService roles = mock(ISysRoleService.class);
    private final TokenService tokens = mock(TokenService.class);

    @BeforeEach void setup()
    {
        ReflectionTestUtils.setField(controller, "roleService", roles);
        ReflectionTestUtils.setField(controller, "tokenService", tokens);
        // checkNotAdminRoleKey 需要可信身份。此测试直接使用普通非 admin 角色。
        com.ruoyi.common.core.domain.entity.SysUser user = new com.ruoyi.common.core.domain.entity.SysUser();
        user.setUserId(1L);
        var login = new com.ruoyi.common.core.domain.model.LoginUser(1L, 1L, user, java.util.Set.of("*:*:*"));
        org.springframework.security.core.context.SecurityContextHolder.getContext().setAuthentication(
            new org.springframework.security.authentication.UsernamePasswordAuthenticationToken(login, null));
    }

    @org.junit.jupiter.api.AfterEach void cleanup()
    {
        org.springframework.security.core.context.SecurityContextHolder.clearContext();
    }

    @Test void dataScopeChangesRevokeAffectedRoleSessions()
    {
        SysRole role = new SysRole(); role.setRoleId(4L);
        when(roles.authDataScope(role)).thenReturn(1);
        controller.dataScope(role);
        verify(tokens).invalidateRoleSessions(4L);
    }

    @Test void failedDataScopeMutationDoesNotRevokeSessions()
    {
        SysRole role = new SysRole(); role.setRoleId(4L);
        controller.dataScope(role);
        verifyNoInteractions(tokens);
    }
}
