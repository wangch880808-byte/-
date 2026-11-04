package com.ruoyi.education.controller;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.FileSystemResource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.MediaTypeFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.mapper.EduSupportMapper;

/**
 * 课件页预览（临时实现，待第 3 组提供正式预览流或签名 URL 后替换）。
 *
 * <p>存储键取自 edu_resource_page.preview_storage_key，按
 * {@code zhijiao.resource.preview-root} 配置的本地根目录解析。
 * 未配置根目录时返回 503 并说明原因，避免前端拿到 404 后误判为「课件页不存在」。</p>
 */
@RestController
@RequestMapping("/education/resource")
public class EducationResourceController
{
    @Value("${zhijiao.resource.preview-root:}")
    private String previewRoot;

    @Autowired
    private EduSupportMapper eduSupportMapper;

    @GetMapping("/versions/{resourceVersionId}/pages/{pageNo}/preview")
    public ResponseEntity<?> preview(@PathVariable Long resourceVersionId,
            @PathVariable Integer pageNo)
    {
        Long userId = SecurityUtils.getUserId();
        if (eduSupportMapper.countPreviewAccess(userId, resourceVersionId) == 0)
        {
            return fail(403, "无权查看该课件页");
        }
        String storageKey = eduSupportMapper.selectPreviewStorageKey(resourceVersionId, pageNo);
        if (storageKey == null)
        {
            return fail(404, "课件页不存在");
        }
        if (previewRoot == null || previewRoot.trim().isEmpty())
        {
            return fail(503, "课件预览存储未配置（zhijiao.resource.preview-root），待第 3 组提供预览服务");
        }
        Path root = Paths.get(previewRoot.trim()).toAbsolutePath().normalize();
        Path file = root.resolve(storageKey).normalize();
        if (!file.startsWith(root))
        {
            return fail(403, "非法的存储键");
        }
        if (!Files.isRegularFile(file))
        {
            return fail(404, "课件预览文件不存在：" + storageKey);
        }
        MediaType mediaType = MediaTypeFactory.getMediaType(file.getFileName().toString())
                .orElse(MediaType.APPLICATION_OCTET_STREAM);
        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(Duration.ofMinutes(10)).cachePrivate())
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "inline; filename=\"" + file.getFileName().toString() + "\"")
                .body(new FileSystemResource(file));
    }

    private ResponseEntity<Map<String, Object>> fail(int code, String message)
    {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("code", code);
        body.put("msg", message);
        body.put("data", null);
        return ResponseEntity.status(code).body(body);
    }
}
