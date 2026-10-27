package com.ruoyi.education.constant;

/**
 * 教学业务 HTTP 状态码（补充若依 {@link com.ruoyi.common.constant.HttpStatus}）。
 */
public final class EducationHttpStatus
{
    /** 请求语义正确但业务规则不允许（如资源未发布、活动已截止） */
    public static final int UNPROCESSABLE_ENTITY = 422;

    private EducationHttpStatus()
    {
    }
}
