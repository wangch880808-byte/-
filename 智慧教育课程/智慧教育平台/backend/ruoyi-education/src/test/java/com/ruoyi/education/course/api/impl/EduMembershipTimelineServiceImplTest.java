package com.ruoyi.education.course.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.course.api.EduStudentMemberSegment;
import com.ruoyi.education.mapper.EduMembershipTimelineMapper;

class EduMembershipTimelineServiceImplTest
{
    private static final Instant EVENT_AT = Instant.parse("2026-11-20T09:00:00Z");
    private static final Instant DELIVERY_AT = Instant.parse("2026-11-20T11:00:00Z");

    private EduMembershipTimelineMapper mapper;
    private EduMembershipTimelineServiceImpl service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(EduMembershipTimelineMapper.class);
        service = new EduMembershipTimelineServiceImpl();
        ReflectionTestUtils.setField(service, "mapper", mapper);
    }

    private static Timestamp ts(int hour, int minute)
    {
        return Timestamp.from(LocalDateTime.of(2026, 11, 20, hour, minute).toInstant(ZoneOffset.UTC));
    }

    private static Map<String, Object> binding(Long bindingId, String status, Timestamp publishedAt, Timestamp endedAt)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("binding_id", bindingId);
        row.put("teaching_class_id", 1L);
        row.put("course_space_id", 10L);
        row.put("teaching_class_status", status);
        row.put("published_at", publishedAt);
        row.put("ended_at", endedAt);
        return row;
    }

    private static Map<String, Object> segment(Timestamp joinedAt, Timestamp leftAt)
    {
        Map<String, Object> row = new HashMap<>();
        row.put("member_id", 7L);
        row.put("joined_at", joinedAt);
        row.put("left_at", leftAt);
        return row;
    }

    @Test
    void studentInBindingActiveAtEventTimeButNotAfterLeave()
    {
        when(mapper.selectBindingWithClass(100L)).thenReturn(binding(100L, "ACTIVE", ts(8, 0), null));
        when(mapper.selectStudentSegmentsInBinding(eq(100L), eq(9001L), any()))
                .thenReturn(List.of(segment(ts(7, 0), ts(10, 0))));

        EduStudentBindingAccess atNine = service.studentInBinding(100L, 9001L, EVENT_AT);
        assertTrue(atNine.bindingActive());
        assertTrue(atNine.classOpen());
        assertTrue(atNine.active());
        assertEquals(Instant.parse("2026-11-20T07:00:00Z"), atNine.joinedAt());
        assertEquals(Instant.parse("2026-11-20T10:00:00Z"), atNine.leftAt());

        when(mapper.selectStudentSegmentsInBinding(eq(100L), eq(9001L), any())).thenReturn(List.of());
        EduStudentBindingAccess atEleven = service.studentInBinding(100L, 9001L, DELIVERY_AT);
        assertTrue(atEleven.bindingActive());
        assertFalse(atEleven.active());
        assertNull(atEleven.joinedAt());
        assertNull(atEleven.leftAt());
    }

    @Test
    void studentInBindingInactiveWhenBindingNotCoveringAt()
    {
        when(mapper.selectBindingWithClass(101L)).thenReturn(binding(101L, "ACTIVE", ts(10, 0), null));
        when(mapper.selectStudentSegmentsInBinding(eq(101L), eq(9001L), any()))
                .thenReturn(List.of(segment(ts(7, 0), null)));

        EduStudentBindingAccess access = service.studentInBinding(101L, 9001L, EVENT_AT);
        assertFalse(access.bindingActive());
        assertFalse(access.active());
        assertNull(access.joinedAt());
    }

    @Test
    void studentInBindingReportsEndedBindingAndClosedClassIndependently()
    {
        when(mapper.selectBindingWithClass(102L)).thenReturn(binding(102L, "DRAFT", ts(8, 0), ts(9, 0)));
        when(mapper.selectStudentSegmentsInBinding(eq(102L), eq(9001L), any()))
                .thenReturn(List.of(segment(ts(7, 0), null)));

        EduStudentBindingAccess access = service.studentInBinding(102L, 9001L, EVENT_AT);
        assertFalse(access.bindingActive());
        assertFalse(access.classOpen());
        assertFalse(access.active());
    }

    @Test
    void studentInBindingThrowsWhenBindingMissingOrAtNull()
    {
        when(mapper.selectBindingWithClass(999L)).thenReturn(null);
        assertThrows(ServiceException.class, () -> service.studentInBinding(999L, 9001L, EVENT_AT));
        assertThrows(ServiceException.class, () -> service.studentInBinding(null, 9001L, EVENT_AT));
        assertThrows(ServiceException.class, () -> service.studentInBinding(100L, 9001L, null));
    }

    @Test
    void studentsInBindingMapsSegments()
    {
        when(mapper.selectBindingWithClass(100L)).thenReturn(binding(100L, "ACTIVE", ts(8, 0), null));
        Map<String, Object> row = new HashMap<>();
        row.put("member_id", 7L);
        row.put("teaching_class_id", 1L);
        row.put("user_id", 9001L);
        row.put("user_name", "u9001");
        row.put("nick_name", "学生一");
        row.put("joined_at", ts(7, 0));
        row.put("left_at", null);
        when(mapper.selectStudentsInBinding(eq(100L), any())).thenReturn(List.of(row));

        List<EduStudentMemberSegment> segments = service.studentsInBinding(100L, EVENT_AT);
        assertEquals(1, segments.size());
        EduStudentMemberSegment segment = segments.get(0);
        assertEquals(9001L, segment.userId());
        assertEquals("学生一", segment.nickName());
        assertEquals(Instant.parse("2026-11-20T07:00:00Z"), segment.joinedAt());
        assertNull(segment.leftAt());
    }

    @Test
    void studentBindingsAlwaysReportActiveAndEmptyForNullUser()
    {
        Map<String, Object> row = new HashMap<>();
        row.put("binding_id", 100L);
        row.put("teaching_class_id", 1L);
        row.put("course_space_id", 10L);
        row.put("teaching_class_status", "ACTIVE");
        row.put("member_id", 7L);
        row.put("joined_at", ts(7, 0));
        row.put("left_at", null);
        when(mapper.selectStudentBindings(eq(9001L), any())).thenReturn(List.of(row));

        List<EduStudentBindingAccess> bindings = service.studentBindings(9001L, EVENT_AT);
        assertEquals(1, bindings.size());
        EduStudentBindingAccess access = bindings.get(0);
        assertTrue(access.bindingActive());
        assertTrue(access.classOpen());
        assertTrue(access.active());

        assertTrue(service.studentBindings(null, EVENT_AT).isEmpty());
    }

    @Test
    void studentInBindingRejectsOverlappingMemberSegments()
    {
        when(mapper.selectBindingWithClass(100L)).thenReturn(binding(100L, "ACTIVE", ts(8, 0), null));
        when(mapper.selectStudentSegmentsInBinding(eq(100L), eq(9001L), any()))
                .thenReturn(List.of(segment(ts(7, 0), ts(12, 0)), segment(ts(8, 45), null)));

        assertThrows(ServiceException.class, () -> service.studentInBinding(100L, 9001L, EVENT_AT));
    }

    @Test
    void studentsInBindingRejectsDuplicateStudentRows()
    {
        when(mapper.selectBindingWithClass(100L)).thenReturn(binding(100L, "ACTIVE", ts(8, 0), null));
        Map<String, Object> first = new HashMap<>();
        first.put("member_id", 7L);
        first.put("teaching_class_id", 1L);
        first.put("user_id", 9001L);
        first.put("user_name", "u9001");
        first.put("nick_name", "学生一");
        first.put("joined_at", ts(7, 0));
        first.put("left_at", ts(12, 0));
        Map<String, Object> second = new HashMap<>(first);
        second.put("member_id", 8L);
        second.put("joined_at", ts(8, 45));
        second.put("left_at", null);
        when(mapper.selectStudentsInBinding(eq(100L), any())).thenReturn(List.of(first, second));

        assertThrows(ServiceException.class, () -> service.studentsInBinding(100L, EVENT_AT));
    }

    @Test
    void studentBindingsRejectsDuplicateBindingRows()
    {
        Map<String, Object> row = new HashMap<>();
        row.put("binding_id", 100L);
        row.put("teaching_class_id", 1L);
        row.put("course_space_id", 10L);
        row.put("teaching_class_status", "ACTIVE");
        row.put("member_id", 7L);
        row.put("joined_at", ts(7, 0));
        row.put("left_at", null);
        when(mapper.selectStudentBindings(eq(9001L), any())).thenReturn(List.of(row, row));

        assertThrows(ServiceException.class, () -> service.studentBindings(9001L, EVENT_AT));
    }

    @Test
    void unknownNonNullBindingEndFailsClosed()
    {
        Map<String, Object> row = binding(100L, "ACTIVE", ts(8, 0), null);
        row.put("ended_at", "2026-11-20T08:30:00Z");
        when(mapper.selectBindingWithClass(100L)).thenReturn(row);

        assertThrows(ServiceException.class, () -> service.studentInBinding(100L, 9001L, EVENT_AT));
    }

    @Test
    void unknownNonNullMembershipEndFailsClosed()
    {
        when(mapper.selectBindingWithClass(100L)).thenReturn(binding(100L, "ACTIVE", ts(8, 0), null));
        Map<String, Object> row = segment(ts(7, 0), null);
        row.put("left_at", "2026-11-20T08:30:00Z");
        when(mapper.selectStudentSegmentsInBinding(eq(100L), eq(9001L), any())).thenReturn(List.of(row));

        assertThrows(ServiceException.class, () -> service.studentInBinding(100L, 9001L, EVENT_AT));
    }
}
