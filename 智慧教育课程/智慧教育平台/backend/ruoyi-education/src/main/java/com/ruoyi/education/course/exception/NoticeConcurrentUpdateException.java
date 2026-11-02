package com.ruoyi.education.course.exception;

import com.ruoyi.common.constant.HttpStatus;

public class NoticeConcurrentUpdateException extends EducationException
{
    private static final long serialVersionUID = 1L;

    public NoticeConcurrentUpdateException()
    {
        super("通知状态已被其他请求更新，请刷新后重试", HttpStatus.CONFLICT);
    }
}
