package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.exception.ForbiddenException;
import com.ruoyi.education.service.IEduPortalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 学期管理：三端页面「学期管理」页使用。
 * 路径与字段按 frontend/pages-admin.js 的调用约定，返回若依标准的 {code,msg,rows,total} / {code,msg,data}。
 */
@RestController
@RequestMapping("/education/term")
public class EduTermController extends BaseController {

    @Autowired
    private IEduPortalService portalService;

    @Autowired
    private EduSchoolScope schoolScope;

    @GetMapping("/list")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:term:list')")
    public TableDataInfo list(String keyword, Long schoolId) {
        if (!portalService.isAdmin()) {
            throw new ForbiddenException("只有管理员可以查看学期");
        }
        Set<Long> schools = schoolScope.schools(schoolScope.visibleDepartments("education:term:list"))
                .stream().map(SysDept::getDeptId).collect(Collectors.toSet());
        if (schoolId != null && !schools.contains(schoolId)) {
            throw new ForbiddenException("没有权限查看该学校的学期");
        }
        startPage();
        return getDataTable(portalService.termList(keyword, schoolId == null ? schools : Set.of(schoolId)));
    }

    @GetMapping("/schools")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:term:add')")
    public AjaxResult schools() {
        return success(portalService.termSchools());
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:term:add')")
    @com.ruoyi.common.annotation.Log(title="教学组织与课程",businessType=com.ruoyi.common.enums.BusinessType.INSERT)
    public AjaxResult add(@RequestBody Map<String, Object> body) {
        return success(portalService.createTerm(body));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:term:edit')")
    @com.ruoyi.common.annotation.Log(title="教学组织与课程",businessType=com.ruoyi.common.enums.BusinessType.UPDATE)
    public AjaxResult edit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        portalService.updateTerm(id, body);
        return success();
    }

    @PutMapping("/{id}/current")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:term:current')")
    @com.ruoyi.common.annotation.Log(title="教学组织与课程",businessType=com.ruoyi.common.enums.BusinessType.UPDATE)
    public AjaxResult setCurrent(@PathVariable Long id) {
        portalService.setCurrentTerm(id);
        return success();
    }
}
