package com.ruoyi.education.group3.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Executor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group3.contract.PublishedResourceProvider;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Constants;
import com.ruoyi.education.group3.support.Group3Requests;
import com.ruoyi.education.group3.support.Group3Security;
import com.ruoyi.education.group3.support.Group3Time;

@Service
public class Group3ResourceService implements PublishedResourceProvider
{
    private final Group3Mapper mapper;
    private final Group3AccessService accessService;
    private final Group3FileStorageService storage;
    private final Executor conversionExecutor;

    public Group3ResourceService(
            Group3Mapper mapper,
            Group3AccessService accessService,
            Group3FileStorageService storage,
            Executor group3ConversionExecutor)
    {
        this.mapper = mapper;
        this.accessService = accessService;
        this.storage = storage;
        this.conversionExecutor = group3ConversionExecutor;
    }

    public Map<String, Object> overview(Long bindingId)
    {
        Map<String, Object> binding = accessService.requireAnyBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("binding", binding);
        result.put("outline", mapper.selectCourseOutline(courseSpaceId));
        result.put("knowledgePoints", mapper.selectKnowledgePoints(courseSpaceId));
        if (Group3Security.isTeacher())
        {
            result.put("resources", decorateResources(mapper.selectTeacherResources(bindingId, courseSpaceId)));
            result.put("materialVersions", mapper.selectReadyResourceVersions(
                    bindingId, courseSpaceId, Group3Security.currentUserId()));
            result.put("questions", mapper.selectQuestions(bindingId, courseSpaceId));
            result.put("questionVersions", mapper.selectQuestionVersions(
                    bindingId, courseSpaceId, Group3Security.currentUserId()));
            result.put("lessons", mapper.selectLessons(bindingId));
        }
        else
        {
            result.put("resources", decorateResources(mapper.selectStudentMaterials(bindingId,
                    Group3Security.currentUserId(), null)));
        }
        return Group3Time.withServerTime(result);
    }

    @Transactional
    public Map<String, Object> upload(
            Long bindingId,
            String title,
            Long unitId,
            MultipartFile file,
            String releaseSource,
            String origin)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String cleanTitle = cleanTitle(title, file == null ? null : file.getOriginalFilename());
        validateUnit(courseSpaceId, unitId);

        Group3FileStorageService.StoredUpload stored = storage.store(file, courseSpaceId);
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("courseSpaceId", courseSpaceId);
        resource.put("folderId", null);
        resource.put("unitId", unitId);
        resource.put("title", cleanTitle);
        resource.put("resourceKind", "FILE");
        resource.put("origin", normalizeOrigin(origin));
        resource.put("userId", Group3Security.currentUserId());
        mapper.insertResource(resource);
        Long resourceId = Group3AccessService.asLong(resource.get("id"));

        Map<String, Object> version = createFileVersion(resourceId, courseSpaceId, bindingId, stored);
        publishToClass(bindingId, courseSpaceId, resourceId, version, releaseSource);
        return version;
    }

    /** 压缩包导入：按字节写入文件并创建资料与首个版本。 */
    @Transactional
    public Map<String, Object> importFile(
            Long bindingId, String title, String originalName, byte[] content)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Group3FileStorageService.StoredUpload stored =
                storage.store(content, originalName, courseSpaceId);
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("courseSpaceId", courseSpaceId);
        resource.put("folderId", null);
        resource.put("unitId", null);
        resource.put("title", cleanTitle(title, originalName));
        resource.put("resourceKind", "FILE");
        resource.put("origin", Group3Constants.RELEASE_STUDIO);
        resource.put("userId", Group3Security.currentUserId());
        mapper.insertResource(resource);
        Long resourceId = Group3AccessService.asLong(resource.get("id"));
        Map<String, Object> version = createFileVersionWithNo(resourceId, courseSpaceId, bindingId, stored, 1);
        publishToClass(bindingId, courseSpaceId, resourceId, version, Group3Constants.RELEASE_STUDIO);
        return version;
    }

    @Transactional
    public Map<String, Object> uploadVersion(
            Long resourceId, Long bindingId, MultipartFile file, String releaseSource, String origin)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Map<String, Object> existing = requireResourceInCourse(courseSpaceId, resourceId, bindingId);
        // 课前准备只能给自己上传的资料换版本，不能改写课程建设资料。
        if (Group3Constants.RELEASE_PREPARE.equalsIgnoreCase(String.valueOf(origin))
                && !Group3Constants.RELEASE_PREPARE.equals(existing.get("origin")))
        {
            throw new ServiceException("课程建设资料请在课程建设中上传新版本", HttpStatus.FORBIDDEN);
        }
        if (!"FILE".equals(existing.get("resourceKind")))
        {
            throw new ServiceException("外链资料不能上传文件版本", HttpStatus.BAD_REQUEST);
        }
        mapper.lockResource(resourceId);
        Group3FileStorageService.StoredUpload stored = storage.store(file, courseSpaceId);
        int versionNo = mapper.selectNextResourceVersion(resourceId);
        Map<String, Object> version = createFileVersionWithNo(resourceId, courseSpaceId, bindingId, stored, versionNo);
        publishToClass(bindingId, courseSpaceId, resourceId, version, releaseSource);
        return version;
    }

    /**
     * 按调用方显式指定的发布来源决定是否对学生可见；
     * NONE 或空表示只保存资料，由课前准备等场景自行设置时间窗发布。
     */
    private void publishToClass(
            Long bindingId, Long courseSpaceId, Long resourceId, Map<String, Object> version, String releaseSource)
    {
        if (releaseSource == null || releaseSource.isBlank()
                || Group3Constants.RELEASE_NONE.equalsIgnoreCase(releaseSource.trim()))
        {
            return;
        }
        Long versionId = Group3AccessService.asLong(version.get("versionId"));
        if (versionId == null)
        {
            return;
        }
        mapper.lockBinding(bindingId);
        releaseVersion(bindingId, courseSpaceId, resourceId, versionId,
                releaseSource, Group3Time.nowUtc(), null);
    }

    public List<Map<String, Object>> versions(Long resourceId, Long bindingId)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        requireResourceInCourse(courseSpaceId, resourceId, bindingId);
        List<Map<String, Object>> versions = mapper.selectResourceVersions(resourceId);
        versions.forEach(this::decorateVersion);
        return versions;
    }

    public Map<String, Object> versionDetail(Long versionId, Long bindingId)
    {
        Map<String, Object> version = accessService.visibleVersion(bindingId, versionId);
        decorateVersion(version);
        List<Map<String, Object>> pages = mapper.selectResourcePages(versionId);
        for (Map<String, Object> page : pages)
        {
            page.remove("previewStorageKey");
            page.put("url", "/education/group3/versions/" + versionId + "/pages/"
                    + page.get("pageNo") + "?bindingId=" + bindingId);
        }
        version.put("pages", pages);
        return version;
    }

    public Resource loadVersionFile(Long versionId, Long bindingId)
    {
        Map<String, Object> version = accessService.visibleVersion(bindingId, versionId);
        String storageKey = (String) version.get("storageKey");
        if (storageKey == null || storageKey.isBlank())
        {
            throw new ServiceException("外链资料没有本地文件", HttpStatus.BAD_REQUEST);
        }
        return storage.load(storageKey);
    }

    public Path pagePath(Long versionId, Long bindingId, int pageNo)
    {
        accessService.visibleVersion(bindingId, versionId);
        return mapper.selectResourcePages(versionId).stream()
                .filter(page -> Integer.parseInt(String.valueOf(page.get("pageNo"))) == pageNo)
                .map(page -> storage.path((String) page.get("previewStorageKey")))
                .findFirst()
                .orElseThrow(() -> new ServiceException("预览页不存在", HttpStatus.NOT_FOUND));
    }

    public String fileName(Long versionId, Long bindingId)
    {
        Map<String, Object> version = accessService.visibleVersion(bindingId, versionId);
        String key = (String) version.get("storageKey");
        return key == null ? String.valueOf(version.get("title")) : storage.fileName(key);
    }

    public String contentType(Long versionId, Long bindingId)
    {
        Map<String, Object> version = accessService.visibleVersion(bindingId, versionId);
        String key = (String) version.get("storageKey");
        return key == null ? "text/uri-list" : storage.contentType(key);
    }

    /** 教师更新资料增强设置：下载开关、预览水印、视频任务点。 */
    @Transactional
    public Map<String, Object> updateEnhance(Long resourceId, Group3Requests.ResourceEnhance request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        requireResourceInCourse(courseSpaceId, resourceId, request.bindingId());

        String watermark = request.watermarkText() == null ? null : request.watermarkText().trim();
        if (watermark != null && watermark.length() > 200)
        {
            throw new ServiceException("水印文字不能超过 200 字", HttpStatus.BAD_REQUEST);
        }
        Integer requiredSeconds = request.taskRequiredSeconds();
        if (requiredSeconds != null && requiredSeconds < 0)
        {
            throw new ServiceException("任务观看秒数不能为负", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceId", resourceId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("downloadEnabled", request.downloadEnabled() == null ? null : (request.downloadEnabled() ? 1 : 0));
        row.put("watermarkText", request.watermarkText() == null ? null : watermark);
        row.put("taskEnabled", request.taskEnabled() == null ? null : (request.taskEnabled() ? 1 : 0));
        row.put("taskRequiredSeconds", requiredSeconds);
        mapper.updateResourceEnhance(row);
        return row;
    }

    /** 学生查询自己在本教学班的观看进度汇总。 */
    public Map<String, Object> studentProgress(Long bindingId)
    {
        accessService.requireAnyBinding(bindingId);
        Long userId = Group3Security.currentUserId();
        List<Map<String, Object>> rows = mapper.selectResourceProgress(bindingId, userId);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("progress", rows);
        return body;
    }

    /** 学生上报视频观看进度；服务端取较大值防回退，达到任务阈值即标记完成。 */
    @Transactional
    public Map<String, Object> reportWatch(Long resourceId, Long bindingId, Group3Requests.WatchProgress request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireAnyBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Long userId = Group3Security.currentUserId();

        Map<String, Object> info = mapper.selectResourceTaskInfo(resourceId, bindingId);
        if (info == null || !courseSpaceId.equals(Group3AccessService.asLong(info.get("courseSpaceId"))))
        {
            throw new ServiceException("资料不存在或无权访问", HttpStatus.NOT_FOUND);
        }
        Integer watched = request.watchedSeconds() == null ? 0 : request.watchedSeconds();
        if (watched < 0)
        {
            throw new ServiceException("观看秒数无效", HttpStatus.BAD_REQUEST);
        }
        boolean enabled = info.get("taskEnabled") != null && ((Number) info.get("taskEnabled")).intValue() == 1;
        long required = 0;
        if (enabled)
        {
            if (info.get("taskRequiredSeconds") != null && ((Number) info.get("taskRequiredSeconds")).longValue() > 0)
            {
                required = ((Number) info.get("taskRequiredSeconds")).longValue();
            }
            else if (info.get("byteSize") != null)
            {
                // 未设置绝对秒数时，按视频文件大小粗估时长不可靠，默认要求观看 30 秒
                required = 30L;
            }
        }
        boolean completed = enabled && watched >= required;

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceId", resourceId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("bindingId", bindingId);
        row.put("userId", userId);
        row.put("watchedSeconds", watched);
        row.put("completed", completed ? 1 : 0);
        row.put("completedAt", completed ? Group3Time.nowUtc() : null);
        mapper.upsertResourceProgress(row);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("watchedSeconds", watched);
        result.put("requiredSeconds", required);
        result.put("taskEnabled", enabled);
        result.put("completed", completed);
        return result;
    }

    /** 教师下载原文件始终允许；学生端下载受 download_enabled 控制。 */
    public void requireDownloadAllowed(Long versionId, Long bindingId)
    {
        Map<String, Object> version = accessService.visibleVersion(bindingId, versionId);
        Object flag = version.get("downloadEnabled");
        if (Group3Security.isTeacher())
        {
            return;
        }
        if (flag == null || ((Number) flag).intValue() != 1)
        {
            throw new ServiceException("该资料不允许下载", HttpStatus.FORBIDDEN);
        }
    }

    @Transactional
    public Map<String, Object> release(Group3Requests.ReleaseResource request)
    {
        if (request == null || request.bindingId() == null || request.resourceVersionId() == null)
        {
            throw new ServiceException("教学班和资源版本不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        mapper.lockBinding(request.bindingId());
        Map<String, Object> version = accessService.teacherVersion(request.bindingId(), request.resourceVersionId());
        // 允许在转换完成前发布：学生端查询只返回 READY 版本，转换完成后自然可见，
        // 避免「上传后立即发布资料阅读」被 PENDING 状态阻断。

        LocalDateTime opensAt = Group3Time.parseUtc(request.opensAt(), "开放时间");
        LocalDateTime closesAt = Group3Time.parseUtc(request.closesAt(), "截止时间");
        if (opensAt == null)
        {
            opensAt = Group3Time.nowUtc();
        }
        if (closesAt != null && !closesAt.isAfter(opensAt))
        {
            throw new ServiceException("截止时间必须晚于开放时间", HttpStatus.BAD_REQUEST);
        }
        if (closesAt != null && !closesAt.isAfter(Group3Time.nowUtc()))
        {
            throw new ServiceException("截止时间必须晚于当前时间", HttpStatus.BAD_REQUEST);
        }

        Long resourceId = Group3AccessService.asLong(version.get("resourceId"));
        Map<String, Object> row = releaseVersion(request.bindingId(),
                Group3AccessService.asLong(binding.get("courseSpaceId")),
                resourceId, request.resourceVersionId(), request.source(), opensAt, closesAt);
        row.put("serverNow", Group3Time.nowIso());
        return row;
    }

    /**
     * 写入一条教学班发布记录。发布按「资源 + 来源」独立：
     * 课程建设（STUDIO）与课前准备（PREPARE）各保留自己的有效发布，互不影响。
     */
    private Map<String, Object> releaseVersion(
            Long bindingId,
            Long courseSpaceId,
            Long resourceId,
            Long versionId,
            String source,
            LocalDateTime opensAt,
            LocalDateTime closesAt)
    {
        String requestedSource = normalizeReleaseSource(source);
        Map<String, Object> active = mapper.selectActiveRelease(bindingId, resourceId, requestedSource);
        if (active != null && versionId.equals(Group3AccessService.asLong(active.get("resourceVersionId"))))
        {
            Map<String, Object> result = new LinkedHashMap<>(active);
            result.put("idempotent", true);
            return result;
        }

        // 只替换同一来源的旧发布，另一来源（课程建设/课前准备）保持原样。
        Map<String, Object> ended = new LinkedHashMap<>();
        ended.put("bindingId", bindingId);
        ended.put("resourceId", resourceId);
        ended.put("source", requestedSource);
        ended.put("endKind", "REPLACED");
        ended.put("userId", Group3Security.currentUserId());
        ended.put("reason", "同来源发布被新版本替换");
        mapper.endReleasesBySource(ended);

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", bindingId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("resourceId", resourceId);
        row.put("resourceVersionId", versionId);
        row.put("releaseSource", requestedSource);
        row.put("userId", Group3Security.currentUserId());
        row.put("opensAt", opensAt);
        row.put("closesAt", closesAt);
        mapper.insertRelease(row);
        row.put("releaseId", row.get("id"));
        return row;
    }

    /** 发布来源只接受 PREPARE，其余一律按课程建设 STUDIO 处理。 */
    private String normalizeReleaseSource(String source)
    {
        return source != null && Group3Constants.RELEASE_PREPARE.equalsIgnoreCase(source.trim())
                ? Group3Constants.RELEASE_PREPARE : Group3Constants.RELEASE_STUDIO;
    }

    private String normalizeOrigin(String origin)
    {
        return origin != null && Group3Constants.RELEASE_PREPARE.equalsIgnoreCase(origin.trim())
                ? Group3Constants.RELEASE_PREPARE : Group3Constants.RELEASE_STUDIO;
    }

    /**
     * 课前准备删除资料环节：只处理 PREPARE 发布，绝不影响课程建设资料。
     * MATERIAL 仅撤回课前准备发布；DRAFT 额外归档课前准备自己上传的草稿资源。
     */
    @Transactional
    public void removePrepareStage(Long bindingId, Long resourceId, boolean draft)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        mapper.lockBinding(bindingId);

        Map<String, Object> ended = new LinkedHashMap<>();
        ended.put("bindingId", bindingId);
        ended.put("resourceId", resourceId);
        ended.put("source", Group3Constants.RELEASE_PREPARE);
        ended.put("endKind", "REVOKED");
        ended.put("userId", Group3Security.currentUserId());
        ended.put("reason", "课前准备删除资料环节");
        mapper.endReleasesBySource(ended);

        if (!draft)
        {
            return;
        }
        String origin = mapper.selectResourceOrigin(resourceId, courseSpaceId);
        if (origin == null)
        {
            throw new ServiceException("资料不存在", HttpStatus.NOT_FOUND);
        }
        if (!Group3Constants.RELEASE_PREPARE.equals(origin))
        {
            throw new ServiceException("课程建设资料请在课程建设中管理，此处仅撤回课前准备发布", HttpStatus.FORBIDDEN);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("resourceId", resourceId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("status", "ARCHIVED");
        mapper.updateResourceStatus(row);
    }

    @Transactional
    public Map<String, Object> revoke(Long releaseId, Long bindingId, String reason)
    {
        accessService.requireTeacherBinding(bindingId);
        Map<String, Object> release = mapper.selectReleaseById(releaseId);
        if (release == null
                || !bindingId.equals(Group3AccessService.asLong(release.get("bindingId")))
                || release.get("endedAt") != null)
        {
            throw new ServiceException("发布记录不存在或已经撤回", HttpStatus.CONFLICT);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("releaseId", releaseId);
        row.put("bindingId", bindingId);
        row.put("userId", Group3Security.currentUserId());
        row.put("reason", reason == null || reason.isBlank() ? "教师主动撤回" : reason.trim());
        int changed = mapper.revokeRelease(row);
        if (changed == 0)
        {
            throw new ServiceException("撤回失败，发布状态可能已经变化", HttpStatus.CONFLICT);
        }
        row.put("revoked", true);
        return row;
    }

    /**
     * 学生端「课程资料」：只返回课程建设（STUDIO）发布的资料。
     */
    public List<Map<String, Object>> studentMaterials(Long bindingId)
    {
        accessService.requireStudentBinding(bindingId);
        return decorateResources(mapper.selectStudentMaterials(
                bindingId, Group3Security.currentUserId(), Group3Constants.RELEASE_STUDIO));
    }

    /**
     * 学生端「课前学习」：只返回课前准备（PREPARE）发布的资料。
     */
    public List<Map<String, Object>> studentPrepMaterials(Long bindingId)
    {
        accessService.requireStudentBinding(bindingId);
        return decorateResources(mapper.selectStudentMaterials(
                bindingId, Group3Security.currentUserId(), Group3Constants.RELEASE_PREPARE));
    }

    @Override
    public List<Map<String, Object>> publishedResources(Long bindingId)
    {
        Map<String, Object> binding = accessService.requireAnyBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        List<Map<String, Object>> rows;
        if (Group3Security.isTeacher())
        {
            rows = mapper.selectTeacherResources(bindingId, courseSpaceId).stream()
                    .filter(row -> row.get("releaseId") != null)
                    .toList();
        }
        else
        {
            rows = mapper.selectStudentMaterials(bindingId, Group3Security.currentUserId(), null);
        }
        return rows.stream().map(row -> {
            Long versionId = Group3AccessService.asLong(row.get("versionId"));
            return publishedContract(bindingId, versionId, row);
        }).toList();
    }

    @Override
    public Map<String, Object> resourceVersion(Long bindingId, Long versionId)
    {
        return publishedContract(bindingId, versionId, null);
    }

    private Map<String, Object> publishedContract(
            Long bindingId, Long versionId, Map<String, Object> source)
    {
        Map<String, Object> detail = versionDetail(versionId, bindingId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("bindingId", bindingId);
        result.put("resourceId", detail.get("resourceId"));
        result.put("versionId", versionId);
        result.put("title", detail.get("title"));
        result.put("versionNo", detail.get("versionNo"));
        result.put("mimeType", detail.get("mimeType"));
        result.put("mediaKind", detail.get("mediaKind"));
        result.put("pageCount", detail.get("pages") instanceof List<?> pages ? pages.size() : 0);
        result.put("pages", detail.get("pages"));
        result.put("releaseId", source == null ? detail.get("releaseId") : source.get("releaseId"));
        result.put("opensAt", source == null ? detail.get("opensAt") : source.get("opensAt"));
        result.put("closesAt", source == null ? detail.get("closesAt") : source.get("closesAt"));
        result.put("downloadUrl", "/education/group3/versions/" + versionId
                + "/download?bindingId=" + bindingId);
        result.put("serverTime", Group3Time.nowIso());
        return result;
    }

    /** 转换失败后基于原文件重试，不要求教师重复上传产生垃圾版本。 */
    public Map<String, Object> retryConversion(Long versionId, Long bindingId)
    {
        Map<String, Object> version = accessService.teacherVersion(bindingId, versionId);
        String status = String.valueOf(version.get("processingStatus"));
        if (!Group3Constants.PROCESSING_FAILED.equals(status))
        {
            throw new ServiceException("只有转换失败的版本可以重试", HttpStatus.CONFLICT);
        }
        String storageKey = (String) version.get("storageKey");
        if (storageKey == null || storageKey.isBlank())
        {
            throw new ServiceException("该版本没有可重试的源文件", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> pending = new LinkedHashMap<>();
        pending.put("versionId", versionId);
        pending.put("processingStatus", Group3Constants.PROCESSING_PENDING);
        mapper.updateResourceVersionStatus(pending);
        conversionExecutor.execute(
                () -> processConversion(versionId, storageKey, storage.fileName(storageKey)));
        Map<String, Object> result = new LinkedHashMap<>(version);
        result.put("processingStatus", Group3Constants.PROCESSING_PENDING);
        result.put("processingMessage", "已重新提交转换，请稍后刷新");
        return result;
    }

    private Map<String, Object> createFileVersion(
            Long resourceId,
            Long courseSpaceId,
            Long bindingId,
            Group3FileStorageService.StoredUpload stored) throws ServiceException
    {
        return createFileVersionWithNo(resourceId, courseSpaceId, bindingId, stored, 1);
    }

    private Map<String, Object> createFileVersionWithNo(
            Long resourceId,
            Long courseSpaceId,
            Long bindingId,
            Group3FileStorageService.StoredUpload stored,
            int versionNo)
    {
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("resourceId", resourceId);
        version.put("resourceKind", "FILE");
        version.put("versionNo", versionNo);
        version.put("storageKey", stored.storageKey());
        version.put("externalUri", null);
        version.put("sha256", stored.sha256());
        version.put("mimeType", stored.mimeType());
        version.put("byteSize", stored.byteSize());
        version.put("processingStatus", Group3Constants.PROCESSING_PENDING);
        version.put("userId", Group3Security.currentUserId());
        mapper.insertResourceVersion(version);
        Long versionId = Group3AccessService.asLong(version.get("id"));
        version.put("versionId", versionId);

        // 转换放入独立线程池异步执行，避免大文件阻塞请求线程；
        // 调用方通过版本状态轮询 PENDING → READY / FAILED。
        conversionExecutor.execute(
                () -> processConversion(versionId, stored.storageKey(), stored.originalName()));
        version.put("processingStatus", Group3Constants.PROCESSING_PENDING);
        version.put("processingMessage", "文件已上传，正在转换，请稍候刷新");
        return version;
    }

    private void processConversion(Long versionId, String storageKey, String originalName)
    {
        try
        {
            List<Group3FileStorageService.ConvertedPage> pages = storage.convert(storageKey, originalName);
            mapper.clearResourcePages(versionId);
            for (Group3FileStorageService.ConvertedPage page : pages)
            {
                Map<String, Object> pageRow = new LinkedHashMap<>();
                pageRow.put("versionId", versionId);
                pageRow.put("pageNo", page.pageNo());
                pageRow.put("previewStorageKey", page.previewStorageKey());
                pageRow.put("widthPx", page.width());
                pageRow.put("heightPx", page.height());
                mapper.insertResourcePage(pageRow);
            }
            Map<String, Object> ready = new LinkedHashMap<>();
            ready.put("versionId", versionId);
            ready.put("processingStatus", Group3Constants.PROCESSING_READY);
            mapper.updateResourceVersionStatus(ready);
        }
        catch (RuntimeException error)
        {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("versionId", versionId);
            failed.put("processingStatus", Group3Constants.PROCESSING_FAILED);
            failed.put("processingMessage", error.getMessage());
            mapper.updateResourceVersionStatus(failed);
        }
    }

    private Map<String, Object> requireResourceInCourse(
            Long courseSpaceId, Long resourceId, Long bindingId)
    {
        List<Map<String, Object>> resources =
                mapper.selectTeacherResources(bindingId, courseSpaceId);
        return resources.stream()
                .filter(item -> resourceId.equals(Group3AccessService.asLong(item.get("resourceId"))))
                .findFirst()
                .orElseThrow(() -> new ServiceException("资料不存在或不属于当前课程", HttpStatus.NOT_FOUND));
    }

    private String cleanTitle(String title, String fallback)
    {
        String value = title == null ? "" : title.trim();
        if (value.isBlank() && fallback != null)
        {
            value = fallback;
        }
        if (value.isBlank())
        {
            throw new ServiceException("资料名称不能为空", HttpStatus.BAD_REQUEST);
        }
        if (value.length() > 200)
        {
            throw new ServiceException("资料名称不能超过 200 个字符", HttpStatus.BAD_REQUEST);
        }
        return value;
    }

    private void validateUnit(Long courseSpaceId, Long unitId)
    {
        if (unitId == null)
        {
            return;
        }
        boolean found = mapper.selectCourseOutline(courseSpaceId).stream()
                .anyMatch(unit -> unitId.equals(Group3AccessService.asLong(unit.get("unitId"))));
        if (!found)
        {
            throw new ServiceException("所选章节不属于当前课程", HttpStatus.BAD_REQUEST);
        }
    }

    private List<Map<String, Object>> decorateResources(List<Map<String, Object>> resources)
    {
        resources.forEach(this::decorateVersion);
        return resources;
    }

    private void decorateVersion(Map<String, Object> version)
    {
        String status = String.valueOf(version.get("processingStatus"));
        version.put("processingMessage", switch (status)
        {
            case Group3Constants.PROCESSING_PENDING -> "资料处理中，请稍后刷新";
            case Group3Constants.PROCESSING_FAILED -> "资料转换失败，请检查文件后重试或上传新版本";
            case Group3Constants.PROCESSING_READY -> "资料可用";
            default -> "状态未知";
        });
        String storageKey = (String) version.get("storageKey");
        if (storageKey != null && !storageKey.isBlank())
        {
            version.put("mediaKind", storage.mediaKind(storageKey));
            version.put("fileName", storage.fileName(storageKey));
        }
        else if (version.get("externalUri") != null)
        {
            version.put("mediaKind", "LINK");
        }
    }
}
