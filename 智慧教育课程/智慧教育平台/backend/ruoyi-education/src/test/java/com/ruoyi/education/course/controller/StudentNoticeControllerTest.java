package com.ruoyi.education.course.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.domain.StudentNotice;
import com.ruoyi.education.course.exception.NoticeAccessDeniedException;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.NoticeConcurrentUpdateException;
import com.ruoyi.education.course.exception.NoticeNotFoundException;
import com.ruoyi.education.course.exception.NoticeValidationException;
import com.ruoyi.education.course.service.StudentNoticePage;
import com.ruoyi.education.course.service.StudentNoticeReadAllResult;
import com.ruoyi.education.course.service.StudentNoticeReadResult;
import com.ruoyi.education.course.service.StudentNoticeService;
import com.ruoyi.education.course.service.StudentNoticeUnreadCountResult;
import com.ruoyi.education.course.web.AnnouncementExceptionHandler;

class StudentNoticeControllerTest
{
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 28, 12, 0, 0, 123_000_000);

    private StudentNoticeService service;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        service = mock(StudentNoticeService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StudentNoticeController(service))
                .setControllerAdvice(new AnnouncementExceptionHandler(), new TestServiceExceptionAdvice())
                .build();
    }

    @Test
    void listReturnsContractFieldsWithoutBodyOrRecipientUserId() throws Exception
    {
        when(service.list(10001L, NoticeKind.GRADE, true, 1, 10))
                .thenReturn(new StudentNoticePage(1, List.of(notice(1L, null))));

        mockMvc.perform(get("/education/student/notices")
                        .param("bindingId", "10001")
                        .param("kind", "GRADE")
                        .param("unreadOnly", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.msg").value("查询成功"))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.rows.length()").value(1))
                .andExpect(jsonPath("$.rows[0].id").value("1"))
                .andExpect(jsonPath("$.rows[0].kind").value("GRADE"))
                .andExpect(jsonPath("$.rows[0].bindingId").value("10001"))
                .andExpect(jsonPath("$.rows[0].sourceId").value("30001"))
                .andExpect(jsonPath("$.rows[0].title").value("title"))
                .andExpect(jsonPath("$.rows[0].visibleAt").value("2026-10-28T11:00:00.123Z"))
                .andExpect(jsonPath("$.rows[0].readAt").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.rows[0].isRead").value(false))
                .andExpect(jsonPath("$.rows[0].createdAt").value("2026-10-28T10:00:00.123Z"))
                .andExpect(jsonPath("$.rows[0].recipientUserId").doesNotExist())
                .andExpect(jsonPath("$.rows[0].body").doesNotExist());
    }

    @Test
    void listWithoutParametersPassesDefaultsAndIgnoresSpoofedRecipient() throws Exception
    {
        when(service.list(null, null, false, 1, 10))
                .thenReturn(new StudentNoticePage(0, List.of()));

        mockMvc.perform(get("/education/student/notices")
                        .param("recipientUserId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.total").value(0))
                .andExpect(jsonPath("$.rows.length()").value(0));

        verify(service).list(null, null, false, 1, 10);
    }

    @Test
    void invalidKindEmptyStringReturns400WithoutCallingService() throws Exception
    {
        expectBadRequest("/education/student/notices?kind=");
        verifyNoInteractions(service);
    }

    @Test
    void invalidKindWhitespaceReturns400WithoutCallingService() throws Exception
    {
        mockMvc.perform(get("/education/student/notices").param("kind", "   "))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
        verifyNoInteractions(service);
    }

    @Test
    void invalidKindUnknownValueReturns400WithoutCallingService() throws Exception
    {
        expectBadRequest("/education/student/notices?kind=DRAFT");
        verifyNoInteractions(service);
    }

    @Test
    void invalidUnreadOnlyEmptyStringReturns400WithoutCallingService() throws Exception
    {
        expectBadRequest("/education/student/notices?unreadOnly=");
        verifyNoInteractions(service);
    }

    @Test
    void invalidUnreadOnlyOtherValueReturns400WithoutCallingService() throws Exception
    {
        expectBadRequest("/education/student/notices?unreadOnly=yes");
        verifyNoInteractions(service);
    }

    @Test
    void invalidBindingIdEmptyStringReturns400WithoutCallingService() throws Exception
    {
        expectBadRequest("/education/student/notices?bindingId=");
        verifyNoInteractions(service);
    }

    @Test
    void invalidBindingIdZeroReturns400WithoutCallingService() throws Exception
    {
        expectBadRequest("/education/student/notices?bindingId=0");
        verifyNoInteractions(service);
    }

    @Test
    void invalidPageNumberReturns400WithoutCallingService() throws Exception
    {
        expectBadRequest("/education/student/notices?pageNum=0");
        verifyNoInteractions(service);
    }

    @Test
    void invalidPageSizeReturns400WithoutCallingService() throws Exception
    {
        expectBadRequest("/education/student/notices?pageSize=101");
        verifyNoInteractions(service);
    }

    @Test
    void detailReturnsNoticeMetadataWithoutBody() throws Exception
    {
        when(service.get(1L)).thenReturn(notice(1L, null));

        mockMvc.perform(get("/education/student/notices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.kind").value("GRADE"))
                .andExpect(jsonPath("$.data.isRead").value(false))
                .andExpect(jsonPath("$.data.body").doesNotExist())
                .andExpect(jsonPath("$.data.recipientUserId").doesNotExist());
    }

    @Test
    void unreadCountRouteUsesLiteralPathNotNoticeId() throws Exception
    {
        when(service.unreadCount(10001L))
                .thenReturn(new StudentNoticeUnreadCountResult(3, AT));

        mockMvc.perform(get("/education/student/notices/unread-count")
                        .param("bindingId", "10001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.unreadCount").value(3))
                .andExpect(jsonPath("$.data.asOf").value("2026-10-28T12:00:00.123Z"));

        verify(service).unreadCount(10001L);
        verify(service, never()).get(any());
    }

    @Test
    void readReturnsAlreadyReadAndOriginalTime() throws Exception
    {
        when(service.markRead(1L))
                .thenReturn(new StudentNoticeReadResult(1L, AT, false));

        mockMvc.perform(post("/education/student/notices/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.id").value("1"))
                .andExpect(jsonPath("$.data.readAt").value("2026-10-28T12:00:00.123Z"))
                .andExpect(jsonPath("$.data.alreadyRead").value(false));
    }

    @Test
    void readAllReturnsMarkedAndRemainingCounts() throws Exception
    {
        when(service.markAllRead())
                .thenReturn(new StudentNoticeReadAllResult(3, 0, AT));

        mockMvc.perform(put("/education/student/notices/read-all"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.markedCount").value(3))
                .andExpect(jsonPath("$.data.unreadCount").value(0))
                .andExpect(jsonPath("$.data.operatedAt").value("2026-10-28T12:00:00.123Z"));
    }

    @Test
    void readAllIgnoresBindingKindUnreadOnlyPagingAndSpoofedRecipient() throws Exception
    {
        when(service.markAllRead()).thenReturn(new StudentNoticeReadAllResult(0, 0, AT));

        mockMvc.perform(put("/education/student/notices/read-all")
                        .param("bindingId", "10001")
                        .param("kind", "GRADE")
                        .param("unreadOnly", "true")
                        .param("pageNum", "2")
                        .param("pageSize", "1")
                        .param("recipientUserId", "9999"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200));

        verify(service).markAllRead();
    }

    @Test
    void invalidNoticeIdReturns400WithoutCallingService() throws Exception
    {
        expectBadRequest("/education/student/notices/abc");
        verifyNoInteractions(service);
    }

    @Test
    void accessDeniedMapsTo403() throws Exception
    {
        when(service.list(any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new NoticeAccessDeniedException());

        mockMvc.perform(get("/education/student/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void notFoundMapsTo404() throws Exception
    {
        when(service.get(1L)).thenThrow(new NoticeNotFoundException());

        mockMvc.perform(get("/education/student/notices/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404));
    }

    @Test
    void authorizationUnavailableMapsTo503() throws Exception
    {
        when(service.unreadCount((Long) any()))
                .thenThrow(new NoticeAuthorizationUnavailableException("学生通知授权服务尚未接入"));

        mockMvc.perform(get("/education/student/notices/unread-count"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(503));
    }

    @Test
    void validationMapsTo400() throws Exception
    {
        when(service.list(any(), any(), anyBoolean(), anyInt(), anyInt()))
                .thenThrow(new NoticeValidationException("pageSize 必须在 1 到 100 之间"));

        mockMvc.perform(get("/education/student/notices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void concurrentUpdateMapsTo409() throws Exception
    {
        when(service.markRead(1L)).thenThrow(new NoticeConcurrentUpdateException());

        mockMvc.perform(post("/education/student/notices/1/read"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(409));
    }

    @Test
    void allEndpointsRequireStudentRoleAndExcludeAdminAndTeacher()
    {
        int securedEndpoints = 0;
        for (Method method : StudentNoticeController.class.getDeclaredMethods())
        {
            PreAuthorize preAuthorize = method.getAnnotation(PreAuthorize.class);
            if (preAuthorize == null)
            {
                continue;
            }
            securedEndpoints++;
            String value = preAuthorize.value();
            assertTrue(value.contains("hasRole('student')"), method.getName());
            assertTrue(value.contains("lacksRole('admin')"), method.getName());
            assertTrue(value.contains("lacksRole('teacher')"), method.getName());
        }
        assertEquals(5, securedEndpoints);
    }

    @Test
    void writeEndpointsHaveAuditLogAndGetsDoNot()
    {
        assertNotNull(method("read").getAnnotation(Log.class));
        assertNotNull(method("readAll").getAnnotation(Log.class));
        assertNull(method("list").getAnnotation(Log.class));
        assertNull(method("get").getAnnotation(Log.class));
        assertNull(method("unreadCount").getAnnotation(Log.class));
    }

    private void expectBadRequest(String url) throws Exception
    {
        mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    private Method method(String name)
    {
        for (Method method : StudentNoticeController.class.getDeclaredMethods())
        {
            if (method.getName().equals(name))
            {
                return method;
            }
        }
        throw new IllegalStateException("missing method " + name);
    }

    private StudentNotice notice(Long id, LocalDateTime readAt)
    {
        StudentNotice notice = new StudentNotice();
        notice.setId(id);
        notice.setRecipientUserId(9001L);
        notice.setBindingId(10001L);
        notice.setNoticeKind(NoticeKind.GRADE);
        notice.setSourceId(30001L);
        notice.setTitle("title");
        notice.setVisibleAt(LocalDateTime.of(2026, 10, 28, 11, 0, 0, 123_000_000));
        notice.setReadAt(readAt);
        notice.setCreatedAt(LocalDateTime.of(2026, 10, 28, 10, 0, 0, 123_000_000));
        return notice;
    }

    @RestControllerAdvice
    static class TestServiceExceptionAdvice
    {
        @ExceptionHandler(ServiceException.class)
        public AjaxResult handle(ServiceException e)
        {
            return AjaxResult.error(e.getCode(), e.getMessage());
        }
    }
}
