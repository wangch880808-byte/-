package com.ruoyi.education.exception;

/**
 * 无权限：当前登录人没有访问该教学对象的范围。
 *
 * <p>与「业务失败」区分开，控制器会把它翻译成 403，前端据此显示「无权限」状态而不是普通报错。</p>
 */
public class EduForbiddenException extends RuntimeException {
    public EduForbiddenException(String message) {
        super(message);
    }
}
