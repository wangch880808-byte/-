package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class AnnouncementValidationException extends AnnouncementException
{
    private static final long serialVersionUID = 1L;

    public AnnouncementValidationException(String message)
    {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
