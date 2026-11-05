package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.education.service.IEduPortalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 教学班、班级成员与任课教师授权：管理员「班级管理」页、教师「我的课程 → 绑定教学班」使用。
 */
@RestController
@RequestMapping("/education/teaching-class")
public class EduTeachingClassController extends BaseController {

    @Autowired
    private IEduPortalService portalService;

    @GetMapping("/list")
    public TableDataInfo list(String keyword, Long termId, String status) {
        startPage();
        return getDataTable(portalService.teachingClassList(keyword, termId, status));
    }

    /** 当前教师被授权的教学班（教师端绑定课程时的下拉） */
    @GetMapping("/mine")
    public TableDataInfo mine() {
        return getDataTable(portalService.myTeachingClasses());
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(portalService.teachingClassDetail(id));
    }

    @PostMapping
    public AjaxResult add(@RequestBody Map<String, Object> body) {
        return success(portalService.createTeachingClass(body));
    }

    @PutMapping("/{id}")
    public AjaxResult edit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        portalService.updateTeachingClass(id, body);
        return success();
    }

    /* ---------- 成员 ---------- */

    @GetMapping("/{id}/members")
    public TableDataInfo members(@PathVariable Long id) {
        return getDataTable(portalService.classMembers(id));
    }

    @PostMapping("/{id}/members")
    public AjaxResult addMember(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        portalService.addMember(id, body);
        return success();
    }

    @DeleteMapping("/{id}/members/{studentId}")
    public AjaxResult removeMember(@PathVariable Long id, @PathVariable Long studentId,
                                   @RequestParam(required = false) String reason) {
        portalService.removeMember(id, studentId, reason);
        return success();
    }

    /* ---------- 任课教师授权 ---------- */

    @GetMapping("/{id}/teachers")
    public TableDataInfo teachers(@PathVariable Long id) {
        return getDataTable(portalService.classTeachers(id));
    }

    @PostMapping("/{id}/teachers")
    public AjaxResult addTeacher(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        portalService.addTeacher(id, body);
        return success();
    }

    @DeleteMapping("/{id}/teachers/{userId}")
    public AjaxResult removeTeacher(@PathVariable Long id, @PathVariable Long userId) {
        portalService.removeTeacher(id, userId);
        return success();
    }
}
