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
}
