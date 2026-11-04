package com.ruoyi.education.overview.exception;

/**
 * 总览越权或缺少可信身份/组织范围。
 *
 * <p>用于教师查看非本人教学班、学生缺少本人身份、管理员缺少组织范围等场景。
 * 控制器只从服务端认证上下文取身份，不接受客户端传入 userId/studentId 冒充。</p>
 */
public class OverviewAccessDeniedException extends OverviewException
{
    private static final long serialVersionUID = 1L;

    /**
     * 创建越权异常。
     *
     * @param message 面向调用方的中文错误信息
     */
    public OverviewAccessDeniedException(String message)
    {
        super(message, 403);
    }
}
