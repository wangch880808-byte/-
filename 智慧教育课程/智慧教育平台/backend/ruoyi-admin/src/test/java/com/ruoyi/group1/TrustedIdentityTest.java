package com.ruoyi.group1;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.web.controller.system.SysIdentityController;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class TrustedIdentityTest
{
    @AfterEach void cleanup() { SecurityContextHolder.clearContext(); }

    @Test void requestCannotImpersonateAnotherUserOrRole() throws Exception
    {
        SysUser user = new SysUser(); user.setUserId(9L);
        SysRole active = new SysRole(); active.setRoleKey("teacher"); active.setStatus("0");
        SysRole disabled = new SysRole(); disabled.setRoleKey("admin"); disabled.setStatus("1");
        user.setRoles(List.of(active, disabled));
        LoginUser login = new LoginUser(9L, 8L, user, Set.of());
        SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(login, null));
        org.junit.jupiter.api.Assertions.assertFalse(com.ruoyi.common.utils.SecurityUtils.hasRole("admin"));
        org.junit.jupiter.api.Assertions.assertTrue(com.ruoyi.common.utils.SecurityUtils.hasAnyRole("teacher"));
        MockMvcBuilders.standaloneSetup(new SysIdentityController()).build()
            .perform(get("/system/identity/current").param("userId", "1").param("role", "admin"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.data.userId").value("9"))
            .andExpect(jsonPath("$.data.deptId").value("8"))
            .andExpect(jsonPath("$.data.roles.length()").value(1))
            .andExpect(jsonPath("$.data.roles[0]").value("teacher"));
    }

    @Test void missingIdentityIsUnauthorized() throws Exception
    {
        MockMvcBuilders.standaloneSetup(new SysIdentityController())
            .setControllerAdvice(new GlobalExceptionHandler()).build()
            .perform(get("/system/identity/current"))
            .andExpect(jsonPath("$.code").value(401));
    }
}
