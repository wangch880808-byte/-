package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class NoticeNotFoundException extends EducationException
{
    private static final long serialVersionUID = 1L;

    public NoticeNotFoundException()
    {
        super("通知不存在或不可访问", HttpStatus.NOT_FOUND);
    }
}
