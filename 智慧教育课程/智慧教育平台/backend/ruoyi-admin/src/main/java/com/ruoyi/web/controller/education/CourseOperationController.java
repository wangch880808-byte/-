package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.education.service.IEduPortalService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 课程操作日志：课程创建者与团队教师可见，覆盖课程与教学班相关操作。
 */
@RestController
@RequestMapping("/education/course/{courseId}/operations")
public class CourseOperationController extends BaseController {

    private final IEduPortalService portalService;

    public CourseOperationController(IEduPortalService portalService) {
        this.portalService = portalService;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('education:course:list')")
    public TableDataInfo list(@PathVariable Long courseId) {
        startPage();
        return getDataTable(portalService.courseOperations(courseId));
    }
}
