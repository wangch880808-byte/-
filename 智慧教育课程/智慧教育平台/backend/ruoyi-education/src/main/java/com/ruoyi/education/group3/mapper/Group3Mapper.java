package com.ruoyi.education.group3.mapper;

import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/**
 * Group3 自有数据访问：资源、题库、备课、预习、文件夹，以及按课程空间过滤的课程大纲/知识点。
 * 教学班、任课、在班、课程绑定等课程领域数据一律通过
 * {@code com.ruoyi.education.course.api.EduCourseAccessService} 契约取得，
 * 本 Mapper 不再直接查询 {@code edu_teaching_class}、{@code edu_class_teacher}、
 * {@code edu_class_student}、{@code edu_course_class_binding}。
 */
public interface Group3Mapper
{
    List<Map<String, Object>> selectCourseOutline(@Param("courseSpaceId") Long courseSpaceId);

    List<Map<String, Object>> selectOutlineWithStats(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("bindingId") Long bindingId);

    List<Map<String, Object>> selectKnowledgePoints(@Param("courseSpaceId") Long courseSpaceId);

    List<Map<String, Object>> selectUnitQuestions(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("unitId") Long unitId);

    List<Map<String, Object>> selectPointQuestions(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("pointId") Long pointId);

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
            @Param("versionId") Long versionId,
            @Param("courseSpaceId") Long courseSpaceId);

    Map<String, Object> selectStudentVisibleVersion(
            @Param("bindingId") Long bindingId,
            @Param("versionId") Long versionId);

    Map<String, Object> selectVersionById(@Param("versionId") Long versionId);

    Map<String, Object> selectReleaseById(@Param("releaseId") Long releaseId);

    Map<String, Object> selectActiveRelease(
            @Param("bindingId") Long bindingId,
            @Param("resourceId") Long resourceId,
            @Param("source") String source);

    String selectResourceOrigin(
            @Param("resourceId") Long resourceId,
            @Param("courseSpaceId") Long courseSpaceId);

    int endReleasesBySource(Map<String, Object> row);

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

    int updateResource(Map<String, Object> row);

    int updateResourceStatus(Map<String, Object> row);

    /** 更新资料增强设置：下载开关、水印、任务点配置。 */
    int updateResourceEnhance(Map<String, Object> row);

    /** 读取资料的增强设置与媒体信息，供学生端做进度判定。 */
    Map<String, Object> selectResourceTaskInfo(
            @Param("resourceId") Long resourceId,
            @Param("bindingId") Long bindingId);

    /** 学生观看进度 upsert。 */
    int upsertResourceProgress(Map<String, Object> row);

    /** 查询学生在指定绑定的观看进度。 */
    List<Map<String, Object>> selectResourceProgress(
            @Param("bindingId") Long bindingId,
            @Param("userId") Long userId);

    /** 学生在计分讨论话题中的参与次数，供学习进度看板统计。 */
    List<Map<String, Object>> selectStudentDiscussionParticipation(
            @Param("bindingId") Long bindingId,
            @Param("userId") Long userId);

    /** 学生已提交且解析已公布的题目作答，供作答回看与错题本使用。 */
    List<Map<String, Object>> selectStudentAnswerReview(
            @Param("bindingId") Long bindingId,
            @Param("userId") Long userId);

    /** 查询学生在指定教学班的个人笔记。 */
    Map<String, Object> selectStudentNote(
            @Param("bindingId") Long bindingId,
            @Param("userId") Long userId);

    /** 新增或更新学生个人笔记。 */
    int upsertStudentNote(Map<String, Object> row);

    /** 学情契约：按资料汇总全班观看进度。 */
    List<Map<String, Object>> selectResourceProgressStats(@Param("bindingId") Long bindingId);

    /** 学情契约：按预习任务汇总全班提交情况。 */
    List<Map<String, Object>> selectAssignmentSubmissionStats(@Param("bindingId") Long bindingId);

    /** 学情契约：按话题与学生汇总讨论参与。 */
    List<Map<String, Object>> selectDiscussionParticipationStats(@Param("bindingId") Long bindingId);

    /** 学情契约：全班逐题作答记录（含未公布解析的提交事实）。 */
    List<Map<String, Object>> selectSubmissionAnalytics(@Param("bindingId") Long bindingId);

    /** 学情契约：作答记录总条数，用于标记结果是否被截断。 */
    int selectSubmissionAnalyticsCount(@Param("bindingId") Long bindingId);

    int endRelease(Map<String, Object> row);

    int insertRelease(Map<String, Object> row);

    int revokeRelease(Map<String, Object> row);

    List<Map<String, Object>> selectStudentMaterials(
            @Param("bindingId") Long bindingId,
            @Param("userId") Long userId,
            @Param("source") String source);

    List<Map<String, Object>> selectQuestions(
            @Param("bindingId") Long bindingId,
            @Param("courseSpaceId") Long courseSpaceId);

    Map<String, Object> selectQuestionForShare(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("questionId") Long questionId);

    int insertQuestionShare(Map<String, Object> row);

    int reopenQuestionShare(
            @Param("shareId") Long shareId,
            @Param("userId") Long userId);

    int withdrawQuestionShares(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("shareIds") List<Long> shareIds);

    List<Map<String, Object>> selectQuestionShares(
            @Param("keyword") String keyword,
            @Param("questionKind") String questionKind,
            @Param("difficulty") String difficulty,
            @Param("collegeId") Long collegeId,
            @Param("limit") int limit);

    List<Map<String, Object>> selectQuestionShareColleges();

    Map<String, Object> selectQuestionShare(
            @Param("shareId") Long shareId,
            @Param("publishedOnly") boolean publishedOnly);

    int countImportedShare(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("shareId") Long shareId);

    int incrementShareImportCount(@Param("shareId") Long shareId);

    int insertImportedQuestion(Map<String, Object> row);

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

    /** 组卷：按题型/难度/知识点/标签筛选当前有效版本的题目。 */
    List<Map<String, Object>> selectQuestionsForCompose(Map<String, Object> row);

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

    /** 课前准备已删除环节：资料 / 预习任务 / 讨论 / 备课方案的软归档记录。 */
    List<Map<String, Object>> selectRemovedPrepareStages(@Param("bindingId") Long bindingId);

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

    /** 发布去重：同教学班、同课次、同标题、同开放/截止时间且题目数一致的未撤回预习任务。 */
    Long selectDuplicatePreview(
            @Param("bindingId") Long bindingId,
            @Param("lessonId") Long lessonId,
            @Param("title") String title,
            @Param("opensAt") java.time.LocalDateTime opensAt,
            @Param("closesAt") java.time.LocalDateTime closesAt,
            @Param("itemCount") int itemCount);

    Map<String, Object> selectPreviewSummary(@Param("assignmentId") Long assignmentId);

    /** 当前教学班所有未撤回的预习任务（用于给新入班学生补发目标）。 */
    List<Map<String, Object>> selectPublishedPreviewAssignments(@Param("bindingId") Long bindingId);

    int insertAssignmentTargetIfAbsent(
            @Param("assignmentId") Long assignmentId,
            @Param("teachingClassId") Long teachingClassId,
            @Param("userId") Long userId,
            @Param("enrollmentId") Long enrollmentId);

    int deleteAssignmentItems(@Param("assignmentId") Long assignmentId);

    int deleteAssignmentTargets(@Param("assignmentId") Long assignmentId);

    int deletePreviewAssignment(
            @Param("assignmentId") Long assignmentId,
            @Param("bindingId") Long bindingId);

    int selectMaxUnitSort(@Param("courseSpaceId") Long courseSpaceId, @Param("parentUnitId") Long parentUnitId);

    /** 保留策略：清理超过保留期限的 FAILED 资源版本（仅清理失败版本，不影响 READY/PENDING 版本）。 */
    List<Map<String, Object>> selectExpiredFailedVersions(@Param("before") java.time.LocalDateTime before);

    int deleteResourceVersion(@Param("versionId") Long versionId);

    /** 保留策略清理时取出版本下全部预览文件键，用于逐个删除。 */
    List<String> selectResourcePageKeys(@Param("versionId") Long versionId);

    /** 软归档预习任务 */
    int updateAssignmentStatus(Map<String, Object> row);

    /** 软归档备课包 */
    int updateLessonPlanStatus(Map<String, Object> row);

    /** 课前准备已删除资料环节的恢复：重新激活最近一次被删除的 PREPARE 发布。 */
    int restorePrepareRelease(
            @Param("bindingId") Long bindingId,
            @Param("resourceId") Long resourceId);

    int countActivePrepareRelease(
            @Param("bindingId") Long bindingId,
            @Param("resourceId") Long resourceId);

    /* ---------- 资料 / 题库文件夹 ---------- */

    List<Map<String, Object>> selectResourceFolders(@Param("courseSpaceId") Long courseSpaceId);

    List<Map<String, Object>> selectQuestionFolders(@Param("courseSpaceId") Long courseSpaceId);

    int countQuestionFolder(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("folderId") Long folderId);

    int archiveQuestionFolder(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("folderId") Long folderId,
            @Param("archived") boolean archived);

    int insertResourceFolder(Map<String, Object> row);

    int insertQuestionFolder(Map<String, Object> row);

    int updateResourceFolder(Map<String, Object> row);

    int updateQuestionFolder(Map<String, Object> row);

    int deleteResourceFolder(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("folderId") Long folderId);

    int deleteQuestionFolder(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("folderId") Long folderId);

    int moveResourcesToFolder(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("folderId") Long folderId,
            @Param("ids") List<Long> ids);

    int moveQuestionsToFolder(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("folderId") Long folderId,
            @Param("ids") List<Long> ids);

    /* ---------- 讨论区：文件夹 ---------- */

    List<Map<String, Object>> selectDiscussionFolders(@Param("bindingId") Long bindingId);

    int selectNextDiscussionFolderSort(@Param("bindingId") Long bindingId);

    int insertDiscussionFolder(Map<String, Object> row);

    /* ---------- 讨论区：话题 ---------- */

    List<Map<String, Object>> selectDiscussionTopics(
            @Param("bindingId") Long bindingId,
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("folderId") Long folderId,
            @Param("sort") String sort,
            @Param("scope") String scope,
            @Param("view") String view);

    List<Map<String, Object>> selectStudentDiscussionTopics(
            @Param("bindingId") Long bindingId,
            @Param("userId") Long userId,
            @Param("keyword") String keyword,
            @Param("folderId") Long folderId,
            @Param("sort") String sort,
            @Param("scope") String scope);

    Map<String, Object> selectDiscussionTopic(
            @Param("bindingId") Long bindingId,
            @Param("topicId") Long topicId,
            @Param("userId") Long userId);

    int insertDiscussionTopic(Map<String, Object> row);

    int updateDiscussionTopicSettings(Map<String, Object> row);

    int updateDiscussionTopicFlags(Map<String, Object> row);

    int updateDiscussionTopicStatus(
            @Param("bindingId") Long bindingId,
            @Param("topicId") Long topicId,
            @Param("status") String status);

    int updateDiscussionTopicCounters(
            @Param("bindingId") Long bindingId,
            @Param("topicId") Long topicId,
            @Param("replyDelta") int replyDelta,
            @Param("viewDelta") int viewDelta);

    int updateDiscussionTopicLikeCount(
            @Param("topicId") Long topicId,
            @Param("delta") int delta);

    int updateDiscussionTopicFolder(
            @Param("bindingId") Long bindingId,
            @Param("topicId") Long topicId,
            @Param("folderId") Long folderId);

    int updateDiscussionTopicScoreConfig(Map<String, Object> row);

    /* ---------- 讨论区：回复 ---------- */

    List<Map<String, Object>> selectDiscussionPosts(
            @Param("topicId") Long topicId,
            @Param("userId") Long userId);

    Map<String, Object> selectDiscussionPost(
            @Param("topicId") Long topicId,
            @Param("postId") Long postId);

    int insertDiscussionPost(Map<String, Object> row);

    int updateDiscussionPostStatus(
            @Param("topicId") Long topicId,
            @Param("postId") Long postId,
            @Param("status") String status);

    int updateDiscussionPostLikeCount(
            @Param("postId") Long postId,
            @Param("delta") int delta);

    /* ---------- 讨论区：点赞 / 评分 ---------- */

    int selectDiscussionLike(
            @Param("topicId") Long topicId,
            @Param("postId") Long postId,
            @Param("userId") Long userId);

    int insertDiscussionLike(
            @Param("topicId") Long topicId,
            @Param("postId") Long postId,
            @Param("userId") Long userId);

    int deleteDiscussionLike(
            @Param("topicId") Long topicId,
            @Param("postId") Long postId,
            @Param("userId") Long userId);

    java.math.BigDecimal selectDiscussionScore(
            @Param("topicId") Long topicId,
            @Param("userId") Long userId);

    int upsertDiscussionScore(Map<String, Object> row);

    /* ---------- 时间轴环节更新：QUIZ / PLAN 及资料发布时间 ---------- */

    Map<String, Object> selectAssignmentStage(
            @Param("bindingId") Long bindingId,
            @Param("assignmentId") Long assignmentId);

    int updateAssignmentStage(Map<String, Object> row);

    Map<String, Object> selectLessonPlanStage(
            @Param("bindingId") Long bindingId,
            @Param("planId") Long planId);

    int updateLessonPlanStage(Map<String, Object> row);

    int updatePlanTemplateDuration(Map<String, Object> row);

    int updateActiveReleaseTimes(Map<String, Object> row);

    /* ---------- 公共资源库 ---------- */

    List<Map<String, Object>> selectPublicCategories(@Param("kind") String kind);

    List<Map<String, Object>> selectPublicResources(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("resourceKind") String resourceKind,
            @Param("categoryKind") String categoryKind,
            @Param("excludeCategoryKind") String excludeCategoryKind,
            @Param("offset") int offset,
            @Param("limit") int limit);

    int countPublicResources(
            @Param("keyword") String keyword,
            @Param("categoryId") Long categoryId,
            @Param("resourceKind") String resourceKind,
            @Param("categoryKind") String categoryKind,
            @Param("excludeCategoryKind") String excludeCategoryKind);

    Map<String, Object> selectPublicResourceDetail(@Param("id") Long id);

    Map<String, Object> selectPublicResourceVersion(@Param("id") Long id);

    List<Map<String, Object>> selectPublicResourcePages(@Param("versionId") Long versionId);

    int insertPublicResource(Map<String, Object> row);

    int insertPublicResourceVersion(Map<String, Object> row);

    int insertPublicResourcePage(Map<String, Object> row);

    int increasePublicViewCount(@Param("id") Long id);

    int increasePublicDownloadCount(@Param("id") Long id);

    int countActiveReleasedResourceByTitle(
            @Param("courseSpaceId") Long courseSpaceId,
            @Param("bindingId") Long bindingId,
            @Param("title") String title);

    int insertPublicFavorite(Map<String, Object> row);

    int deletePublicFavorite(
            @Param("resourceId") Long resourceId,
            @Param("userId") Long userId);

    int checkPublicFavorite(
            @Param("resourceId") Long resourceId,
            @Param("userId") Long userId);

    List<Map<String, Object>> selectMyPublicFavorites(@Param("userId") Long userId);

    List<Map<String, Object>> selectMyPublicResources(
            @Param("userId") Long userId,
            @Param("offset") int offset,
            @Param("limit") int limit);

    int countMyPublicResources(@Param("userId") Long userId);

    int updatePublicVersionStatus(Map<String, Object> row);

    /* ========== 环节思政融入点 ========== */

    List<Map<String, Object>> selectStageIdeologyPoints(
            @Param("bindingId") Long bindingId,
            @Param("stageKind") String stageKind,
            @Param("stageId") Long stageId);

    List<Map<String, Object>> selectAllStageIdeologyPoints(@Param("bindingId") Long bindingId);

    /**
     * 逐学生 × 逐环节的完成明细，用于教师端统计“完成本堂课全部环节的学生人数”。
     * 返回 kind（VIDEO/QUIZ/DISCUSSION）、refId、userId。
     */
    List<Map<String, Object>> selectCompletionUsers(@Param("bindingId") Long bindingId);

    int deleteStageIdeologyPoints(
            @Param("bindingId") Long bindingId,
            @Param("stageKind") String stageKind,
            @Param("stageId") Long stageId);

    int insertStageIdeologyPoint(Map<String, Object> row);

    Long selectIdeologyCategoryId(@Param("categoryId") Long categoryId);

    int countStageResource(
            @Param("bindingId") Long bindingId,
            @Param("resourceId") Long resourceId);

    int countStageQuiz(
            @Param("bindingId") Long bindingId,
            @Param("assignmentId") Long assignmentId);

    int countStageDiscussion(
            @Param("bindingId") Long bindingId,
            @Param("topicId") Long topicId);
}
