package com.ruoyi.education.overview.exception;

/**
 * 总览聚合业务异常基类。
 *
 * <p>本阶段与 6-02 解耦：6-02 的 {@code EducationException} 尚未合并到 main，
 * 因此这里先提供等价的本地基类。6-02 合并后应改为继承
 * {@code com.ruoyi.education.course.exception.EducationException}，
 * 以复用统一异常处理和业务码。</p>
 *
 * <p>TODO：6-02 的 PR #18/#20/#21/#22 合并、{@code EducationException}
 * 在 main 可用后，将本类父类改为 {@code EducationException}；
 * 解除条件为 6-02 通知/公告代码进入 main 且编译通过。</p>
 */
public abstract class OverviewException extends RuntimeException
{
    private static final long serialVersionUID = 1L;

    private final int code;

    /**
     * 创建总览业务异常。
     *
     * @param message 面向调用方的中文错误信息，不包含 SQL、堆栈或内部字段
     * @param code    业务码，沿用若依响应体的 code 语义
     */
    protected OverviewException(String message, int code)
    {
        super(message);
        this.code = code;
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
        super(message, cause);
        this.code = code;
    }

    /**
     * 返回业务码。
     *
     * @return 若依响应体使用的业务码
     */
    public int getCode()
    {
        return code;
    }
}
