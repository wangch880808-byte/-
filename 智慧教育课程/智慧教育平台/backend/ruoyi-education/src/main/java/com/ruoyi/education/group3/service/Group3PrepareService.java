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
import com.ruoyi.education.group3.contract.PreviewSubmissionHandler;
import com.ruoyi.education.group3.contract.PreviewTaskProvider;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Constants;
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
    private final PreviewSubmissionHandler submissionHandler;

    public Group3PrepareService(
            Group3Mapper mapper,
            Group3AccessService accessService,
            Group3ResourceService resourceService,
            ObjectMapper objectMapper,
            PreviewSubmissionHandler submissionHandler)
    {
        this.mapper = mapper;
        this.accessService = accessService;
        this.resourceService = resourceService;
        this.objectMapper = objectMapper;
        this.submissionHandler = submissionHandler;
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

    /** 把本课程题目以当前版本快照发布到题目资源库。 */
    @Transactional
    public Map<String, Object> shareQuestions(Group3Requests.ShareQuestions request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        List<Long> questionIds = distinct(request.questionIds());
        if (questionIds.isEmpty())
        {
            throw new ServiceException("请选择要共享的题目", HttpStatus.BAD_REQUEST);
        }
        if (questionIds.size() > 100)
        {
            throw new ServiceException("单次最多共享 100 道题", HttpStatus.BAD_REQUEST);
        }
        Long userId = Group3Security.currentUserId();
        List<Long> shareIds = new ArrayList<>();
        int published = 0;
        int reopened = 0;
        int skipped = 0;
        int missing = 0;
        for (Long questionId : questionIds)
        {
            Map<String, Object> question = mapper.selectQuestionForShare(courseSpaceId, questionId);
            if (question == null)
            {
                missing++;
                continue;
            }
            if ("ARCHIVED".equals(String.valueOf(question.get("status"))))
            {
                skipped++;
                continue;
            }
            Long existingShareId = Group3AccessService.asLong(question.get("shareId"));
            if (existingShareId != null)
            {
                if ("PUBLISHED".equals(String.valueOf(question.get("shareStatus"))))
                {
                    skipped++;
                }
                else
                {
                    mapper.reopenQuestionShare(existingShareId, userId);
                    reopened++;
                }
                shareIds.add(existingShareId);
                continue;
            }
            Map<String, Object> share = new LinkedHashMap<>();
            share.put("questionVersionId", Group3AccessService.asLong(question.get("versionId")));
            share.put("courseSpaceId", courseSpaceId);
            share.put("userId", userId);
            mapper.insertQuestionShare(share);
            shareIds.add(Group3AccessService.asLong(share.get("id")));
            published++;
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("published", published);
        result.put("reopened", reopened);
        result.put("skipped", skipped);
        result.put("missing", missing);
        result.put("shareIds", shareIds);
        return result;
    }

    /** 撤回本课程已共享的题目，已引入的副本不受影响。 */
    @Transactional
    public Map<String, Object> withdrawSharedQuestions(Group3Requests.WithdrawShares request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        List<Long> shareIds = distinct(request.shareIds());
        if (shareIds.isEmpty())
        {
            throw new ServiceException("请选择要撤回的共享题目", HttpStatus.BAD_REQUEST);
        }
        int withdrawn = mapper.withdrawQuestionShares(courseSpaceId, shareIds);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("withdrawn", withdrawn);
        return result;
    }

    /** 题目资源库列表，仅教师可访问。 */
    public List<Map<String, Object>> questionLibrary(String keyword, String questionKind, String difficulty, Long collegeId)
    {
        Group3Security.requireTeacher();
        String normalizedKeyword = keyword == null ? null : keyword.trim();
        if (normalizedKeyword != null && normalizedKeyword.isEmpty())
        {
            normalizedKeyword = null;
        }
        String kind = questionKind == null || questionKind.isBlank() ? null : questionKind.trim().toUpperCase();
        String level = difficulty == null || difficulty.isBlank() ? null : difficulty.trim().toUpperCase();
        return mapper.selectQuestionShares(normalizedKeyword, kind, level, collegeId, 200);
    }

    /** 题目资源库可选学院及共享数量。 */
    public List<Map<String, Object>> questionLibraryColleges()
    {
        Group3Security.requireTeacher();
        return mapper.selectQuestionShareColleges();
    }

    /** 题目资源库详情，包含答案与解析，仅教师可访问。 */
    public Map<String, Object> questionLibraryDetail(Long shareId)
    {
        Group3Security.requireTeacher();
        Map<String, Object> share = mapper.selectQuestionShare(shareId, true);
        if (share == null)
        {
            throw new ServiceException("共享题目不存在或已下架", HttpStatus.NOT_FOUND);
        }
        parseJsonField(share, "optionsJson", "options");
        parseJsonField(share, "answerJson", "answer");
        share.put("answerVisible", true);
        return share;
    }

    /** 把资源库题目复制到当前课程题库，知识点不跨课程搬运。 */
    @Transactional
    public Map<String, Object> importSharedQuestions(Group3Requests.ImportSharedQuestions request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Long folderId = request.folderId();
        if (folderId != null && mapper.countQuestionFolder(courseSpaceId, folderId) == 0)
        {
            throw new ServiceException("题库文件夹不存在", HttpStatus.BAD_REQUEST);
        }
        List<Long> shareIds = distinct(request.shareIds());
        if (shareIds.isEmpty())
        {
            throw new ServiceException("请选择要引入的题目", HttpStatus.BAD_REQUEST);
        }
        if (shareIds.size() > 50)
        {
            throw new ServiceException("单次最多引入 50 道题", HttpStatus.BAD_REQUEST);
        }
        Long userId = Group3Security.currentUserId();
        List<Long> importedQuestionIds = new ArrayList<>();
        int skipped = 0;
        for (Long shareId : shareIds)
        {
            Map<String, Object> share = mapper.selectQuestionShare(shareId, true);
            if (share == null || mapper.countImportedShare(courseSpaceId, shareId) > 0)
            {
                skipped++;
                continue;
            }
            String title = share.get("title") == null ? "共享题目" : share.get("title").toString();
            if (title.length() > 200)
            {
                title = title.substring(0, 200);
            }
            Map<String, Object> question = new LinkedHashMap<>();
            question.put("courseSpaceId", courseSpaceId);
            question.put("folderId", folderId);
            question.put("title", title);
            question.put("userId", userId);
            question.put("originShareId", shareId);
            mapper.insertImportedQuestion(question);
            Long questionId = Group3AccessService.asLong(question.get("id"));

            List<String> options = jsonToStringList(share.get("optionsJson"));
            JsonNode answer = parseJson(share.get("answerJson"));
            String explanation = share.get("explanation") == null ? "" : share.get("explanation").toString();
            String rubric = extractRubric(share.get("rubricJson"));
            BigDecimal maxScore = share.get("maxScore") instanceof BigDecimal score
                    ? score : new BigDecimal(String.valueOf(share.get("maxScore")));
            insertQuestionVersion(
                    questionId, courseSpaceId, 1,
                    share.get("questionKind") == null ? null : share.get("questionKind").toString(),
                    share.get("stem") == null ? "" : share.get("stem").toString(),
                    options, answer, explanation, rubric, maxScore,
                    share.get("difficulty") == null ? null : share.get("difficulty").toString(),
                    share.get("tags") == null ? null : share.get("tags").toString());
            mapper.incrementShareImportCount(shareId);
            importedQuestionIds.add(questionId);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("imported", importedQuestionIds.size());
        result.put("skipped", skipped);
        result.put("questionIds", importedQuestionIds);
        return result;
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
        Long folderId = request.folderId();
        if (folderId != null && mapper.countQuestionFolder(courseSpaceId, folderId) == 0)
        {
            throw new ServiceException("题库文件夹不存在", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> question = new LinkedHashMap<>();
        question.put("courseSpaceId", courseSpaceId);
        question.put("folderId", folderId);
        question.put("title", title);
        question.put("userId", Group3Security.currentUserId());
        mapper.insertQuestion(question);
        Long questionId = Group3AccessService.asLong(question.get("id"));
        Map<String, Object> version = insertQuestionVersion(
                questionId, courseSpaceId, 1, request.questionKind(), request.stem(),
                request.options(), request.answer(), request.explanation(),
                request.scoringRubric(), request.maxScore(),
                request.difficulty(), request.tags());
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
                request.options(), request.answer(), request.explanation(),
                request.scoringRubric(), request.maxScore(),
                request.difficulty(), request.tags());
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
                    closesAt.toInstant(java.time.ZoneOffset.UTC).toString(),
                    Group3Constants.RELEASE_PREPARE);
            resourceService.release(release);
        }

        // 整卷发布：questionVersionIds 支持一次绑定多道题；questionVersionId 保留单题兼容。
        List<Long> requestedQuestionVersionIds = distinct(request.questionVersionIds());
        if (requestedQuestionVersionIds.isEmpty() && request.questionVersionId() != null)
        {
            requestedQuestionVersionIds = List.of(request.questionVersionId());
        }
        if (requestedQuestionVersionIds.size() > 100)
        {
            throw new ServiceException("单份试卷最多 100 道题", HttpStatus.BAD_REQUEST);
        }
        List<Map<String, Object>> questionVersions = new ArrayList<>();
        for (Long versionId : requestedQuestionVersionIds)
        {
            Map<String, Object> questionVersion = mapper.selectQuestionVersion(
                    request.bindingId(), versionId, Group3Security.currentUserId());
            if (questionVersion == null)
            {
                throw new ServiceException("预习题目不属于当前课程", HttpStatus.BAD_REQUEST);
            }
            questionVersions.add(questionVersion);
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
        int sortOrder = 1;
        BigDecimal totalScore = BigDecimal.ZERO;
        for (Map<String, Object> questionVersion : questionVersions)
        {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("assignmentId", assignmentId);
            item.put("courseSpaceId", courseSpaceId);
            item.put("sortOrder", sortOrder++);
            item.put("itemKind", "QUESTION");
            item.put("prompt", questionVersion.get("stem"));
            item.put("questionId", questionVersion.get("questionId"));
            item.put("questionVersionId", questionVersion.get("versionId"));
            item.put("maxScore", questionVersion.get("maxScore"));
            mapper.insertAssignmentItem(item);
            Object score = questionVersion.get("maxScore");
            if (score instanceof BigDecimal value)
            {
                totalScore = totalScore.add(value);
            }
            else if (score != null)
            {
                totalScore = totalScore.add(new BigDecimal(score.toString()));
            }
        }
        Map<String, Object> target = new LinkedHashMap<>();
        target.put("assignmentId", assignmentId);
        target.put("teachingClassId", teachingClassId);
        mapper.insertAssignmentTargets(target);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("assignmentId", assignmentId);
        result.put("itemCount", questionVersions.size());
        result.put("totalScore", totalScore);
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
        Map<String, Object> submission = submissionHandler.existingSubmission(
                Group3AccessService.asLong(task.get("targetId")));
        task.put("submitted", submission != null);
        task.put("submission", submission);
        // 预习详情只附带课前准备发布的资料，避免把课程建设资料越界带入课前学习。
        task.put("resources", resourceService.studentPrepMaterials(bindingId));
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
        Map<String, Object> target = submissionHandler.studentTarget(assignmentId, userId);
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
        Map<String, Object> existing = submissionHandler.existingSubmission(targetId);
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
            validateStudentAnswer(item, resolveAnswer(item, answers));
        }

        // 持久化移交 PreviewSubmissionHandler；默认由本地实现写共享作业表，
        // 第五组统一作业服务提供 @Primary 实现后即自动接管评分/改交/成绩。
        Map<String, Object> result = submissionHandler.submit(
                new PreviewSubmissionHandler.PreviewSubmissionCommand(
                        bindingId, assignmentId, targetId, userId, items, answers));
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
            String scoringRubric,
            BigDecimal maxScore,
            String difficulty,
            String tags)
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
        row.put("rubricJson", serializeScoringRubric(scoringRubric, normalizedKind));
        row.put("difficulty", normalizeDifficulty(difficulty));
        row.put("tags", tags == null || tags.isBlank() ? null : tags.trim());
        row.put("maxScore", maxScore);
        row.put("userId", Group3Security.currentUserId());
        mapper.insertQuestionVersion(row);
        Long versionId = Group3AccessService.asLong(row.get("id"));
        row.put("versionId", versionId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("options", options);
        row.put("answer", answer);
        row.put("answerVisible", true);
        row.put("difficulty", normalizeDifficulty(difficulty));
        row.put("tags", tags == null || tags.isBlank() ? null : tags.trim());
        return row;
    }

    private String normalizeDifficulty(String difficulty)
    {
        if (difficulty == null || difficulty.isBlank())
        {
            return "MEDIUM";
        }
        String value = difficulty.trim().toUpperCase();
        if (!Set.of("EASY", "MEDIUM", "HARD").contains(value))
        {
            throw new ServiceException("难度取值无效", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    /** 组卷：按条件从当前教学班课程题库抽题，返回题目明细供教师编排，不落库。 */
    public Map<String, Object> composePaper(Group3Requests.ComposePaper request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));

        int count = request.count() == null ? 10 : request.count();
        if (count < 1 || count > 100)
        {
            throw new ServiceException("组卷题量须在 1 到 100 之间", HttpStatus.BAD_REQUEST);
        }
        String mode = request.mode() == null ? "" : request.mode().trim().toUpperCase();
        if (!mode.isEmpty() && !Set.of("RANDOM", "SEQUENTIAL").contains(mode))
        {
            throw new ServiceException("组卷方式无效", HttpStatus.BAD_REQUEST);
        }
        String difficulty = request.difficulty() == null || request.difficulty().isBlank()
                ? null : normalizeDifficulty(request.difficulty());
        String questionKind = request.questionKind() == null || request.questionKind().isBlank()
                ? null : request.questionKind().trim().toUpperCase();
        if (questionKind != null && !Set.of("SINGLE", "MULTIPLE", "TRUE_FALSE", "SHORT_TEXT").contains(questionKind))
        {
            throw new ServiceException("题型无效", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("courseSpaceId", courseSpaceId);
        row.put("questionKind", questionKind);
        row.put("difficulty", difficulty);
        row.put("knowledgePoint", request.knowledgePoint() == null || request.knowledgePoint().isBlank()
                ? null : request.knowledgePoint().trim());
        row.put("tag", request.tag() == null || request.tag().isBlank() ? null : request.tag().trim());
        row.put("mode", mode);
        row.put("count", count);

        List<Map<String, Object>> questions = mapper.selectQuestionsForCompose(row);
        questions.forEach(question -> {
            parseJsonField(question, "optionsJson", "options");
            parseJsonField(question, "answerJson", "answer");
        });
        BigDecimal totalScore = BigDecimal.ZERO;
        for (Map<String, Object> question : questions)
        {
            Object score = question.get("maxScore");
            if (score instanceof BigDecimal value)
            {
                totalScore = totalScore.add(value);
            }
            else if (score != null)
            {
                totalScore = totalScore.add(new BigDecimal(score.toString()));
            }
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("questions", questions);
        result.put("count", questions.size());
        result.put("totalScore", totalScore);
        return result;
    }

    private String serializeScoringRubric(String scoringRubric, String normalizedKind)
    {
        if (scoringRubric == null || scoringRubric.isBlank())
        {
            return null;
        }
        // 只有简答题才存评分标准，其他题型忽略
        if (!"SHORT_TEXT".equals(normalizedKind))
        {
            return null;
        }
        try
        {
            return objectMapper.writeValueAsString(java.util.Map.of(
                    "rubric", scoringRubric.trim(),
                    "updatedAt", java.time.Instant.now().toString()));
        }
        catch (Exception ignore)
        {
            return null;
        }
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

    /**
     * 解析单题答案：优先按 assignmentItemId 精确匹配；
     * 若只有一份答案且未精确匹配，则回退使用该唯一答案（兼容单题任务）。
     */
    private JsonNode resolveAnswer(Map<String, Object> item, Map<String, JsonNode> answers)
    {
        String itemId = String.valueOf(item.get("assignmentItemId"));
        JsonNode answer = answers.get(itemId);
        if (answer == null && answers.size() == 1)
        {
            answer = answers.values().iterator().next();
        }
        return answer;
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

    private List<String> jsonToStringList(Object value)
    {
        JsonNode node = parseJson(value);
        List<String> items = new ArrayList<>();
        if (node != null && node.isArray())
        {
            node.forEach(item -> items.add(item.asText()));
        }
        return items;
    }

    private String extractRubric(Object value)
    {
        JsonNode node = parseJson(value);
        if (node == null)
        {
            return "";
        }
        return node.isTextual() ? node.asText() : node.path("rubric").asText("");
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

    private <T> List<T> distinct(List<T> values)
    {
        return values == null ? List.of() : new ArrayList<>(new LinkedHashSet<>(values));
    }
}
