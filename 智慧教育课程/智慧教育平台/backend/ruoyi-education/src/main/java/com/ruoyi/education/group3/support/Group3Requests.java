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
            String scoringRubric,
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
            String scoringRubric,
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
}
