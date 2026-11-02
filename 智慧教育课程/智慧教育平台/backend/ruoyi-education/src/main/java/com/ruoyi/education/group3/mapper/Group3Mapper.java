package com.ruoyi.education.group3.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * Group 3 owns resource and prepare data. Course/class queries are read-only
 * authorization inputs until the course contract service is available.
 */
public interface Group3Mapper
{
    List<Map<String, Object>> selectTeacherContexts(@Param("userId") Long userId);

    List<Map<String, Object>> selectStudentContexts(@Param("userId") Long userId);

    Map<String, Object> selectTeacherBinding(
            @Param("userId") Long userId,
            @Param("bindingId") Long bindingId);

    Map<String, Object> selectStudentBinding(
            @Param("userId") Long userId,
            @Param("bindingId") Long bindingId);

    Map<String, Object> selectBinding(@Param("bindingId") Long bindingId);

    List<Map<String, Object>> selectCourseOutline(@Param("courseSpaceId") Long courseSpaceId);

    List<Map<String, Object>> selectKnowledgePoints(@Param("courseSpaceId") Long courseSpaceId);

    List<Map<String, Object>> selectTeacherResources(
            @Param("bindingId") Long bindingId,
            @Param("courseSpaceId") Long courseSpaceId);

    List<Map<String, Object>> selectReadyResourceVersions(
            @Param("bindingId") Long bindingId,
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("userId") Long userId);

    List<Map<String, Object>> selectResourceVersions(@Param("resourceId") Long resourceId);

    List<Map<String, Object>> selectResourcePages(@Param("versionId") Long versionId);

    Map<String, Object> selectTeacherVersion(
            @Param("bindingId") Long bindingId,
            @Param("versionId") Long versionId,
            @Param("userId") Long userId);

    Map<String, Object> selectStudentVisibleVersion(
            @Param("bindingId") Long bindingId,
            @Param("versionId") Long versionId,
            @Param("userId") Long userId);

    Map<String, Object> selectVersionById(@Param("versionId") Long versionId);

    Map<String, Object> selectReleaseById(@Param("releaseId") Long releaseId);

    Map<String, Object> selectActiveRelease(
            @Param("bindingId") Long bindingId,
            @Param("resourceId") Long resourceId);

    Long lockBinding(@Param("bindingId") Long bindingId);

    Long lockResource(@Param("resourceId") Long resourceId);

    Long lockQuestion(@Param("questionId") Long questionId);

    Long lockLesson(@Param("lessonId") Long lessonId);

    int selectNextResourceVersion(@Param("resourceId") Long resourceId);

    int selectNextQuestionVersion(@Param("questionId") Long questionId);

    int insertResource(Map<String, Object> row);

    int insertResourceVersion(Map<String, Object> row);

    int updateResourceVersionStatus(Map<String, Object> row);

    int insertResourcePage(Map<String, Object> row);

    int clearResourcePages(@Param("versionId") Long versionId);

    int endRelease(Map<String, Object> row);

    int insertRelease(Map<String, Object> row);

    int revokeRelease(Map<String, Object> row);

    List<Map<String, Object>> selectStudentMaterials(
            @Param("bindingId") Long bindingId,
            @Param("userId") Long userId);

    List<Map<String, Object>> selectQuestions(
            @Param("bindingId") Long bindingId,
            @Param("courseSpaceId") Long courseSpaceId);

    List<Map<String, Object>> selectQuestionVersions(
            @Param("bindingId") Long bindingId,
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("userId") Long userId);

    Map<String, Object> selectQuestionVersion(
            @Param("bindingId") Long bindingId,
            @Param("versionId") Long versionId,
            @Param("userId") Long userId);

    int insertQuestion(Map<String, Object> row);

    int insertQuestionVersion(Map<String, Object> row);

    int linkQuestionKnowledgePoint(
            @Param("questionId") Long questionId,
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("knowledgePointId") Long knowledgePointId);

    int updateQuestionStatus(
            @Param("bindingId") Long bindingId,
            @Param("questionId") Long questionId,
            @Param("userId") Long userId,
            @Param("status") String status);

    List<Map<String, Object>> selectLessons(@Param("bindingId") Long bindingId);

    Map<String, Object> selectLesson(
            @Param("bindingId") Long bindingId,
            @Param("lessonId") Long lessonId);

    List<Map<String, Object>> selectPlans(
            @Param("bindingId") Long bindingId,
            @Param("userId") Long userId);

    List<Map<String, Object>> selectPlanMaterials(@Param("planId") Long planId);

    List<Map<String, Object>> selectPlanTemplates(@Param("planId") Long planId);

    int selectNextPlanVersion(@Param("lessonId") Long lessonId);

    int insertLessonPlan(Map<String, Object> row);

    int insertPlanMaterial(Map<String, Object> row);

    int insertActivityTemplate(Map<String, Object> row);

    List<Map<String, Object>> selectTeacherPreviewTasks(@Param("bindingId") Long bindingId);

    List<Map<String, Object>> selectStudentPreviewTasks(
            @Param("bindingId") Long bindingId,
            @Param("userId") Long userId);

    Map<String, Object> selectStudentPreviewTask(
            @Param("bindingId") Long bindingId,
            @Param("assignmentId") Long assignmentId,
            @Param("userId") Long userId);

    List<Map<String, Object>> selectStudentPreviewItems(
            @Param("assignmentId") Long assignmentId,
            @Param("includeAnswer") boolean includeAnswer);

    Map<String, Object> selectStudentTarget(
            @Param("assignmentId") Long assignmentId,
            @Param("userId") Long userId);

    Map<String, Object> selectStudentTargetForUpdate(
            @Param("assignmentId") Long assignmentId,
            @Param("userId") Long userId);

    Map<String, Object> selectSubmissionByTarget(@Param("targetId") Long targetId);

    Long selectSubmissionIdByTarget(@Param("targetId") Long targetId);

    int selectNextSubmissionVersion(@Param("submissionId") Long submissionId);

    int insertAssignment(Map<String, Object> row);

    int insertAssignmentItem(Map<String, Object> row);

    int insertAssignmentTargets(Map<String, Object> row);

    int insertSubmission(Map<String, Object> row);

    int insertSubmissionVersion(Map<String, Object> row);

    int insertSubmissionAnswer(Map<String, Object> row);

    int insertLearningEvent(Map<String, Object> row);

    int selectTargetCount(@Param("assignmentId") Long assignmentId);

    int selectSubmissionCount(@Param("assignmentId") Long assignmentId);
}
