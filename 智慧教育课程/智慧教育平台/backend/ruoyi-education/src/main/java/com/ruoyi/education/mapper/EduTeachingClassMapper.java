package com.ruoyi.education.mapper;

import com.ruoyi.education.domain.EduTeachingClass;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface EduTeachingClassMapper {
    List<EduTeachingClass> selectList(EduTeachingClass query);

    List<EduTeachingClass> selectListByTeacherUserId(@Param("userId") Long userId);

    List<EduTeachingClass> selectListByStudentUserId(@Param("userId") Long userId);

    EduTeachingClass selectById(@Param("id") Long id);

    int insert(EduTeachingClass teachingClass);

    int update(EduTeachingClass teachingClass);
}
