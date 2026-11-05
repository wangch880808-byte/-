package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.education.service.IEduPortalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 学期管理：三端页面「学期管理」页使用。
 * 路径与字段按 frontend/pages-admin.js 的调用约定，返回若依标准的 {code,msg,rows,total} / {code,msg,data}。
 */
@RestController
@RequestMapping("/education/term")
public class EduTermController extends BaseController {

    @Autowired
    private IEduPortalService portalService;

    @GetMapping("/list")
    public TableDataInfo list(String keyword) {
        startPage();
        return getDataTable(portalService.termList(keyword));
    }

    @PostMapping
    public AjaxResult add(@RequestBody Map<String, Object> body) {
        return success(portalService.createTerm(body));
    }

    @PutMapping("/{id}")
    public AjaxResult edit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        portalService.updateTerm(id, body);
        return success();
    }

    @PutMapping("/{id}/current")
    public AjaxResult setCurrent(@PathVariable Long id) {
        portalService.setCurrentTerm(id);
        return success();
    }
}
