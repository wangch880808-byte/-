package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class AnnouncementPersistenceException extends AnnouncementException
{
    private static final long serialVersionUID = 1L;

    public AnnouncementPersistenceException(String message)
    {
        super(message, HttpStatus.ERROR);
    }

    public AnnouncementPersistenceException(String message, Throwable cause)
    {
        super(message, HttpStatus.ERROR, cause);
    }
}
