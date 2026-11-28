package com.ruoyi.web.controller.education;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.course.exception.AnnouncementNotFoundException;
import com.ruoyi.education.course.exception.NoticeNotFoundException;
import com.ruoyi.education.course.web.AnnouncementExceptionHandler;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.overview.controller.OverviewController;
import com.ruoyi.education.overview.dto.OverviewWindow;
import com.ruoyi.education.overview.exception.OverviewAccessDeniedException;
import com.ruoyi.education.overview.security.OverviewIdentityProvider;
import com.ruoyi.education.overview.service.OverviewQueryService;
import com.ruoyi.education.overview.support.OverviewWindowFactory;
import com.ruoyi.education.overview.web.OverviewExceptionHandler;
import com.ruoyi.framework.web.exception.GlobalExceptionHandler;

/**
 * 总览 Advice 与新主线真实异常处理链的共存验证。
 *
 * <p>本测试在 ruoyi-admin 中通过 standalone MockMvc 同时加载真实
 * {@link EduApiExceptionAdvice}、{@link OverviewExceptionHandler}、
 * {@link AnnouncementExceptionHandler} 和 {@link GlobalExceptionHandler}，
 * 不启动 Spring 容器、不连接数据库、不依赖真实登录或角色拦截。</p>
 *
 * <p>验证重点：总览控制器内由总览 advice 接管业务 400/403 与参数类型错误；
 * 非总览参数类型错误回归全局 advice；公告/通知 404 与第 2 组
 * {@link EduForbiddenException} 继续由各自真实处理器转换。</p>
 */
class OverviewAdviceCompatibilityTest
{
    private static final OverviewWindow WINDOW = OverviewWindow.of(
            Instant.parse("2026-11-18T00:00:00Z"),
            Instant.parse("2026-11-19T00:00:00Z"));

    private OverviewQueryService queryService;

    private OverviewWindowFactory windowFactory;

    private OverviewIdentityProvider identityProvider;

    private MockMvc mockMvc;

    /**
     * 组装总览控制器、真实 advice 链和一个无数据库的探针控制器。
     */
    @BeforeEach
    void setUp()
    {
        queryService = mock(OverviewQueryService.class);
        windowFactory = mock(OverviewWindowFactory.class);
        identityProvider = mock(OverviewIdentityProvider.class);
        OverviewController overviewController = new OverviewController(queryService,
                windowFactory, identityProvider);
        mockMvc = MockMvcBuilders.standaloneSetup(overviewController, new ProbeController())
                .setControllerAdvice(new EduApiExceptionAdvice(),
                        new OverviewExceptionHandler(),
                        new AnnouncementExceptionHandler(),
                        new GlobalExceptionHandler())
                .build();
    }

    /**
     * 总览业务校验异常必须由总览 advice 映射为业务 400。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void overviewValidationMapsToBusiness400() throws Exception
    {
        when(windowFactory.fromQuery(isNull(), isNull())).thenReturn(WINDOW);

        mockMvc.perform(get("/education/teacher/overview").param("classId", "0"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg", containsString("classId")));
    }

    /**
     * 总览控制器内的参数类型错误由总览 advice 接管，而不是全局处理器。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void overviewTypeMismatchUsesOverviewAdvice() throws Exception
    {
        mockMvc.perform(get("/education/teacher/overview").param("classId", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400))
                .andExpect(jsonPath("$.msg", containsString("参数格式错误: classId")))
                .andExpect(jsonPath("$.msg", not(containsString("请求参数类型不匹配"))));
    }

    /**
     * 总览越权异常必须保持业务 403。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void overviewAccessDeniedMapsToBusiness403() throws Exception
    {
        when(windowFactory.fromQuery(isNull(), isNull())).thenReturn(WINDOW);
        when(identityProvider.currentUserId()).thenReturn(7L);
        when(queryService.teacherOverview(eq(7L), isNull(), eq(WINDOW)))
                .thenThrow(new OverviewAccessDeniedException("无权查看该教学班总览"));

        mockMvc.perform(get("/education/teacher/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("无权查看该教学班总览"));
    }

    /**
     * 非总览参数类型错误必须继续由真实全局处理器处理。
     *
     * <p>当前 main 的全局处理器返回 HTTP 200 + body.code=500 和统一提示；
     * 不能把本测试外缺少全局处理器时的默认 HTTP 400 当作生产契约。</p>
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void nonOverviewTypeMismatchUsesGlobalHandler() throws Exception
    {
        mockMvc.perform(get("/probe/type").param("id", "bad"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(500))
                .andExpect(jsonPath("$.msg", containsString("请求参数类型不匹配")))
                .andExpect(content().string(not(containsString("参数格式错误"))));
    }

    /**
     * 公告与通知的业务 404 仍由教育业务 advice 保持原响应。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void announcementAndNoticeNotFoundRemain404() throws Exception
    {
        mockMvc.perform(get("/probe/announcement"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("公告不存在或不可访问"));

        mockMvc.perform(get("/probe/notice"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(404))
                .andExpect(jsonPath("$.msg").value("通知不存在或不可访问"));
    }

    /**
     * 第 2 组 EduForbiddenException 仍由 EduApiExceptionAdvice 转换为 403。
     *
     * @throws Exception MockMvc 调用异常
     */
    @Test
    void eduForbiddenStillMapsTo403() throws Exception
    {
        mockMvc.perform(get("/probe/forbidden"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403))
                .andExpect(jsonPath("$.msg").value("你没有该教学班权限"));
    }

    /**
     * 不属于总览控制器的探针端点，用于验证非总览处理链。
     */
    @RestController
    static class ProbeController
    {
        /**
         * 触发全局参数类型错误处理。
         *
         * @param id 非数字参数
         * @return 成功响应；类型错误由 advice 转换
         */
        @GetMapping("/probe/type")
        public AjaxResult type(@RequestParam Long id)
        {
            return AjaxResult.success(id);
        }

        /**
         * 模拟公告不存在。
         *
         * @return 不返回；抛出公告 404
         */
        @GetMapping("/probe/announcement")
        public AjaxResult announcement()
        {
            throw new AnnouncementNotFoundException();
        }

        /**
         * 模拟通知不存在。
         *
         * @return 不返回；抛出通知 404
         */
        @GetMapping("/probe/notice")
        public AjaxResult notice()
        {
            throw new NoticeNotFoundException();
        }

        /**
         * 模拟第 2 组对象级越权。
         *
         * @return 不返回；抛出 EduForbiddenException
         */
        @GetMapping("/probe/forbidden")
        public AjaxResult forbidden()
        {
            throw new EduForbiddenException("你没有该教学班权限");
        }
    }
}
