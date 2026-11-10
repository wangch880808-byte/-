package com.ruoyi.education.course.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.exception.AnnouncementAccessDeniedException;
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.AnnouncementNotFoundException;
import com.ruoyi.education.course.exception.AnnouncementPersistenceException;
import com.ruoyi.education.course.service.CourseAnnouncementPage;
import com.ruoyi.education.course.service.StudentAnnouncementService;
import com.ruoyi.education.course.web.AnnouncementExceptionHandler;

class StudentAnnouncementControllerTest
{
    private StudentAnnouncementService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        service = mock(StudentAnnouncementService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StudentAnnouncementController(service))
                .setControllerAdvice(new AnnouncementExceptionHandler(), new TestServiceExceptionAdvice())
                .build();
    }

    @Test
    void listReturnsContractSummaryWithoutBodyOrTeacherFields() throws Exception
    {
        when(service.list(10001L, 1, 10))
                .thenReturn(new CourseAnnouncementPage(1, List.of(announcement())));

        mockMvc.perform(get("/education/student/announcements").param("bindingId", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("查询成功"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].id").value("20001"))
                .andExpect(jsonPath("$.rows[0].bindingId").value("10001"))
                .andExpect(jsonPath("$.rows[0].title").value("title"))
                .andExpect(jsonPath("$.rows[0].opensAt").value("2026-10-24T01:00:00.123Z"))
                .andExpect(jsonPath("$.rows[0].closesAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rows[0].createdAt").value("2026-10-24T00:00:00.123Z"))
                .andExpect(jsonPath("$.rows[0].body").doesNotExist())
                .andExpect(jsonPath("$.rows[0].status").doesNotExist())
                .andExpect(jsonPath("$.rows[0].visibilityState").doesNotExist())
                .andExpect(jsonPath("$.rows[0].createdByUserId").doesNotExist())
                .andExpect(jsonPath("$.rows[0].publishedByUserId").doesNotExist());
    }

    @Test
    void listWithoutBindingIdPassesNullToService() throws Exception
    {
        when(service.list(null, 1, 10))
                .thenReturn(new CourseAnnouncementPage(1, List.of(announcement())));

        mockMvc.perform(get("/education/student/announcements"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void emptyListReturnsEmptyRows() throws Exception
    {
        when(service.list(10001L, 1, 10))
                .thenReturn(new CourseAnnouncementPage(0, List.of()));

        mockMvc.perform(get("/education/student/announcements").param("bindingId", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    @Test
    void detailReturnsBodyWithoutTeacherFields() throws Exception
    {
        when(service.get(20001L)).thenReturn(announcement());

        mockMvc.perform(get("/education/student/announcements/20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("20001"))
                .andExpect(jsonPath("$.data.bindingId").value("10001"))
                .andExpect(jsonPath("$.data.body").value("body"))
                .andExpect(jsonPath("$.data.opensAt").value("2026-10-24T01:00:00.123Z"))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-24T00:00:00.123Z"))
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.visibilityState").doesNotExist())
                .andExpect(jsonPath("$.data.createdByUserId").doesNotExist())
                .andExpect(jsonPath("$.data.publishedByUserId").doesNotExist());
    }

    @Test
    void blankBindingIdReturns400WithoutServiceCall() throws Exception
    {
        assertBadRequest(get("/education/student/announcements").param("bindingId", ""));
        assertBadRequest(get("/education/student/announcements").param("bindingId", "   "));
        verifyNoInteractions(service);
    }

    @Test
    void invalidParametersReturn400() throws Exception
    {
        assertBadRequest(get("/education/student/announcements").param("bindingId", "abc"));
        assertBadRequest(get("/education/student/announcements").param("pageNum", "0"));
        assertBadRequest(get("/education/student/announcements").param("pageSize", "101"));
        assertBadRequest(get("/education/student/announcements").param("pageSize", "abc"));
        assertBadRequest(get("/education/student/announcements/abc"));
    }

    @Test
    void mapsBusinessErrorCodes() throws Exception
    {
        assertDetailError(new AnnouncementAccessDeniedException(), 403);
        assertDetailError(new AnnouncementNotFoundException(), 404);
        assertDetailError(new AnnouncementPersistenceException("persistence failed"), 500);
        assertDetailError(new AnnouncementAuthorizationUnavailableException("unavailable"), 503);
        assertDetailError(new ServiceException("unauthorized", 401), 401);
    }

    @Test
    void endpointsRequireStudentRole() throws Exception
    {
        Method list = StudentAnnouncementController.class.getMethod(
                "list", String.class, String.class, String.class);
        Method get = StudentAnnouncementController.class.getMethod("get", String.class);

        assertStudentRole(list);
        assertStudentRole(get);
    }

    private void assertDetailError(RuntimeException error, int code) throws Exception
    {
        doThrow(error).when(service).get(20001L);

        mockMvc.perform(get("/education/student/announcements/20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void assertBadRequest(MockHttpServletRequestBuilder request) throws Exception
    {
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    private void assertStudentRole(Method method)
    {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("@ss.hasRole('student') and @ss.lacksRole('admin') and @ss.lacksRole('teacher')",
                preAuthorize.value());
    }

    private CourseAnnouncement announcement()
    {
        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setId(20001L);
        announcement.setBindingId(10001L);
        announcement.setTitle("title");
        announcement.setBody("body");
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setOpensAt(LocalDateTime.of(2026, 10, 24, 1, 0, 0, 123_000_000));
        announcement.setCreatedAt(LocalDateTime.of(2026, 10, 24, 0, 0, 0, 123_000_000));
        return announcement;
    }

    @RestControllerAdvice
    static class TestServiceExceptionAdvice
    {
        @ExceptionHandler(ServiceException.class)
        public AjaxResult handle(ServiceException e)
        {
            return AjaxResult.error(e.getCode() == null ? 500 : e.getCode(), e.getMessage());
        }
    }
}
