package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class AnnouncementAccessDeniedException extends AnnouncementException
{
    private static final long serialVersionUID = 1L;

    public AnnouncementAccessDeniedException()
    {
        super("没有该教学班公告的操作权限", HttpStatus.FORBIDDEN);
    }
}
