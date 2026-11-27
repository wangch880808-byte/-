package com.ruoyi.web.controller.education;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.education.service.IEduPortalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * 课程空间：教师端「我的课程」「课程详情」、学生端「在修课程」使用。
 *
 * <p>教师在课程下维护教学班。学生可以访问其所在教学班的已发布课程。</p>
 */
@RestController
@RequestMapping("/education/course")
public class EduCourseController extends BaseController {

    @Autowired
    private IEduPortalService portalService;

    @GetMapping("/list")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:list')")
    public TableDataInfo list(String keyword, String status) {
        startPage();
        return getDataTable(portalService.courseList(keyword, status));
    }

    @GetMapping("/list/progress")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:list')")
    public AjaxResult studentCourseProgress() {
        return success(portalService.studentCourseProgress());
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:list')")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(portalService.courseDetail(id));
    }

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:add')")
    @com.ruoyi.common.annotation.Log(title="新建课程",businessType=com.ruoyi.common.enums.BusinessType.INSERT)
    public AjaxResult add(@RequestBody Map<String, Object> body) {
        return success(portalService.createCourse(body));
    }

    @PutMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:edit')")
    @com.ruoyi.common.annotation.Log(title="修改课程信息",businessType=com.ruoyi.common.enums.BusinessType.UPDATE)
    public AjaxResult edit(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        portalService.updateCourse(id, body);
        return success();
    }

    @DeleteMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:edit')")
    @com.ruoyi.common.annotation.Log(title="删除课程",businessType=com.ruoyi.common.enums.BusinessType.DELETE)
    public AjaxResult delete(@PathVariable Long id) {
        portalService.deleteCourse(id);
        return success();
    }

    @PutMapping("/{id}/status")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:publish')")
    @com.ruoyi.common.annotation.Log(title="变更课程状态",businessType=com.ruoyi.common.enums.BusinessType.UPDATE)
    public AjaxResult changeStatus(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        portalService.updateCourseStatus(id, body == null ? null : String.valueOf(body.get("status")));
        return success();
    }

    @PostMapping("/{courseId}/teaching-classes")
    @PreAuthorize("@ss.hasPermi('education:course:edit')")
    public AjaxResult createCourseClass(@PathVariable Long courseId, @RequestBody CreateCourseClass body) {
        Long classId = portalService.createCourseClass(courseId, body.name(), body.termId());
        EduOperLog.record("新建教学班：" + body.name(), BusinessType.INSERT);
        return success(Map.of("teachingClassId", classId));
    }

    @GetMapping("/{courseId}/teaching-classes/{classId}/students/sources")
    @PreAuthorize("@ss.hasPermi('education:course:edit')")
    public AjaxResult rosterSources(@PathVariable Long courseId, @PathVariable Long classId) {
        return success(portalService.courseRosterSources(courseId, classId));
    }

    @GetMapping("/{courseId}/teaching-classes/{classId}/students/organizations")
    @PreAuthorize("@ss.hasPermi('education:course:edit')")
    public AjaxResult rosterOrganizations(@PathVariable Long courseId, @PathVariable Long classId) {
        return success(portalService.courseRosterOrganizations(courseId, classId));
    }

    @GetMapping("/{courseId}/teaching-classes/{classId}/students/candidates")
    @PreAuthorize("@ss.hasPermi('education:course:edit')")
    public TableDataInfo rosterCandidates(@PathVariable Long courseId, @PathVariable Long classId,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long sourceCourseId,
            @RequestParam(required = false) Long sourceClassId,
            @RequestParam(required = false) Long sourceDeptId,
            @RequestParam(defaultValue = "false") boolean includeDescendants,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return getDataTable(portalService.courseRosterCandidates(
                courseId, classId, keyword, sourceCourseId, sourceClassId, sourceDeptId, includeDescendants, pageNum, pageSize));
    }

    @PostMapping("/{courseId}/teaching-classes/{classId}/students")
    @PreAuthorize("@ss.hasPermi('education:course:edit')")
    public AjaxResult addCourseRosterStudents(@PathVariable Long courseId, @PathVariable Long classId,
            @RequestBody AddCourseRosterStudents body) {
        String result = portalService.addCourseRosterStudents(
                courseId, classId, body.userIds(), body.sourceCourseId(), body.sourceClassId(),
                body.sourceDeptId(), body.includeDescendants(), body.wholeClass());
        String detail = body.wholeClass()
                ? "整班引入，" + result
                : EduOperLog.students(portalService.userBrief(body.userIds()));
        EduOperLog.record("添加教学班学生：" + detail, BusinessType.INSERT);
        return AjaxResult.success((Object) result);
    }

    public record CreateCourseClass(String name, Long termId) {}

    public record AddCourseRosterStudents(List<Long> userIds, Long sourceCourseId, Long sourceClassId,
            Long sourceDeptId, boolean includeDescendants, boolean wholeClass) {}

    @PostMapping("/{id}/classes")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:bind')")
    public AjaxResult bindClass(@PathVariable Long id, @RequestBody Map<String, Object> body) {
        Long classId = body == null || body.get("classId") == null ? null : Long.valueOf(String.valueOf(body.get("classId")));
        String publishedAt = body == null || body.get("publishedAt") == null ? null : String.valueOf(body.get("publishedAt"));
        portalService.bindClass(id, classId, publishedAt);
        String name = classId == null ? null : portalService.className(classId);
        EduOperLog.record("绑定教学班" + (name == null ? "" : "：" + name), BusinessType.INSERT);
        return success();
    }

    @DeleteMapping("/{id}/classes/{bindingId}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:course:bind')")
    public AjaxResult unbindClass(@PathVariable Long id, @PathVariable Long bindingId) {
        String name = portalService.bindingClassName(bindingId);
        portalService.unbindClass(id, bindingId);
        EduOperLog.record("解绑教学班" + (name == null ? "" : "：" + name), BusinessType.DELETE);
        return success();
    }
}
