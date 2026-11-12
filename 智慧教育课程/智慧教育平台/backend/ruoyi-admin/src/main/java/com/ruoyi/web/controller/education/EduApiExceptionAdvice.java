package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.exception.EduForbiddenException;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

/**
 * 把「当前账号没有该教学对象范围」翻译成若依约定的 403（HTTP 200 + body code=403）。
 * 前端 edu-api.js 靠这个 code 显示「无权限」状态，而不是当作普通失败重试。
 *
 * <p>必须比其他 advice 先匹配，否则会被若依 GlobalExceptionHandler 的兜底处理当作 500。</p>
 */
@Order(Ordered.HIGHEST_PRECEDENCE)
@RestControllerAdvice
public class EduApiExceptionAdvice {

    @ExceptionHandler(EduForbiddenException.class)
    public AjaxResult handleForbidden(EduForbiddenException exception) {
        return AjaxResult.error(403, exception.getMessage());
    }
}
