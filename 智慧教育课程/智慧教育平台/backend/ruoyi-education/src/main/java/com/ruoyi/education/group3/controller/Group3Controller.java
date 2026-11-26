package com.ruoyi.education.group3.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.course.api.EduTeacherRef;
import com.ruoyi.education.group3.contract.LearningAnalyticsProvider;
import com.ruoyi.education.group3.contract.PreviewTaskProvider;
import com.ruoyi.education.group3.contract.PublishedResourceProvider;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.service.Group3AccessService;
import com.ruoyi.education.group3.service.Group3DiscussionService;
import com.ruoyi.education.group3.service.Group3LearningAnalyticsService;
import com.ruoyi.education.group3.service.Group3PrepareService;
import com.ruoyi.education.group3.service.Group3ResourceService;
import com.ruoyi.education.group3.service.Group3StudentDashboardService;
import com.ruoyi.education.group3.service.Group3TransferService;
import com.ruoyi.education.group3.service.Group3PublicLibraryService;
import com.ruoyi.education.group3.service.Group3StageIdeologyService;
import com.ruoyi.education.group3.support.Group3Requests;
import com.ruoyi.education.group3.support.Group3Security;
import com.ruoyi.education.service.EduOutlineService;

@RestController
@RequestMapping("/education/group3")
public class Group3Controller
{
    private final Group3AccessService accessService;
    private final Group3ResourceService resourceService;
    private final Group3PrepareService prepareService;
    private final Group3DiscussionService discussionService;
    private final Group3StudentDashboardService studentDashboardService;
    private final Group3TransferService transferService;
    private final Group3Mapper mapper;
    private final PublishedResourceProvider publishedResourceProvider;
    private final PreviewTaskProvider previewTaskProvider;
    private final LearningAnalyticsProvider learningAnalyticsProvider;
    private final EduCourseAccessService courseAccess;
    private final EduOutlineService outline;
    private final Group3PublicLibraryService publicLibraryService;
    private final Group3StageIdeologyService stageIdeologyService;
    private final Group3LearningAnalyticsService learningAnalyticsService;

    public Group3Controller(
            Group3AccessService accessService,
            Group3ResourceService resourceService,
            Group3PrepareService prepareService,
            Group3DiscussionService discussionService,
            Group3StudentDashboardService studentDashboardService,
            Group3TransferService transferService,
            Group3Mapper mapper,
            PublishedResourceProvider publishedResourceProvider,
            PreviewTaskProvider previewTaskProvider,
            LearningAnalyticsProvider learningAnalyticsProvider,
            EduCourseAccessService courseAccess,
            EduOutlineService outline,
            Group3PublicLibraryService publicLibraryService,
            Group3StageIdeologyService stageIdeologyService,
            Group3LearningAnalyticsService learningAnalyticsService)
    {
        this.accessService = accessService;
        this.resourceService = resourceService;
        this.prepareService = prepareService;
        this.discussionService = discussionService;
        this.studentDashboardService = studentDashboardService;
        this.transferService = transferService;
        this.mapper = mapper;
        this.publishedResourceProvider = publishedResourceProvider;
        this.previewTaskProvider = previewTaskProvider;
        this.learningAnalyticsProvider = learningAnalyticsProvider;
        this.courseAccess = courseAccess;
        this.outline = outline;
        this.publicLibraryService = publicLibraryService;
        this.stageIdeologyService = stageIdeologyService;
        this.learningAnalyticsService = learningAnalyticsService;
    }

    @GetMapping("/contexts")
    public R<List<Map<String, Object>>> contexts()
    {
        List<Map<String, Object>> contexts = accessService.contexts();
        contexts.forEach(row -> row.put("selectionKey", row.get("bindingId") + ":" + row.get("courseSpaceId")));
        return R.ok(contexts);
    }

    @GetMapping("/overview")
    public R<Map<String, Object>> overview(@RequestParam Long bindingId)
    {
        return R.ok(resourceService.overview(bindingId));
    }

    @Log(title = "课程资料上传", businessType = BusinessType.INSERT)
    @PostMapping("/resources")
    public R<Map<String, Object>> upload(
            @RequestParam Long bindingId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long unitId,
            @RequestParam(required = false) Long folderId,
            @RequestParam(value = "releaseSource", required = false, defaultValue = "NONE") String releaseSource,
            @RequestParam(value = "origin", required = false, defaultValue = "STUDIO") String origin,
            @RequestParam("file") MultipartFile file)
    {
        Map<String, Object> result = resourceService.upload(bindingId, title, unitId, folderId, file, releaseSource, origin);
        String status = String.valueOf(result.get("processingStatus"));
        String message = switch (status)
        {
            case "READY" -> "资料已上传并完成处理";
            case "FAILED" -> "资料转换失败，请查看处理状态";
            default -> "资料已上传，正在转换，请稍后刷新查看";
        };
        return R.ok(result, message);
    }

    /** 创建外链资料（添加网址 / 在线图书）。 */
    @Log(title = "课程资料链接", businessType = BusinessType.INSERT)
    @PostMapping("/resources/link")
    public R<Map<String, Object>> createLink(@RequestBody Group3Requests.CreateLinkResource request)
    {
        return R.ok(resourceService.createLink(request), "外链资料已创建");
    }

    @Log(title = "课程资料新版本", businessType = BusinessType.UPDATE)
    @PostMapping("/resources/{resourceId}/versions")
    public R<Map<String, Object>> uploadVersion(
            @PathVariable Long resourceId,
            @RequestParam Long bindingId,
            @RequestParam(value = "releaseSource", required = false, defaultValue = "NONE") String releaseSource,
            @RequestParam(value = "origin", required = false, defaultValue = "STUDIO") String origin,
            @RequestParam("file") MultipartFile file)
    {
        return R.ok(resourceService.uploadVersion(resourceId, bindingId, file, releaseSource, origin), "新版本已保存");
    }

    @GetMapping("/resources/{resourceId}/versions")
    public R<List<Map<String, Object>>> versions(
            @PathVariable Long resourceId,
            @RequestParam Long bindingId)
    {
        return R.ok(resourceService.versions(resourceId, bindingId));
    }

    @Log(title = "资料转码重试", businessType = BusinessType.UPDATE)
    @PostMapping("/versions/{versionId}/retry-conversion")
    public R<Map<String, Object>> retryConversion(
            @PathVariable Long versionId,
            @RequestParam Long bindingId)
    {
        return R.ok(resourceService.retryConversion(versionId, bindingId), "已重新提交转换");
    }

    @GetMapping("/versions/{versionId}")
    public R<Map<String, Object>> version(
            @PathVariable Long versionId,
            @RequestParam Long bindingId)
    {
        return R.ok(resourceService.versionDetail(versionId, bindingId));
    }

    @GetMapping("/versions/{versionId}/pages/{pageNo}")
    public ResponseEntity<Resource> page(
            @PathVariable Long versionId,
            @PathVariable int pageNo,
            @RequestParam Long bindingId) throws Exception
    {
        Path path = resourceService.pagePath(versionId, bindingId, pageNo);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("page-" + pageNo + ".png",
                                StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }

    /** 在线播放通道：仅做可见性校验，不受下载开关限制，供视频资料与任务点使用。 */
    @GetMapping("/versions/{versionId}/media")
    public ResponseEntity<Resource> media(
            @PathVariable Long versionId,
            @RequestParam Long bindingId)
    {
        Resource resource = resourceService.loadVersionFile(versionId, bindingId);
        String contentType = resourceService.contentType(versionId, bindingId);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.parseMediaType(contentType))
                .body(resource);
    }

    @GetMapping("/versions/{versionId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long versionId,
            @RequestParam Long bindingId)
    {
        resourceService.requireDownloadAllowed(versionId, bindingId);
        Resource resource = resourceService.loadVersionFile(versionId, bindingId);
        String fileName = resourceService.fileName(versionId, bindingId);
        String contentType = resourceService.contentType(versionId, bindingId);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, StandardCharsets.UTF_8)
                                .build().toString())
                .body(resource);
    }

    @GetMapping("/resources/export")
    public ResponseEntity<Resource> exportResources(@RequestParam Long bindingId)
    {
        byte[] content = transferService.exportResources(bindingId);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("课程资料.zip", StandardCharsets.UTF_8)
                                .build().toString())
                .body(new ByteArrayResource(content));
    }

    @Log(title = "课程资料导入", businessType = BusinessType.IMPORT)
    @PostMapping("/resources/import")
    public R<Map<String, Object>> importResources(
            @RequestParam Long bindingId,
            @RequestParam("file") MultipartFile file)
    {
        Map<String, Object> result = transferService.importResources(bindingId, file);
        return R.ok(result, "导入完成：成功 " + result.get("imported") + " 个");
    }

    @Log(title = "课程资料发布", businessType = BusinessType.UPDATE)
    @PostMapping("/releases")
    public R<Map<String, Object>> release(@RequestBody Group3Requests.ReleaseResource request)
    {
        return R.ok(resourceService.release(request), "发布范围已更新");
    }

    @Log(title = "课程资料撤回", businessType = BusinessType.UPDATE)
    @PostMapping("/releases/{releaseId}/revoke")
    public R<Map<String, Object>> revoke(
            @PathVariable Long releaseId,
            @RequestParam Long bindingId,
            @RequestBody(required = false) Map<String, String> body)
    {
        return R.ok(resourceService.revoke(releaseId, bindingId,
                body == null ? null : body.get("reason")), "资料已撤回");
    }

    @Log(title = "课程资料编辑", businessType = BusinessType.UPDATE)
    @PatchMapping("/resources/{resourceId}")
    public R<Map<String, Object>> updateResource(
            @PathVariable Long resourceId,
            @RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Object titleValue = body.get("title");
        if (titleValue == null || titleValue.toString().isBlank())
        {
            return R.fail("标题不能为空");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceId", resourceId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("title", titleValue.toString().trim());
        mapper.updateResource(row);
        return R.ok(row, "资料已更新");
    }

    @Log(title = "课程资料归档", businessType = BusinessType.UPDATE)
    @PatchMapping("/resources/{resourceId}/status")
    public R<Map<String, Object>> archiveResource(
            @PathVariable Long resourceId,
            @RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String status = (String) body.get("status");
        if (!"DRAFT".equals(status) && !"ACTIVE".equals(status) && !"ARCHIVED".equals(status))
        {
            return R.fail("status 必须是 DRAFT / ACTIVE / ARCHIVED");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceId", resourceId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("status", status);
        mapper.updateResourceStatus(row);
        return R.ok(row, "资料状态已更新");
    }

    @Log(title = "资料讲解信息", businessType = BusinessType.UPDATE)
    @PatchMapping("/resources/{resourceId}/enhance")
    public R<Map<String, Object>> updateResourceEnhance(
            @PathVariable Long resourceId,
            @RequestBody Group3Requests.ResourceEnhance request)
    {
        return R.ok(resourceService.updateEnhance(resourceId, request), "资料设置已保存");
    }

    @GetMapping("/student/progress")
    public R<Map<String, Object>> studentWatchProgress(@RequestParam Long bindingId)
    {
        return R.ok(resourceService.studentProgress(bindingId));
    }

    @PostMapping("/student/materials/{resourceId}/watch")
    public R<Map<String, Object>> reportWatch(
            @PathVariable Long resourceId,
            @RequestBody Group3Requests.WatchProgress request)
    {
        return R.ok(resourceService.reportWatch(resourceId, request.bindingId(), request), "进度已记录");
    }

    /** 学生学习进度看板：任务点完成状态、整体完成率与待办提醒。 */
    @GetMapping("/student/dashboard")
    public R<Map<String, Object>> studentDashboard(@RequestParam Long bindingId)
    {
        return R.ok(studentDashboardService.dashboard(bindingId));
    }

    /** 作答回看 / 错题本：仅返回解析已公布的提交。 */
    @GetMapping("/student/answer-review")
    public R<Map<String, Object>> studentAnswerReview(@RequestParam Long bindingId)
    {
        return R.ok(studentDashboardService.answerReview(bindingId));
    }

    /** 学生个人笔记：读取本人笔记。 */
    @GetMapping("/student/note")
    public R<Map<String, Object>> studentNote(@RequestParam Long bindingId)
    {
        return R.ok(studentDashboardService.note(bindingId));
    }

    /** 学生个人笔记：保存本人笔记。 */
    @PutMapping("/student/note")
    public R<Map<String, Object>> saveStudentNote(@RequestBody Group3Requests.StudentNote request)
    {
        return R.ok(studentDashboardService.saveNote(request.bindingId(), request.content()), "笔记已保存");
    }

    @GetMapping("/questions")
    public R<List<Map<String, Object>>> questions(@RequestParam Long bindingId)
    {
        return R.ok(prepareService.questions(bindingId));
    }

    /** 题目资源库：发布题目。 */
    @Log(title = "题库分享", businessType = BusinessType.GRANT)
    @PostMapping("/questions/library/share")
    public R<Map<String, Object>> shareQuestions(@RequestBody Group3Requests.ShareQuestions request)
    {
        Map<String, Object> result = prepareService.shareQuestions(request);
        return R.ok(result, "已发布到题目资源库");
    }

    /** 题目资源库：撤回共享。 */
    @Log(title = "题库分享", businessType = BusinessType.UPDATE)
    @PostMapping("/questions/library/withdraw")
    public R<Map<String, Object>> withdrawSharedQuestions(@RequestBody Group3Requests.WithdrawShares request)
    {
        Map<String, Object> result = prepareService.withdrawSharedQuestions(request);
        return R.ok(result, "已撤回共享");
    }

    /** 题目资源库：检索共享题目。 */
    @GetMapping("/questions/library")
    public R<List<Map<String, Object>>> questionLibrary(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String questionKind,
            @RequestParam(required = false) String difficulty,
            @RequestParam(required = false) Long collegeId)
    {
        return R.ok(prepareService.questionLibrary(keyword, questionKind, difficulty, collegeId));
    }

    /** 题目资源库：可选学院列表。 */
    @GetMapping("/questions/library/colleges")
    public R<List<Map<String, Object>>> questionLibraryColleges()
    {
        return R.ok(prepareService.questionLibraryColleges());
    }

    /** 题目资源库：共享题目详情（含答案与解析）。 */
    @GetMapping("/questions/library/{shareId}")
    public R<Map<String, Object>> questionLibraryDetail(@PathVariable Long shareId)
    {
        return R.ok(prepareService.questionLibraryDetail(shareId));
    }

    /** 题目资源库：引入到当前课程题库。 */
    @Log(title = "题库导入", businessType = BusinessType.IMPORT)
    @PostMapping("/questions/library/import")
    public R<Map<String, Object>> importSharedQuestions(@RequestBody Group3Requests.ImportSharedQuestions request)
    {
        Map<String, Object> result = prepareService.importSharedQuestions(request);
        return R.ok(result, "题目已引入当前题库");
    }

    @Log(title = "题库题目", businessType = BusinessType.INSERT)
    @PostMapping("/questions")
    public R<Map<String, Object>> createQuestion(@RequestBody Group3Requests.Question request)
    {
        return R.ok(prepareService.createQuestion(request), "题目已创建");
    }

    @Log(title = "题库组卷", businessType = BusinessType.INSERT)
    @PostMapping("/questions/compose")
    public R<Map<String, Object>> composePaper(@RequestBody Group3Requests.ComposePaper request)
    {
        return R.ok(prepareService.composePaper(request), "组卷完成");
    }

    @Log(title = "题库题目", businessType = BusinessType.UPDATE)
    @PostMapping("/questions/{questionId}/versions")
    public R<Map<String, Object>> createQuestionVersion(
            @PathVariable Long questionId,
            @RequestBody Group3Requests.QuestionVersion request)
    {
        return R.ok(prepareService.createQuestionVersion(questionId, request), "题目新版本已创建");
    }

    @Log(title = "题库题目", businessType = BusinessType.UPDATE)
    @PatchMapping("/questions/{questionId}/status")
    public R<Map<String, Object>> updateQuestionStatus(
            @PathVariable Long questionId,
            @RequestParam Long bindingId,
            @RequestBody Group3Requests.Status request)
    {
        return R.ok(prepareService.updateQuestionStatus(bindingId, questionId, request), "题目状态已更新");
    }

    @GetMapping("/questions/export")
    public ResponseEntity<Resource> exportQuestions(@RequestParam Long bindingId)
    {
        byte[] content = transferService.exportQuestions(bindingId);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("课程题库.xlsx", StandardCharsets.UTF_8)
                                .build().toString())
                .body(new ByteArrayResource(content));
    }

    @PostMapping("/questions/import/preview")
    public R<List<Map<String, Object>>> previewQuestionImport(
            @RequestParam Long bindingId,
            @RequestParam("file") MultipartFile file)
    {
        return R.ok(transferService.previewQuestionImport(bindingId, file));
    }

    @Log(title = "题库导入", businessType = BusinessType.IMPORT)
    @PostMapping("/questions/import")
    public R<Map<String, Object>> importQuestions(@RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        List<Map<String, Object>> questions = new ArrayList<>();
        if (body.get("questions") instanceof List<?> list)
        {
            for (Object item : list)
            {
                if (item instanceof Map<?, ?> map)
                {
                    Map<String, Object> row = new LinkedHashMap<>();
                    map.forEach((key, value) -> row.put(String.valueOf(key), value));
                    questions.add(row);
                }
            }
        }
        Map<String, Object> result = transferService.importQuestions(bindingId, questions);
        return R.ok(result, "导入完成：成功 " + result.get("imported") + " 题");
    }

    @GetMapping("/plans")
    public R<List<Map<String, Object>>> plans(@RequestParam Long bindingId)
    {
        return R.ok(prepareService.plans(bindingId));
    }

    @Log(title = "备课方案", businessType = BusinessType.INSERT)
    @PostMapping("/plans")
    public R<Map<String, Object>> createPlan(@RequestBody Group3Requests.LessonPlan request)
    {
        return R.ok(prepareService.createPlan(request), "备课方案已保存");
    }

    @GetMapping("/preview-tasks")
    public R<List<Map<String, Object>>> previewTasks(@RequestParam Long bindingId)
    {
        return R.ok(previewTaskProvider.previewTasksForCurrentUser(bindingId));
    }

    @Log(title = "预习任务", businessType = BusinessType.INSERT)
    @PostMapping("/preview-tasks")
    public R<Map<String, Object>> publishPreview(@RequestBody Group3Requests.PreviewTask request)
    {
        return R.ok(prepareService.publishPreview(request), "预习任务已发布");
    }

    @Log(title = "预习任务", businessType = BusinessType.DELETE)
    @DeleteMapping("/preview-tasks/{assignmentId}")
    public R<Map<String, Object>> deletePreviewTask(
            @PathVariable Long assignmentId,
            @RequestParam Long bindingId)
    {
        return R.ok(prepareService.deletePreviewTask(bindingId, assignmentId), "预习任务已删除");
    }

    @GetMapping("/student/materials")
    public R<List<Map<String, Object>>> studentMaterials(@RequestParam Long bindingId)
    {
        return R.ok(resourceService.studentMaterials(bindingId));
    }

    @GetMapping("/student/prep-materials")
    public R<List<Map<String, Object>>> studentPrepMaterials(@RequestParam Long bindingId)
    {
        return R.ok(resourceService.studentPrepMaterials(bindingId));
    }

    @GetMapping("/student/previews")
    public R<List<Map<String, Object>>> studentPreviews(@RequestParam Long bindingId)
    {
        return R.ok(prepareService.studentPreviewTasks(bindingId));
    }

    @GetMapping("/student/previews/{assignmentId}")
    public R<Map<String, Object>> studentPreviewDetail(
            @PathVariable Long assignmentId,
            @RequestParam Long bindingId)
    {
        return R.ok(prepareService.studentPreviewDetail(bindingId, assignmentId));
    }

    @PostMapping("/student/previews/{assignmentId}/submit")
    public R<Map<String, Object>> submitPreview(
            @PathVariable Long assignmentId,
            @RequestParam Long bindingId,
            @RequestBody Group3Requests.PreviewSubmission request)
    {
        return R.ok(prepareService.submitPreview(bindingId, assignmentId, request), "预习已提交");
    }

    @GetMapping("/contracts/published-resources/{bindingId}")
    public R<List<Map<String, Object>>> publishedResources(@PathVariable Long bindingId)
    {
        return R.ok(publishedResourceProvider.publishedResources(bindingId));
    }

    @GetMapping("/contracts/resource-versions/{versionId}")
    public R<Map<String, Object>> resourceVersion(
            @PathVariable Long versionId,
            @RequestParam Long bindingId)
    {
        return R.ok(publishedResourceProvider.resourceVersion(bindingId, versionId));
    }

    @GetMapping("/contracts/preview-tasks/{bindingId}")
    public R<List<Map<String, Object>>> contractPreviewTasks(@PathVariable Long bindingId)
    {
        return R.ok(previewTaskProvider.previewTasksForCurrentUser(bindingId));
    }

    @GetMapping("/contracts/learning-analytics/{bindingId}")
    public R<Map<String, Object>> learningAnalytics(@PathVariable Long bindingId)
    {
        return R.ok(learningAnalyticsProvider.learningSnapshot(bindingId));
    }

    @GetMapping("/contracts/learning-analytics/{bindingId}/progress")
    public R<Map<String, Object>> learningProgress(@PathVariable Long bindingId)
    {
        return R.ok(learningAnalyticsProvider.courseProgress(bindingId));
    }

    @GetMapping("/contracts/learning-analytics/{bindingId}/submissions")
    public R<Map<String, Object>> learningSubmissions(@PathVariable Long bindingId)
    {
        return R.ok(learningAnalyticsProvider.submissionRecords(bindingId));
    }

    /** 教师端课堂完成度：逐环节完成学生 ID，前端按堂课取交集统计全员完成人数。 */
    @GetMapping("/analytics/class-completion")
    public R<Map<String, Object>> classCompletion(@RequestParam Long bindingId)
    {
        return R.ok(learningAnalyticsService.classCompletion(bindingId));
    }

    @GetMapping("/contracts/learning-analytics/{bindingId}/discussion")
    public R<Map<String, Object>> learningDiscussion(@PathVariable Long bindingId)
    {
        return R.ok(learningAnalyticsProvider.discussionParticipation(bindingId));
    }

    @GetMapping("/health")
    public R<Map<String, Object>> health(@RequestParam Long bindingId)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", "group3-resource-prepare");
        result.put("binding", accessService.requireAnyBinding(bindingId));
        return R.ok(result);
    }

    /* === 课程大纲：章/节 === */

    @Log(title = "课程章节", businessType = BusinessType.INSERT)
    @PostMapping("/outline/units")
    public R<Map<String, Object>> createUnit(@RequestBody Map<String, Object> body)
    {
        Long courseId = teacherCourseId(body.get("bindingId"));
        Map<String, Object> input = new LinkedHashMap<>(body);
        if (input.get("sortOrder") == null)
        {
            Long parentId = Group3AccessService.asLong(input.get("parentUnitId"));
            input.put("sortOrder", mapper.selectMaxUnitSort(courseId, parentId) + 1);
        }
        Long id = outline.saveUnit(courseId, null, input);
        return R.ok(Map.of("id", id, "courseSpaceId", courseId), "章节已创建");
    }

    @Log(title = "课程章节", businessType = BusinessType.UPDATE)
    @PatchMapping("/outline/units/{unitId}")
    public R<Map<String, Object>> updateUnit(@PathVariable Long unitId, @RequestBody Map<String, Object> body)
    {
        Long courseId = teacherCourseId(body.get("bindingId"));
        Map<String, Object> input = outlineInput(mapper.selectCourseOutline(courseId), "unitId", unitId, body,
                List.of("title", "parentUnitId", "sortOrder", "contentText"));
        outline.saveUnit(courseId, unitId, input);
        return R.ok(input, "章节已更新");
    }

    @Log(title = "课程章节", businessType = BusinessType.DELETE)
    @DeleteMapping("/outline/units/{unitId}")
    public R<Map<String, Object>> deleteUnit(@PathVariable Long unitId, @RequestParam Long bindingId)
    {
        outline.delete(teacherCourseId(bindingId), unitId, false);
        return R.ok(Map.of("unitId", unitId), "章节已删除");
    }

    @Log(title = "课程章节", businessType = BusinessType.UPDATE)
    @PatchMapping("/outline/units/{unitId}/sort")
    public R<Map<String, Object>> reorderUnit(@PathVariable Long unitId, @RequestBody Map<String, Object> body)
    {
        return updateUnit(unitId, body);
    }

    @Log(title = "课程知识点", businessType = BusinessType.INSERT)
    @PostMapping("/outline/knowledge-points")
    public R<Map<String, Object>> createKnowledgePoint(@RequestBody Map<String, Object> body)
    {
        Long courseId = teacherCourseId(body.get("bindingId"));
        Long id = outline.savePoint(courseId, null, body);
        return R.ok(Map.of("id", id, "courseSpaceId", courseId), "知识点已创建");
    }

    @Log(title = "课程知识点", businessType = BusinessType.UPDATE)
    @PatchMapping("/outline/knowledge-points/{pointId}")
    public R<Map<String, Object>> updateKnowledgePoint(@PathVariable Long pointId, @RequestBody Map<String, Object> body)
    {
        Long courseId = teacherCourseId(body.get("bindingId"));
        Map<String, Object> input = outlineInput(mapper.selectKnowledgePoints(courseId), "knowledgePointId", pointId,
                body, List.of("code", "name", "unitId", "contentText"));
        outline.savePoint(courseId, pointId, input);
        return R.ok(input, "知识点已更新");
    }

    @Log(title = "课程知识点", businessType = BusinessType.DELETE)
    @DeleteMapping("/outline/knowledge-points/{pointId}")
    public R<Map<String, Object>> deleteKnowledgePoint(@PathVariable Long pointId, @RequestParam Long bindingId)
    {
        outline.delete(teacherCourseId(bindingId), pointId, true);
        return R.ok(Map.of("pointId", pointId), "知识点已删除");
    }

    /** 章节详情：内容说明、章节下知识点、资料、题目与学习进度。 */
    @GetMapping("/outline/units/{unitId}/detail")
    public R<Map<String, Object>> outlineUnitDetail(@PathVariable Long unitId, @RequestParam Long bindingId)
    {
        Long courseId = teacherCourseId(bindingId);
        List<Map<String, Object>> units = mapper.selectOutlineWithStats(courseId, bindingId);
        Map<String, Object> unit = units.stream()
                .filter(row -> unitId.equals(Group3AccessService.asLong(row.get("unitId"))))
                .findFirst()
                .orElseThrow(() -> new ServiceException("章节不存在或不属于本课程", HttpStatus.NOT_FOUND.value()));
        Set<Long> scopeIds = new HashSet<>();
        scopeIds.add(unitId);
        for (Map<String, Object> row : units)
        {
            if (unitId.equals(Group3AccessService.asLong(row.get("parentUnitId"))))
            {
                scopeIds.add(Group3AccessService.asLong(row.get("unitId")));
            }
        }
        List<Map<String, Object>> materials = mapper.selectTeacherResources(bindingId, courseId).stream()
                .filter(row -> row.get("unitId") != null
                        && scopeIds.contains(Group3AccessService.asLong(row.get("unitId")))
                        && !"ARCHIVED".equals(String.valueOf(row.get("resourceStatus"))))
                .toList();
        List<Map<String, Object>> points = mapper.selectKnowledgePoints(courseId).stream()
                .filter(row -> scopeIds.contains(Group3AccessService.asLong(row.get("unitId"))))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("unit", unit);
        result.put("points", points);
        result.put("materials", materials);
        result.put("questions", mapper.selectUnitQuestions(courseId, unitId));
        return R.ok(result);
    }

    /** 知识点详情：学习目标说明与关联题目。 */
    @GetMapping("/outline/knowledge-points/{pointId}/detail")
    public R<Map<String, Object>> outlinePointDetail(@PathVariable Long pointId, @RequestParam Long bindingId)
    {
        Long courseId = teacherCourseId(bindingId);
        Map<String, Object> point = mapper.selectKnowledgePoints(courseId).stream()
                .filter(row -> pointId.equals(Group3AccessService.asLong(row.get("knowledgePointId"))))
                .findFirst()
                .orElseThrow(() -> new ServiceException("知识点不存在或不属于本课程", HttpStatus.NOT_FOUND.value()));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("point", point);
        result.put("questions", mapper.selectPointQuestions(courseId, pointId));
        return R.ok(result);
    }

    private Long teacherCourseId(Object bindingId)
    {
        var binding = accessService.requireTeacherBinding(Group3AccessService.asLong(bindingId));
        return Group3AccessService.asLong(binding.get("courseSpaceId"));
    }

    private Map<String, Object> outlineInput(List<Map<String, Object>> rows, String idKey, Long id,
            Map<String, Object> changes, List<String> fields)
    {
        Map<String, Object> input = rows.stream().filter(row -> id.equals(Group3AccessService.asLong(row.get(idKey))))
                .findFirst().map(LinkedHashMap::new)
                .orElseThrow(() -> new ServiceException("对象不存在或不属于本课程"));
        for (String field : fields)
        {
            if (changes.containsKey(field))
            {
                input.put(field, changes.get(field));
            }
        }
        return input;
    }

    /* === 教学班详情 === */

    @GetMapping("/classrooms")
    public R<Map<String, Object>> classroomDetail(@RequestParam Long bindingId)
    {
        Map<String, Object> detail = accessService.requireAnyBinding(bindingId);
        Long teachingClassId = Group3AccessService.asLong(detail.get("teachingClassId"));
        int studentCount = courseAccess.activeMembers(teachingClassId).size();
        List<Map<String, Object>> teachers = new ArrayList<>();
        for (EduTeacherRef t : courseAccess.activeTeachers(teachingClassId))
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", t.id());
            row.put("userId", t.userId());
            row.put("userName", t.userName());
            row.put("nickName", t.nickName());
            row.put("teacherRole", t.teacherRole());
            row.put("canPublish", t.canPublish());
            row.put("canGrade", t.canGrade());
            row.put("canExport", t.canExport());
            row.put("validTo", t.validTo());
            teachers.add(row);
        }
        Map<String, Object> result = new LinkedHashMap<>(detail);
        result.put("studentCount", studentCount);
        result.put("teachers", teachers);
        // 开放范围：表无此字段，暂以 classKind 推断，前端展示用
        String openScope = "正式教学班";
        if ("TRIAL".equalsIgnoreCase(String.valueOf(detail.get("classKind"))))
        {
            openScope = "试听教学班";
        }
        result.put("openScope", openScope);
        return R.ok(result);
    }

    /* === 讨论区 === */

    @GetMapping("/discussions")
    public R<List<Map<String, Object>>> discussions(
            @RequestParam Long bindingId,
            @RequestParam(required = false) String view,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long folderId,
            @RequestParam(required = false) String sort,
            @RequestParam(required = false) String scope)
    {
        return R.ok(discussionService.topics(bindingId, view, keyword, folderId, sort, scope));
    }

    @GetMapping("/discussions/{topicId}")
    public R<Map<String, Object>> discussionDetail(
            @PathVariable Long topicId,
            @RequestParam Long bindingId)
    {
        return R.ok(discussionService.topicDetail(bindingId, topicId));
    }

    @Log(title = "课程讨论", businessType = BusinessType.INSERT)
    @PostMapping("/discussions")
    public R<Map<String, Object>> createDiscussion(
            @RequestBody Group3Requests.DiscussionTopic request)
    {
        Long topicId = discussionService.createTopic(request);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        return R.ok(result, "话题已保存");
    }

    @PostMapping("/discussions/{topicId}/posts")
    public R<Map<String, Object>> createDiscussionPost(
            @PathVariable Long topicId,
            @RequestBody Group3Requests.DiscussionPost request)
    {
        Group3Requests.DiscussionPost body = topicId.equals(request.topicId())
                ? request
                : new Group3Requests.DiscussionPost(request.bindingId(), topicId,
                        request.parentPostId(), request.content(), request.anonymous(),
                        request.attachmentName(), request.attachmentUrl());
        Long postId = discussionService.createPost(body);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("postId", postId);
        return R.ok(result, "回复已发布");
    }

    @Log(title = "课程讨论", businessType = BusinessType.UPDATE)
    @PostMapping("/discussions/action")
    public R<Map<String, Object>> discussionAction(
            @RequestBody Group3Requests.DiscussionAction request)
    {
        return R.ok(discussionService.action(request), "讨论操作已完成");
    }

    @GetMapping("/discussion-folders")
    public R<List<Map<String, Object>>> discussionFolders(@RequestParam Long bindingId)
    {
        return R.ok(discussionService.folders(bindingId));
    }

    @Log(title = "课程讨论", businessType = BusinessType.INSERT)
    @PostMapping("/discussion-folders")
    public R<Map<String, Object>> createDiscussionFolder(
            @RequestBody Group3Requests.DiscussionFolder request)
    {
        return R.ok(discussionService.createFolder(request), "讨论文件夹已创建");
    }

    /* === 时间轴环节更新 === */

    @Log(title = "备课方案阶段", businessType = BusinessType.UPDATE)
    @PostMapping("/stages/update")
    public R<Map<String, Object>> updateStage(@RequestBody Group3Requests.UpdateStage request)
    {
        return R.ok(discussionService.updateStage(request), "环节已更新");
    }

    /* === 删除环节（软归档） === */

    @Log(title = "备课方案阶段", businessType = BusinessType.DELETE)
    @PostMapping("/stages/remove")
    public R<Map<String, Object>> removeStage(@RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        String kind = (String) body.get("kind");
        Number id = (Number) body.get("id");
        if (id == null)
        {
            return R.fail("id 不能为空");
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Long targetId = id.longValue();

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("status", "ARCHIVED");

        switch (kind == null ? "" : kind.toUpperCase())
        {
            case "QUIZ":
                row.put("assignmentId", targetId);
                row.put("bindingId", bindingId);
                mapper.updateAssignmentStatus(row);
                break;
            case "PLAN":
                row.put("planId", targetId);
                row.put("bindingId", bindingId);
                mapper.updateLessonPlanStatus(row);
                break;
            case "MATERIAL":
            case "DRAFT":
                // 课前准备只操作自己的 PREPARE 发布与草稿，不归档课程建设资源。
                resourceService.removePrepareStage(bindingId, targetId, "DRAFT".equals(kind.toUpperCase()));
                break;
            case "DISCUSSION":
                Map<String, Object> archived = discussionService.removeStage(
                        new Group3Requests.RemoveStage(bindingId, "DISCUSSION", targetId));
                archived.put("bindingId", bindingId);
                return R.ok(archived, "环节已归档");
            default:
                return R.fail("kind 必须是 DISCUSSION / QUIZ / PLAN / MATERIAL / DRAFT");
        }

        Map<String, Object> removed = new LinkedHashMap<>();
        removed.put("kind", kind);
        removed.put("id", targetId);
        removed.put("bindingId", bindingId);
        return R.ok(removed, "环节已归档");
    }

    /* === 已删除环节（查看） === */

    @GetMapping("/stages/removed")
    public R<List<Map<String, Object>>> removedStages(@RequestParam Long bindingId)
    {
        return R.ok(prepareService.removedStages(bindingId));
    }

    /* === 恢复已删除环节 === */

    @Log(title = "备课方案阶段", businessType = BusinessType.UPDATE)
    @PostMapping("/stages/restore")
    public R<Map<String, Object>> restoreStage(@RequestBody Map<String, Object> body)
    {
        Object rawBinding = body.get("bindingId");
        Number id = (Number) body.get("id");
        if (rawBinding == null || id == null)
        {
            return R.fail("bindingId 与 id 不能为空");
        }
        Long bindingId = Long.valueOf(rawBinding.toString());
        String kind = String.valueOf(body.get("kind") == null ? "" : body.get("kind")).toUpperCase();
        Long targetId = id.longValue();
        accessService.requireTeacherBinding(bindingId);
        switch (kind)
        {
            case "QUIZ":
                return R.ok(prepareService.restoreQuizStage(bindingId, targetId), "预习任务已恢复");
            case "PLAN":
                return R.ok(prepareService.restorePlanStage(bindingId, targetId), "备课方案已恢复");
            case "MATERIAL":
            case "DRAFT":
                resourceService.restorePrepareStage(bindingId, targetId, "DRAFT".equals(kind));
                break;
            case "DISCUSSION":
                return R.ok(discussionService.restoreArchivedStage(bindingId, targetId), "讨论话题已恢复");
            default:
                return R.fail("kind 必须是 DISCUSSION / QUIZ / PLAN / MATERIAL / DRAFT");
        }
        Map<String, Object> restored = new LinkedHashMap<>();
        restored.put("kind", kind);
        restored.put("id", targetId);
        restored.put("bindingId", bindingId);
        return R.ok(restored, "环节已恢复");
    }

    /* === 资料 / 题库文件夹 === */

    @GetMapping("/folders")
    public R<List<Map<String, Object>>> folders(
            @RequestParam Long bindingId,
            @RequestParam String kind)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        return R.ok(foldersOf(courseSpaceId, kind));
    }

    @Log(title = "课程资料文件夹", businessType = BusinessType.INSERT)
    @PostMapping("/folders")
    public R<Map<String, Object>> createFolder(@RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String kind = String.valueOf(body.getOrDefault("kind", "RESOURCE")).toUpperCase();
        String name = body.get("name") == null ? "" : body.get("name").toString().trim();
        String visibility = body.get("visibility") == null ? "PUBLIC"
                : body.get("visibility").toString().toUpperCase();
        if (name.isBlank())
        {
            return R.fail("文件夹名称不能为空");
        }
        if (name.length() > 60)
        {
            return R.fail("文件夹名称不能超过 60 个字符");
        }
        if (!Set.of("PUBLIC", "PRIVATE", "PARTIAL").contains(visibility))
        {
            return R.fail("可见性无效");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("courseSpaceId", courseSpaceId);
        row.put("name", name);
        row.put("visibility", visibility);
        row.put("userId", Group3Security.currentUserId());
        if ("QUESTION".equals(kind))
        {
            mapper.insertQuestionFolder(row);
        }
        else
        {
            mapper.insertResourceFolder(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("name", name);
        result.put("visibility", visibility);
        return R.ok(result, "文件夹已创建");
    }

    @Log(title = "课程资料文件夹", businessType = BusinessType.UPDATE)
    @PatchMapping("/folders/{folderId}")
    public R<Map<String, Object>> updateFolder(
            @PathVariable Long folderId,
            @RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String kind = String.valueOf(body.getOrDefault("kind", "RESOURCE")).toUpperCase();
        String name = body.get("name") == null ? "" : body.get("name").toString().trim();
        String visibility = body.get("visibility") == null ? "PUBLIC"
                : body.get("visibility").toString().toUpperCase();
        if (name.isBlank())
        {
            return R.fail("文件夹名称不能为空");
        }
        if (!Set.of("PUBLIC", "PRIVATE", "PARTIAL").contains(visibility))
        {
            return R.fail("可见性无效");
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("folderId", folderId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("name", name);
        row.put("visibility", visibility);
        int changed = "QUESTION".equals(kind)
                ? mapper.updateQuestionFolder(row)
                : mapper.updateResourceFolder(row);
        if (changed == 0)
        {
            return R.fail("文件夹不存在或不属于当前课程");
        }
        return R.ok(row, "文件夹已更新");
    }

    /** 题库文件夹归档 / 取消归档。 */
    @Log(title = "课程资料文件夹", businessType = BusinessType.UPDATE)
    @PostMapping("/folders/{folderId}/archive")
    public R<Map<String, Object>> archiveFolder(
            @PathVariable Long folderId,
            @RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String kind = String.valueOf(body.getOrDefault("kind", "QUESTION")).toUpperCase();
        if (!"QUESTION".equals(kind))
        {
            return R.fail("仅题库文件夹支持归档");
        }
        boolean archived = body.get("archived") == null
                || Boolean.parseBoolean(body.get("archived").toString());
        int changed = mapper.archiveQuestionFolder(courseSpaceId, folderId, archived);
        if (changed == 0)
        {
            return R.fail("文件夹不存在或不属于当前课程");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("folderId", folderId);
        result.put("archived", archived);
        return R.ok(result, archived ? "文件夹已归档" : "文件夹已取消归档");
    }

    @Log(title = "课程资料文件夹", businessType = BusinessType.DELETE)
    @DeleteMapping("/folders/{folderId}")
    public R<Map<String, Object>> deleteFolder(
            @PathVariable Long folderId,
            @RequestParam Long bindingId,
            @RequestParam String kind)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        int changed = "QUESTION".equalsIgnoreCase(kind)
                ? mapper.deleteQuestionFolder(courseSpaceId, folderId)
                : mapper.deleteResourceFolder(courseSpaceId, folderId);
        if (changed == 0)
        {
            return R.fail("文件夹不存在或不属于当前课程");
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("folderId", folderId);
        return R.ok(result, "文件夹已删除");
    }

    /** 文件夹整体上架：发布文件夹内尚未发布的资料。 */
    @Log(title = "课程资料文件夹", businessType = BusinessType.UPDATE)
    @PostMapping("/folders/{folderId}/release")
    public R<Map<String, Object>> publishFolder(@PathVariable Long folderId, @RequestParam Long bindingId)
    {
        return R.ok(resourceService.publishFolder(bindingId, folderId), "文件夹已上架");
    }

    /** 文件夹整体下架：撤回文件夹内已发布的资料。 */
    @Log(title = "课程资料文件夹", businessType = BusinessType.UPDATE)
    @PostMapping("/folders/{folderId}/revoke")
    public R<Map<String, Object>> revokeFolder(@PathVariable Long folderId, @RequestParam Long bindingId)
    {
        return R.ok(resourceService.revokeFolder(bindingId, folderId), "文件夹已下架");
    }

    /** 打包下载文件夹内的文件资料。 */
    @GetMapping("/folders/{folderId}/download")
    public ResponseEntity<Resource> downloadFolder(@PathVariable Long folderId, @RequestParam Long bindingId)
    {
        byte[] content = resourceService.zipFolder(bindingId, folderId);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.parseMediaType("application/zip"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename("文件夹资料.zip", StandardCharsets.UTF_8)
                                .build().toString())
                .body(new ByteArrayResource(content));
    }

    @Log(title = "课程资料文件夹", businessType = BusinessType.UPDATE)
    @PostMapping("/folders/move")
    public R<Map<String, Object>> moveToFolder(@RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String kind = String.valueOf(body.getOrDefault("kind", "RESOURCE")).toUpperCase();
        Long folderId = body.get("folderId") == null ? null : Long.valueOf(body.get("folderId").toString());
        List<Long> ids = new ArrayList<>();
        if (body.get("ids") instanceof List<?> list)
        {
            for (Object item : list)
            {
                if (item != null && !item.toString().isBlank())
                {
                    ids.add(Long.valueOf(item.toString()));
                }
            }
        }
        if (ids.isEmpty())
        {
            return R.fail("请选择要移动的项");
        }
        if (folderId != null)
        {
            boolean exists = foldersOf(courseSpaceId, kind).stream()
                    .anyMatch(folder -> folderId.equals(Group3AccessService.asLong(folder.get("id"))));
            if (!exists)
            {
                return R.fail("目标文件夹不存在");
            }
        }
        int moved = "QUESTION".equals(kind)
                ? mapper.moveQuestionsToFolder(courseSpaceId, folderId, ids)
                : mapper.moveResourcesToFolder(courseSpaceId, folderId, ids);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("moved", moved);
        result.put("folderId", folderId);
        return R.ok(result, "已移动 " + moved + " 项");
    }

    private List<Map<String, Object>> foldersOf(Long courseSpaceId, String kind)
    {
        return "QUESTION".equalsIgnoreCase(kind)
                ? mapper.selectQuestionFolders(courseSpaceId)
                : mapper.selectResourceFolders(courseSpaceId);
    }

    /* ========== 环节思政融入点 ========== */

    @GetMapping("/ideology/stage-points")
    public R<List<Map<String, Object>>> stageIdeologyPoints(
            @RequestParam Long bindingId,
            @RequestParam(required = false) String stageKind,
            @RequestParam(required = false) Long stageId)
    {
        if (stageKind == null || stageKind.isBlank() || stageId == null)
        {
            return R.ok(stageIdeologyService.allPoints(bindingId));
        }
        return R.ok(stageIdeologyService.points(bindingId, stageKind, stageId));
    }

    @Log(title = "环节思政融入点", businessType = BusinessType.UPDATE)
    @PutMapping("/ideology/stage-points")
    public R<List<Map<String, Object>>> saveStageIdeologyPoints(@RequestBody Map<String, Object> request)
    {
        Long bindingId = request.get("bindingId") == null ? null
                : Long.valueOf(String.valueOf(request.get("bindingId")));
        Long stageId = request.get("stageId") == null ? null
                : Long.valueOf(String.valueOf(request.get("stageId")));
        String stageKind = request.get("stageKind") == null ? null : String.valueOf(request.get("stageKind"));
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = request.get("points") instanceof List
                ? (List<Map<String, Object>>) request.get("points") : List.of();
        return R.ok(stageIdeologyService.replace(bindingId, stageKind, stageId, points), "思政融入点已保存");
    }

    /* ========== 公共资源库 ========== */

    @GetMapping("/public/categories")
    public R<List<Map<String, Object>>> publicCategories(
            @RequestParam(required = false, defaultValue = "RESOURCE") String kind)
    {
        return R.ok(publicLibraryService.categories(kind));
    }

    @GetMapping("/public/resources")
    public R<Map<String, Object>> publicResources(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String resourceKind,
            @RequestParam(required = false) String categoryKind,
            @RequestParam(required = false) String excludeCategoryKind,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        return R.ok(publicLibraryService.listResources(keyword, categoryId, resourceKind, categoryKind,
                excludeCategoryKind, pageNum, pageSize));
    }

    @GetMapping("/public/resources/{id}")
    public R<Map<String, Object>> publicResourceDetail(@PathVariable Long id)
    {
        return R.ok(publicLibraryService.detail(id));
    }

    @Log(title = "公共资源库", businessType = BusinessType.INSERT)
    @PostMapping("/public/resources/upload")
    public R<Map<String, Object>> uploadPublicResource(
            @RequestParam String title,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) String description,
            @RequestParam(required = false) String tags,
            @RequestParam(required = false) String difficulty,
            @RequestParam("file") MultipartFile file)
    {
        return R.ok(publicLibraryService.uploadFile(title, categoryId, description, tags, difficulty, file),
                "资源上传成功");
    }

    @Log(title = "公共资源库", businessType = BusinessType.INSERT)
    @PostMapping("/public/resources/link")
    public R<Map<String, Object>> uploadPublicLink(@RequestBody Map<String, Object> request)
    {
        String title = (String) request.get("title");
        Long categoryId = request.get("categoryId") != null
                ? Long.valueOf(String.valueOf(request.get("categoryId"))) : null;
        String description = (String) request.get("description");
        String tags = (String) request.get("tags");
        String difficulty = (String) request.get("difficulty");
        String externalUri = (String) request.get("externalUri");
        String linkKind = (String) request.get("linkKind");
        return R.ok(publicLibraryService.uploadLink(title, categoryId, description, tags, difficulty,
                externalUri, linkKind), "资源已发布");
    }

    @GetMapping("/public/versions/{versionId}/pages/{pageNo}")
    public ResponseEntity<Resource> publicPage(
            @PathVariable Long versionId,
            @PathVariable int pageNo) throws Exception
    {
        java.nio.file.Path path = publicLibraryService.pagePath(versionId, pageNo);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.IMAGE_PNG)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline().filename("page-" + pageNo + ".png",
                                java.nio.charset.StandardCharsets.UTF_8).build().toString())
                .body(new FileSystemResource(path));
    }

    @GetMapping("/public/versions/{versionId}/pages")
    public R<List<Map<String, Object>>> publicPages(@PathVariable Long versionId)
    {
        return R.ok(publicLibraryService.pages(versionId));
    }

    @GetMapping("/public/versions/{versionId}/download")
    public ResponseEntity<Resource> publicDownload(@PathVariable Long versionId)
    {
        Resource resource = publicLibraryService.loadVersionFile(versionId);
        String fileName = publicLibraryService.fileName(versionId);
        String contentType = publicLibraryService.contentType(versionId);
        return ResponseEntity.ok()
                .cacheControl(org.springframework.http.CacheControl.noStore())
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(fileName, java.nio.charset.StandardCharsets.UTF_8)
                                .build().toString())
                .body(resource);
    }

    @PostMapping("/public/resources/{id}/favorite")
    public R<Map<String, Object>> togglePublicFavorite(@PathVariable Long id)
    {
        Map<String, Object> result = publicLibraryService.toggleFavorite(id);
        return R.ok(result, (String) result.get("message"));
    }

    @GetMapping("/public/favorites")
    public R<List<Map<String, Object>>> myPublicFavorites()
    {
        return R.ok(publicLibraryService.myFavorites());
    }

    @GetMapping("/public/my-resources")
    public R<Map<String, Object>> myPublicResources(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize)
    {
        return R.ok(publicLibraryService.myUploads(pageNum, pageSize));
    }

    @Log(title = "公共资源库", businessType = BusinessType.IMPORT)
    @PostMapping("/public/resources/{id}/import")
    public R<Map<String, Object>> importPublicResource(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body)
    {
        Long bindingId = body.get("bindingId") != null
                ? Long.valueOf(String.valueOf(body.get("bindingId"))) : null;
        Long folderId = body.get("folderId") != null
                ? Long.valueOf(String.valueOf(body.get("folderId"))) : null;
        Long unitId = body.get("unitId") != null
                ? Long.valueOf(String.valueOf(body.get("unitId"))) : null;
        Map<String, Object> result = publicLibraryService.importToCourse(id, bindingId, folderId, unitId);
        return R.ok(result, "已添加到课程资料");
    }
}
