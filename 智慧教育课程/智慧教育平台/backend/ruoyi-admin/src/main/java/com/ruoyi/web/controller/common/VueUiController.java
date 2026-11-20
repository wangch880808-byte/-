package com.ruoyi.web.controller.common;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

/** 官方 RuoYi-Vue3 构建入口，产物打包于 static/vue。 */
@Controller
public class VueUiController
{
    @GetMapping({ "/vue", "/vue/" })
    public String index()
    {
        return "forward:/vue/index.html";
    }

    /** 旧地址只跳转到唯一前端，不再提供静态版页面。 */
    @GetMapping({ "/login.html", "/admin.html", "/teacher.html", "/student.html" })
    public String retiredEntry()
    {
        return "redirect:/vue/";
    }
}
