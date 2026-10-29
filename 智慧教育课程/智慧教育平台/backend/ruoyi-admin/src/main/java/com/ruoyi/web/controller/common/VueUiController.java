package com.ruoyi.web.controller.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * Vue 前端入口（构建产物打包在 static/vue）。
 * 只处理 /vue 和 /vue/ 两个入口，静态资源仍由 Spring 静态资源处理器提供。
 */
@Controller
public class VueUiController
{
    @GetMapping({ "/vue", "/vue/" })
    public String index()
    {
        return "forward:/vue/index.html";
    }
}
