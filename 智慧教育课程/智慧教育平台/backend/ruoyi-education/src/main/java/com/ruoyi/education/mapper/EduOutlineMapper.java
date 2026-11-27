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
    int updateUnitOrder(@Param("courseId") Long courseId, @Param("id") Long id,
            @Param("parentUnitId") Long parentUnitId, @Param("sortOrder") int sortOrder);
    int deleteUnit(@Param("courseId") Long courseId, @Param("id") Long id);
    int insertPoint(Map<String,Object> body);
    int updatePoint(Map<String,Object> body);
    int deletePoint(@Param("courseId") Long courseId, @Param("id") Long id);

    /* ---------------- 目录草稿（教师编辑草稿，学生读已发布） ---------------- */
    java.util.List<Map<String, Object>> draftUnits(@Param("courseId") Long courseId);
    java.util.List<Map<String, Object>> draftPoints(@Param("courseId") Long courseId);
    int draftCount(@Param("courseId") Long courseId);
    int copyPublishedUnitsToDraft(@Param("courseId") Long courseId);
    int copyPublishedPointsToDraft(@Param("courseId") Long courseId);
    /** 分配不与其正式表冲突的草稿自增 ID（取正式表与草稿表当前最大 id 的下一值）。 */
    Long nextUnitId();
    Long nextPointId();
    int insertUnitDraft(Map<String,Object> body);
    int updateUnitDraft(Map<String,Object> body);
    int updateUnitOrderDraft(@Param("courseId") Long courseId, @Param("id") Long id,
            @Param("parentUnitId") Long parentUnitId, @Param("sortOrder") int sortOrder);
    int deleteUnitDraft(@Param("courseId") Long courseId, @Param("id") Long id);
    int deletePointsOfUnitDraft(@Param("courseId") Long courseId, @Param("unitId") Long unitId);
    int insertPointDraft(Map<String,Object> body);
    int updatePointDraft(Map<String,Object> body);
    int deletePointDraft(@Param("courseId") Long courseId, @Param("id") Long id);
    Map<String, Object> contentDraft(@Param("courseId") Long courseId, @Param("id") Long id, @Param("point") boolean point);
    int saveContentDraft(@Param("courseId") Long courseId, @Param("id") Long id, @Param("point") boolean point,
            @Param("content") String content);
    int publishDeleteMissingUnits(@Param("courseId") Long courseId);
    int publishDeleteMissingSections(@Param("courseId") Long courseId);
    int publishUpsertUnits(@Param("courseId") Long courseId);
    int publishUpsertSections(@Param("courseId") Long courseId);
    int publishDeleteMissingPoints(@Param("courseId") Long courseId);
    int publishUpsertPoints(@Param("courseId") Long courseId);
    int deleteAttachmentsOfMissingPoints(@Param("courseId") Long courseId);
    int deleteAttachmentsOfMissingUnits(@Param("courseId") Long courseId);
    int setOutlineDirty(@Param("courseId") Long courseId, @Param("dirty") int dirty);

    /* ---------------- 学习进度 ---------------- */
    java.util.List<Long> learnedUnitIds(@Param("courseId") Long courseId, @Param("userId") Long userId);
    Map<String, Object> lastViewed(@Param("courseId") Long courseId, @Param("userId") Long userId);
    int markViewed(@Param("courseId") Long courseId, @Param("userId") Long userId, @Param("unitId") Long unitId);
    int clearLearned(@Param("courseId") Long courseId, @Param("userId") Long userId, @Param("unitId") Long unitId);
    java.util.List<Map<String, Object>> courseListProgress(@Param("userId") Long userId,
            @Param("courseIds") java.util.List<Long> courseIds);
}
