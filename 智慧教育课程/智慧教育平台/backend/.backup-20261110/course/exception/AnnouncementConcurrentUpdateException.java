package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class AnnouncementConcurrentUpdateException extends AnnouncementException
{
    private static final long serialVersionUID = 1L;

    public AnnouncementConcurrentUpdateException()
    {
        super("公告状态已被其他请求更新，请刷新后重试", HttpStatus.CONFLICT);
    }
}
