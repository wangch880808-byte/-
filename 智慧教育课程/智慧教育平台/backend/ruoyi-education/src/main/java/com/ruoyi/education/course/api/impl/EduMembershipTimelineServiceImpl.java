package com.ruoyi.education.course.api.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.course.api.EduStudentMemberSegment;
import com.ruoyi.education.mapper.EduMembershipTimelineMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * {@link EduMembershipTimelineService} 的默认实现。
 *
 * <p>一次调用一到两条轻量查询；成员段/绑定的时点覆盖条件写在 SQL WHERE，
 * 状态位（bindingActive/classOpen/active）由本类按同一时点快照统一计算，避免口径分叉。</p>
 */
@Service
public class EduMembershipTimelineServiceImpl implements EduMembershipTimelineService {

    private static final Logger log = LoggerFactory.getLogger(EduMembershipTimelineServiceImpl.class);

    @Autowired
    private EduMembershipTimelineMapper mapper;

    @Override
    public EduStudentBindingAccess studentInBinding(Long bindingId, Long userId, Instant at) {
        Date point = requireAt(at);
        Map<String, Object> binding = requireBinding(bindingId);
        Map<String, Object> segment = userId == null ? null
                : mapper.selectStudentSegmentInBinding(bindingId, userId, point);
        boolean bindingActive = covers(instant(binding.get("published_at")), instant(binding.get("ended_at")), at);
        boolean active = bindingActive && segment != null;
        return new EduStudentBindingAccess(
                number(binding.get("binding_id")),
                number(binding.get("teaching_class_id")),
                number(binding.get("course_space_id")),
                userId,
                bindingActive,
                "ACTIVE".equals(binding.get("teaching_class_status")),
                active,
                active ? instant(segment.get("joined_at")) : null,
                active ? instant(segment.get("left_at")) : null);
    }

    @Override
    public List<EduStudentBindingAccess> studentBindings(Long userId, Instant at) {
        if (userId == null) return List.of();
        Date point = requireAt(at);
        List<EduStudentBindingAccess> result = new ArrayList<>();
        for (Map<String, Object> row : mapper.selectStudentBindings(userId, point)) {
            result.add(new EduStudentBindingAccess(
                    number(row.get("binding_id")),
                    number(row.get("teaching_class_id")),
                    number(row.get("course_space_id")),
                    userId,
                    true,
                    "ACTIVE".equals(row.get("teaching_class_status")),
                    true,
                    instant(row.get("joined_at")),
                    instant(row.get("left_at"))));
        }
        return result;
    }

    @Override
    public List<EduStudentMemberSegment> studentsInBinding(Long bindingId, Instant at) {
        Date point = requireAt(at);
        requireBinding(bindingId);
        List<EduStudentMemberSegment> result = new ArrayList<>();
        for (Map<String, Object> row : mapper.selectStudentsInBinding(bindingId, point)) {
            result.add(new EduStudentMemberSegment(
                    number(row.get("member_id")),
                    number(row.get("teaching_class_id")),
                    number(row.get("user_id")),
                    text(row.get("user_name")),
                    text(row.get("nick_name")),
                    instant(row.get("joined_at")),
                    instant(row.get("left_at"))));
        }
        return result;
    }

    private Map<String, Object> requireBinding(Long bindingId) {
        if (bindingId == null) throw new ServiceException("课程绑定编号不能为空");
        Map<String, Object> binding = mapper.selectBindingWithClass(bindingId);
        if (binding == null) throw new ServiceException("课程绑定不存在");
        return binding;
    }

    private static Date requireAt(Instant at) {
        if (at == null) throw new ServiceException("查询时点不能为空");
        return Date.from(at);
    }

    private static boolean covers(Instant from, Instant to, Instant at) {
        return from != null && !from.isAfter(at) && (to == null || to.isAfter(at));
    }

    private static Instant instant(Object value) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        log.warn("成员时点列返回了无法识别的类型：{}", value.getClass().getName());
        return null;
    }

    private static Long number(Object value) {
        if (value == null) return null;
        if (value instanceof Number number) return number.longValue();
        return Long.valueOf(String.valueOf(value).trim());
    }

    private static String text(Object value) {
        if (value == null) return null;
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }
}
