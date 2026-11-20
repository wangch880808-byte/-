package com.ruoyi.education.mapper;

import com.ruoyi.education.domain.EduClassStudent;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface EduClassStudentMapper {
    List<EduClassStudent> selectByClassId(@Param("classId") Long classId);

    EduClassStudent selectActive(@Param("classId") Long classId, @Param("userId") Long userId);

    int insert(EduClassStudent record);

    int leave(@Param("classId") Long classId, @Param("userId") Long userId,
              @Param("leftByUserId") Long leftByUserId, @Param("reason") String reason);
}
