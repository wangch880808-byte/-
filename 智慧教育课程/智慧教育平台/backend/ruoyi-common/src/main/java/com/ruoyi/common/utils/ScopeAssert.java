package com.ruoyi.common.utils;

import com.ruoyi.common.exception.ForbiddenException;

/**
 * 对象级权限断言工具（第 1 组 1A 公共能力）
 *
 * 供第 2～6 组在服务端做对象归属、组织范围、班级范围校验。校验失败统一抛
 * {@link ForbiddenException}，由全局异常处理器返回 403；提示文案不携带对象
 * 名称等存在性信息，避免被用于探测数据是否存在。
 *
 * 用法示例：
 * <pre>
 * // 校验课程/教学班归属
 * ScopeAssert.requireOwner(teachingClass.isManagedBy(SecurityUtils.getUserId()));
 * // 自定义拒绝原因
 * ScopeAssert.require(user.getDeptId() != null, "无权访问该组织数据");
 * </pre>
 *
 * @author ruoyi
 */
public final class ScopeAssert
{
    private static final String DEFAULT_FORBIDDEN_MESSAGE = "无权访问该对象";

    private ScopeAssert()
    {
    }

    /**
     * 校验条件必须成立，否则拒绝访问（统一返回 403）
     *
     * @param condition 校验条件
     * @param message   拒绝原因（不应包含对象存在性信息）
     */
    public static void require(boolean condition, String message)
    {
        if (!condition)
        {
            throw new ForbiddenException(message);
        }
    }

    /**
     * 校验调用方是否为对象的合法归属方（本人、本组织、本教学班）
     *
     * @param condition 归属校验结果
     */
    public static void requireOwner(boolean condition)
    {
        require(condition, DEFAULT_FORBIDDEN_MESSAGE);
    }
}
