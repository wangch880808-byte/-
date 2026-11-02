package com.ruoyi.education.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Vue 前端入口跳转。
 *
 * <p>Spring Boot 的欢迎页只覆盖站点根路径，嵌套目录 {@code /app/} 不会被解析成
 * {@code index.html}，直接访问会得到 “No static resource app.”。这里显式转发到
 * 打包进 static 的入口文件，保证 {@code /app/} 可用。</p>
 */
@Controller
public class WebEntryController
{
    @GetMapping({"/app", "/app/"})
    public String appEntry()
    {
        return "forward:/app/index.html";
    }
}
