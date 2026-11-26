package com.ruoyi.education.group3.service;

import java.nio.file.Path;
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
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Security;
import com.ruoyi.education.group3.support.Group3Constants;
import java.time.LocalDateTime;

@Service
public class Group3PublicLibraryService
{
    private final Group3Mapper mapper;
    private final Group3FileStorageService storage;
    private final Executor conversionExecutor;
    private final Group3AccessService accessService;

    public Group3PublicLibraryService(
            Group3Mapper mapper,
            Group3FileStorageService storage,
            Executor group3ConversionExecutor,
            Group3AccessService accessService)
    {
        this.mapper = mapper;
        this.storage = storage;
        this.conversionExecutor = group3ConversionExecutor;
        this.accessService = accessService;
    }

    /* ========== 分类 ========== */

    public List<Map<String, Object>> categories(String kind)
    {
        return mapper.selectPublicCategories(kind == null || kind.isBlank() ? "RESOURCE" : kind.toUpperCase());
    }

    /* ========== 资源列表 ========== */

    public Map<String, Object> listResources(String keyword, Long categoryId, String resourceKind, String categoryKind,
            String excludeCategoryKind, int pageNum, int pageSize)
    {
        int offset = (pageNum - 1) * pageSize;
        String normalizedCategoryKind = categoryKind == null || categoryKind.isBlank() ? null : categoryKind.toUpperCase();
        String normalizedExcludeKind = excludeCategoryKind == null || excludeCategoryKind.isBlank()
                ? null : excludeCategoryKind.toUpperCase();
        List<Map<String, Object>> list = mapper.selectPublicResources(
                keyword == null ? null : keyword.trim(),
                categoryId,
                resourceKind == null || resourceKind.isBlank() ? null : resourceKind.toUpperCase(),
                normalizedCategoryKind,
                normalizedExcludeKind,
                offset,
                pageSize);
        list.forEach(this::decorateResource);
        int total = mapper.countPublicResources(
                keyword == null ? null : keyword.trim(),
                categoryId,
                resourceKind == null || resourceKind.isBlank() ? null : resourceKind.toUpperCase(),
                normalizedCategoryKind,
                normalizedExcludeKind);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /* ========== 资源详情 ========== */

    public Map<String, Object> detail(Long id)
    {
        Map<String, Object> detail = mapper.selectPublicResourceDetail(id);
        if (detail == null)
        {
            throw new ServiceException("资源不存在", HttpStatus.NOT_FOUND);
        }
        decorateResource(detail);
        mapper.increasePublicViewCount(id);
        detail.put("viewCount", ((Number) detail.getOrDefault("viewCount", 0)).intValue() + 1);
        // 收藏状态
        if (Group3Security.currentUserId() != null)
        {
            detail.put("favorited", mapper.checkPublicFavorite(id, Group3Security.currentUserId()) > 0);
        }
        else
        {
            detail.put("favorited", false);
        }
        return detail;
    }

    /* ========== 上传文件资源 ========== */

    @Transactional
    public Map<String, Object> uploadFile(String title, Long categoryId, String description,
            String tags, String difficulty, MultipartFile file)
    {
        if (title == null || title.trim().isBlank())
        {
            throw new ServiceException("资源名称不能为空", HttpStatus.BAD_REQUEST);
        }
        if (title.length() > 200)
        {
            throw new ServiceException("资源名称不能超过 200 个字符", HttpStatus.BAD_REQUEST);
        }
        if (file == null || file.isEmpty())
        {
            throw new ServiceException("请选择文件", HttpStatus.BAD_REQUEST);
        }
        Long userId = Group3Security.currentUserId();
        Group3FileStorageService.StoredUpload stored = storage.store(file, 0L);

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("title", title.trim());
        resource.put("categoryId", categoryId);
        resource.put("resourceKind", "FILE");
        resource.put("description", description == null ? null : description.trim());
        resource.put("tags", tags == null ? null : tags.trim());
        resource.put("difficulty", difficulty == null || difficulty.isBlank() ? "MEDIUM" : difficulty.toUpperCase());
        resource.put("userId", userId);
        mapper.insertPublicResource(resource);
        Long resourceId = ((Number) resource.get("id")).longValue();

        Map<String, Object> version = new LinkedHashMap<>();
        version.put("resourceId", resourceId);
        version.put("resourceKind", "FILE");
        version.put("storageKey", stored.storageKey());
        version.put("externalUri", null);
        version.put("sha256", stored.sha256());
        version.put("mimeType", stored.mimeType());
        version.put("byteSize", stored.byteSize());
        version.put("processingStatus", Group3Constants.PROCESSING_PENDING);
        version.put("userId", userId);
        mapper.insertPublicResourceVersion(version);
        Long versionId = ((Number) version.get("id")).longValue();

        // 异步转换预览页
        conversionExecutor.execute(() -> processConversion(versionId, stored.storageKey(), stored.originalName()));

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resourceId);
        result.put("versionId", versionId);
        result.put("title", title.trim());
        result.put("processingStatus", Group3Constants.PROCESSING_PENDING);
        result.put("processingMessage", "资源已上传，正在转换，请稍后刷新查看");
        return result;
    }

    /* ========== 上传外链资源 ========== */

    @Transactional
    public Map<String, Object> uploadLink(String title, Long categoryId, String description,
            String tags, String difficulty, String externalUri, String linkKind)
    {
        if (title == null || title.trim().isBlank())
        {
            throw new ServiceException("资源名称不能为空", HttpStatus.BAD_REQUEST);
        }
        if (title.length() > 200)
        {
            throw new ServiceException("资源名称不能超过 200 个字符", HttpStatus.BAD_REQUEST);
        }
        if (externalUri == null || externalUri.trim().isBlank())
        {
            throw new ServiceException("链接地址不能为空", HttpStatus.BAD_REQUEST);
        }
        if (!externalUri.trim().matches("^https?://.*"))
        {
            throw new ServiceException("链接地址必须以 http:// 或 https:// 开头", HttpStatus.BAD_REQUEST);
        }
        Long userId = Group3Security.currentUserId();

        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("title", title.trim());
        resource.put("categoryId", categoryId);
        resource.put("resourceKind", "LINK");
        resource.put("description", description == null ? null : description.trim());
        resource.put("tags", tags == null ? null : tags.trim());
        resource.put("difficulty", difficulty == null || difficulty.isBlank() ? "MEDIUM" : difficulty.toUpperCase());
        resource.put("userId", userId);
        mapper.insertPublicResource(resource);
        Long resourceId = ((Number) resource.get("id")).longValue();

        Map<String, Object> version = new LinkedHashMap<>();
        version.put("resourceId", resourceId);
        version.put("resourceKind", "LINK");
        version.put("storageKey", null);
        version.put("externalUri", externalUri.trim());
        version.put("sha256", null);
        version.put("mimeType", "text/uri-list");
        version.put("byteSize", null);
        version.put("processingStatus", Group3Constants.PROCESSING_READY);
        version.put("userId", userId);
        mapper.insertPublicResourceVersion(version);
        Long versionId = ((Number) version.get("id")).longValue();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("id", resourceId);
        result.put("versionId", versionId);
        result.put("title", title.trim());
        result.put("processingStatus", Group3Constants.PROCESSING_READY);
        result.put("processingMessage", "资源已发布");
        return result;
    }

    /* ========== 下载资源 ========== */

    public Resource loadVersionFile(Long versionId)
    {
        Map<String, Object> version = mapper.selectPublicResourceVersion(versionId);
        if (version == null)
        {
            throw new ServiceException("资源版本不存在", HttpStatus.NOT_FOUND);
        }
        String storageKey = (String) version.get("storageKey");
        if (storageKey == null || storageKey.isBlank())
        {
            throw new ServiceException("外链资源没有本地文件", HttpStatus.BAD_REQUEST);
        }
        Long resourceId = ((Number) version.get("resourceId")).longValue();
        mapper.increasePublicDownloadCount(resourceId);
        return storage.load(storageKey);
    }

    public String fileName(Long versionId)
    {
        Map<String, Object> version = mapper.selectPublicResourceVersion(versionId);
        String key = (String) version.get("storageKey");
        return key == null ? String.valueOf(version.get("title")) : storage.fileName(key);
    }

    public String contentType(Long versionId)
    {
        Map<String, Object> version = mapper.selectPublicResourceVersion(versionId);
        String key = (String) version.get("storageKey");
        return key == null ? "text/uri-list" : storage.contentType(key);
    }

    /* ========== 预览页 ========== */

    public Path pagePath(Long versionId, int pageNo)
    {
        List<Map<String, Object>> pages = mapper.selectPublicResourcePages(versionId);
        return pages.stream()
                .filter(page -> Integer.parseInt(String.valueOf(page.get("pageNo"))) == pageNo)
                .map(page -> storage.path((String) page.get("previewStorageKey")))
                .findFirst()
                .orElseThrow(() -> new ServiceException("预览页不存在", HttpStatus.NOT_FOUND));
    }

    public List<Map<String, Object>> pages(Long versionId)
    {
        List<Map<String, Object>> pages = mapper.selectPublicResourcePages(versionId);
        for (Map<String, Object> page : pages)
        {
            page.remove("previewStorageKey");
            page.put("url", "/education/group3/public/versions/" + versionId + "/pages/"
                    + page.get("pageNo"));
        }
        return pages;
    }

    /* ========== 收藏 ========== */

    @Transactional
    public Map<String, Object> toggleFavorite(Long resourceId)
    {
        Long userId = Group3Security.currentUserId();
        if (userId == null)
        {
            throw new ServiceException("请先登录", HttpStatus.UNAUTHORIZED);
        }
        Map<String, Object> detail = mapper.selectPublicResourceDetail(resourceId);
        if (detail == null)
        {
            throw new ServiceException("资源不存在", HttpStatus.NOT_FOUND);
        }
        boolean favorited = mapper.checkPublicFavorite(resourceId, userId) > 0;
        Map<String, Object> result = new LinkedHashMap<>();
        if (favorited)
        {
            mapper.deletePublicFavorite(resourceId, userId);
            result.put("favorited", false);
            result.put("message", "已取消收藏");
        }
        else
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resourceId", resourceId);
            row.put("userId", userId);
            mapper.insertPublicFavorite(row);
            result.put("favorited", true);
            result.put("message", "已收藏");
        }
        return result;
    }

    public List<Map<String, Object>> myFavorites()
    {
        Long userId = Group3Security.currentUserId();
        return mapper.selectMyPublicFavorites(userId);
    }

    /* ========== 我的上传 ========== */

    public Map<String, Object> myUploads(int pageNum, int pageSize)
    {
        Long userId = Group3Security.currentUserId();
        int offset = (pageNum - 1) * pageSize;
        List<Map<String, Object>> list = mapper.selectMyPublicResources(userId, offset, pageSize);
        list.forEach(this::decorateResource);
        int total = mapper.countMyPublicResources(userId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("list", list);
        result.put("total", total);
        result.put("pageNum", pageNum);
        result.put("pageSize", pageSize);
        return result;
    }

    /* ========== 辅助方法 ========== */

    private void processConversion(Long versionId, String storageKey, String originalName)
    {
        try
        {
            List<Group3FileStorageService.ConvertedPage> pages = storage.convert(storageKey, originalName);
            for (Group3FileStorageService.ConvertedPage page : pages)
            {
                Map<String, Object> pageRow = new LinkedHashMap<>();
                pageRow.put("versionId", versionId);
                pageRow.put("pageNo", page.pageNo());
                pageRow.put("previewStorageKey", page.previewStorageKey());
                pageRow.put("widthPx", page.width());
                pageRow.put("heightPx", page.height());
                mapper.insertPublicResourcePage(pageRow);
            }
            Map<String, Object> ready = new LinkedHashMap<>();
            ready.put("versionId", versionId);
            ready.put("processingStatus", Group3Constants.PROCESSING_READY);
            // 注意：公共版本表没有 update 方法，直接用 SQL 更新
            // 这里我们复用一个简单的 update 方式
            mapper.updatePublicVersionStatus(ready);
        }
        catch (RuntimeException error)
        {
            Map<String, Object> failed = new LinkedHashMap<>();
            failed.put("versionId", versionId);
            failed.put("processingStatus", Group3Constants.PROCESSING_FAILED);
            mapper.updatePublicVersionStatus(failed);
        }
    }

    /* ========== 引用到课程资料 ========== */

    @Transactional
    public Map<String, Object> importToCourse(Long publicResourceId, Long bindingId, Long folderId, Long unitId)
    {
        if (bindingId == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));

        Map<String, Object> pubDetail = mapper.selectPublicResourceDetail(publicResourceId);
        if (pubDetail == null)
        {
            throw new ServiceException("公共资源不存在", HttpStatus.NOT_FOUND);
        }
        String resourceKind = String.valueOf(pubDetail.get("resourceKind"));
        Long versionId = Group3AccessService.asLong(pubDetail.get("versionId"));
        Map<String, Object> pubVersion = mapper.selectPublicResourceVersion(versionId);
        if (pubVersion == null)
        {
            throw new ServiceException("资源版本不存在", HttpStatus.NOT_FOUND);
        }

        Long userId = Group3Security.currentUserId();

        String resourceTitle = String.valueOf(pubDetail.get("title"));
        if (mapper.countActiveReleasedResourceByTitle(courseSpaceId, bindingId, resourceTitle) > 0)
        {
            throw new ServiceException("该资源已在课程资料中", HttpStatus.BAD_REQUEST);
        }

        // 1. 创建课程资源记录
        Map<String, Object> resource = new LinkedHashMap<>();
        resource.put("courseSpaceId", courseSpaceId);
        resource.put("folderId", folderId);
        resource.put("unitId", unitId);
        resource.put("title", resourceTitle);
        resource.put("resourceKind", resourceKind);
        resource.put("origin", Group3Constants.RELEASE_STUDIO);
        resource.put("userId", userId);
        mapper.insertResource(resource);
        Long newResourceId = Group3AccessService.asLong(resource.get("id"));

        // 2. 创建版本记录
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("resourceId", newResourceId);
        version.put("resourceKind", resourceKind);
        version.put("versionNo", 1);
        version.put("storageKey", pubVersion.get("storageKey"));
        version.put("externalUri", pubVersion.get("externalUri"));
        version.put("sha256", pubVersion.get("sha256"));
        version.put("mimeType", pubVersion.get("mimeType"));
        version.put("byteSize", pubVersion.get("byteSize"));
        version.put("processingStatus", pubVersion.get("processingStatus"));
        version.put("userId", userId);
        mapper.insertResourceVersion(version);
        Long newVersionId = Group3AccessService.asLong(version.get("id"));
        version.put("versionId", newVersionId);

        // 3. 复制预览页（文件类型）
        if ("FILE".equals(resourceKind))
        {
            List<Map<String, Object>> pages = mapper.selectPublicResourcePages(versionId);
            for (Map<String, Object> page : pages)
            {
                Map<String, Object> newPage = new LinkedHashMap<>();
                newPage.put("versionId", newVersionId);
                newPage.put("pageNo", page.get("pageNo"));
                newPage.put("previewStorageKey", page.get("previewStorageKey"));
                newPage.put("widthPx", page.get("widthPx"));
                newPage.put("heightPx", page.get("heightPx"));
                mapper.insertResourcePage(newPage);
            }
        }

        // 4. 发布到教学班
        mapper.lockBinding(bindingId);
        releaseVersion(bindingId, courseSpaceId, newResourceId, newVersionId,
                Group3Constants.RELEASE_STUDIO, LocalDateTime.now(), null);

        // 5. 公共库下载计数+1
        mapper.increasePublicDownloadCount(publicResourceId);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("resourceId", newResourceId);
        result.put("versionId", newVersionId);
        result.put("title", pubDetail.get("title"));
        return result;
    }

    private void releaseVersion(Long bindingId, Long courseSpaceId, Long resourceId,
            Long versionId, String releaseSource, LocalDateTime releaseAt, String note)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", bindingId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("resourceId", resourceId);
        row.put("resourceVersionId", versionId);
        row.put("releaseSource", releaseSource);
        row.put("opensAt", releaseAt);
        row.put("closesAt", null);
        row.put("userId", Group3Security.currentUserId());
        mapper.insertRelease(row);
    }

    private void decorateResource(Map<String, Object> resource)
    {
        String storageKey = (String) resource.get("storageKey");
        if (storageKey != null && !storageKey.isBlank())
        {
            resource.put("mediaKind", storage.mediaKind(storageKey));
            resource.put("fileName", storage.fileName(storageKey));
        }
        else if (resource.get("externalUri") != null)
        {
            resource.put("mediaKind", "LINK");
        }
    }
}
