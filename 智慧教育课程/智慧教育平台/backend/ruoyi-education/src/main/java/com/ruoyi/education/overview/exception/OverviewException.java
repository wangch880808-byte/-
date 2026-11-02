package com.ruoyi.education.overview.exception;

import com.ruoyi.education.course.exception.EducationException;

/**
 * 总览聚合业务异常基类。
 *
 * <p>6-02 已合并到 main，本类复用其公共 {@link EducationException} 基类，
 * 从而由教育业务异常处理链统一读取业务码；总览仍保留独立异常类型，
 * 便于控制器作用域 advice 区分总览错误。</p>
 *
 * <p>业务码、message 和 cause 的构造语义与 6-02 合并前保持一致；
 * 本类不重复存储 code，统一由 {@link EducationException} 维护。</p>
 */
public abstract class OverviewException extends EducationException
{
    private static final long serialVersionUID = 1L;

    /**
     * 创建总览业务异常。
     *
     * @param message 面向调用方的中文错误信息，不包含 SQL、堆栈或内部字段
     * @param code    业务码，沿用若依响应体的 code 语义
     */
    protected OverviewException(String message, int code)
    {
        super(message, code);
    }

    /**
     * 创建带原因的总览业务异常。
     *
     * @param message 面向调用方的中文错误信息
     * @param code    业务码
     * @param cause   原始异常，只用于服务端日志，不向前端暴露
     */
    protected OverviewException(String message, int code, Throwable cause)
    {
        super(message, code, cause);
    }
}
