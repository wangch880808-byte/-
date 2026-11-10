package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class AnnouncementStateConflictException extends AnnouncementException
{
    private static final long serialVersionUID = 1L;

    public AnnouncementStateConflictException(String message)
    {
        super(message, HttpStatus.CONFLICT);
    }
}
