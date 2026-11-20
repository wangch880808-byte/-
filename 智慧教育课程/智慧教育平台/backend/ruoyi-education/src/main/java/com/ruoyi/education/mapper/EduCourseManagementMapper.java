package com.ruoyi.education.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

public interface EduCourseManagementMapper {
    Map<String, Object> teamAccess(@Param("courseId") Long courseId, @Param("userId") Long userId);
    Map<String, Object> teamClassAccess(@Param("classId") Long classId, @Param("userId") Long userId);
    List<Map<String, Object>> team(@Param("courseId") Long courseId);
    List<Map<String, Object>> teacherCandidates(@Param("courseId") Long courseId,
            @Param("organizationId") Long organizationId, @Param("keyword") String keyword,
            @Param("userId") Long userId);
    int saveTeacher(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("manageStudents") boolean manageStudents, @Param("export") boolean export,
            @Param("operatorId") Long operatorId);
    int removeTeacher(@Param("courseId") Long courseId, @Param("userId") Long userId,
            @Param("operatorId") Long operatorId);
    Long lockClass(@Param("classId") Long classId);
    int activeBindingCount(@Param("classId") Long classId);
    int courseClassHistory(@Param("courseId") Long courseId, @Param("classId") Long classId);
    int renameClass(@Param("classId") Long classId, @Param("name") String name);
    int detachClass(@Param("courseId") Long courseId, @Param("classId") Long classId,
            @Param("operatorId") Long operatorId);
    int archiveClass(@Param("classId") Long classId);
    List<Long> activeStudentIds(@Param("classId") Long classId, @Param("ids") List<Long> ids);
    int endStudents(@Param("classId") Long classId, @Param("ids") List<Long> ids,
            @Param("operatorId") Long operatorId, @Param("reason") String reason);
}
