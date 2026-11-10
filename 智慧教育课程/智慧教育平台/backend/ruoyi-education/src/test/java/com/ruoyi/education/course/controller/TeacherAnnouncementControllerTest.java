package com.ruoyi.education.course.controller;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.dto.TeacherAnnouncementCreateRequest;
import com.ruoyi.education.course.dto.TeacherAnnouncementUpdateRequest;
import com.ruoyi.education.course.exception.AnnouncementAccessDeniedException;
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.AnnouncementNotFoundException;
import com.ruoyi.education.course.exception.AnnouncementPersistenceException;
import com.ruoyi.education.course.exception.AnnouncementStateConflictException;
import com.ruoyi.education.course.exception.AnnouncementValidationException;
import com.ruoyi.education.course.service.CourseAnnouncementPage;
import com.ruoyi.education.course.service.CourseAnnouncementService;
import com.ruoyi.education.course.web.AnnouncementExceptionHandler;

class TeacherAnnouncementControllerTest
{
    private static final Clock CLOCK =
            Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC);

    private static final Clock SUB_MILLISECOND_CLOCK =
            Clock.fixed(Instant.parse("2026-10-24T12:00:00.000600Z"), ZoneOffset.UTC);

    private CourseAnnouncementService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        service = mock(CourseAnnouncementService.class);
        mockMvc = mockMvc(CLOCK);
    }

    @Test
    void createReturnsDetailWithStringIdsAndUtcMillis() throws Exception
    {
        CourseAnnouncement draft = announcement(AnnouncementStatus.DRAFT);
        when(service.createDraft(eq(10001L), eq("title"), eq("body"), isNull(), isNull()))
                .thenReturn(draft);

        mockMvc.perform(post("/education/teacher/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bindingId":"10001","title":"title","body":"body",
                                 "opensAt":null,"closesAt":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("操作成功"))
                .andExpect(jsonPath("$.data.id").value("20001"))
                .andExpect(jsonPath("$.data.bindingId").value("10001"))
                .andExpect(jsonPath("$.data.body").value("body"))
                .andExpect(jsonPath("$.data.status").value("DRAFT"))
                .andExpect(jsonPath("$.data.visibilityState").value("DRAFT"))
                .andExpect(jsonPath("$.data.createdByUserId").value("90001"))
                .andExpect(jsonPath("$.data.publishedByUserId").value(nullValue()))
                .andExpect(jsonPath("$.data.createdAt").value("2026-10-24T01:00:00.123Z"));
    }

    @Test
    void createConvertsOffsetTimeToUtc() throws Exception
    {
        when(service.createDraft(eq(10001L), eq("title"), eq("body"),
                eq(LocalDateTime.of(2026, 10, 25, 0, 0, 0, 123_000_000)), isNull()))
                .thenReturn(announcement(AnnouncementStatus.DRAFT));

        mockMvc.perform(post("/education/teacher/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bindingId":"10001","title":"title","body":"body",
                                 "opensAt":"2026-10-25T08:00:00.123+08:00","closesAt":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));
    }

    @Test
    void invalidTimePrecisionReturns400() throws Exception
    {
        mockMvc.perform(post("/education/teacher/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"bindingId":"10001","title":"title","body":"body",
                                 "opensAt":"2026-10-25T00:00:00.1234Z","closesAt":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void updateReturnsDetail() throws Exception
    {
        CourseAnnouncement draft = announcement(AnnouncementStatus.DRAFT);
        when(service.updateDraft(eq(20001L), eq("title"), eq("body"), isNull(), isNull()))
                .thenReturn(draft);

        mockMvc.perform(put("/education/teacher/announcements/20001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"title","body":"body","opensAt":null,"closesAt":null}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("20001"))
                .andExpect(jsonPath("$.data.body").value("body"));
    }

    @Test
    void publishReturnsPublishedDetailWithOpenVisibility() throws Exception
    {
        CourseAnnouncement published = announcement(AnnouncementStatus.PUBLISHED);
        when(service.publish(20001L)).thenReturn(published);

        mockMvc.perform(post("/education/teacher/announcements/20001/publish"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("PUBLISHED"))
                .andExpect(jsonPath("$.data.visibilityState").value("OPEN"))
                .andExpect(jsonPath("$.data.publishedByUserId").value("90001"))
                .andExpect(jsonPath("$.data.opensAt").value("2026-10-24T01:00:00.123Z"));
    }

    @Test
    void archiveReturnsArchivedDetail() throws Exception
    {
        CourseAnnouncement archived = announcement(AnnouncementStatus.ARCHIVED);
        when(service.archive(20001L)).thenReturn(archived);

        mockMvc.perform(post("/education/teacher/announcements/20001/archive"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.status").value("ARCHIVED"))
                .andExpect(jsonPath("$.data.visibilityState").value("ARCHIVED"));
    }

    @Test
    void listReturnsPagedRowsWithoutBody() throws Exception
    {
        CourseAnnouncement draft = announcement(AnnouncementStatus.DRAFT);
        CourseAnnouncement published = announcement(AnnouncementStatus.PUBLISHED);
        when(service.listTeacherAnnouncementsPage(10001L, null, 1, 10))
                .thenReturn(new CourseAnnouncementPage(2, List.of(draft, published)));

        mockMvc.perform(get("/education/teacher/announcements")
                        .param("bindingId", "10001")
                        .param("pageNum", "1")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("查询成功"))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.rows.length()").value(2))
                .andExpect(jsonPath("$.rows[0].id").value("20001"))
                .andExpect(jsonPath("$.rows[0].bindingId").value("10001"))
                .andExpect(jsonPath("$.rows[0].visibilityState").value("DRAFT"))
                .andExpect(jsonPath("$.rows[0].body").doesNotExist())
                .andExpect(jsonPath("$.rows[1].body").doesNotExist());
    }

    @Test
    void listPassesStatusFilterToService() throws Exception
    {
        when(service.listTeacherAnnouncementsPage(10001L, AnnouncementStatus.PUBLISHED, 1, 10))
                .thenReturn(new CourseAnnouncementPage(1,
                        List.of(announcement(AnnouncementStatus.PUBLISHED))));

        mockMvc.perform(get("/education/teacher/announcements")
                        .param("bindingId", "10001")
                        .param("status", "PUBLISHED"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows[0].status").value("PUBLISHED"));
    }

    @Test
    void emptyListReturnsEmptyRows() throws Exception
    {
        when(service.listTeacherAnnouncementsPage(10001L, null, 1, 10))
                .thenReturn(new CourseAnnouncementPage(0, List.of()));

        mockMvc.perform(get("/education/teacher/announcements")
                        .param("bindingId", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.rows.length()").value(0));
    }

    @Test
    void detailReturnsBodyAndVisibility() throws Exception
    {
        when(service.getTeacherAnnouncement(20001L))
                .thenReturn(announcement(AnnouncementStatus.PUBLISHED));

        mockMvc.perform(get("/education/teacher/announcements/20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("20001"))
                .andExpect(jsonPath("$.data.body").value("body"))
                .andExpect(jsonPath("$.data.visibilityState").value("OPEN"));
    }

    @Test
    void visibilityUsesRoundedNowAtOpenBoundary() throws Exception
    {
        CourseAnnouncement published = announcement(AnnouncementStatus.PUBLISHED);
        published.setOpensAt(LocalDateTime.of(2026, 10, 24, 12, 0, 0, 1_000_000));
        when(service.getTeacherAnnouncement(20001L)).thenReturn(published);

        mockMvc(SUB_MILLISECOND_CLOCK).perform(get("/education/teacher/announcements/20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibilityState").value("OPEN"));
    }

    @Test
    void visibilityUsesRoundedNowAtCloseBoundary() throws Exception
    {
        CourseAnnouncement published = announcement(AnnouncementStatus.PUBLISHED);
        published.setOpensAt(LocalDateTime.of(2026, 10, 24, 12, 0, 0));
        published.setClosesAt(LocalDateTime.of(2026, 10, 24, 12, 0, 0, 1_000_000));
        when(service.getTeacherAnnouncement(20001L)).thenReturn(published);

        mockMvc(SUB_MILLISECOND_CLOCK).perform(get("/education/teacher/announcements/20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.visibilityState").value("CLOSED"));
    }

    @Test
    void blankTimeStringsReturn400OnCreateAndUpdate() throws Exception
    {
        String empty = "\"\"";
        String blank = "\"   \"";

        assertBadRequest(post("/education/teacher/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson(empty, "null")));
        assertBadRequest(post("/education/teacher/announcements")
                .contentType(MediaType.APPLICATION_JSON)
                .content(createJson("null", blank)));
        assertBadRequest(put("/education/teacher/announcements/20001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson(empty, "null")));
        assertBadRequest(put("/education/teacher/announcements/20001")
                .contentType(MediaType.APPLICATION_JSON)
                .content(updateJson("null", blank)));
    }

    @Test
    void malformedJsonReturns400() throws Exception
    {
        mockMvc.perform(post("/education/teacher/announcements")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void invalidQueryAndPathParametersReturn400() throws Exception
    {
        assertBadRequest(get("/education/teacher/announcements").param("bindingId", "10001")
                .param("pageNum", "0"));
        assertBadRequest(get("/education/teacher/announcements").param("bindingId", "10001")
                .param("pageSize", "101"));
        assertBadRequest(get("/education/teacher/announcements").param("bindingId", "10001")
                .param("pageSize", "0"));
        assertBadRequest(get("/education/teacher/announcements").param("bindingId", "10001")
                .param("pageNum", "abc"));
        assertBadRequest(get("/education/teacher/announcements").param("bindingId", "abc"));
        assertBadRequest(get("/education/teacher/announcements").param("bindingId", "10001")
                .param("status", "BAD"));
        assertBadRequest(get("/education/teacher/announcements/abc"));
    }

    @Test
    void mapsAnnouncementExceptionsToBusinessCodes() throws Exception
    {
        assertDetailError(new AnnouncementValidationException("bad"), 400);
        assertDetailError(new AnnouncementAccessDeniedException(), 403);
        assertDetailError(new AnnouncementNotFoundException(), 404);
        assertDetailError(new AnnouncementStateConflictException("conflict"), 409);
        assertDetailError(new AnnouncementPersistenceException("persistence failed"), 500);
        assertDetailError(new AnnouncementAuthorizationUnavailableException("unavailable"), 503);
        assertDetailError(new ServiceException("unauthorized", 401), 401);
    }

    @Test
    void writeEndpointsDeclareAuditAndPermissionAnnotations() throws Exception
    {
        assertWriteAnnotations("create", new Class<?>[] { TeacherAnnouncementCreateRequest.class },
                BusinessType.INSERT, "education:announcement:add");
        assertWriteAnnotations("update",
                new Class<?>[] { String.class, TeacherAnnouncementUpdateRequest.class },
                BusinessType.UPDATE, "education:announcement:edit");
        assertWriteAnnotations("publish", new Class<?>[] { String.class },
                BusinessType.UPDATE, "education:announcement:publish");
        assertWriteAnnotations("archive", new Class<?>[] { String.class },
                BusinessType.UPDATE, "education:announcement:archive");

        assertPermission("list", new Class<?>[] { String.class, String.class, String.class, String.class },
                "education:announcement:list");
        assertPermission("get", new Class<?>[] { String.class },
                "education:announcement:list");
    }

    private MockMvc mockMvc(Clock clock)
    {
        return MockMvcBuilders.standaloneSetup(new TeacherAnnouncementController(service, clock))
                .setControllerAdvice(new AnnouncementExceptionHandler(), new TestServiceExceptionAdvice())
                .build();
    }

    private String createJson(String opensAt, String closesAt)
    {
        return "{\"bindingId\":\"10001\",\"title\":\"title\",\"body\":\"body\","
                + "\"opensAt\":" + opensAt + ",\"closesAt\":" + closesAt + "}";
    }

    private String updateJson(String opensAt, String closesAt)
    {
        return "{\"title\":\"title\",\"body\":\"body\","
                + "\"opensAt\":" + opensAt + ",\"closesAt\":" + closesAt + "}";
    }

    private void assertDetailError(RuntimeException error, int code) throws Exception
    {
        doThrow(error).when(service).getTeacherAnnouncement(20001L);

        mockMvc.perform(get("/education/teacher/announcements/20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(code));
    }

    private void assertBadRequest(org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder request)
            throws Exception
    {
        mockMvc.perform(request)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    private void assertWriteAnnotations(String methodName, Class<?>[] parameterTypes,
            BusinessType businessType, String permission) throws Exception
    {
        Method method = TeacherAnnouncementController.class.getMethod(methodName, parameterTypes);
        Log log = method.getAnnotation(Log.class);
        assertNotNull(log);
        assertEquals("教师公告", log.title());
        assertEquals(businessType, log.businessType());
        assertPermission(method, permission);
    }

    private void assertPermission(String methodName, Class<?>[] parameterTypes, String permission)
            throws Exception
    {
        assertPermission(TeacherAnnouncementController.class.getMethod(methodName, parameterTypes), permission);
    }

    private void assertPermission(Method method, String permission)
    {
        PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
        assertNotNull(preAuthorize);
        assertEquals("@ss.hasPermi('" + permission + "')", preAuthorize.value());
    }

    private CourseAnnouncement announcement(AnnouncementStatus status)
    {
        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setId(20001L);
        announcement.setBindingId(10001L);
        announcement.setTitle("title");
        announcement.setBody("body");
        announcement.setStatus(status);
        announcement.setCreatedByUserId(90001L);
        announcement.setCreatedAt(LocalDateTime.of(2026, 10, 24, 1, 0, 0, 123_000_000));
        if (status == AnnouncementStatus.PUBLISHED || status == AnnouncementStatus.ARCHIVED)
        {
            announcement.setPublishedByUserId(90001L);
            announcement.setOpensAt(LocalDateTime.of(2026, 10, 24, 1, 0, 0, 123_000_000));
        }
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
