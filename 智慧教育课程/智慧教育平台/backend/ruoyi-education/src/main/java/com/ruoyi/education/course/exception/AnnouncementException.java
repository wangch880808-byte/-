package com.ruoyi.education.course.exception;

/**
 * 公告业务异常基类。具体子类用于区分参数、未找到、越权、状态冲突、
 * 并发冲突、授权不可用和持久化失败。
 */
public abstract class AnnouncementException extends EducationException
{
    private static final long serialVersionUID = 1L;

    protected AnnouncementException(String message, int code)
    {
        super(message, code);
    }

    protected AnnouncementException(String message, int code, Throwable cause)
    {
        super(message, code, cause);
    }
}
