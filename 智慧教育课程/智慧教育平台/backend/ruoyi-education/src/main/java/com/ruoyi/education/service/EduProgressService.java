package com.ruoyi.education.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.mapper.EduOutlineMapper;
import com.ruoyi.education.security.EduActor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 学生学习进度：章节已学/未学标记与最近学习位置。
 * 打开章节内容自动记为已学，也可手动取消已学。
 */
@Service
public class EduProgressService {
    private final EduOutlineMapper mapper;
    private final EduCourseAccessService access;

    public EduProgressService(EduOutlineMapper mapper, EduCourseAccessService access) {
        this.mapper = mapper;
        this.access = access;
    }

    private void requireStudent(Long courseId) {
        if (!"student".equals(EduActor.role())) {
            throw new EduForbiddenException("仅学生可以记录学习进度");
        }
        access.requireCourseReadable(courseId, SecurityUtils.getUserId());
    }

    public Map<String, Object> progress(Long courseId) {
        requireStudent(courseId);
        Long userId = SecurityUtils.getUserId();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("learnedUnitIds", mapper.learnedUnitIds(courseId, userId));
        Map<String, Object> last = mapper.lastViewed(courseId, userId);
        result.put("lastUnitId", last == null ? null : last.get("unitId"));
        result.put("lastUnitTitle", last == null ? null : last.get("title"));
        return result;
    }

    @Transactional
    public Map<String, Object> view(Long courseId, Long unitId) {
        requireStudent(courseId);
        requireUnit(courseId, unitId);
        mapper.markViewed(courseId, SecurityUtils.getUserId(), unitId);
        return progress(courseId);
    }

    @Transactional
    public Map<String, Object> mark(Long courseId, Long unitId, boolean learned) {
        requireStudent(courseId);
        requireUnit(courseId, unitId);
        Long userId = SecurityUtils.getUserId();
        if (learned) mapper.markViewed(courseId, userId, unitId);
        else mapper.clearLearned(courseId, userId, unitId);
        return progress(courseId);
    }

    private void requireUnit(Long courseId, Long unitId) {
        if (unitId == null || mapper.unit(courseId, unitId) == null) {
            throw new ServiceException("章节不存在或不属于本课程");
        }
    }
}
