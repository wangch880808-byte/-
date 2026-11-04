package com.ruoyi.education.service;

import java.util.List;
import com.ruoyi.education.dto.course.BindingSummaryResponse;
import com.ruoyi.education.dto.course.ClassroomPrepResponse;

/**
 * 课程绑定临时服务（待第 2 组 API 替换）。
 */
public interface IEducationCourseService
{
    List<BindingSummaryResponse> listMyBindings(Long userId);

    ClassroomPrepResponse getClassroomPrep(Long bindingId, Long userId);
}
