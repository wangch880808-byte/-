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

    public record ReleaseResource(
            Long bindingId,
            Long resourceVersionId,
            String opensAt,
            String closesAt)
    {
    }

    public record Question(
            Long bindingId,
            String title,
            String questionKind,
            String stem,
            List<String> options,
            JsonNode answer,
            String explanation,
            BigDecimal maxScore,
            List<Long> knowledgePointIds)
    {
    }

    public record QuestionVersion(
            Long bindingId,
            String questionKind,
            String stem,
            List<String> options,
            JsonNode answer,
            String explanation,
            BigDecimal maxScore,
            List<Long> knowledgePointIds)
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

    /**
     * 从某堂课的时间轴上移除一个环节。kind 取 MATERIAL / DRAFT / QUIZ / PLAN，
     * id 是对应真实对象的主键（resourceId / assignmentId / planId）。
     * 采用归档软删除，不物理删除业务数据与学生提交记录。
     */
    public record RemoveStage(Long bindingId, String kind, Long id)
    {
    }

    /**
     * 原地修改环节设置。kind 取 MATERIAL / QUIZ / PLAN，id 同上。
     * 时间使用 ISO-8601 UTC；closesAt 为空表示资料长期开放。
     */
    public record UpdateStage(
            Long bindingId,
            String kind,
            Long id,
            String title,
            String instructions,
            String privateNotes,
            String opensAt,
            String closesAt,
            String answerOpensAt)
    {
    }

    /** 发布讨论话题：一个话题就是时间轴上的一个「发布讨论」环节。 */
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
            Integer scoreMax,
            Boolean pinned,
            Boolean featured)
    {
    }

    /** 发表主题帖或回复；parentPostId 为空表示主题帖。 */
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

    /**
     * 讨论管理动作：pin / feature / unpin / unfeature / archive / restore /
     * score / remove-post / like / folder / move / settings。
     */
    public record DiscussionAction(
            Long bindingId,
            Long topicId,
            Long postId,
            String action,
            String value)
    {
    }

    /** 新建讨论文件夹。 */
    public record DiscussionFolder(Long bindingId, String name)
    {
    }
}
