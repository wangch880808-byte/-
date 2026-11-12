package com.ruoyi.education.group3.controller;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
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
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.service.Group3AccessService;
import com.ruoyi.education.group3.service.Group3PrepareService;
import com.ruoyi.education.group3.service.Group3ResourceService;
import com.ruoyi.education.group3.service.Group3TransferService;
import com.ruoyi.education.group3.support.Group3Requests;
import com.ruoyi.education.group3.support.Group3Security;

@RestController
@RequestMapping("/education/group3")
public class Group3Controller
{
    private final Group3AccessService accessService;
    private final Group3ResourceService resourceService;
    private final Group3PrepareService prepareService;
    private final Group3TransferService transferService;
    private final Group3Mapper mapper;
    private final PublishedResourceProvider publishedResourceProvider;
    private final PreviewTaskProvider previewTaskProvider;

    public Group3Controller(
            Group3AccessService accessService,
            Group3ResourceService resourceService,
            Group3PrepareService prepareService,
            Group3TransferService transferService,
            Group3Mapper mapper,
            PublishedResourceProvider publishedResourceProvider,
            PreviewTaskProvider previewTaskProvider)
    {
        this.accessService = accessService;
        this.resourceService = resourceService;
        this.prepareService = prepareService;
        this.transferService = transferService;
        this.mapper = mapper;
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

    @PostMapping("/resources/import")
    public R<Map<String, Object>> importResources(
            @RequestParam Long bindingId,
            @RequestParam("file") MultipartFile file)
    {
        Map<String, Object> result = transferService.importResources(bindingId, file);
        return R.ok(result, "导入完成：成功 " + result.get("imported") + " 个");
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

    @PatchMapping("/resources/{resourceId}")
    public R<Map<String, Object>> updateResource(
            @PathVariable Long resourceId,
            @RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceId", resourceId);
        row.put("courseSpaceId", courseSpaceId);
        if (body.get("title") != null) row.put("title", body.get("title").toString().trim());
        mapper.updateResource(row);
        return R.ok(row, "资料已更新");
    }

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

    /* === 课程大纲：章/节 === */

    @PostMapping("/outline/units")
    public R<Map<String, Object>> createUnit(@RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String unitKind = (String) body.get("unitKind");
        String title = (String) body.get("title");
        if (title == null || title.isBlank())
        {
            return R.fail("标题不能为空");
        }
        if (!"CHAPTER".equals(unitKind) && !"SECTION".equals(unitKind))
        {
            return R.fail("unitKind 必须是 CHAPTER 或 SECTION");
        }
        Long parentUnitId = body.get("parentUnitId") == null ? null
                : Long.valueOf(body.get("parentUnitId").toString());
        Integer maxSort = mapper.selectMaxUnitSort(courseSpaceId, parentUnitId);
        Integer sortOrder = Integer.valueOf(body.get("sortOrder") == null
                ? Integer.toString(maxSort + 1)
                : body.get("sortOrder").toString());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("courseSpaceId", courseSpaceId);
        row.put("parentUnitId", parentUnitId);
        row.put("unitKind", unitKind);
        row.put("title", title.trim());
        row.put("sortOrder", sortOrder);
        mapper.insertUnit(row);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("courseSpaceId", courseSpaceId);
        return R.ok(result, unitKind.equals("CHAPTER") ? "章节已创建" : "小节已创建");
    }

    @PatchMapping("/outline/units/{unitId}")
    public R<Map<String, Object>> updateUnit(
            @PathVariable Long unitId,
            @RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("unitId", unitId);
        row.put("courseSpaceId", courseSpaceId);
        if (body.get("title") != null) row.put("title", body.get("title").toString().trim());
        if (body.containsKey("parentUnitId"))
        {
            Object p = body.get("parentUnitId");
            row.put("parentUnitId", p == null ? null : Long.valueOf(p.toString()));
        }
        if (body.get("sortOrder") != null) row.put("sortOrder",
                Integer.valueOf(body.get("sortOrder").toString()));
        mapper.updateUnit(row);
        return R.ok(row, "章节已更新");
    }

    @DeleteMapping("/outline/units/{unitId}")
    public R<Map<String, Object>> deleteUnit(
            @PathVariable Long unitId,
            @RequestParam Long bindingId)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        // 先删子知识点，再删单元
        mapper.deleteKnowledgePointsByUnit(unitId, courseSpaceId);
        // 如果是章，把其子节点（小节）也一并删除
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("parentUnitId", unitId);
        row.put("courseSpaceId", courseSpaceId);
        List<Map<String, Object>> children = mapper.selectCourseOutline(courseSpaceId);
        // selectCourseOutline 返回所有单元，这里靠后续 deleteUnit 级联
        for (Map<String, Object> child : children)
        {
            if (String.valueOf(child.get("parentUnitId")).equals(String.valueOf(unitId)))
            {
                mapper.deleteKnowledgePointsByUnit(Group3AccessService.asLong(child.get("unitId")), courseSpaceId);
                mapper.deleteUnit(Group3AccessService.asLong(child.get("unitId")), courseSpaceId);
            }
        }
        mapper.deleteUnit(unitId, courseSpaceId);
        Map<String, Object> deleted = new LinkedHashMap<>();
        deleted.put("unitId", unitId);
        return R.ok(deleted, "章节及子项已删除");
    }

    @PatchMapping("/outline/units/{unitId}/sort")
    public R<Map<String, Object>> reorderUnit(
            @PathVariable Long unitId,
            @RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Integer sortOrder = Integer.valueOf(body.get("sortOrder").toString());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("unitId", unitId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("sortOrder", sortOrder);
        mapper.updateUnitSort(row);
        return R.ok(row, "排序已更新");
    }

    /* === 课程大纲：知识点 === */

    @PostMapping("/outline/knowledge-points")
    public R<Map<String, Object>> createKnowledgePoint(@RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String code = (String) body.get("code");
        String name = (String) body.get("name");
        if (code == null || code.isBlank())
        {
            return R.fail("知识点编码不能为空");
        }
        if (name == null || name.isBlank())
        {
            return R.fail("知识点名称不能为空");
        }
        Long unitId = body.get("unitId") == null ? null : Long.valueOf(body.get("unitId").toString());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("courseSpaceId", courseSpaceId);
        row.put("unitId", unitId);
        row.put("code", code.trim());
        row.put("name", name.trim());
        mapper.insertKnowledgePoint(row);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", row.get("id"));
        result.put("courseSpaceId", courseSpaceId);
        return R.ok(result, "知识点已创建");
    }

    @PatchMapping("/outline/knowledge-points/{pointId}")
    public R<Map<String, Object>> updateKnowledgePoint(
            @PathVariable Long pointId,
            @RequestBody Map<String, Object> body)
    {
        Long bindingId = Long.valueOf(body.get("bindingId").toString());
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("pointId", pointId);
        row.put("courseSpaceId", courseSpaceId);
        if (body.get("code") != null) row.put("code", body.get("code").toString().trim());
        if (body.get("name") != null) row.put("name", body.get("name").toString().trim());
        if (body.containsKey("unitId"))
        {
            Object u = body.get("unitId");
            row.put("unitId", u == null ? null : Long.valueOf(u.toString()));
        }
        mapper.updateKnowledgePoint(row);
        return R.ok(row, "知识点已更新");
    }

    @DeleteMapping("/outline/knowledge-points/{pointId}")
    public R<Map<String, Object>> deleteKnowledgePoint(
            @PathVariable Long pointId,
            @RequestParam Long bindingId)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        mapper.deleteKnowledgePoint(pointId, courseSpaceId);
        Map<String, Object> removed = new LinkedHashMap<>();
        removed.put("pointId", pointId);
        return R.ok(removed, "知识点已删除");
    }

    /* === 教学班详情 === */

    @GetMapping("/classrooms")
    public R<Map<String, Object>> classroomDetail(@RequestParam Long bindingId)
    {
        Long userId = Group3Security.currentUserId();
        Map<String, Object> detail = mapper.selectClassroomDetail(bindingId, userId);
        if (detail == null || detail.isEmpty())
        {
            return R.fail(404, "教学班不存在或无权限");
        }
        Long teachingClassId = Group3AccessService.asLong(detail.get("teachingClassId"));
        int studentCount = mapper.selectActiveStudentCount(teachingClassId);
        List<Map<String, Object>> teachers = mapper.selectClassTeachers(teachingClassId);
        Map<String, Object> result = new LinkedHashMap<>(detail);
        result.put("studentCount", studentCount);
        result.put("teachers", teachers);
        // 开放范围：表无此字段，暂以 classKind + term 推断，前端展示用
        String openScope = "正式教学班";
        if ("TRIAL".equalsIgnoreCase(String.valueOf(detail.get("classKind"))))
        {
            openScope = "试听教学班";
        }
        result.put("openScope", openScope);
        return R.ok(result);
    }

    /* === 删除环节（软归档） === */

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
                row.put("resourceId", targetId);
                row.put("courseSpaceId", courseSpaceId);
                mapper.updateResourceStatus(row);
                break;
            default:
                return R.fail("kind 必须是 QUIZ / PLAN / MATERIAL / DRAFT");
        }

        Map<String, Object> removed = new LinkedHashMap<>();
        removed.put("kind", kind);
        removed.put("id", targetId);
        removed.put("bindingId", bindingId);
        return R.ok(removed, "环节已归档");
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
}
