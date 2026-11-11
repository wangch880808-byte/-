package com.ruoyi.education.mapper;

import com.ruoyi.education.domain.EduClassTeacher;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface EduClassTeacherMapper {
    List<EduClassTeacher> selectByClassId(@Param("classId") Long classId);

    EduClassTeacher selectActive(@Param("classId") Long classId, @Param("userId") Long userId);

    int insert(EduClassTeacher record);

    int revoke(@Param("classId") Long classId, @Param("userId") Long userId,
               @Param("endedByUserId") Long endedByUserId);
}
