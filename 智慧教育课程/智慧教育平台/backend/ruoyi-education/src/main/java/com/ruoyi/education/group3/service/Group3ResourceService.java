package com.ruoyi.education.group3.service;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group3.contract.PublishedResourceProvider;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Requests;
import com.ruoyi.education.group3.support.Group3Security;
import com.ruoyi.education.group3.support.Group3Time;

@Service
public class Group3ResourceService implements PublishedResourceProvider
{
    private final Group3Mapper mapper;
    private final Group3AccessService accessService;
    private final Group3FileStorageService storage;

    public Group3ResourceService(
            Group3Mapper mapper,
            Group3AccessService accessService,
            Group3FileStorageService storage)
    {
        this.mapper = mapper;
        this.accessService = accessService;
        this.storage = storage;
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
                    Group3Security.currentUserId())));
        }
        return Group3Time.withServerTime(result);
    }

    @Transactional
    public Map<String, Object> upload(
            Long bindingId,
            String title,
            Long unitId,
            MultipartFile file)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String cleanTitle = cleanTitle(title, file == null ? null : file.getOriginalFilename());
        validateUnit(courseSpaceId, unitId);

        Group3FileStorageService.StoredUpload stored = storage.store(file, courseSpaceId);
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("courseSpaceId", courseSpaceId);
        resource.put("title", cleanTitle);
        resource.put("resourceKind", "FILE");
        resource.put("userId", Group3Security.currentUserId());
        mapper.insertResource(resource);
        Long resourceId = Group3AccessService.asLong(resource.get("id"));

        return createFileVersion(resourceId, courseSpaceId, bindingId, stored, unitId);
    }

    @Transactional
    public Map<String, Object> uploadVersion(Long resourceId, Long bindingId, MultipartFile file)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Map<String, Object> existing = requireResourceInCourse(courseSpaceId, resourceId, bindingId);
        if (!"FILE".equals(existing.get("resourceKind")))
        {
            throw new ServiceException("外链资料不能上传文件版本", HttpStatus.BAD_REQUEST);
        }
        mapper.lockResource(resourceId);
        Group3FileStorageService.StoredUpload stored = storage.store(file, courseSpaceId);
        int versionNo = mapper.selectNextResourceVersion(resourceId);
        return createFileVersionWithNo(resourceId, courseSpaceId, bindingId, stored, versionNo);
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
        if (!"READY".equals(version.get("processingStatus")))
        {
            throw new ServiceException("资料尚未处理完成，不能发布", HttpStatus.CONFLICT);
        }

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
        Map<String, Object> active = mapper.selectActiveRelease(request.bindingId(), resourceId);
        if (active != null
                && request.resourceVersionId().equals(Group3AccessService.asLong(active.get("resourceVersionId"))))
        {
            Map<String, Object> result = new LinkedHashMap<>(active);
            result.put("idempotent", true);
            return result;
        }
        if (active != null)
        {
            Map<String, Object> ended = new LinkedHashMap<>();
            ended.put("releaseId", active.get("releaseId"));
            ended.put("endedAt", Group3Time.nowUtc());
            ended.put("endKind", "REPLACED");
            ended.put("userId", Group3Security.currentUserId());
            ended.put("reason", "发布新的资源版本");
            mapper.endRelease(ended);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", request.bindingId());
        row.put("courseSpaceId", binding.get("courseSpaceId"));
        row.put("resourceId", resourceId);
        row.put("resourceVersionId", request.resourceVersionId());
        row.put("userId", Group3Security.currentUserId());
        row.put("opensAt", opensAt);
        row.put("closesAt", closesAt);
        mapper.insertRelease(row);
        row.put("releaseId", row.get("id"));
        row.put("serverNow", Group3Time.nowIso());
        return row;
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

    public List<Map<String, Object>> studentMaterials(Long bindingId)
    {
        accessService.requireStudentBinding(bindingId);
        return decorateResources(mapper.selectStudentMaterials(bindingId, Group3Security.currentUserId()));
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
            rows = mapper.selectStudentMaterials(bindingId, Group3Security.currentUserId());
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

    private Map<String, Object> createFileVersion(
            Long resourceId,
            Long courseSpaceId,
            Long bindingId,
            Group3FileStorageService.StoredUpload stored,
            Long unitId) throws ServiceException
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
        version.put("processingStatus", "PENDING");
        version.put("userId", Group3Security.currentUserId());
        mapper.insertResourceVersion(version);
        Long versionId = Group3AccessService.asLong(version.get("id"));
        version.put("versionId", versionId);

        try
        {
            List<Group3FileStorageService.ConvertedPage> pages =
                    storage.convert(stored.storageKey(), stored.originalName());
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
            ready.put("processingStatus", "READY");
            mapper.updateResourceVersionStatus(ready);
            version.put("processingStatus", "READY");
            version.put("pageCount", pages.size());
            version.put("processingMessage", pages.isEmpty()
                    ? "文件已就绪，可直接受控下载"
                    : "转换完成，可逐页预览");
            return version;
        }
        catch (RuntimeException error)
        {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("versionId", versionId);
            failed.put("processingStatus", "FAILED");
            mapper.updateResourceVersionStatus(failed);
            version.put("processingStatus", "FAILED");
            version.put("processingMessage", error.getMessage());
            return version;
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
            case "PENDING" -> "资料处理中，请稍后刷新";
            case "FAILED" -> "资料转换失败，请检查文件后重试或上传新版本";
            case "READY" -> "资料可用";
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
