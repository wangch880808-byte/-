package com.ruoyi.education.course.exception;

public class NoticeAuthorizationUnavailableException extends EducationException
{
    private static final long serialVersionUID = 1L;

    public NoticeAuthorizationUnavailableException(String message)
    {
        super(message, 503);
    }

    public NoticeAuthorizationUnavailableException(String message, Throwable cause)
    {
        super(message, 503, cause);
    }
}
