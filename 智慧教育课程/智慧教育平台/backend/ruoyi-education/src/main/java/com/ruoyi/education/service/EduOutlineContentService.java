package com.ruoyi.education.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.mapper.EduOutlineMapper;
import com.ruoyi.education.security.EduActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

/**
 * 章节/知识点内容与附件。
 *
 * <p>教师编辑的内容写入草稿表，发布目录后学生才能看到；学生读正式表。
 * 附件按章节 ID 存正式附件表，草稿沿用同一 ID，不随草稿/发布切换。</p>
 */
@Service
public class EduOutlineContentService {
    private final EduOutlineMapper outline;
    private final EduCourseAccessService access;
    private final EduCourseManagementService management;

    public EduOutlineContentService(EduOutlineMapper outline, EduCourseAccessService access,
            EduCourseManagementService management) {
        this.outline = outline;
        this.access = access;
        this.management = management;
    }

    public Map<String, Object> read(Long courseId, Long nodeId, boolean point) {
        access.requireCourseReadable(courseId, SecurityUtils.getUserId());
        boolean teacher = "teacher".equals(EduActor.role()) || EduActor.admin();
        Map<String, Object> result;
        if (teacher && outline.draftCount(courseId) > 0) {
            result = outline.contentDraft(courseId, nodeId, point);
            if (result == null) result = outline.content(courseId, nodeId, point);
        } else {
            result = outline.content(courseId, nodeId, point);
        }
        if (result == null) throw new ServiceException("章节或知识点不存在");
        result.put("attachments", outline.listAttachments(courseId, nodeId, point));
        return result;
    }

    @Transactional
    public void save(Long courseId, Long nodeId, boolean point, String content) {
        requireEditable(courseId, nodeId, point);
        if (content == null || content.length() > 12000) throw new ServiceException("正文最多 12000 字");
        ensureDraft(courseId);
        if (outline.saveContentDraft(courseId, nodeId, point, content) == 0) {
            throw new ServiceException("章节或知识点不存在");
        }
        outline.setOutlineDirty(courseId, 1);
    }

    @Transactional
    public void attach(Long courseId, Long nodeId, boolean point, String fileName, byte[] bytes) {
        requireEditable(courseId, nodeId, point);
        if (outline.listAttachments(courseId, nodeId, point).size() >= 30) {
            throw new ServiceException("每个章节或知识点最多保留 30 个附件");
        }
        outline.insertAttachment(courseId, point ? null : nodeId, point ? nodeId : null, fileName, bytes.length, bytes);
    }

    public Map<String, Object> download(Long courseId, Long nodeId, boolean point, Long fileId) {
        access.requireCourseReadable(courseId, SecurityUtils.getUserId());
        Map<String, Object> file = outline.attachment(courseId, nodeId, point, fileId);
        if (file == null) throw new ServiceException("附件不存在");
        return file;
    }

    @Transactional
    public void removeAttachment(Long courseId, Long nodeId, boolean point, Long fileId) {
        requireEditable(courseId, nodeId, point);
        if (outline.deleteAttachment(courseId, nodeId, point, fileId) == 0) throw new ServiceException("附件不存在");
    }

    private void requireEditable(Long courseId, Long nodeId, boolean point) {
        EduActor.requireTeacher();
        management.requireManagePermission(courseId, point ? "knowledgeGraph" : "outline",
                point ? "你没有「编辑知识图谱」的权限" : "你没有「编辑课程章节」的权限");
        requireNode(courseId, nodeId, point);
    }

    private void ensureDraft(Long courseId) {
        if (outline.draftCount(courseId) > 0) return;
        outline.copyPublishedUnitsToDraft(courseId);
        outline.copyPublishedPointsToDraft(courseId);
    }

    private void requireNode(Long courseId, Long nodeId, boolean point) {
        if (point) {
            boolean exists = outline.draftCount(courseId) > 0
                    ? outline.draftPoints(courseId).stream().anyMatch(row -> nodeId.equals(((Number) row.get("id")).longValue()))
                    : outline.point(courseId, nodeId) != null;
            if (!exists) throw new ServiceException("知识点不存在或不属于本课程");
        } else {
            boolean exists = outline.draftCount(courseId) > 0
                    ? outline.draftUnits(courseId).stream().anyMatch(row -> nodeId.equals(((Number) row.get("id")).longValue()))
                    : outline.unit(courseId, nodeId) != null;
            if (!exists) throw new ServiceException("章节不存在或不属于本课程");
        }
    }
}
