package com.ruoyi.education.service;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.mapper.EduCourseSpaceMapper;
import com.ruoyi.education.mapper.EduOutlineMapper;
import com.ruoyi.education.security.EduActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.Map;

@Service
public class EduOutlineContentService {
    private final EduOutlineMapper outline;
    private final EduCourseSpaceMapper spaces;
    private final EduCourseAccessService access;

    public EduOutlineContentService(EduOutlineMapper outline, EduCourseSpaceMapper spaces, EduCourseAccessService access) {
        this.outline = outline;
        this.spaces = spaces;
        this.access = access;
    }

    public Map<String, Object> read(Long courseId, Long nodeId, boolean point) {
        access.requireCourseReadable(courseId, SecurityUtils.getUserId());
        Map<String, Object> result = new LinkedHashMap<>(requireNode(courseId, nodeId, point));
        result.put("attachments", outline.listAttachments(courseId, nodeId, point));
        return result;
    }

    @Transactional
    public void save(Long courseId, Long nodeId, boolean point, String content) {
        requireEditable(courseId, nodeId, point);
        if (content == null || content.length() > 12000) throw new ServiceException("正文最多 12000 字");
        outline.saveContent(courseId, nodeId, point, content);
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
        EduCourseSpace course = spaces.selectByIdForUpdate(courseId);
        if (course == null) throw new ServiceException("课程不存在");
        if (!EduActor.admin() && !SecurityUtils.getUserId().equals(course.getOwnerUserId())) {
            throw new EduForbiddenException("只有课程负责人可以编辑内容");
        }
        if ("ARCHIVED".equals(course.getStatus())) throw new ServiceException("已归档课程只允许查看");
        requireNode(courseId, nodeId, point);
    }

    private Map<String, Object> requireNode(Long courseId, Long nodeId, boolean point) {
        Map<String, Object> row = outline.content(courseId, nodeId, point);
        if (row == null) throw new ServiceException("章节或知识点不存在");
        return row;
    }
}
