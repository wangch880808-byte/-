package com.ruoyi.education.course.exception;

public class AnnouncementAuthorizationUnavailableException extends AnnouncementException
{
    private static final long serialVersionUID = 1L;

    public AnnouncementAuthorizationUnavailableException(String message)
    {
        super(message, 503);
    }

    public AnnouncementAuthorizationUnavailableException(String message, Throwable cause)
    {
        super(message, 503, cause);
    }
}
