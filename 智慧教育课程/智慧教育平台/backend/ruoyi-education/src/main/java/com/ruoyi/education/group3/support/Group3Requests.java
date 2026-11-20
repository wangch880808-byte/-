package com.ruoyi.education.group3.support;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

public final class Group3Requests
{
    private Group3Requests()
    {
    }

    /**
     * 资料发布请求。
     * <p>
     * source 区分发布入口：{@code PREPARE} 表示课前准备，缺省或其他值按课程建设（{@code STUDIO}）处理。
     * </p>
     */
    public record ReleaseResource(
            Long bindingId,
            Long resourceVersionId,
            String opensAt,
            String closesAt,
            String source)
    {
        public ReleaseResource(Long bindingId, Long resourceVersionId, String opensAt, String closesAt)
        {
            this(bindingId, resourceVersionId, opensAt, closesAt, "STUDIO");
        }
    }

    public record Question(
            Long bindingId,
            String title,
            String questionKind,
            String stem,
            List<String> options,
            JsonNode answer,
            String explanation,
            String scoringRubric,
            BigDecimal maxScore,
            List<Long> knowledgePointIds,
            String difficulty,
            String tags)
    {
    }

    public record QuestionVersion(
            Long bindingId,
            String questionKind,
            String stem,
            List<String> options,
            JsonNode answer,
            String explanation,
            String scoringRubric,
            BigDecimal maxScore,
            List<Long> knowledgePointIds,
            String difficulty,
            String tags)
    {
    }

    /** 组卷请求：按题型/难度/知识点/标签筛选，随机或顺序取指定数量。 */
    public record ComposePaper(
            Long bindingId,
            String questionKind,
            String difficulty,
            String knowledgePoint,
            String tag,
            Integer count,
            String mode)
    {
    }

    /** 资料增强设置：下载开关、预览水印、视频任务点配置。 */
    public record ResourceEnhance(
            Long bindingId,
            Boolean downloadEnabled,
            String watermarkText,
            Boolean taskEnabled,
            Integer taskRequiredSeconds)
    {
    }

    /** 学生视频观看进度上报。 */
    public record WatchProgress(
            Long bindingId,
            Integer watchedSeconds)
    {
    }

    public record Status(String status)
    {
    }

    public record LessonPlan(
            Long bindingId,
            Long lessonId,
            String title,
            String privateNotes,
            List<Long> materialVersionIds,
            Long quizQuestionVersionId,
            String quizTitle,
            Integer durationSeconds,
            Boolean markReady)
    {
    }

    public record PreviewTask(
            Long bindingId,
            Long lessonId,
            String title,
            String instructions,
            String opensAt,
            String closesAt,
            String answerOpensAt,
            Long questionVersionId,
            List<Long> resourceVersionIds)
    {
    }

    public record PreviewSubmission(Map<String, JsonNode> answers)
    {
    }

    /** 讨论话题（DISCUSSION 环节）创建请求。 */
    public record DiscussionTopic(
            Long bindingId,
            Long lessonId,
            Long folderId,
            String title,
            String content,
            String category,
            String knowledgePoint,
            String publishScope,
            String opensAt,
            String closesAt,
            Boolean allowAnonymous,
            Boolean allowAttachment,
            Boolean allowReply,
            Boolean allowStudentTopic,
            Boolean scoreEnabled,
            BigDecimal scoreMax,
            Boolean pinned,
            Boolean featured)
    {
    }

    /** 讨论回复请求；parentPostId 为空表示顶层发言，非空表示楼中楼回复。 */
    public record DiscussionPost(
            Long bindingId,
            Long topicId,
            Long parentPostId,
            String content,
            Boolean anonymous,
            String attachmentName,
            String attachmentUrl)
    {
    }

    /** 讨论操作请求；value 用于承载文件夹 ID、评分值或 settings 字段。 */
    public record DiscussionAction(
            Long bindingId,
            Long topicId,
            Long postId,
            String action,
            Object value)
    {
    }

    /** 讨论文件夹创建请求。 */
    public record DiscussionFolder(
            Long bindingId,
            String name)
    {
    }

    /** 时间轴环节更新请求；DISCUSSION 用于更新话题设置，其他 kind 更新对应业务对象。 */
    public record UpdateStage(
            Long bindingId,
            String kind,
            Long id,
            String title,
            String instructions,
            String privateNotes,
            String opensAt,
            String closesAt,
            String answerOpensAt,
            Integer durationSeconds)
    {
    }

    /** 时间轴环节删除（软归档）请求。 */
    public record RemoveStage(
            Long bindingId,
            String kind,
            Long id)
    {
    }
}
