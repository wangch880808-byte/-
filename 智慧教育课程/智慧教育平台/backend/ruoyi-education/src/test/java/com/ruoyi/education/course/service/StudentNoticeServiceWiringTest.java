package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import java.util.Collections;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;

class StudentNoticeServiceWiringTest
{
    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void springCreatesServiceWithoutAuthorizationAdapterAndFailsClosed()
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.registerBean(StudentNoticeMapper.class,
                    () -> mock(StudentNoticeMapper.class));
            context.register(StudentNoticeService.class);
            context.refresh();

            StudentNoticeService service = context.getBean(StudentNoticeService.class);
            assertNotNull(service);

            Long userId = 930000000001L;
            LoginUser loginUser = new LoginUser(userId, null, new SysUser(userId), Set.of());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));

            assertThrows(NoticeAuthorizationUnavailableException.class,
                    () -> service.list(null, null, false, 1, 10));
            assertThrows(NoticeAuthorizationUnavailableException.class,
                    () -> service.markAllRead());
        }
    }
}
