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
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.mapper.CourseAnnouncementMapper;

class CourseAnnouncementServiceWiringTest
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
            context.registerBean(CourseAnnouncementMapper.class,
                    () -> mock(CourseAnnouncementMapper.class));
            context.registerBean(AnnouncementNoticeFanoutPort.class,
                    () -> mock(AnnouncementNoticeFanoutPort.class));
            context.register(CourseAnnouncementService.class);
            context.refresh();

            CourseAnnouncementService service = context.getBean(CourseAnnouncementService.class);
            assertNotNull(service);

            Long userId = 930000000001L;
            LoginUser loginUser = new LoginUser(userId, null, new SysUser(userId), Set.of());
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));

            assertThrows(AnnouncementAuthorizationUnavailableException.class,
                    () -> service.createDraft(100L, "title", "body", null, null));
        }
    }
}
