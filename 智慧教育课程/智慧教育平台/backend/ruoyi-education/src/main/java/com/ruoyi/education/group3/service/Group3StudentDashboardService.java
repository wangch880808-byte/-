package com.ruoyi.education.group3.service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
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
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Security;
import com.ruoyi.education.group3.support.Group3Time;

/**
 * 学生端学习进度、待办与作答回看的聚合服务。
 *
 * <p>所有数据均以当前登录学生的教学班绑定和目标记录为边界；
 * 参考答案只在教师设置的公布时间之后由 SQL 过滤出来，避免提前泄露。</p>
 */
@Service
public class Group3StudentDashboardService
{
    private static final int NEW_WINDOW_DAYS = 7;
    private static final int DUE_WINDOW_DAYS = 3;
    private static final long DEFAULT_TASK_SECONDS = 30L;

    private final Group3Mapper mapper;
    private final Group3AccessService accessService;
    private final Group3ResourceService resourceService;
    private final Group3PrepareService prepareService;
    private final Group3DiscussionService discussionService;
    private final ObjectMapper objectMapper;

    public Group3StudentDashboardService(
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

    /** 学生端学习进度看板：任务点完成状态、整体完成率与待办提醒。 */
    public Map<String, Object> dashboard(Long bindingId)
    {
        if (bindingId == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        accessService.requireStudentBinding(bindingId);
        Long userId = Group3Security.currentUserId();
        LocalDateTime now = Group3Time.nowUtc();
        List<Map<String, Object>> items = new ArrayList<>();

        Map<Long, Map<String, Object>> progress = new LinkedHashMap<>();
        for (Map<String, Object> row : safe(mapper.selectResourceProgress(bindingId, userId)))
        {
            progress.put(Group3AccessService.asLong(row.get("resourceId")), row);
        }
        items.addAll(videoItems(bindingId, progress));
        items.addAll(quizItems(bindingId));
        items.addAll(discussionItems(bindingId, userId));
        items.sort(Comparator.comparing((Map<String, Object> item) -> parseTime(item.get("opensAt")))
                .reversed());

        int total = items.size();
        int completed = (int) items.stream().filter(item -> Boolean.TRUE.equals(item.get("completed"))).count();
        int upcoming = (int) items.stream().filter(item -> "UPCOMING".equals(item.get("state"))).count();
        int open = (int) items.stream().filter(item -> "OPEN".equals(item.get("state"))).count();
        int closed = (int) items.stream().filter(item -> "CLOSED".equals(item.get("state"))).count();
        int percent = total == 0 ? 0 : (int) Math.round(completed * 100.0 / total);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("total", total);
        summary.put("completed", completed);
        summary.put("pending", total - completed);
        summary.put("upcoming", upcoming);
        summary.put("open", open);
        summary.put("closed", closed);
        summary.put("percent", percent);

        List<Map<String, Object>> todos = new ArrayList<>();
        for (Map<String, Object> item : items)
        {
            Map<String, Object> todo = todoOf(item, now);
            if (todo != null)
            {
                todos.add(todo);
            }
        }
        todos.sort(Comparator.comparingInt((Map<String, Object> todo) -> todoPriority(todo.get("tone")))
                .thenComparing(todo -> String.valueOf(todo.get("closesAt") == null
                        ? todo.get("opensAt") : todo.get("closesAt"))));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("summary", summary);
        result.put("items", items);
        result.put("todos", todos);
        result.put("serverTime", Group3Time.nowIso());
        return result;
    }

    /** 学生个人笔记：读取本人在该教学班的笔记。 */
    public Map<String, Object> note(Long bindingId)
    {
        if (bindingId == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        accessService.requireStudentBinding(bindingId);
        Long userId = Group3Security.currentUserId();
        Map<String, Object> row = mapper.selectStudentNote(bindingId, userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bindingId", bindingId);
        result.put("content", row == null || row.get("content") == null ? "" : row.get("content"));
        result.put("updatedAt", row == null ? null : row.get("updatedAt"));
        return result;
    }

    /** 学生个人笔记：保存本人笔记，长度上限 20000 字。 */
    public Map<String, Object> saveNote(Long bindingId, String content)
    {
        if (bindingId == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        accessService.requireStudentBinding(bindingId);
        String text = content == null ? "" : content.trim();
        if (text.length() > 20000)
        {
            throw new ServiceException("笔记内容不能超过 20000 字", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", bindingId);
        row.put("userId", Group3Security.currentUserId());
        row.put("content", text);
        mapper.upsertStudentNote(row);
        return note(bindingId);
    }

    /** 作答回看 / 错题本：只返回解析已公布且本人已提交的题目。 */
    public Map<String, Object> answerReview(Long bindingId)
    {
        if (bindingId == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        accessService.requireStudentBinding(bindingId);
        Long userId = Group3Security.currentUserId();
        List<Map<String, Object>> reviews = new ArrayList<>();
        List<Map<String, Object>> wrongQuestions = new ArrayList<>();
        Map<String, Integer> knowledgePointCounts = new LinkedHashMap<>();

        for (Map<String, Object> row : safe(mapper.selectStudentAnswerReview(bindingId, userId)))
        {
            String kind = text(row.get("questionKind"));
            JsonNode correctAnswer = parseJson(row.get("answerJson"));
            JsonNode studentAnswer = parseJson(row.get("studentAnswerJson"));
            Boolean correct = evaluate(kind, correctAnswer, studentAnswer);

            Map<String, Object> item = new LinkedHashMap<>(row);
            item.put("options", parseJson(row.get("optionsJson")));
            item.put("correctAnswer", correctAnswer);
            item.put("studentAnswer", studentAnswer);
            item.put("answerVisible", Boolean.TRUE);
            item.put("graded", correct != null);
            item.put("correct", correct);
            item.remove("answerJson");
            item.remove("optionsJson");
            item.remove("studentAnswerJson");
            reviews.add(item);

            if (Boolean.FALSE.equals(correct))
            {
                wrongQuestions.add(item);
                for (String name : splitNames(text(row.get("knowledgePoints"))))
                {
                    knowledgePointCounts.merge(name, 1, Integer::sum);
                }
            }
        }

        List<Map<String, Object>> knowledgePoints = new ArrayList<>();
        knowledgePointCounts.forEach((name, count) -> {
            Map<String, Object> point = new LinkedHashMap<>();
            point.put("name", name);
            point.put("wrongCount", count);
            knowledgePoints.add(point);
        });
        knowledgePoints.sort(Comparator.comparingInt((Map<String, Object> point) ->
                (Integer) point.get("wrongCount")).reversed());

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("reviews", reviews);
        result.put("wrongQuestions", wrongQuestions);
        result.put("knowledgePoints", knowledgePoints);
        result.put("serverTime", Group3Time.nowIso());
        return result;
    }

    private List<Map<String, Object>> videoItems(
            Long bindingId, Map<Long, Map<String, Object>> progress)
    {
        Map<Long, Map<String, Object>> unique = new LinkedHashMap<>();
        for (Map<String, Object> row : safe(resourceService.studentMaterials(bindingId)))
        {
            unique.putIfAbsent(Group3AccessService.asLong(row.get("resourceId")), row);
        }
        for (Map<String, Object> row : safe(resourceService.studentPrepMaterials(bindingId)))
        {
            unique.putIfAbsent(Group3AccessService.asLong(row.get("resourceId")), row);
        }

        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> row : unique.values())
        {
            if (!flag(row.get("taskEnabled")))
            {
                continue;
            }
            Long resourceId = Group3AccessService.asLong(row.get("resourceId"));
            Map<String, Object> state = progress.get(resourceId);
            boolean completed = state != null && flag(state.get("completed"));
            long watched = state == null ? 0L : longValue(state.get("watchedSeconds"));
            long required = longValue(row.get("taskRequiredSeconds"));
            if (required <= 0)
            {
                required = DEFAULT_TASK_SECONDS;
            }
            int percent = completed ? 100 : (int) Math.min(99, watched * 100L / required);
            Map<String, Object> item = baseItem(row, "VIDEO", "视频任务点", resourceId);
            item.put("completed", completed);
            item.put("progress", percent);
            item.put("progressText", completed ? "已完成" : "观看 " + watched + " / " + required + " 秒");
            result.add(item);
        }
        return result;
    }

    private List<Map<String, Object>> quizItems(Long bindingId)
    {
        Map<Long, Map<String, Object>> grouped = new LinkedHashMap<>();
        for (Map<String, Object> row : safe(prepareService.studentPreviewTasks(bindingId)))
        {
            Long assignmentId = Group3AccessService.asLong(row.get("assignmentId"));
            grouped.computeIfAbsent(assignmentId, key -> new LinkedHashMap<>());
            if (row.get("submissionId") != null)
            {
                grouped.get(assignmentId).put("submitted", Boolean.TRUE);
                grouped.get(assignmentId).putIfAbsent("submittedAt", row.get("submittedAt"));
            }
            grouped.get(assignmentId).putIfAbsent("title", row.get("title"));
            grouped.get(assignmentId).putIfAbsent("opensAt", row.get("opensAt"));
            grouped.get(assignmentId).putIfAbsent("closesAt", row.get("closesAt"));
            grouped.get(assignmentId).putIfAbsent("state", row.get("state"));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        grouped.forEach((assignmentId, row) -> {
            boolean submitted = Boolean.TRUE.equals(row.get("submitted"));
            Map<String, Object> item = baseItem(row, "QUIZ", "预习任务", assignmentId);
            item.put("completed", submitted);
            item.put("progress", submitted ? 100 : 0);
            item.put("progressText", submitted ? "已提交" : "未提交");
            if (submitted && row.get("submittedAt") != null)
            {
                item.put("completedAt", row.get("submittedAt"));
            }
            result.add(item);
        });
        return result;
    }

    private List<Map<String, Object>> discussionItems(Long bindingId, Long userId)
    {
        Map<Long, Integer> participation = new LinkedHashMap<>();
        for (Map<String, Object> row : safe(mapper.selectStudentDiscussionParticipation(bindingId, userId)))
        {
            participation.put(Group3AccessService.asLong(row.get("topicId")),
                    (int) longValue(row.get("postCount")));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (Map<String, Object> topic : safe(discussionService.topics(bindingId, null, null, null, null, null)))
        {
            if (!flag(topic.get("scoreEnabled")))
            {
                continue;
            }
            Long topicId = Group3AccessService.asLong(topic.get("topicId"));
            int count = participation.getOrDefault(topicId, 0);
            Map<String, Object> item = baseItem(topic, "DISCUSSION", "讨论任务", topicId);
            item.put("completed", count > 0);
            item.put("progress", count > 0 ? 100 : 0);
            item.put("progressText", count > 0 ? "已参与 " + count + " 次" : "未参与");
            result.add(item);
        }
        return result;
    }

    private Map<String, Object> baseItem(
            Map<String, Object> source, String kind, String sourceLabel, Long refId)
    {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("key", kind + ":" + refId);
        item.put("kind", kind);
        item.put("refId", refId);
        item.put("title", text(source.get("title")));
        item.put("source", sourceLabel);
        item.put("opensAt", source.get("opensAt"));
        item.put("closesAt", source.get("closesAt"));
        item.put("state", stateOf(source.get("state"), source.get("opensAt"), source.get("closesAt")));
        return item;
    }

    private Map<String, Object> todoOf(Map<String, Object> item, LocalDateTime now)
    {
        if (Boolean.TRUE.equals(item.get("completed")))
        {
            return null;
        }
        String state = text(item.get("state"));
        LocalDateTime opens = parseTime(item.get("opensAt"));
        LocalDateTime closes = parseTime(item.get("closesAt"));
        String tone;
        String label;
        if (closes != null && !closes.isBefore(now) && closes.isBefore(now.plusDays(DUE_WINDOW_DAYS)))
        {
            tone = "DUE_SOON";
            label = "即将截止";
        }
        else if (opens != null && !opens.isBefore(now.minusDays(NEW_WINDOW_DAYS)) && !opens.isAfter(now))
        {
            tone = "NEW";
            label = "新发布";
        }
        else if ("CLOSED".equals(state))
        {
            tone = "OVERDUE";
            label = "已逾期未完成";
        }
        else
        {
            return null;
        }
        Map<String, Object> todo = new LinkedHashMap<>(item);
        todo.put("tone", tone);
        todo.put("label", label);
        return todo;
    }

    private String stateOf(Object explicitState, Object opensAt, Object closesAt)
    {
        String state = text(explicitState);
        if ("ARCHIVED".equals(state) || "CLOSED".equals(state) || "UPCOMING".equals(state)
                || "OPEN".equals(state))
        {
            return state;
        }
        LocalDateTime now = Group3Time.nowUtc();
        LocalDateTime opens = parseTime(opensAt);
        LocalDateTime closes = parseTime(closesAt);
        if (opens != null && now.isBefore(opens))
        {
            return "UPCOMING";
        }
        if (closes != null && !now.isBefore(closes))
        {
            return "CLOSED";
        }
        return "OPEN";
    }

    private int todoPriority(Object tone)
    {
        return switch (text(tone))
        {
            case "OVERDUE" -> 0;
            case "DUE_SOON" -> 1;
            case "NEW" -> 2;
            default -> 9;
        };
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

    private LocalDateTime parseTime(Object value)
    {
        if (value instanceof LocalDateTime localDateTime)
        {
            return localDateTime;
        }
        if (value instanceof Timestamp timestamp)
        {
            return timestamp.toLocalDateTime();
        }
        if (value == null)
        {
            return null;
        }
        try
        {
            return Group3Time.parseUtc(value.toString(), "时间");
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private List<String> splitNames(String value)
    {
        List<String> names = new ArrayList<>();
        if (value == null || value.isBlank())
        {
            return names;
        }
        for (String part : value.split("[,，、]"))
        {
            String name = part.trim();
            if (!name.isEmpty() && !names.contains(name))
            {
                names.add(name);
            }
        }
        return names;
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
