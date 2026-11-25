package com.ruoyi.education.course.api.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.course.api.EduStudentMemberSegment;
import com.ruoyi.education.mapper.EduMembershipTimelineMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * {@link EduMembershipTimelineService} 的默认实现。
 *
 * <p>一次调用一到两条轻量查询；成员段/绑定的时点覆盖条件写在 SQL WHERE，
 * 状态位（bindingActive/classOpen/active）由本类按同一时点快照统一计算，避免口径分叉。</p>
 *
 * <p>异常数据不猜测：同一学生在同一教学班有多条覆盖 at 的入班记录（跨行重叠）、
 * 或时点列返回无法识别的非空值，一律抛 {@link ServiceException} 失败关闭，
 * 消费方按授权不可用处理；真正的数据库 NULL（如 left_at/ended_at 为空）仍按无上界处理。</p>
 */
@Service
public class EduMembershipTimelineServiceImpl implements EduMembershipTimelineService {

    @Autowired
    private EduMembershipTimelineMapper mapper;

    @Override
    public EduStudentBindingAccess studentInBinding(Long bindingId, Long userId, Instant at) {
        Date point = requireAt(at);
        Map<String, Object> binding = requireBinding(bindingId);
        boolean bindingActive = covers(instant(binding.get("published_at"), "published_at"),
                instant(binding.get("ended_at"), "ended_at"), at);
        Map<String, Object> segment = null;
        if (userId != null) {
            List<Map<String, Object>> segments = mapper.selectStudentSegmentsInBinding(bindingId, userId, point);
            if (segments != null && segments.size() > 1) {
                throw new ServiceException("学生在课程绑定下存在多条重叠入班记录，无法判定唯一授权"
                        + "（bindingId=" + bindingId + ", userId=" + userId + "）");
            }
            segment = (segments == null || segments.isEmpty()) ? null : segments.get(0);
        }
        boolean active = bindingActive && segment != null;
        return new EduStudentBindingAccess(
                number(binding.get("binding_id")),
                number(binding.get("teaching_class_id")),
                number(binding.get("course_space_id")),
                userId,
                bindingActive,
                "ACTIVE".equals(binding.get("teaching_class_status")),
                active,
                active ? instant(segment.get("joined_at"), "joined_at") : null,
                active ? instant(segment.get("left_at"), "left_at") : null);
    }

    @Override
    public List<EduStudentBindingAccess> studentBindings(Long userId, Instant at) {
        if (userId == null) return List.of();
        Date point = requireAt(at);
        List<Map<String, Object>> rows = mapper.selectStudentBindings(userId, point);
        rejectDuplicateIds(rows, "binding_id",
                "学生在同一课程绑定下存在多条重叠入班记录（userId=" + userId + "）");
        List<EduStudentBindingAccess> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new EduStudentBindingAccess(
                    number(row.get("binding_id")),
                    number(row.get("teaching_class_id")),
                    number(row.get("course_space_id")),
                    userId,
                    true,
                    "ACTIVE".equals(row.get("teaching_class_status")),
                    true,
                    instant(row.get("joined_at"), "joined_at"),
                    instant(row.get("left_at"), "left_at")));
        }
        return result;
    }

    @Override
    public List<EduStudentMemberSegment> studentsInBinding(Long bindingId, Instant at) {
        Date point = requireAt(at);
        requireBinding(bindingId);
        List<Map<String, Object>> rows = mapper.selectStudentsInBinding(bindingId, point);
        rejectDuplicateIds(rows, "user_id",
                "课程绑定下存在同一学生的多条重叠入班记录（bindingId=" + bindingId + "）");
        List<EduStudentMemberSegment> result = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            result.add(new EduStudentMemberSegment(
                    number(row.get("member_id")),
                    number(row.get("teaching_class_id")),
                    number(row.get("user_id")),
                    text(row.get("user_name")),
                    text(row.get("nick_name")),
                    instant(row.get("joined_at"), "joined_at"),
                    instant(row.get("left_at"), "left_at")));
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

    /** 同一业务主键（binding_id / user_id）出现多行即跨行重叠，按授权歧义失败关闭。 */
    private static void rejectDuplicateIds(List<Map<String, Object>> rows, String idKey, String message) {
        if (rows == null || rows.size() < 2) return;
        Set<Object> seen = new HashSet<>();
        for (Map<String, Object> row : rows) {
            if (!seen.add(row.get(idKey))) throw new ServiceException(message);
        }
    }

    /** 真正的数据库 NULL 返回 null（无上界）；无法识别的非空值一律抛异常，不猜测成无结束时间。 */
    private static Instant instant(Object value, String column) {
        if (value == null) return null;
        if (value instanceof Timestamp timestamp) return timestamp.toInstant();
        if (value instanceof Date date) return date.toInstant();
        if (value instanceof LocalDateTime local) return local.toInstant(ZoneOffset.UTC);
        throw new ServiceException("成员时点列 " + column + " 返回了无法识别的值，类型："
                + value.getClass().getName());
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
