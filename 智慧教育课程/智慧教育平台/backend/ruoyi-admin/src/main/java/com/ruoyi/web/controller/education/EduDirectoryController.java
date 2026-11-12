package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.education.service.IEduPortalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教学基础数据：正式课程、教师、学生、组织架构。
 * 管理员端「教师管理 / 学生管理 / 组织架构管理」和教师端建课下拉使用。
 */
@RestController
public class EduDirectoryController extends BaseController {

    @Autowired
    private IEduPortalService portalService;

    @GetMapping("/education/official-course/list")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasAnyPermi('education:course:list,education:class:list')")
    public TableDataInfo officialCourses(String keyword) {
        startPage();
        return getDataTable(portalService.officialCourses(keyword));
    }

    @GetMapping("/education/teacher/list")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public TableDataInfo teachers(String keyword, Long deptId) {
        startPage();
        return getDataTable(portalService.teacherList(keyword, deptId));
    }

    @GetMapping("/education/teacher/{userId}/classes")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public TableDataInfo teacherClasses(@PathVariable Long userId) {
        return getDataTable(portalService.teacherClasses(userId));
    }

    @GetMapping("/education/student/list")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public TableDataInfo students(String keyword) {
        startPage();
        return getDataTable(portalService.studentList(keyword));
    }

    @GetMapping("/education/student/{userId}/classes")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public TableDataInfo studentClasses(@PathVariable Long userId) {
        return getDataTable(portalService.studentClasses(userId));
    }

    @GetMapping("/education/organization/tree")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public AjaxResult organizationTree() {
        return success(portalService.deptTree());
    }
}
