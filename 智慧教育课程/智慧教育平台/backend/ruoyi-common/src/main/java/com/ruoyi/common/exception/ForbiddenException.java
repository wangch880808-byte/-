package com.ruoyi.common.exception;

import com.ruoyi.common.constant.HttpStatus;

/**
 * 无权限（拒绝访问）异常
 *
 * 用于业务层对象级越权校验：服务端拒绝并统一返回 403，提示文案不得携带对象
 * 名称、班级名称等存在性信息，避免侧信道泄露。业务代码请优先使用
 * {@link com.ruoyi.common.utils.ScopeAssert}。
 *
 * @author ruoyi
 */
public final class ForbiddenException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    /**
     * 错误码
     */
    private Integer code;

    /**
     * 错误提示
     */
    private String message;

    /**
     * 空构造方法，避免反序列化问题
     */
    public ForbiddenException()
    {
        this(null);
    }

    public ForbiddenException(String message)
    {
        this.message = message == null || message.isBlank() ? "无权访问该对象" : message;
        this.code = HttpStatus.FORBIDDEN;
    }

    @Override
    public String getMessage()
    {
        return message;
    }

    public Integer getCode()
    {
        return code;
    }

    public ForbiddenException setMessage(String message)
    {
        this.message = message;
        return this;
    }
}
