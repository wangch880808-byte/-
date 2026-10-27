package com.ruoyi.education.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.domain.course.BindingContext;

/**
 * 教学支撑查询（课程成员、资源发布、题目快照）。
 */
public interface EduSupportMapper
{
    int countTeacherOfBinding(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    int countActiveStudentOfBinding(@Param("userId") Long userId, @Param("bindingId") Long bindingId);

    BindingContext selectBindingContext(@Param("bindingId") Long bindingId);

    List<BindingContext> selectTeacherBindings(@Param("userId") Long userId);

    List<BindingContext> selectStudentBindings(@Param("userId") Long userId);

    int countValidRelease(@Param("bindingId") Long bindingId,
            @Param("releaseId") Long releaseId,
            @Param("resourceVersionId") Long resourceVersionId);

    int countResourcePage(@Param("resourceVersionId") Long resourceVersionId,
            @Param("pageNo") Integer pageNo);

    int countPagesByVersion(@Param("resourceVersionId") Long resourceVersionId);

    Map<String, Object> selectQuestionVersionRow(@Param("questionVersionId") Long questionVersionId);

    List<Map<String, Object>> selectActiveStudentsForBinding(@Param("bindingId") Long bindingId);

    Map<String, Object> selectDefaultSlideRelease(@Param("bindingId") Long bindingId);

    Map<String, Object> selectLatestLessonPlan(@Param("bindingId") Long bindingId);

    Map<String, Object> selectDefaultQuizTemplate(@Param("bindingId") Long bindingId);
}
