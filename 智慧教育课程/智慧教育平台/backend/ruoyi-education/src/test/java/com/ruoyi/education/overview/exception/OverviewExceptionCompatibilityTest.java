package com.ruoyi.education.overview.exception;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;

import org.junit.jupiter.api.Test;
import com.ruoyi.education.course.exception.EducationException;

/**
 * 验证总览异常继承公共 EducationException 后，业务码、message 和 cause 行为不变。
 */
class OverviewExceptionCompatibilityTest
{
    @Test
    void validationExceptionKeepsCodeAndMessage()
    {
        OverviewValidationException exception = new OverviewValidationException("参数错误");

        assertInstanceOf(EducationException.class, exception);
        assertEquals(400, exception.getCode());
        assertEquals("参数错误", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void testExceptionKeepsArbitraryCodeAndMessage()
    {
        TestOverviewException exception = new TestOverviewException("无权查看", 403);

        assertInstanceOf(EducationException.class, exception);
        assertEquals(403, exception.getCode());
        assertEquals("无权查看", exception.getMessage());
        assertNull(exception.getCause());
    }

    @Test
    void causeConstructorKeepsCause()
    {
        IllegalStateException cause = new IllegalStateException("downstream");
        TestOverviewException exception = new TestOverviewException("聚合失败", 503, cause);

        assertInstanceOf(EducationException.class, exception);
        assertEquals(503, exception.getCode());
        assertEquals("聚合失败", exception.getMessage());
        assertSame(cause, exception.getCause());
    }

    /**
     * 测试用具体异常，覆盖带 cause 的受保护构造方法。
     */
    private static final class TestOverviewException extends OverviewException
    {
        private static final long serialVersionUID = 1L;

        private TestOverviewException(String message, int code)
        {
            super(message, code);
        }

        private TestOverviewException(String message, int code, Throwable cause)
        {
            super(message, code, cause);
        }
    }
}
