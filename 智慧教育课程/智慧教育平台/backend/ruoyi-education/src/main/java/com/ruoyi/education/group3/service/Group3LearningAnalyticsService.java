package com.ruoyi.education.group3.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group3.contract.LearningAnalyticsProvider;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Time;

/**
 * 学情数据契约的本地实现：向统计/AI 组提供全班完成率、作答记录与讨论参与度。
 *
 * <p>本服务只读第三组自己的数据，成员范围通过第二组契约取得；全班数据仅对
 * 本班具备发布权限的任课教师开放。答案与解析只在教师设定的公布时间之后输出。</p>
 */
@Service
public class Group3LearningAnalyticsService implements LearningAnalyticsProvider
{
    private static final int MAX_RECORDS = 500;

    private final Group3Mapper mapper;
    private final Group3AccessService accessService;
    private final Group3ResourceService resourceService;
    private final Group3PrepareService prepareService;
    private final Group3DiscussionService discussionService;
    private final ObjectMapper objectMapper;

    public Group3LearningAnalyticsService(
            Group3Mapper mapper,
            Group3AccessService accessService,
            Group3ResourceService resourceService,
            Group3PrepareService prepareService,
            Group3DiscussionService discussionService,
            ObjectMapper objectMapper)
    {
        this.mapper = mapper;
        this.accessService = accessService;
        this.resourceService = resourceService;
        this.prepareService = prepareService;
        this.discussionService = discussionService;
        this.objectMapper = objectMapper;
    }

    @Override
    public Map<String, Object> learningSnapshot(Long bindingId)
    {
        require(bindingId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("progress", progressOf(bindingId));
        result.put("submissions", submissionsOf(bindingId));
        result.put("discussion", discussionOf(bindingId));
        result.put("serverTime", Group3Time.nowIso());
        return result;
    }

    @Override
    public Map<String, Object> courseProgress(Long bindingId)
    {
        require(bindingId);
        return progressOf(bindingId);
    }

    @Override
    public Map<String, Object> submissionRecords(Long bindingId)
    {
        require(bindingId);
        return submissionsOf(bindingId);
    }

    @Override
    public Map<String, Object> discussionParticipation(Long bindingId)
    {
        require(bindingId);
        return discussionOf(bindingId);
    }

    private void require(Long bindingId)
    {
        if (bindingId == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        accessService.requireTeacherBinding(bindingId);
    }

    /** 分母口径：有效在班学生数 × 任务点数；每个任务点的完成人数封顶到学生数。 */
    private Map<String, Object> progressOf(Long bindingId)
    {
        int studentCount = accessService.activeStudentIds(bindingId).size();
        Map<Long, Map<String, Object>> videoStats = new LinkedHashMap<>();
        for (Map<String, Object> row : safe(mapper.selectResourceProgressStats(bindingId)))
        {
            videoStats.put(Group3AccessService.asLong(row.get("resourceId")), row);
        }
        Map<Long, Map<String, Object>> quizStats = new LinkedHashMap<>();
        for (Map<String, Object> row : safe(mapper.selectAssignmentSubmissionStats(bindingId)))
        {
            quizStats.put(Group3AccessService.asLong(row.get("assignmentId")), row);
        }
        Map<Long, Integer> discussionStats = new LinkedHashMap<>();
        for (Map<String, Object> row : safe(mapper.selectDiscussionParticipationStats(bindingId)))
        {
            Long topicId = Group3AccessService.asLong(row.get("topicId"));
            if (longValue(row.get("postCount")) > 0)
            {
                discussionStats.merge(topicId, 1, Integer::sum);
            }
        }

        List<Map<String, Object>> tasks = new ArrayList<>();
        Map<Long, Map<String, Object>> quizTasks = new LinkedHashMap<>();
        for (Map<String, Object> row : safe(prepareService.teacherPreviewTasks(bindingId)))
        {
            Long assignmentId = Group3AccessService.asLong(row.get("assignmentId"));
            quizTasks.putIfAbsent(assignmentId, row);
        }
        quizTasks.forEach((assignmentId, row) -> tasks.add(
                task("QUIZ", assignmentId, row.get("title"), row.get("opensAt"), row.get("closesAt"),
                        quizStats.get(assignmentId), "submittedCount")));

        for (Map<String, Object> row : safe(resourceService.publishedResources(bindingId)))
        {
            if (!flag(row.get("taskEnabled")))
            {
                continue;
            }
            tasks.add(task("VIDEO", Group3AccessService.asLong(row.get("resourceId")), row.get("title"),
                    row.get("opensAt"), row.get("closesAt"),
                    videoStats.get(Group3AccessService.asLong(row.get("resourceId"))), "completedCount"));
        }

        for (Map<String, Object> topic : safe(discussionService.topics(bindingId, null, null, null, null, null)))
        {
            if (!flag(topic.get("scoreEnabled")))
            {
                continue;
            }
            Long topicId = Group3AccessService.asLong(topic.get("topicId"));
            Map<String, Object> stat = new LinkedHashMap<>();
            stat.put("completedCount", discussionStats.getOrDefault(topicId, 0));
            tasks.add(task("DISCUSSION", topicId, topic.get("title"), topic.get("opensAt"),
                    topic.get("closesAt"), stat, "completedCount"));
        }

        tasks.sort(Comparator.comparing((Map<String, Object> item) -> text(item.get("opensAt"))));
        int videoCount = countKind(tasks, "VIDEO");
        int quizCount = countKind(tasks, "QUIZ");
        int discussionCount = countKind(tasks, "DISCUSSION");
        int totalOpportunities = tasks.size() * studentCount;
        int completed = 0;
        for (Map<String, Object> task : tasks)
        {
            completed += (Integer) task.get("completedCount");
        }
        int percent = totalOpportunities == 0 ? 0
                : (int) Math.round(completed * 100.0 / totalOpportunities);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("studentCount", studentCount);
        summary.put("taskCount", tasks.size());
        summary.put("videoTaskCount", videoCount);
        summary.put("quizTaskCount", quizCount);
        summary.put("discussionTaskCount", discussionCount);
        summary.put("totalOpportunities", totalOpportunities);
        summary.put("completed", completed);
        summary.put("percent", percent);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("tasks", tasks);
        result.put("serverTime", Group3Time.nowIso());
        return result;
    }

    private Map<String, Object> task(String kind, Long refId, Object title, Object opensAt, Object closesAt,
            Map<String, Object> stat, String completedField)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("kind", kind);
        item.put("refId", refId);
        item.put("title", text(title));
        item.put("opensAt", opensAt);
        item.put("closesAt", closesAt);
        int completed = stat == null ? 0 : (int) Math.min(longValue(stat.get(completedField)), Integer.MAX_VALUE);
        item.put("completedCount", completed);
        return item;
    }

    private Map<String, Object> submissionsOf(Long bindingId)
    {
        int total = mapper.selectSubmissionAnalyticsCount(bindingId);
        List<Map<String, Object>> records = new ArrayList<>();
        for (Map<String, Object> row : safe(mapper.selectSubmissionAnalytics(bindingId)))
        {
            boolean visible = flag(row.get("answerVisible"));
            JsonNode correctAnswer = visible ? parseJson(row.get("answerJson")) : null;
            JsonNode studentAnswer = visible ? parseJson(row.get("studentAnswerJson")) : null;
            Boolean correct = visible
                    ? evaluate(text(row.get("questionKind")), correctAnswer, studentAnswer) : null;

            Map<String, Object> record = new LinkedHashMap<>();
            record.put("assignmentId", row.get("assignmentId"));
            record.put("assignmentTitle", row.get("assignmentTitle"));
            record.put("assignmentKind", row.get("assignmentKind"));
            record.put("targetId", row.get("targetId"));
            record.put("studentUserId", row.get("studentUserId"));
            record.put("submissionVersionId", row.get("submissionVersionId"));
            record.put("assignmentItemId", row.get("assignmentItemId"));
            record.put("sortOrder", row.get("sortOrder"));
            record.put("prompt", row.get("prompt"));
            record.put("maxScore", row.get("maxScore"));
            record.put("questionVersionId", row.get("questionVersionId"));
            record.put("questionKind", row.get("questionKind"));
            record.put("difficulty", row.get("difficulty"));
            record.put("tags", row.get("tags"));
            record.put("knowledgePoints", row.get("knowledgePoints"));
            record.put("submittedAt", row.get("submittedAt"));
            record.put("isLate", flag(row.get("isLate")));
            record.put("answerVisible", visible);
            record.put("graded", correct != null);
            record.put("correct", correct);
            if (visible)
            {
                record.put("options", parseJson(row.get("optionsJson")));
                record.put("correctAnswer", correctAnswer);
                record.put("studentAnswer", studentAnswer);
                record.put("explanation", row.get("explanation"));
            }
            records.add(record);
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("total", total);
        result.put("truncated", total > records.size());
        result.put("serverTime", Group3Time.nowIso());
        return result;
    }

    private Map<String, Object> discussionOf(Long bindingId)
    {
        Map<Long, Map<String, Object>> byStudent = new LinkedHashMap<>();
        List<Map<String, Object>> topicBreakdown = new ArrayList<>();
        for (Map<String, Object> row : safe(mapper.selectDiscussionParticipationStats(bindingId)))
        {
            Long userId = Group3AccessService.asLong(row.get("userId"));
            Map<String, Object> student = byStudent.computeIfAbsent(userId, key -> {
                Map<String, Object> created = new LinkedHashMap<>();
                created.put("userId", key);
                created.put("topicCount", 0);
                created.put("postCount", 0L);
                created.put("replyCount", 0L);
                created.put("likeCount", 0L);
                created.put("lastPostAt", null);
                return created;
            });
            student.put("topicCount", (Integer) student.get("topicCount") + 1);
            student.put("postCount", (Long) student.get("postCount") + longValue(row.get("postCount")));
            student.put("replyCount", (Long) student.get("replyCount") + longValue(row.get("replyCount")));
            student.put("likeCount", (Long) student.get("likeCount") + longValue(row.get("likeCount")));
            if (compareIso(student.get("lastPostAt"), row.get("lastPostAt")) < 0)
            {
                student.put("lastPostAt", row.get("lastPostAt"));
            }

            Map<String, Object> detail = new LinkedHashMap<>(row);
            detail.remove("userId");
            detail.put("userId", userId);
            topicBreakdown.add(detail);
        }
        topicBreakdown.sort(Comparator.comparing(
                (Map<String, Object> item) -> text(item.get("lastPostAt"))).reversed());

        List<Map<String, Object>> records = new ArrayList<>(byStudent.values());
        records.sort(Comparator.comparingLong((Map<String, Object> item) -> (Long) item.get("postCount")).reversed());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("records", records);
        result.put("topicBreakdown", topicBreakdown);
        result.put("total", records.size());
        result.put("serverTime", Group3Time.nowIso());
        return result;
    }

    private int countKind(List<Map<String, Object>> tasks, String kind)
    {
        return (int) tasks.stream().filter(item -> kind.equals(item.get("kind"))).count();
    }

    private int compareIso(Object left, Object right)
    {
        return text(left).compareTo(text(right));
    }

    private Boolean evaluate(String kind, JsonNode correct, JsonNode student)
    {
        if (correct == null || student == null)
        {
            return null;
        }
        return switch (kind)
        {
            case "SINGLE" -> correct.has("choiceIndex") && student.has("choiceIndex")
                    && correct.path("choiceIndex").asInt(-1) == student.path("choiceIndex").asInt(-2);
            case "MULTIPLE" -> {
                Set<Integer> expected = intSet(correct.path("choiceIndices"));
                Set<Integer> actual = intSet(student.path("choiceIndices"));
                yield expected != null && actual != null && expected.equals(actual);
            }
            case "TRUE_FALSE" -> correct.has("correctBoolean") && student.has("correctBoolean")
                    && correct.path("correctBoolean").asBoolean() == student.path("correctBoolean").asBoolean();
            default -> null;
        };
    }

    private Set<Integer> intSet(JsonNode node)
    {
        if (node == null || !node.isArray())
        {
            return null;
        }
        Set<Integer> values = new LinkedHashSet<>();
        for (JsonNode item : node)
        {
            values.add(item.asInt());
        }
        return values;
    }

    private JsonNode parseJson(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof JsonNode node)
        {
            return node;
        }
        try
        {
            return objectMapper.readTree(value.toString());
        }
        catch (Exception ignored)
        {
            return null;
        }
    }

    private List<Map<String, Object>> safe(List<Map<String, Object>> rows)
    {
        return rows == null ? List.of() : rows;
    }

    private boolean flag(Object value)
    {
        if (value instanceof Boolean bool)
        {
            return bool;
        }
        if (value instanceof Number number)
        {
            return number.intValue() != 0;
        }
        return value != null && ("true".equalsIgnoreCase(value.toString())
                || "1".equals(value.toString()));
    }

    private long longValue(Object value)
    {
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        if (value == null)
        {
            return 0L;
        }
        try
        {
            return Long.parseLong(value.toString());
        }
        catch (NumberFormatException ignored)
        {
            return 0L;
        }
    }

    private String text(Object value)
    {
        return value == null ? "" : value.toString();
    }
}
