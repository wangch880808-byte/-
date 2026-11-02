package com.ruoyi.education.course.web;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.course.exception.AnnouncementException;
import com.ruoyi.education.course.exception.EducationException;

/**
 * 将课程沟通业务异常转换为若依统一响应体中的业务码。
 *
 * <p>前端按 HTTP 200 + {@code code} 处理 400/403/404/409/503；
 * 不使用另建 Controller，也不改变若依其他异常的既有行为。</p>
 *
 * <p>总览的局部 {@code OverviewExceptionHandler} 优先级更高，只接管
 * {@code OverviewController} 的异常；本类继续处理其余 EducationException，
 * 两者职责按控制器作用域和异常类型明确分离。</p>
 */
@RestControllerAdvice
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class AnnouncementExceptionHandler
{
    private static final Logger log = LoggerFactory.getLogger(AnnouncementExceptionHandler.class);

    @ExceptionHandler(EducationException.class)
    public AjaxResult handleAnnouncementException(EducationException e)
    {
        if (e.getCode() >= 500)
        {
            String scope = e instanceof AnnouncementException ? "公告接口" : "学生通知接口";
            log.error("{}服务端异常: code={}", scope, e.getCode(), e);
        }
        return AjaxResult.error(e.getCode(), e.getMessage());
    }
}
