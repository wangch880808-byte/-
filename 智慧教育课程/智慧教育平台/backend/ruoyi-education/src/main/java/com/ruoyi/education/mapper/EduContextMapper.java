package com.ruoyi.education.mapper;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;
public interface EduContextMapper {
 List<Map<String,Object>> courses(@Param("userId") Long userId,@Param("role") String role);
 List<Map<String,Object>> classes(@Param("courseId") Long courseId,@Param("userId") Long userId,@Param("role") String role);
}
