package com.ruoyi.web.controller.education;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.service.EduOutlineContentService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@RestController
@RequestMapping("/education/course/{courseId}/outline/{kind:units|points}/{nodeId}/content")
public class EduOutlineContentController {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> FILE_EXTENSIONS = Set.of("pdf", "doc", "docx", "ppt", "pptx", "xls", "xlsx",
            "txt", "jpg", "jpeg", "png", "gif", "webp", "mp4", "webm", "mp3", "wav");
    private final EduOutlineContentService content;

    public EduOutlineContentController(EduOutlineContentService content) {
        this.content = content;
    }

    @GetMapping
    @PreAuthorize("@ss.hasPermi('education:course:list')")
    public AjaxResult get(@PathVariable Long courseId, @PathVariable String kind, @PathVariable Long nodeId) {
        return AjaxResult.success(content.read(courseId, nodeId, "points".equals(kind)));
    }

    @PutMapping
    @PreAuthorize("@ss.hasPermi('education:course:edit')")
    @Log(title = "课程内容", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    public AjaxResult save(@PathVariable Long courseId, @PathVariable String kind, @PathVariable Long nodeId,
            @RequestBody Map<String, String> body) {
        content.save(courseId, nodeId, "points".equals(kind), body.get("contentText"));
        return AjaxResult.success();
    }

    @PostMapping("/attachments")
    @PreAuthorize("@ss.hasPermi('education:course:edit')")
    @Log(title = "课程附件", businessType = BusinessType.INSERT, isSaveRequestData = false)
    public AjaxResult attach(@PathVariable Long courseId, @PathVariable String kind, @PathVariable Long nodeId,
            @RequestParam("file") MultipartFile file) throws IOException {
        String name = file.getOriginalFilename();
        if (file.isEmpty() || file.getSize() > MAX_FILE_SIZE || name == null) {
            throw new ServiceException("请选择不超过 10 MB 的文件");
        }
        name = name.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        int dot = name.lastIndexOf('.');
        if (name.length() > 200 || name.chars().anyMatch(Character::isISOControl) || dot <= 0
                || !FILE_EXTENSIONS.contains(name.substring(dot + 1).toLowerCase(Locale.ROOT))) {
            throw new ServiceException("请选择常用文档、图片、视频或音频文件");
        }
        content.attach(courseId, nodeId, "points".equals(kind), name, file.getBytes());
        return AjaxResult.success();
    }

    @GetMapping("/attachments/{fileId}")
    @PreAuthorize("@ss.hasPermi('education:course:list')")
    public ResponseEntity<byte[]> download(@PathVariable Long courseId, @PathVariable String kind,
            @PathVariable Long nodeId, @PathVariable Long fileId) {
        Map<String, Object> file = content.download(courseId, nodeId, "points".equals(kind), fileId);
        return ResponseEntity.ok().contentType(MediaType.APPLICATION_OCTET_STREAM)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename((String) file.get("fileName"), StandardCharsets.UTF_8).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "private, no-store")
                .header("X-Content-Type-Options", "nosniff")
                .body((byte[]) file.get("fileData"));
    }

    @DeleteMapping("/attachments/{fileId}")
    @PreAuthorize("@ss.hasPermi('education:course:edit')")
    @Log(title = "课程附件", businessType = BusinessType.DELETE)
    public AjaxResult remove(@PathVariable Long courseId, @PathVariable String kind,
            @PathVariable Long nodeId, @PathVariable Long fileId) {
        content.removeAttachment(courseId, nodeId, "points".equals(kind), fileId);
        return AjaxResult.success();
    }
}
