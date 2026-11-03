package com.ruoyi.group1;

import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.core.redis.RedisCache;
import com.ruoyi.framework.web.service.TokenService;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class SessionRevocationTest
{
    private RedisCache redis;
    private TokenService tokens;

    @BeforeEach void setup()
    {
        redis = mock(RedisCache.class);
        tokens = new TokenService();
        ReflectionTestUtils.setField(tokens, "redisCache", redis);
    }

    private LoginUser user(long id, long roleId)
    {
        SysRole role = new SysRole();
        role.setRoleId(roleId); role.setStatus("0");
        SysUser user = new SysUser();
        user.setUserId(id); user.setRoles(List.of(role));
        return new LoginUser(id, 1L, user, Set.of("fixture:read"));
    }

    @Test void changedRoleInvalidatesAllItsSessionsButNotOtherUsersOrSuperAdmin()
    {
        when(redis.keys(anyString())).thenReturn(List.of("a", "b", "other", "admin"));
        when(redis.getCacheObject("a")).thenReturn(user(9, 4));
        when(redis.getCacheObject("b")).thenReturn(user(10, 4));
        when(redis.getCacheObject("other")).thenReturn(user(11, 5));
        when(redis.getCacheObject("admin")).thenReturn(user(1, 4));
        tokens.refreshPermissionByRoleId(4L, null);
        verify(redis).deleteObject("a"); verify(redis).deleteObject("b");
        verify(redis, never()).deleteObject("other"); verify(redis, never()).deleteObject("admin");
        verify(redis, never()).setCacheObject(anyString(), any(), anyInt(), any());
    }

    @Test void userRevocationDeletesEverySessionUsingTheActualRedisKey()
    {
        when(redis.keys(anyString())).thenReturn(List.of("a", "b", "other"));
        when(redis.getCacheObject("a")).thenReturn(user(9, 4));
        when(redis.getCacheObject("b")).thenReturn(user(9, 5));
        when(redis.getCacheObject("other")).thenReturn(user(10, 4));
        tokens.deleteByUserId(9L);
        verify(redis).deleteObject("a"); verify(redis).deleteObject("b");
        verify(redis, never()).deleteObject("other");
    }

    @Test void menuAndOrganizationChangesInvalidateOnlyNonAdminSessions()
    {
        when(redis.keys(anyString())).thenReturn(List.of("a", "admin", "expired"));
        when(redis.getCacheObject("a")).thenReturn(user(9, 4));
        when(redis.getCacheObject("admin")).thenReturn(user(1, 1));
        tokens.invalidateNonAdminSessions();
        verify(redis).deleteObject("a");
        verify(redis, never()).deleteObject("admin"); verify(redis, never()).deleteObject("expired");
    }

    @Test void missingSessionsAndNullRoleAreHarmless()
    {
        tokens.invalidateRoleSessions(null); tokens.deleteByUserId(null);
        verifyNoInteractions(redis);
        when(redis.keys(anyString())).thenReturn(List.of());
        tokens.invalidateRoleSessions(4L);
        verify(redis, never()).deleteObject(anyString());
    }
}
