package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class NoticeValidationException extends EducationException
{
    private static final long serialVersionUID = 1L;

    public NoticeValidationException(String message)
    {
        super(message, HttpStatus.BAD_REQUEST);
    }
}
