package com.ruoyi.education.group3.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group3.contract.PreviewTaskProvider;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Requests;
import com.ruoyi.education.group3.support.Group3Security;
import com.ruoyi.education.group3.support.Group3Time;

@Service
public class Group3PrepareService implements PreviewTaskProvider
{
    private final Group3Mapper mapper;
    private final Group3AccessService accessService;
    private final Group3ResourceService resourceService;
    private final ObjectMapper objectMapper;

    public Group3PrepareService(
            Group3Mapper mapper,
            Group3AccessService accessService,
            Group3ResourceService resourceService,
            ObjectMapper objectMapper)
    {
        this.mapper = mapper;
        this.accessService = accessService;
        this.resourceService = resourceService;
        this.objectMapper = objectMapper;
    }

    public List<Map<String, Object>> questions(Long bindingId)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        List<Map<String, Object>> questions = mapper.selectQuestions(bindingId, courseSpaceId);
        questions.forEach(question -> {
            parseJsonField(question, "optionsJson", "options");
            parseJsonField(question, "answerJson", "answer");
            question.put("answerVisible", true);
        });
        return questions;
    }

    @Transactional
    public Map<String, Object> createQuestion(Group3Requests.Question request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String title = requiredText(request.title(), "题目名称", 200);
        validateQuestion(request.questionKind(), request.stem(), request.options(), request.answer(),
                request.maxScore(), courseSpaceId, request.knowledgePointIds());

        Map<String, Object> question = new LinkedHashMap<>();
        question.put("courseSpaceId", courseSpaceId);
        question.put("title", title);
        question.put("userId", Group3Security.currentUserId());
        mapper.insertQuestion(question);
        Long questionId = Group3AccessService.asLong(question.get("id"));
        Map<String, Object> version = insertQuestionVersion(
                questionId, courseSpaceId, 1, request.questionKind(), request.stem(),
                request.options(), request.answer(), request.explanation(), request.maxScore());
        linkKnowledgePoints(questionId, courseSpaceId, request.knowledgePointIds());
        version.put("questionId", questionId);
        version.put("title", title);
        return version;
    }

    @Transactional
    public Map<String, Object> createQuestionVersion(Long questionId, Group3Requests.QuestionVersion request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        mapper.lockQuestion(questionId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Map<String, Object> existing = mapper.selectQuestions(request.bindingId(), courseSpaceId).stream()
                .filter(question -> questionId.equals(Group3AccessService.asLong(question.get("questionId"))))
                .findFirst()
                .orElseThrow(() -> new ServiceException("题目不存在", HttpStatus.NOT_FOUND));
        validateQuestion(request.questionKind(), request.stem(), request.options(), request.answer(),
                request.maxScore(), courseSpaceId, request.knowledgePointIds());
        int versionNo = mapper.selectNextQuestionVersion(questionId);
        Map<String, Object> version = insertQuestionVersion(
                questionId, courseSpaceId, versionNo, request.questionKind(), request.stem(),
                request.options(), request.answer(), request.explanation(), request.maxScore());
        linkKnowledgePoints(questionId, courseSpaceId, request.knowledgePointIds());
        version.put("questionId", questionId);
        version.put("title", existing.get("title"));
        return version;
    }

    public Map<String, Object> updateQuestionStatus(
            Long bindingId, Long questionId, Group3Requests.Status request)
    {
        accessService.requireTeacherBinding(bindingId);
        if (request == null || request.status() == null)
        {
            throw new ServiceException("状态不能为空", HttpStatus.BAD_REQUEST);
        }
        String status = request.status().toUpperCase();
        if (!Set.of("DRAFT", "ACTIVE", "ARCHIVED").contains(status))
        {
            throw new ServiceException("题目状态无效", HttpStatus.BAD_REQUEST);
        }
        int changed = mapper.updateQuestionStatus(
                bindingId, questionId, Group3Security.currentUserId(), status);
        if (changed == 0)
        {
            throw new ServiceException("无权修改该题目", HttpStatus.FORBIDDEN);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questionId", questionId);
        result.put("status", status);
        return result;
    }

    public List<Map<String, Object>> plans(Long bindingId)
    {
        accessService.requireTeacherBinding(bindingId);
        List<Map<String, Object>> plans = mapper.selectPlans(bindingId, Group3Security.currentUserId());
        for (Map<String, Object> plan : plans)
        {
            Long planId = Group3AccessService.asLong(plan.get("planId"));
            plan.put("materials", mapper.selectPlanMaterials(planId));
            plan.put("templates", mapper.selectPlanTemplates(planId));
        }
        return plans;
    }

    @Transactional
    public Map<String, Object> createPlan(Group3Requests.LessonPlan request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        if (request.lessonId() == null)
        {
            throw new ServiceException("请选择课次", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> lesson = mapper.selectLesson(request.bindingId(), request.lessonId());
        if (lesson == null)
        {
            throw new ServiceException("课次不属于当前教学班", HttpStatus.BAD_REQUEST);
        }
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String title = requiredText(request.title(), "备课方案名称", 160);
        String privateNotes = request.privateNotes() == null ? "" : request.privateNotes();

        List<Long> materialVersionIds = distinct(request.materialVersionIds());
        List<Map<String, Object>> materialVersions = new ArrayList<>();
        for (Long versionId : materialVersionIds)
        {
            Map<String, Object> version = accessService.teacherVersion(request.bindingId(), versionId);
            if (!"READY".equals(version.get("processingStatus")))
            {
                throw new ServiceException("备课材料尚未处理完成", HttpStatus.CONFLICT);
            }
            materialVersions.add(version);
        }

        Map<String, Object> questionVersion = null;
        if (request.quizQuestionVersionId() != null)
        {
            questionVersion = mapper.selectQuestionVersion(
                    request.bindingId(), request.quizQuestionVersionId(), Group3Security.currentUserId());
            if (questionVersion == null)
            {
                throw new ServiceException("课堂活动题目不属于当前课程", HttpStatus.BAD_REQUEST);
            }
        }

        mapper.lockLesson(request.lessonId());
        int versionNo = mapper.selectNextPlanVersion(request.lessonId());
        Map<String, Object> plan = new LinkedHashMap<>();
        plan.put("lessonId", request.lessonId());
        plan.put("bindingId", request.bindingId());
        plan.put("versionNo", versionNo);
        plan.put("title", title);
        plan.put("privateNotes", privateNotes);
        plan.put("status", Boolean.TRUE.equals(request.markReady()) ? "READY" : "DRAFT");
        plan.put("userId", Group3Security.currentUserId());
        mapper.insertLessonPlan(plan);
        Long planId = Group3AccessService.asLong(plan.get("id"));

        int sortOrder = 1;
        for (Map<String, Object> version : materialVersions)
        {
            Map<String, Object> material = new LinkedHashMap<>();
            material.put("planId", planId);
            material.put("bindingId", request.bindingId());
            material.put("courseSpaceId", courseSpaceId);
            material.put("resourceId", version.get("resourceId"));
            material.put("resourceVersionId", version.get("versionId"));
            material.put("sortOrder", sortOrder++);
            material.put("privateNotes", "");
            mapper.insertPlanMaterial(material);
        }
        if (questionVersion != null)
        {
            Map<String, Object> template = new LinkedHashMap<>();
            template.put("planId", planId);
            template.put("bindingId", request.bindingId());
            template.put("courseSpaceId", courseSpaceId);
            template.put("templateKind", "QUIZ");
            template.put("title", request.quizTitle() == null || request.quizTitle().isBlank()
                    ? "课前测验" : request.quizTitle().trim());
            template.put("questionId", questionVersion.get("questionId"));
            template.put("questionVersionId", questionVersion.get("versionId"));
            template.put("durationSeconds", request.durationSeconds() == null
                    ? 900 : Math.max(60, request.durationSeconds()));
            template.put("status", "READY");
            template.put("userId", Group3Security.currentUserId());
            mapper.insertActivityTemplate(template);
        }
        plan.put("planId", planId);
        plan.put("materials", mapper.selectPlanMaterials(planId));
        plan.put("templates", mapper.selectPlanTemplates(planId));
        return plan;
    }

    @Transactional
    public Map<String, Object> publishPreview(Group3Requests.PreviewTask request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Long teachingClassId = Group3AccessService.asLong(binding.get("teachingClassId"));
        Map<String, Object> lesson = null;
        if (request.lessonId() != null)
        {
            lesson = mapper.selectLesson(request.bindingId(), request.lessonId());
            if (lesson == null)
            {
                throw new ServiceException("课次不属于当前教学班", HttpStatus.BAD_REQUEST);
            }
        }

        LocalDateTime opensAt = Group3Time.parseUtc(request.opensAt(), "开放时间");
        LocalDateTime closesAt = Group3Time.parseUtc(request.closesAt(), "截止时间");
        LocalDateTime answerOpensAt = Group3Time.parseUtc(request.answerOpensAt(), "解析公布时间");
        if (opensAt == null)
        {
            opensAt = Group3Time.nowUtc();
        }
        if (closesAt == null || !closesAt.isAfter(opensAt))
        {
            throw new ServiceException("截止时间必须晚于开放时间", HttpStatus.BAD_REQUEST);
        }
        if (answerOpensAt != null && answerOpensAt.isBefore(closesAt))
        {
            throw new ServiceException("解析公布时间不能早于截止时间", HttpStatus.BAD_REQUEST);
        }

        for (Long versionId : distinct(request.resourceVersionIds()))
        {
            Group3Requests.ReleaseResource release = new Group3Requests.ReleaseResource(
                    request.bindingId(), versionId,
                    opensAt.toInstant(java.time.ZoneOffset.UTC).toString(),
                    closesAt.toInstant(java.time.ZoneOffset.UTC).toString());
            resourceService.release(release);
        }

        Map<String, Object> questionVersion = null;
        if (request.questionVersionId() != null)
        {
            questionVersion = mapper.selectQuestionVersion(
                    request.bindingId(), request.questionVersionId(), Group3Security.currentUserId());
            if (questionVersion == null)
            {
                throw new ServiceException("预习题目不属于当前课程", HttpStatus.BAD_REQUEST);
            }
        }

        Map<String, Object> assignment = new LinkedHashMap<>();
        assignment.put("bindingId", request.bindingId());
        assignment.put("teachingClassId", teachingClassId);
        assignment.put("courseSpaceId", courseSpaceId);
        assignment.put("lessonId", request.lessonId());
        assignment.put("title", requiredText(request.title(), "预习任务名称", 200));
        assignment.put("instructions", request.instructions() == null ? "" : request.instructions().trim());
        assignment.put("opensAt", opensAt);
        assignment.put("closesAt", closesAt);
        assignment.put("answerOpensAt", answerOpensAt);
        assignment.put("userId", Group3Security.currentUserId());
        mapper.insertAssignment(assignment);
        Long assignmentId = Group3AccessService.asLong(assignment.get("id"));
        if (questionVersion != null)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("assignmentId", assignmentId);
            item.put("courseSpaceId", courseSpaceId);
            item.put("sortOrder", 1);
            item.put("itemKind", "QUESTION");
            item.put("prompt", questionVersion.get("stem"));
            item.put("questionId", questionVersion.get("questionId"));
            item.put("questionVersionId", questionVersion.get("versionId"));
            item.put("maxScore", questionVersion.get("maxScore"));
            mapper.insertAssignmentItem(item);
        }
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("assignmentId", assignmentId);
        target.put("teachingClassId", teachingClassId);
        mapper.insertAssignmentTargets(target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assignmentId", assignmentId);
        result.put("targetCount", mapper.selectTargetCount(assignmentId));
        result.put("status", "PUBLISHED");
        result.put("opensAt", opensAt + "Z");
        result.put("closesAt", closesAt + "Z");
        return result;
    }

    @Override
    public List<Map<String, Object>> previewTasksForCurrentUser(Long bindingId)
    {
        if (Group3Security.isTeacher())
        {
            return teacherPreviewTasks(bindingId);
        }
        return studentPreviewTasks(bindingId);
    }

    public List<Map<String, Object>> teacherPreviewTasks(Long bindingId)
    {
        accessService.requireTeacherBinding(bindingId);
        return mapper.selectTeacherPreviewTasks(bindingId);
    }

    public List<Map<String, Object>> studentPreviewTasks(Long bindingId)
    {
        accessService.requireStudentBinding(bindingId);
        List<Map<String, Object>> tasks = mapper.selectStudentPreviewTasks(
                bindingId, Group3Security.currentUserId());
        for (Map<String, Object> task : tasks)
        {
            parseJsonField(task, "optionsJson", "options");
            task.put("state", taskState(
                    (String) task.get("status"), task.get("opensAt"), task.get("closesAt")));
            task.put("submitted", task.get("submissionId") != null);
            task.put("answerVisible", isReached(task.get("answerOpensAt")));
        }
        return tasks;
    }

    public Map<String, Object> studentPreviewDetail(Long bindingId, Long assignmentId)
    {
        accessService.requireStudentBinding(bindingId);
        Map<String, Object> task = mapper.selectStudentPreviewTask(
                bindingId, assignmentId, Group3Security.currentUserId());
        if (task == null)
        {
            throw new ServiceException("预习任务不存在或不属于本人", HttpStatus.NOT_FOUND);
        }
        LocalDateTime now = Group3Time.nowUtc();
        LocalDateTime opensAt = localTime(task.get("opensAtRaw"));
        LocalDateTime closesAt = localTime(task.get("closesAtRaw"));
        LocalDateTime answerOpensAt = localTime(task.get("answerOpensAtRaw"));
        String state = !now.isBefore(opensAt) && now.isBefore(closesAt) ? "OPEN"
                : now.isBefore(opensAt) ? "UPCOMING" : "CLOSED";
        boolean answerVisible = answerOpensAt != null && !now.isBefore(answerOpensAt);
        task.put("state", state);
        task.put("answerVisible", answerVisible);
        task.put("answerVisibleAt", task.get("answerOpensAt"));

        List<Map<String, Object>> items = "UPCOMING".equals(state)
                ? List.of()
                : mapper.selectStudentPreviewItems(assignmentId, answerVisible);
        for (Map<String, Object> item : items)
        {
            parseJsonField(item, "optionsJson", "options");
            parseJsonField(item, "answerJson", "answer");
            item.put("answerVisible", answerVisible);
        }
        task.put("items", items);
        Map<String, Object> submission = mapper.selectSubmissionByTarget(
                Group3AccessService.asLong(task.get("targetId")));
        task.put("submitted", submission != null);
        task.put("submission", submission);
        task.put("resources", resourceService.studentMaterials(bindingId));
        task.remove("opensAtRaw");
        task.remove("closesAtRaw");
        task.remove("answerOpensAtRaw");
        return Group3Time.withServerTime(task);
    }

    @Transactional
    public Map<String, Object> submitPreview(
            Long bindingId, Long assignmentId, Group3Requests.PreviewSubmission request)
    {
        accessService.requireStudentBinding(bindingId);
        Long userId = Group3Security.currentUserId();
        Map<String, Object> target = mapper.selectStudentTargetForUpdate(assignmentId, userId);
        if (target == null
                || !bindingId.equals(Group3AccessService.asLong(target.get("bindingId"))))
        {
            throw new ServiceException("预习任务不存在或不属于本人", HttpStatus.FORBIDDEN);
        }
        LocalDateTime now = Group3Time.nowUtc();
        LocalDateTime opensAt = localTime(target.get("opensAt"));
        LocalDateTime closesAt = localTime(target.get("closesAt"));
        if (now.isBefore(opensAt))
        {
            throw new ServiceException("预习任务尚未开放", HttpStatus.CONFLICT);
        }
        if (!now.isBefore(closesAt))
        {
            throw new ServiceException("预习任务已截止", HttpStatus.CONFLICT);
        }
        String assignmentStatus = String.valueOf(target.get("status"));
        if (!("PUBLISHED".equals(assignmentStatus) || "CLOSED".equals(assignmentStatus)))
        {
            throw new ServiceException("预习任务当前不可提交", HttpStatus.CONFLICT);
        }

        Long targetId = Group3AccessService.asLong(target.get("targetId"));
        Map<String, Object> existing = mapper.selectSubmissionByTarget(targetId);
        if (existing != null)
        {
            existing.put("idempotent", true);
            existing.put("submitted", true);
            return Group3Time.withServerTime(existing);
        }

        List<Map<String, Object>> items = mapper.selectStudentPreviewItems(assignmentId, false);
        if (items.isEmpty())
        {
            throw new ServiceException("该预习任务没有可提交的题目", HttpStatus.CONFLICT);
        }
        Map<String, JsonNode> answers = request == null || request.answers() == null
                ? Map.of() : request.answers();
        for (Map<String, Object> item : items)
        {
            String itemId = String.valueOf(item.get("assignmentItemId"));
            JsonNode answer = answers.get(itemId);
            if (answer == null && answers.size() == 1)
            {
                answer = answers.values().iterator().next();
            }
            validateStudentAnswer(item, answer);
        }

        Map<String, Object> submissionRow = new LinkedHashMap<>();
        submissionRow.put("assignmentId", assignmentId);
        submissionRow.put("targetId", targetId);
        mapper.insertSubmission(submissionRow);
        Long submissionId = mapper.selectSubmissionIdByTarget(targetId);
        if (submissionId == null)
        {
            throw new ServiceException("提交记录创建失败，请重试", HttpStatus.ERROR);
        }
        int versionNo = mapper.selectNextSubmissionVersion(submissionId);
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("submissionId", submissionId);
        version.put("assignmentId", assignmentId);
        version.put("versionNo", versionNo);
        version.put("late", false);
        mapper.insertSubmissionVersion(version);
        Long submissionVersionId = Group3AccessService.asLong(version.get("id"));

        for (Map<String, Object> item : items)
        {
            String itemId = String.valueOf(item.get("assignmentItemId"));
            JsonNode answer = answers.get(itemId);
            if (answer == null && answers.size() == 1)
            {
                answer = answers.values().iterator().next();
            }
            Map<String, Object> answerRow = new LinkedHashMap<>();
            answerRow.put("submissionVersionId", submissionVersionId);
            answerRow.put("assignmentId", assignmentId);
            answerRow.put("assignmentItemId", item.get("assignmentItemId"));
            answerRow.put("answerJson", answer.toString());
            mapper.insertSubmissionAnswer(answerRow);
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("bindingId", bindingId);
        event.put("studentUserId", userId);
        event.put("assignmentId", assignmentId);
        event.put("submissionVersionId", submissionVersionId);
        event.put("clientEventKey", "preview-" + assignmentId + "-" + userId + "-v" + versionNo);
        event.put("metadataJson", "{\"questionCount\":" + items.size() + "}");
        mapper.insertLearningEvent(event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submissionId);
        result.put("submissionVersionId", submissionVersionId);
        result.put("versionNo", versionNo);
        result.put("submitted", true);
        result.put("submittedAt", Group3Time.nowIso());
        return Group3Time.withServerTime(result);
    }

    private Map<String, Object> insertQuestionVersion(
            Long questionId,
            Long courseSpaceId,
            int versionNo,
            String questionKind,
            String stem,
            List<String> options,
            JsonNode answer,
            String explanation,
            BigDecimal maxScore)
    {
        String normalizedKind = questionKind == null ? null : questionKind.toUpperCase();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("questionId", questionId);
        row.put("versionNo", versionNo);
        row.put("questionKind", normalizedKind);
        row.put("stem", stem.trim());
        row.put("optionsJson", toJson(options));
        row.put("answerJson", toJson(answer));
        row.put("explanation", explanation == null ? "" : explanation.trim());
        row.put("maxScore", maxScore);
        row.put("userId", Group3Security.currentUserId());
        mapper.insertQuestionVersion(row);
        Long versionId = Group3AccessService.asLong(row.get("id"));
        row.put("versionId", versionId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("options", options);
        row.put("answer", answer);
        row.put("answerVisible", true);
        return row;
    }

    private void validateQuestion(
            String kind,
            String stem,
            List<String> options,
            JsonNode answer,
            BigDecimal maxScore,
            Long courseSpaceId,
            List<Long> knowledgePointIds)
    {
        String questionKind = kind == null ? "" : kind.toUpperCase();
        if (!Set.of("SINGLE", "MULTIPLE", "TRUE_FALSE", "SHORT_TEXT").contains(questionKind))
        {
            throw new ServiceException("题型无效", HttpStatus.BAD_REQUEST);
        }
        requiredText(stem, "题干", 10000);
        if (answer == null || answer.isNull())
        {
            throw new ServiceException("请填写答案", HttpStatus.BAD_REQUEST);
        }
        if (maxScore == null || maxScore.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("分值不能小于 0", HttpStatus.BAD_REQUEST);
        }
        if ("SINGLE".equals(questionKind) || "MULTIPLE".equals(questionKind))
        {
            if (options == null || options.size() < 2 || options.stream().anyMatch(value -> value == null || value.isBlank()))
            {
                throw new ServiceException("选择题至少需要两个非空选项", HttpStatus.BAD_REQUEST);
            }
        }
        if (!distinct(knowledgePointIds).isEmpty())
        {
            Set<Long> allowed = new LinkedHashSet<>();
            mapper.selectKnowledgePoints(courseSpaceId)
                    .forEach(point -> allowed.add(Group3AccessService.asLong(point.get("knowledgePointId"))));
            if (!allowed.containsAll(knowledgePointIds))
            {
                throw new ServiceException("知识点不属于当前课程", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private void linkKnowledgePoints(Long questionId, Long courseSpaceId, List<Long> ids)
    {
        for (Long knowledgePointId : distinct(ids))
        {
            mapper.linkQuestionKnowledgePoint(questionId, courseSpaceId, knowledgePointId);
        }
    }

    private void validateStudentAnswer(Map<String, Object> item, JsonNode answer)
    {
        if (answer == null || answer.isNull() || answer.isMissingNode()
                || (answer.isTextual() && answer.asText().isBlank()))
        {
            throw new ServiceException("请完成题目后再提交", HttpStatus.BAD_REQUEST);
        }
        String kind = String.valueOf(item.get("questionKind"));
        JsonNode options = parseJson(item.get("optionsJson"));
        if ("SINGLE".equals(kind) && !answer.has("choiceIndex"))
        {
            throw new ServiceException("单选题答案格式不正确", HttpStatus.BAD_REQUEST);
        }
        if ("MULTIPLE".equals(kind) && !answer.has("choiceIndices"))
        {
            throw new ServiceException("多选题答案格式不正确", HttpStatus.BAD_REQUEST);
        }
        if ("TRUE_FALSE".equals(kind) && !answer.has("correctBoolean"))
        {
            throw new ServiceException("判断题答案格式不正确", HttpStatus.BAD_REQUEST);
        }
        if ("SINGLE".equals(kind) && options != null && options.isArray())
        {
            int index = answer.path("choiceIndex").asInt(-1);
            if (index < 0 || index >= options.size())
            {
                throw new ServiceException("所选选项不存在", HttpStatus.BAD_REQUEST);
            }
        }
    }

    private String taskState(String status, Object opensAt, Object closesAt)
    {
        if ("ARCHIVED".equals(status))
        {
            return "ARCHIVED";
        }
        LocalDateTime now = Group3Time.nowUtc();
        LocalDateTime opens = parseIsoTime(opensAt);
        LocalDateTime closes = parseIsoTime(closesAt);
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

    private boolean isReached(Object isoTime)
    {
        LocalDateTime time = parseIsoTime(isoTime);
        return time != null && !Group3Time.nowUtc().isBefore(time);
    }

    private LocalDateTime parseIsoTime(Object value)
    {
        if (value == null)
        {
            return null;
        }
        try
        {
            return LocalDateTime.ofInstant(Instant.parse(value.toString()), java.time.ZoneOffset.UTC);
        }
        catch (RuntimeException ignored)
        {
            return null;
        }
    }

    private LocalDateTime localTime(Object value)
    {
        if (value instanceof Timestamp timestamp)
        {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime)
        {
            return localDateTime;
        }
        throw new ServiceException("服务端时间字段异常", HttpStatus.ERROR);
    }

    private void parseJsonField(Map<String, Object> row, String source, String target)
    {
        row.put(target, parseJson(row.get(source)));
        row.remove(source);
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
        catch (JsonProcessingException error)
        {
            return objectMapper.getNodeFactory().textNode(value.toString());
        }
    }

    private String toJson(Object value)
    {
        try
        {
            return objectMapper.writeValueAsString(value);
        }
        catch (JsonProcessingException error)
        {
            throw new ServiceException("题目内容无法序列化", HttpStatus.BAD_REQUEST);
        }
    }

    private String requiredText(String value, String label, int maxLength)
    {
        String text = value == null ? "" : value.trim();
        if (text.isBlank())
        {
            throw new ServiceException(label + "不能为空", HttpStatus.BAD_REQUEST);
        }
        if (text.length() > maxLength)
        {
            throw new ServiceException(label + "不能超过 " + maxLength + " 个字符", HttpStatus.BAD_REQUEST);
        }
        return text;
    }

    /**
     * 原地修改环节设置：直接更新对应的真实业务记录，不需要删除后重建。
     * 学生提交、成绩与资源版本均不改动；所有更新都带班级校验与教师发布权限。
     */
    @Transactional
    public Map<String, Object> updateStage(Group3Requests.UpdateStage request)
    {
        if (request == null || request.bindingId() == null || request.id() == null)
        {
            throw new ServiceException("缺少环节标识", HttpStatus.BAD_REQUEST);
        }
        Long bindingId = request.bindingId();
        accessService.requireTeacherBinding(bindingId);
        Long userId = Group3Security.currentUserId();
        String kind = String.valueOf(request.kind() == null ? "" : request.kind()).toUpperCase();

        LocalDateTime opensAt = Group3Time.parseUtc(request.opensAt(), "开放时间");
        LocalDateTime closesAt = Group3Time.parseUtc(request.closesAt(), "截止时间");
        LocalDateTime answerOpensAt = Group3Time.parseUtc(request.answerOpensAt(), "解析公布时间");

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", bindingId);
        row.put("userId", userId);
        row.put("title", blankToNull(request.title()));
        row.put("instructions", request.instructions());
        row.put("privateNotes", request.privateNotes());

        int changed;
        switch (kind)
        {
            case "MATERIAL":
            case "RESOURCE":
                if (opensAt == null)
                {
                    throw new ServiceException("请填写开放时间", HttpStatus.BAD_REQUEST);
                }
                if (closesAt != null && !closesAt.isAfter(opensAt))
                {
                    throw new ServiceException("截止时间必须晚于开放时间", HttpStatus.BAD_REQUEST);
                }
                row.put("resourceId", request.id());
                row.put("opensAt", opensAt);
                row.put("closesAt", closesAt);
                changed = mapper.updateReleaseWindow(row);
                kind = "MATERIAL";
                break;
            case "QUIZ":
                if (opensAt == null || closesAt == null)
                {
                    throw new ServiceException("请填写开放时间与截止时间", HttpStatus.BAD_REQUEST);
                }
                if (!closesAt.isAfter(opensAt))
                {
                    throw new ServiceException("截止时间必须晚于开放时间", HttpStatus.BAD_REQUEST);
                }
                if (answerOpensAt != null && answerOpensAt.isBefore(closesAt))
                {
                    throw new ServiceException("解析公布时间不能早于截止时间", HttpStatus.BAD_REQUEST);
                }
                row.put("assignmentId", request.id());
                row.put("opensAt", opensAt);
                row.put("closesAt", closesAt);
                if (answerOpensAt != null)
                {
                    row.put("answerOpensAt", answerOpensAt);
                }
                changed = mapper.updateAssignmentWindow(row);
                break;
            case "PLAN":
                if (row.get("title") == null && row.get("privateNotes") == null)
                {
                    throw new ServiceException("没有需要保存的修改", HttpStatus.BAD_REQUEST);
                }
                row.put("planId", request.id());
                changed = mapper.updatePlanContent(row);
                break;
            default:
                throw new ServiceException("不支持的环节类型：" + kind, HttpStatus.BAD_REQUEST);
        }
        if (changed == 0)
        {
            throw new ServiceException("环节不存在、已撤回或无权操作", HttpStatus.CONFLICT);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bindingId", bindingId);
        result.put("kind", kind);
        result.put("id", request.id());
        result.put("updated", true);
        result.put("opensAt", iso(opensAt));
        result.put("closesAt", iso(closesAt));
        result.put("answerOpensAt", iso(answerOpensAt));
        result.put("title", row.get("title"));
        return result;
    }

    private String blankToNull(String value)
    {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private String iso(LocalDateTime value)
    {
        return value == null ? null : value.atZone(java.time.ZoneOffset.UTC).toInstant().toString();
    }

    /**
     * 从某堂课的时间轴上移除环节。时间轴上的环节是真实业务对象的视图，
     * 因此移除 = 把对应对象归档（软删除），并结束该资料的有效发布。
     * 不物理删除数据，学生提交记录、成绩与审计信息保持完整。
     */
    @Transactional
    public Map<String, Object> removeStage(Group3Requests.RemoveStage request)
    {
        if (request == null || request.bindingId() == null || request.id() == null)
        {
            throw new ServiceException("缺少环节标识", HttpStatus.BAD_REQUEST);
        }
        Long bindingId = request.bindingId();
        accessService.requireTeacherBinding(bindingId);
        Long userId = Group3Security.currentUserId();
        String kind = String.valueOf(request.kind() == null ? "" : request.kind()).toUpperCase();
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", bindingId);
        row.put("userId", userId);
        int changed;
        switch (kind)
        {
            case "MATERIAL":
            case "DRAFT":
            case "RESOURCE":
                row.put("resourceId", request.id());
                changed = mapper.archiveResource(row);
                if (changed > 0)
                {
                    mapper.endReleasesForResource(row);
                }
                kind = "MATERIAL";
                break;
            case "QUIZ":
                row.put("assignmentId", request.id());
                changed = mapper.archiveAssignment(row);
                break;
            case "PLAN":
                row.put("planId", request.id());
                changed = mapper.archivePlan(row);
                break;
            default:
                throw new ServiceException("不支持的环节类型：" + kind, HttpStatus.BAD_REQUEST);
        }
        if (changed == 0)
        {
            throw new ServiceException("环节不存在、已被移除或无权操作", HttpStatus.CONFLICT);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bindingId", bindingId);
        result.put("kind", kind);
        result.put("id", request.id());
        result.put("removed", true);
        return result;
    }

    private <T> List<T> distinct(List<T> values)
    {
        return values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
    }
}
