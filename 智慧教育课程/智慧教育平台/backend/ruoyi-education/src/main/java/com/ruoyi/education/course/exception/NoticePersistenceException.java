package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class NoticePersistenceException extends EducationException
{
    private static final long serialVersionUID = 1L;

    public NoticePersistenceException(String message)
    {
        super(message, HttpStatus.ERROR);
    }

    public NoticePersistenceException(String message, Throwable cause)
    {
        super(message, HttpStatus.ERROR, cause);
    }
}
