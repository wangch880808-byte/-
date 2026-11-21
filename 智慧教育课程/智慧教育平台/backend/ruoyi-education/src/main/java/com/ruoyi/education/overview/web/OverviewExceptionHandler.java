package com.ruoyi.education.overview.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.overview.controller.OverviewController;
import com.ruoyi.education.overview.exception.OverviewException;

/**
 * 总览接口局部异常处理。
 *
 * <p>6-02 已合并，公共 {@code AnnouncementExceptionHandler} 负责其
 * {@link com.ruoyi.education.course.exception.EducationException} 链。本类只挂载在
 * {@link OverviewController} 上，明确接管总览业务异常和总览查询参数类型错误，
 * 避免全局 advice 对总览异常产生同优先级竞争，也避免改变其他组接口的
 * 类型错误语义。</p>
 *
 * <p>业务异常仍映射为若依统一响应体的 code，保持 HTTP 200 + body.code；
 * 单指标 UNAVAILABLE 语义不变。</p>
 */
@RestControllerAdvice(assignableTypes = OverviewController.class)
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
