package com.ruoyi.education.course.exception;

/**
 * 教育业务异常基类。具体子类用于区分参数、未找到、越权、状态冲突、
 * 并发冲突、授权不可用和持久化失败。
 */
public abstract class EducationException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final int code;

    protected EducationException(String message, int code)
    {
        super(message);
        this.code = code;
    }

    protected EducationException(String message, int code, Throwable cause)
    {
        super(message, cause);
        this.code = code;
    }

    public int getCode()
    {
        return code;
    }
}
