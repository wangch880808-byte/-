package com.ruoyi.web.controller.education;

import java.util.Map;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.service.EduProgressService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 学生学习进度：章节已学/未学、最近学习位置。
 */
@RestController
@RequestMapping("/education/course/{courseId}/outline")
public class EduProgressController {
    private final EduProgressService progress;

    public EduProgressController(EduProgressService progress) {
        this.progress = progress;
    }

    @GetMapping("/progress")
    @PreAuthorize("@ss.hasPermi('education:course:list')")
    public AjaxResult get(@PathVariable Long courseId) {
        return AjaxResult.success(progress.progress(courseId));
    }

    @PostMapping("/units/{unitId}/view")
    @PreAuthorize("@ss.hasPermi('education:course:list')")
    public AjaxResult view(@PathVariable Long courseId, @PathVariable Long unitId) {
        return AjaxResult.success(progress.view(courseId, unitId));
    }

    @PostMapping("/units/{unitId}/progress")
    @PreAuthorize("@ss.hasPermi('education:course:list')")
    public AjaxResult mark(@PathVariable Long courseId, @PathVariable Long unitId, @RequestBody Map<String, Object> body) {
        boolean learned = !Boolean.FALSE.equals(body.get("learned"));
        return AjaxResult.success(progress.mark(courseId, unitId, learned));
    }
}
