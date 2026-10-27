package com.ruoyi.education.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.dto.course.BindingSummaryResponse;
import com.ruoyi.education.dto.course.ClassroomPrepResponse;
import com.ruoyi.education.service.IEducationCourseService;

/**
 * 课程绑定临时接口（待第 2 组 API 替换）。
 */
@RestController
@RequestMapping("/education/course/bindings")
public class EducationCourseController extends BaseController
{
    @Autowired
    private IEducationCourseService educationCourseService;

    @GetMapping("/mine")
    public AjaxResult mine()
    {
        List<BindingSummaryResponse> data = educationCourseService.listMyBindings(SecurityUtils.getUserId());
        return success(data);
    }

    @GetMapping("/{bindingId}/classroom-prep")
    public AjaxResult classroomPrep(@PathVariable Long bindingId)
    {
        ClassroomPrepResponse data = educationCourseService.getClassroomPrep(bindingId, SecurityUtils.getUserId());
        return success(data);
    }
}
