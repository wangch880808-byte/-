package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.education.service.EduCourseManagementService;
import com.ruoyi.education.service.IEduPortalService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/education/course/{courseId}")
@PreAuthorize("@ss.hasPermi('education:course:edit')")
public class EduCourseManagementController extends BaseController {
    private final EduCourseManagementService management;
    private final IEduPortalService portal;

    public EduCourseManagementController(EduCourseManagementService management, IEduPortalService portal) {
        this.management = management;
        this.portal = portal;
    }

    @GetMapping("/team")
    public AjaxResult team(@PathVariable Long courseId) { return success(management.team(courseId)); }

    @GetMapping("/team/candidates")
    public TableDataInfo candidates(@PathVariable Long courseId, String keyword,
            @RequestParam(defaultValue = "1") int pageNum, @RequestParam(defaultValue = "20") int pageSize) {
        return getDataTable(management.teacherCandidates(courseId, keyword, pageNum, pageSize));
    }

    @PostMapping("/team")
    public AjaxResult saveTeacher(@PathVariable Long courseId, @Valid @RequestBody TeamTeacher body) {
        management.saveTeacher(courseId, body.userId(), body.canManageStudents(), body.canExport(), body.permissions());
        List<Map<String, Object>> users = portal.userBrief(List.of(body.userId()));
        EduOperLog.record("设置教师权限：" + (users.isEmpty() ? "教师#" + body.userId() : EduOperLog.person(users.get(0))),
                BusinessType.UPDATE);
        return success();
    }

    @DeleteMapping("/team/{userId}")
    public AjaxResult removeTeacher(@PathVariable Long courseId, @PathVariable Long userId) {
        List<Map<String, Object>> users = portal.userBrief(List.of(userId));
        EduOperLog.record("移除课程教师：" + (users.isEmpty() ? "教师#" + userId : EduOperLog.person(users.get(0))),
                BusinessType.DELETE);
        management.removeTeacher(courseId, userId);
        return success();
    }

    @PutMapping("/teaching-classes/{classId}")
    public AjaxResult renameClass(@PathVariable Long courseId, @PathVariable Long classId,
            @Valid @RequestBody RenameClass body) {
        management.renameClass(courseId, classId, body.name());
        EduOperLog.record("修改教学班名称：" + body.name(), BusinessType.UPDATE);
        return success();
    }

    @DeleteMapping("/teaching-classes/{classId}")
    public AjaxResult deleteClass(@PathVariable Long courseId, @PathVariable Long classId) {
        String name = portal.className(classId);
        management.deleteClass(courseId, classId);
        EduOperLog.record("删除教学班" + (name == null ? "" : "：" + name), BusinessType.DELETE);
        return success();
    }

    @PostMapping("/teaching-classes/{classId}/students/change")
    public AjaxResult changeStudents(@PathVariable Long courseId, @PathVariable Long classId,
            @Valid @RequestBody ChangeStudents body) {
        management.changeStudents(courseId, classId, body.userIds(), body.targetClassId());
        String names = EduOperLog.students(portal.userBrief(body.userIds()));
        String target = body.targetClassId() == null ? null : portal.className(body.targetClassId());
        EduOperLog.record("调整教学班成员：" + names + (target == null ? "（移出教学班）" : " 至 " + target),
                BusinessType.UPDATE);
        return success();
    }

    public record TeamTeacher(@NotNull @Positive Long userId, @NotNull Boolean canManageStudents, @NotNull Boolean canExport, String permissions) {}
    public record RenameClass(@NotBlank @Size(max = 160) String name) {}
    public record ChangeStudents(@NotEmpty @Size(max = 1000) List<@NotNull @Positive Long> userIds,
            @Positive Long targetClassId) {}
}
