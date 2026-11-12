package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class AnnouncementNotFoundException extends AnnouncementException
{
    private static final long serialVersionUID = 1L;

    public AnnouncementNotFoundException()
    {
        super("公告不存在或不可访问", HttpStatus.NOT_FOUND);
    }
}
