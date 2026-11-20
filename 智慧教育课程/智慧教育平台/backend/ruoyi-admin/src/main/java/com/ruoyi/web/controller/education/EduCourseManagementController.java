package com.ruoyi.web.controller.education;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.education.service.EduCourseManagementService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/education/course/{courseId}")
@PreAuthorize("@ss.hasPermi('education:course:edit')")
public class EduCourseManagementController extends BaseController {
    private final EduCourseManagementService management;

    public EduCourseManagementController(EduCourseManagementService management) { this.management = management; }

    @GetMapping("/team")
    public AjaxResult team(@PathVariable Long courseId) { return success(management.team(courseId)); }

    @GetMapping("/team/candidates")
    public TableDataInfo candidates(@PathVariable Long courseId, String keyword,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return getDataTable(management.teacherCandidates(courseId, keyword, pageNum, pageSize));
    }

    @PostMapping("/team")
    @Log(title = "课程教师团队", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    public AjaxResult saveTeacher(@PathVariable Long courseId, @Valid @RequestBody TeamTeacher body) {
        management.saveTeacher(courseId, body.userId(), body.canManageStudents(), body.canExport());
        return success();
    }

    @DeleteMapping("/team/{userId}")
    @Log(title = "课程教师团队", businessType = BusinessType.DELETE)
    public AjaxResult removeTeacher(@PathVariable Long courseId, @PathVariable Long userId) {
        management.removeTeacher(courseId, userId);
        return success();
    }

    @PutMapping("/teaching-classes/{classId}")
    @Log(title = "教学班名称", businessType = BusinessType.UPDATE)
    public AjaxResult renameClass(@PathVariable Long courseId, @PathVariable Long classId,
            @Valid @RequestBody RenameClass body) {
        management.renameClass(courseId, classId, body.name());
        return success();
    }

    @DeleteMapping("/teaching-classes/{classId}")
    @Log(title = "课程教学班", businessType = BusinessType.DELETE)
    public AjaxResult deleteClass(@PathVariable Long courseId, @PathVariable Long classId) {
        management.deleteClass(courseId, classId);
        return success();
    }

    @PostMapping("/teaching-classes/{classId}/students/change")
    @Log(title = "教学班成员调整", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    public AjaxResult changeStudents(@PathVariable Long courseId, @PathVariable Long classId,
            @Valid @RequestBody ChangeStudents body) {
        management.changeStudents(courseId, classId, body.userIds(), body.targetClassId());
        return success();
    }

    public record TeamTeacher(@NotNull @Positive Long userId, @NotNull Boolean canManageStudents, @NotNull Boolean canExport) {}
    public record RenameClass(@NotBlank @Size(max = 160) String name) {}
    public record ChangeStudents(@NotEmpty @Size(max = 1000) List<@NotNull @Positive Long> userIds,
            @Positive Long targetClassId) {}
}
