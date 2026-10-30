package com.ruoyi.education.overview.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.overview.dto.OverviewWindow;
import com.ruoyi.education.overview.security.OverviewIdentityProvider;
import com.ruoyi.education.overview.service.OverviewQueryService;
import com.ruoyi.education.overview.support.OverviewWindowFactory;

/**
 * 三端总览聚合接口。
 *
 * <p>控制器只从服务端身份提供者取用户/组织，不接受客户端传入 userId/studentId。
 * 具体数据由 {@link OverviewQueryService} 通过端口聚合；端口缺失时返回
 * UNAVAILABLE item，不返回假数据。</p>
 *
 * <p>TODO：`education:overview:teacher`、`education:overview:student`、
 * `education:overview:admin` 权限串待第 1 组配置菜单/角色后联调；
 * 解除条件为权限数据落库、真实登录可用，并完成角色拦截验证。</p>
 */
@RestController
@RequestMapping("/education")
public class OverviewController
{
    private final OverviewQueryService queryService;

    private final OverviewWindowFactory windowFactory;

    private final OverviewIdentityProvider identityProvider;

    /**
     * 创建总览控制器。
     *
     * @param queryService     聚合服务
     * @param windowFactory    时间窗工厂
     * @param identityProvider 可信身份提供者
     */
    public OverviewController(OverviewQueryService queryService,
            OverviewWindowFactory windowFactory,
            OverviewIdentityProvider identityProvider)
    {
        this.queryService = queryService;
        this.windowFactory = windowFactory;
        this.identityProvider = identityProvider;
    }

    /**
     * 教师教学总览。
     *
     * @param from    可选开始时间，RFC 3339 UTC
     * @param to      可选结束时间，RFC 3339 UTC
     * @param classId 可选教学班过滤；只能传本人授权班级
     * @return 统一响应体，data 为总览响应
     */
    @PreAuthorize("@ss.hasPermi('education:overview:teacher')")
    @GetMapping("/teacher/overview")
    public AjaxResult teacherOverview(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) Long classId)
    {
        OverviewWindow window = windowFactory.fromQuery(from, to);
        Long userId = identityProvider.currentUserId();
        return AjaxResult.success(queryService.teacherOverview(userId, classId, window));
    }

    /**
     * 学生今日待办。
     *
     * @param from 可选开始时间，RFC 3339 UTC
     * @param to   可选结束时间，RFC 3339 UTC
     * @return 统一响应体，data 为总览响应
     */
    @PreAuthorize("@ss.hasPermi('education:overview:student')")
    @GetMapping("/student/today-todos")
    public AjaxResult studentTodayTodos(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to)
    {
        OverviewWindow window = windowFactory.fromQuery(from, to);
        Long userId = identityProvider.currentUserId();
        return AjaxResult.success(queryService.studentTodayTodos(userId, window));
    }

    /**
     * 管理员管理总览。
     *
     * @param from 可选开始时间，RFC 3339 UTC
     * @param to   可选结束时间，RFC 3339 UTC
     * @return 统一响应体，data 为总览响应
     */
    @PreAuthorize("@ss.hasPermi('education:overview:admin')")
    @GetMapping("/admin/overview")
    public AjaxResult adminOverview(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to)
    {
        OverviewWindow window = windowFactory.fromQuery(from, to);
        Long organizationId = identityProvider.currentOrganizationId();
        return AjaxResult.success(queryService.adminOverview(organizationId, window));
    }

    /**
     * 管理员待办事项。
     *
     * <p>来源未确认前只返回 NO_DATA/UNAVAILABLE，不返回自造待办。</p>
     *
     * @param from 可选开始时间，RFC 3339 UTC
     * @param to   可选结束时间，RFC 3339 UTC
     * @return 统一响应体，data 为总览响应
     */
    @PreAuthorize("@ss.hasPermi('education:overview:admin')")
    @GetMapping("/admin/todos")
    public AjaxResult adminTodos(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to)
    {
        OverviewWindow window = windowFactory.fromQuery(from, to);
        Long organizationId = identityProvider.currentOrganizationId();
        return AjaxResult.success(queryService.adminTodos(organizationId, window));
    }
}
