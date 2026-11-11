package com.ruoyi.education.mapper;

import com.ruoyi.education.domain.EduCourseClassBinding;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface EduCourseClassBindingMapper {
    // 查某个班当前绑定的课程
    List<EduCourseClassBinding> selectActiveListByClassId(@Param("classId") Long classId);

    EduCourseClassBinding selectById(@Param("id") Long id);

    // 查某门课绑定了哪些班
    List<EduCourseClassBinding> selectByCourseId(@Param("courseId") Long courseId);

    int insert(EduCourseClassBinding binding);

    // 解绑：把 ended_at 设成当前时间
    int endBinding(@Param("id") Long id, @Param("endedByUserId") Long endedByUserId);
}
