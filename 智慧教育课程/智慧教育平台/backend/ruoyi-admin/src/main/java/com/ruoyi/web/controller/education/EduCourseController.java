package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.education.service.IEduPortalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * 课程空间：教师端「我的课程」「课程详情」、学生端「在修课程」使用。
 *
 * <p>课程建一次，可以绑定多个教学班；学生能看到课程的前提是：课程已发布 + 所在教学班有有效绑定。</p>
 */
@RestController
@RequestMapping("/education/course")
public class EduCourseController extends BaseController {

    @Autowired
    private IEduPortalService portalService;

    @GetMapping("/list")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:list')")
    public TableDataInfo list(String keyword) {
        startPage();
        return getDataTable(portalService.courseList(keyword));
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:list')")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(portalService.courseDetail(id));
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:add')")
    @com.ruoyi.common.annotation.Log(title="教学组织与课程",businessType=com.ruoyi.common.enums.BusinessType.INSERT)
    public AjaxResult add(@RequestBody Map<String, Object> body) {
        return success(portalService.createCourse(body));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:edit')")
    @com.ruoyi.common.annotation.Log(title="教学组织与课程",businessType=com.ruoyi.common.enums.BusinessType.UPDATE)
    public AjaxResult edit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        portalService.updateCourse(id, body);
        return success();
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:edit')")
    @com.ruoyi.common.annotation.Log(title="课程空间",businessType=com.ruoyi.common.enums.BusinessType.DELETE)
    public AjaxResult delete(@PathVariable Long id) {
        portalService.deleteCourse(id);
        return success();
    }

    @PutMapping("/{id}/status")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:publish')")
    @com.ruoyi.common.annotation.Log(title="教学组织与课程",businessType=com.ruoyi.common.enums.BusinessType.UPDATE)
    public AjaxResult changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        portalService.updateCourseStatus(id, body == null ? null : String.valueOf(body.get("status")));
        return success();
    }

    @PostMapping("/{id}/classes")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:bind')")
    @com.ruoyi.common.annotation.Log(title="教学组织与课程",businessType=com.ruoyi.common.enums.BusinessType.INSERT)
    public AjaxResult bindClass(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long classId = body == null || body.get("classId") == null ? null : Long.valueOf(String.valueOf(body.get("classId")));
        String publishedAt = body == null || body.get("publishedAt") == null ? null : String.valueOf(body.get("publishedAt"));
        portalService.bindClass(id, classId, publishedAt);
        return success();
    }

    @DeleteMapping("/{id}/classes/{bindingId}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:bind')")
    @com.ruoyi.common.annotation.Log(title="教学组织与课程",businessType=com.ruoyi.common.enums.BusinessType.DELETE)
    public AjaxResult unbindClass(@PathVariable Long id, @PathVariable Long bindingId) {
        portalService.unbindClass(id, bindingId);
        return success();
    }
}
