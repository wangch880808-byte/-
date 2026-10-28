package com.ruoyi.education.course.web;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.AnnouncementNotFoundException;
import com.ruoyi.education.course.exception.NoticeAccessDeniedException;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.NoticeConcurrentUpdateException;
import com.ruoyi.education.course.exception.NoticeNotFoundException;
import com.ruoyi.education.course.exception.NoticePersistenceException;
import com.ruoyi.education.course.exception.NoticeValidationException;

@ExtendWith(OutputCaptureExtension.class)
class EducationExceptionHandlerTest
{
    private final AnnouncementExceptionHandler handler = new AnnouncementExceptionHandler();

    @Test
    void mapsNoticeExceptionsToBusinessCodes()
    {
        assertEquals(400, codeOf(handler.handleAnnouncementException(
                new NoticeValidationException("kind 不能为空字符串"))));
        assertEquals(403, codeOf(handler.handleAnnouncementException(
                new NoticeAccessDeniedException())));
        assertEquals(404, codeOf(handler.handleAnnouncementException(
                new NoticeNotFoundException())));
        assertEquals(409, codeOf(handler.handleAnnouncementException(
                new NoticeConcurrentUpdateException())));
        assertEquals(503, codeOf(handler.handleAnnouncementException(
                new NoticeAuthorizationUnavailableException("学生通知授权服务尚未接入"))));
    }

    @Test
    void mapsNoticePersistenceFailureWithoutLeakingDatabaseDetails()
    {
        AjaxResult result = handler.handleAnnouncementException(new NoticePersistenceException(
                "学生通知读取失败",
                new DataIntegrityViolationException("SQL select from edu_user_notice failed")));

        assertEquals(500, codeOf(result));
        assertEquals("学生通知读取失败", result.get(AjaxResult.MSG_TAG));
        assertEquals(false, result.toString().contains("edu_user_notice"));
        assertEquals(false, result.toString().contains("SQL select"));
    }

    @Test
    void logsNoticeFailureWithNoticeScope(CapturedOutput output)
    {
        handler.handleAnnouncementException(new NoticePersistenceException(
                "学生通知读取失败", new DataIntegrityViolationException("SQL select failed")));

        assertTrue(output.getOut().contains("学生通知接口服务端异常"));
        assertFalse(output.getOut().contains("公告接口服务端异常"));
    }

    @Test
    void announcementExceptionBehaviorRemainsUnchanged()
    {
        assertEquals(404, codeOf(handler.handleAnnouncementException(
                new AnnouncementNotFoundException())));
        assertEquals(503, codeOf(handler.handleAnnouncementException(
                new AnnouncementAuthorizationUnavailableException("教学班授权查询失败"))));
    }

    private int codeOf(AjaxResult result)
    {
        return ((Integer) result.get(AjaxResult.CODE_TAG)).intValue();
    }
}
