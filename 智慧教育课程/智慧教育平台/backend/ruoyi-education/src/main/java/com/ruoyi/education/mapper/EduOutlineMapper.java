package com.ruoyi.education.mapper;

import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** 仅操作课程自身的章节、知识点；所有语句同时限定课程和对象 ID。 */
public interface EduOutlineMapper {
    Map<String,Object> unit(@Param("courseId") Long courseId, @Param("id") Long id);
    Map<String,Object> point(@Param("courseId") Long courseId, @Param("id") Long id);
    Map<String, Object> content(@Param("courseId") Long courseId, @Param("id") Long id, @Param("point") boolean point);
    int saveContent(@Param("courseId") Long courseId, @Param("id") Long id, @Param("point") boolean point,
            @Param("content") String content);
    java.util.List<Map<String, Object>> listAttachments(@Param("courseId") Long courseId, @Param("id") Long id, @Param("point") boolean point);
    Map<String, Object> attachment(@Param("courseId") Long courseId, @Param("id") Long id, @Param("point") boolean point,
            @Param("fileId") Long fileId);
    int insertAttachment(@Param("courseId") Long courseId, @Param("unitId") Long unitId, @Param("pointId") Long pointId,
            @Param("fileName") String fileName, @Param("fileSize") int fileSize, @Param("fileData") byte[] fileData);
    int deleteAttachment(@Param("courseId") Long courseId, @Param("id") Long id, @Param("point") boolean point,
            @Param("fileId") Long fileId);
    int insertUnit(Map<String,Object> body);
    int updateUnit(Map<String,Object> body);
    int deleteUnit(@Param("courseId") Long courseId, @Param("id") Long id);
    int insertPoint(Map<String,Object> body);
    int updatePoint(Map<String,Object> body);
    int deletePoint(@Param("courseId") Long courseId, @Param("id") Long id);
}
