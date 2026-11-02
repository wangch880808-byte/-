package com.ruoyi.education.group3.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.education.group3.contract.PreviewTaskProvider;
import com.ruoyi.education.group3.contract.PublishedResourceProvider;
import com.ruoyi.education.group3.service.Group3AccessService;
import com.ruoyi.education.group3.service.Group3PrepareService;
import com.ruoyi.education.group3.service.Group3ResourceService;
import com.ruoyi.education.group3.support.Group3Requests;

@RestController
@RequestMapping("/education/group3")
public class Group3Controller
{
    private final Group3AccessService accessService;
    private final Group3ResourceService resourceService;
    private final Group3PrepareService prepareService;
    private final PublishedResourceProvider publishedResourceProvider;
    private final PreviewTaskProvider previewTaskProvider;

    public Group3Controller(
            Group3AccessService accessService,
            Group3ResourceService resourceService,
            Group3PrepareService prepareService,
            PublishedResourceProvider publishedResourceProvider,
            PreviewTaskProvider previewTaskProvider)
    {
        this.accessService = accessService;
        this.resourceService = resourceService;
        this.prepareService = prepareService;
        this.publishedResourceProvider = publishedResourceProvider;
        this.previewTaskProvider = previewTaskProvider;
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

    @PostMapping("/resources")
    public R<Map<String, Object>> upload(
            @RequestParam Long bindingId,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) Long unitId,
            @RequestParam("file") MultipartFile file)
    {
        Map<String, Object> result = resourceService.upload(bindingId, title, unitId, file);
        return R.ok(result, "READY".equals(result.get("processingStatus"))
                ? "资料已上传并完成处理" : "资料已上传，但转换失败");
    }

    @PostMapping("/resources/{resourceId}/versions")
    public R<Map<String, Object>> uploadVersion(
            @PathVariable Long resourceId,
            @RequestParam Long bindingId,
            @RequestParam("file") MultipartFile file)
    {
        return R.ok(resourceService.uploadVersion(resourceId, bindingId, file), "新版本已保存");
    }

    @GetMapping("/resources/{resourceId}/versions")
    public R<List<Map<String, Object>>> versions(
            @PathVariable Long resourceId,
            @RequestParam Long bindingId)
    {
        return R.ok(resourceService.versions(resourceId, bindingId));
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

    @GetMapping("/versions/{versionId}/download")
    public ResponseEntity<Resource> download(
            @PathVariable Long versionId,
            @RequestParam Long bindingId)
    {
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

    @PostMapping("/releases")
    public R<Map<String, Object>> release(@RequestBody Group3Requests.ReleaseResource request)
    {
        return R.ok(resourceService.release(request), "发布范围已更新");
    }

    @PostMapping("/releases/{releaseId}/revoke")
    public R<Map<String, Object>> revoke(
            @PathVariable Long releaseId,
            @RequestParam Long bindingId,
            @RequestBody(required = false) Map<String, String> body)
    {
        return R.ok(resourceService.revoke(releaseId, bindingId,
                body == null ? null : body.get("reason")), "资料已撤回");
    }

    @GetMapping("/questions")
    public R<List<Map<String, Object>>> questions(@RequestParam Long bindingId)
    {
        return R.ok(prepareService.questions(bindingId));
    }

    @PostMapping("/questions")
    public R<Map<String, Object>> createQuestion(@RequestBody Group3Requests.Question request)
    {
        return R.ok(prepareService.createQuestion(request), "题目已创建");
    }

    @PostMapping("/questions/{questionId}/versions")
    public R<Map<String, Object>> createQuestionVersion(
            @PathVariable Long questionId,
            @RequestBody Group3Requests.QuestionVersion request)
    {
        return R.ok(prepareService.createQuestionVersion(questionId, request), "题目新版本已创建");
    }

    @PatchMapping("/questions/{questionId}/status")
    public R<Map<String, Object>> updateQuestionStatus(
            @PathVariable Long questionId,
            @RequestParam Long bindingId,
            @RequestBody Group3Requests.Status request)
    {
        return R.ok(prepareService.updateQuestionStatus(bindingId, questionId, request), "题目状态已更新");
    }

    @GetMapping("/plans")
    public R<List<Map<String, Object>>> plans(@RequestParam Long bindingId)
    {
        return R.ok(prepareService.plans(bindingId));
    }

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

    @PostMapping("/preview-tasks")
    public R<Map<String, Object>> publishPreview(@RequestBody Group3Requests.PreviewTask request)
    {
        return R.ok(prepareService.publishPreview(request), "预习任务已发布");
    }

    @GetMapping("/student/materials")
    public R<List<Map<String, Object>>> studentMaterials(@RequestParam Long bindingId)
    {
        return R.ok(resourceService.studentMaterials(bindingId));
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

    @GetMapping("/health")
    public R<Map<String, Object>> health(@RequestParam Long bindingId)
    {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("module", "group3-resource-prepare");
        result.put("binding", accessService.requireAnyBinding(bindingId));
        return R.ok(result);
    }
}
