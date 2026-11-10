package com.ruoyi.education.course.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

class CurrentUserAccessTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void readsAuthenticatedUserIdFromSecurityContext()
    {
        Long userId = 930000000001L;
        LoginUser loginUser = new LoginUser(userId, null, new SysUser(userId), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));

        assertEquals(userId, SecurityUtils.getUserId());
    }

    @Test
    void missingAuthenticationIsRejected()
    {
        assertThrows(ServiceException.class, SecurityUtils::getUserId);
    }
}
