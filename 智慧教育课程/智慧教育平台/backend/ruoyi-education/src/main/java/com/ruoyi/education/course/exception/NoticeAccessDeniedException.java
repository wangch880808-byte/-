package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class NoticeAccessDeniedException extends EducationException
{
    private static final long serialVersionUID = 1L;

    public NoticeAccessDeniedException()
    {
        super("没有该教学班通知的访问权限", HttpStatus.FORBIDDEN);
    }
}
