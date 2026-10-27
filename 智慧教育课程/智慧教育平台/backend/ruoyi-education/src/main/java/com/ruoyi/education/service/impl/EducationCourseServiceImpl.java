package com.ruoyi.education.service.impl;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.domain.course.BindingContext;
import com.ruoyi.education.dto.course.BindingSummaryResponse;
import com.ruoyi.education.dto.course.ClassroomPrepResponse;
import com.ruoyi.education.mapper.EduSupportMapper;
import com.ruoyi.education.port.CourseAccessPort;
import com.ruoyi.education.port.ResourceAccessPort;
import com.ruoyi.education.service.IEducationCourseService;

/**
 * 课程绑定临时服务实现。
 */
@Service
public class EducationCourseServiceImpl implements IEducationCourseService
{
    @Autowired
    private CourseAccessPort courseAccessPort;

    @Autowired
    private ResourceAccessPort resourceAccessPort;

    @Autowired
    private EduSupportMapper eduSupportMapper;

    @Override
    public List<BindingSummaryResponse> listMyBindings(Long userId)
    {
        List<BindingSummaryResponse> result = new ArrayList<>();
        for (BindingContext binding : courseAccessPort.listTeacherBindings(userId))
        {
            result.add(toSummary(binding, "TEACHER"));
        }
        for (BindingContext binding : courseAccessPort.listStudentBindings(userId))
        {
            result.add(toSummary(binding, "STUDENT"));
        }
        return result;
    }

    @Override
    public ClassroomPrepResponse getClassroomPrep(Long bindingId, Long userId)
    {
        if (!courseAccessPort.isTeacherOfBinding(userId, bindingId))
        {
            throw new ServiceException("无任课权限", HttpStatus.FORBIDDEN);
        }
        Map<String, Object> release = eduSupportMapper.selectDefaultSlideRelease(bindingId);
        if (release == null || release.isEmpty())
        {
            throw new ServiceException("未找到可开课的课件发布记录（待第3组提供资源列表 API）",
                    HttpStatus.NOT_FOUND);
        }
        Long resourceVersionId = toLong(release.get("resource_version_id"));
        ClassroomPrepResponse prep = new ClassroomPrepResponse();
        prep.setBindingId(bindingId);
        prep.setReleaseId(toLong(release.get("release_id")));
        prep.setResourceVersionId(resourceVersionId);
        prep.setResourceTitle(String.valueOf(release.get("resource_title")));
        prep.setTotalPages(resourceAccessPort.countPages(resourceVersionId));

        Map<String, Object> lessonPlan = eduSupportMapper.selectLatestLessonPlan(bindingId);
        if (lessonPlan != null)
        {
            prep.setLessonId(toLong(lessonPlan.get("lesson_id")));
            prep.setPlanId(toLong(lessonPlan.get("plan_id")));
        }
        Map<String, Object> quiz = eduSupportMapper.selectDefaultQuizTemplate(bindingId);
        if (quiz != null)
        {
            prep.setDefaultQuestionId(toLong(quiz.get("question_id")));
            prep.setDefaultQuestionVersionId(toLong(quiz.get("question_version_id")));
        }
        return prep;
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        return Long.parseLong(String.valueOf(value));
    }

    private BindingSummaryResponse toSummary(BindingContext binding, String role)
    {
        BindingSummaryResponse summary = new BindingSummaryResponse();
        summary.setBindingId(binding.getBindingId());
        summary.setTeachingClassId(binding.getTeachingClassId());
        summary.setCourseSpaceId(binding.getCourseSpaceId());
        summary.setCourseTitle(binding.getCourseTitle());
        summary.setClassName(binding.getClassName());
        summary.setRole(role);
        return summary;
    }
}
