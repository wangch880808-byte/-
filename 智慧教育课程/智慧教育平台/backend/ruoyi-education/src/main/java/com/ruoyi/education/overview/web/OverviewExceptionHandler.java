package com.ruoyi.education.overview.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.overview.exception.OverviewException;

/**
 * 总览接口独立异常处理。
 *
 * <p>本类不 import 或复用 6-02 的 AnnouncementExceptionHandler，独立处理
 * 总览异常。业务异常映射为若依统一响应体的 code；当前与 6-02 合同一致，
 * 使用 HTTP 200 + body.code 表达 400/403 等业务码。</p>
 *
 * <p>只处理总览业务异常和查询参数类型错误，不改变若依其他异常的既有行为。</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE)
public class OverviewExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(OverviewExceptionHandler.class);

    /**
     * 将总览业务异常转换为统一响应。
     *
     * @param e 总览业务异常
     * @return 含业务码的响应体
     */
    @ExceptionHandler(OverviewException.class)
    public AjaxResult handleOverviewException(OverviewException e)
    {
        if (e.getCode() >= 500)
        {
            log.error("总览接口服务端异常: code={}", e.getCode(), e);
        }
        return AjaxResult.error(e.getCode(), e.getMessage());
    }

    /**
     * 将查询参数类型错误映射为 400。
     *
     * @param e 参数类型转换异常
     * @return code=400 的响应体
     */
    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public AjaxResult handleTypeMismatch(MethodArgumentTypeMismatchException e)
    {
        return AjaxResult.error(400, "参数格式错误: " + e.getName());
    }
}
