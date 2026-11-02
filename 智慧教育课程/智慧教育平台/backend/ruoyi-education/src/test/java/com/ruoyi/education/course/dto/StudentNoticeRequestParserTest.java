package com.ruoyi.education.course.dto;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.exception.NoticeValidationException;

class StudentNoticeRequestParserTest
{
    @Test
    void optionalPositiveIdReturnsNullWhenAbsent()
    {
        assertNull(StudentNoticeRequestParser.optionalPositiveId(null, "bindingId"));
    }

    @Test
    void optionalPositiveIdParsesTrimmedPositiveNumber()
    {
        assertEquals(10001L, StudentNoticeRequestParser.optionalPositiveId(" 10001 ", "bindingId"));
    }

    @Test
    void optionalPositiveIdRejectsEmptyString()
    {
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalPositiveId("", "bindingId"));
    }

    @Test
    void optionalPositiveIdRejectsWhitespaceOnly()
    {
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalPositiveId("   ", "bindingId"));
    }

    @Test
    void optionalPositiveIdRejectsZeroAndNegative()
    {
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalPositiveId("0", "bindingId"));
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalPositiveId("-1", "bindingId"));
    }

    @Test
    void optionalPositiveIdRejectsNonNumeric()
    {
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalPositiveId("abc", "bindingId"));
    }

    @Test
    void optionalKindReturnsNullWhenAbsent()
    {
        assertNull(StudentNoticeRequestParser.optionalKind(null));
    }

    @Test
    void optionalKindParsesCaseInsensitiveTrimmedValue()
    {
        assertEquals(NoticeKind.GRADE, StudentNoticeRequestParser.optionalKind("  grade "));
    }

    @Test
    void optionalKindRejectsEmptyString()
    {
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalKind(""));
    }

    @Test
    void optionalKindRejectsWhitespaceOnly()
    {
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalKind("   "));
    }

    @Test
    void optionalKindRejectsUnknownValue()
    {
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalKind("DRAFT"));
    }

    @Test
    void optionalBooleanDefaultsToFalseWhenAbsent()
    {
        assertFalse(StudentNoticeRequestParser.optionalBoolean(null, "unreadOnly", false));
    }

    @Test
    void optionalBooleanParsesTrueAndFalseIgnoringCaseAndWhitespace()
    {
        assertTrue(StudentNoticeRequestParser.optionalBoolean(" TRUE ", "unreadOnly", false));
        assertFalse(StudentNoticeRequestParser.optionalBoolean(" false ", "unreadOnly", true));
    }

    @Test
    void optionalBooleanRejectsEmptyString()
    {
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalBoolean("", "unreadOnly", false));
    }

    @Test
    void optionalBooleanRejectsOtherValues()
    {
        assertThrows(NoticeValidationException.class,
                () -> StudentNoticeRequestParser.optionalBoolean("yes", "unreadOnly", false));
    }
}
