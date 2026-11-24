package com.ruoyi.education.course.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.course.api.EduStudentMemberSegment;

/**
 * 学生通知授权适配器的隔离测试。
 *
 * <p>只使用 Mockito 替身验证 #39 时点快照到授权端口的映射，不连接数据库、
 * 不启动 Spring 上下文。重点覆盖：UTC 时间转换、绑定/成员段映射、教学班
 * status 只取当前值、空结果正常、跨绑定/不完整快照失败关闭。</p>
 */
class MembershipTimelineStudentAnnouncementAuthorizationAdapterTest
{
    private static final Long BINDING_ID = 10L;

    private static final Long CLASS_ID = 20L;

    private static final Long COURSE_ID = 30L;

    private static final Long USER_ID = 7L;

    private static final Instant JOINED = Instant.parse("2026-11-01T00:00:00Z");

    private static final Instant LEFT = Instant.parse("2026-12-01T00:00:00Z");

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 24, 9, 0);

    private static final Instant POINT = AT.toInstant(ZoneOffset.UTC);

    private EduMembershipTimelineService timelineService;

    private MembershipTimelineStudentAnnouncementAuthorizationAdapter adapter;

    @BeforeEach
    void setUp()
    {
        timelineService = mock(EduMembershipTimelineService.class);
        adapter = new MembershipTimelineStudentAnnouncementAuthorizationAdapter(timelineService);
    }

    @Test
    void resolveMapsActiveSnapshotAndUtcWindow()
    {
        when(timelineService.studentInBinding(BINDING_ID, USER_ID, POINT))
                .thenReturn(binding(true, true, true, JOINED, null));

        StudentAnnouncementAccess access = adapter.resolve(BINDING_ID, USER_ID, AT);

        assertEquals(BINDING_ID, access.bindingId());
        assertEquals(CLASS_ID, access.teachingClassId());
        assertEquals(LocalDateTime.ofInstant(JOINED, ZoneOffset.UTC), access.joinedAt());
        assertNull(access.leftAt());
        assertTrue(access.authority().currentStudent());
        assertTrue(access.authority().bindingActive());
        assertTrue(access.authority().teachingClassActive());
        assertTrue(access.authority().active());
        verify(timelineService).studentInBinding(BINDING_ID, USER_ID, POINT);
    }

    @Test
    void resolveKeepsCurrentClassStatusOnly()
    {
        when(timelineService.studentInBinding(BINDING_ID, USER_ID, POINT))
                .thenReturn(binding(true, true, false, JOINED, null));

        StudentAnnouncementAccess access = adapter.resolve(BINDING_ID, USER_ID, AT);

        assertTrue(access.authority().currentStudent());
        assertTrue(access.authority().bindingActive());
        assertFalse(access.authority().teachingClassActive());
        assertFalse(access.authority().active());
    }

    @Test
    void resolveMapsInactiveMemberWithoutWindow()
    {
        when(timelineService.studentInBinding(BINDING_ID, USER_ID, POINT))
                .thenReturn(binding(false, true, true, null, null));

        StudentAnnouncementAccess access = adapter.resolve(BINDING_ID, USER_ID, AT);

        assertFalse(access.authority().currentStudent());
        assertFalse(access.authority().active());
        assertNull(access.joinedAt());
        assertNull(access.leftAt());
    }

    @Test
    void resolveRejectsBindingMismatch()
    {
        when(timelineService.studentInBinding(BINDING_ID, USER_ID, POINT))
                .thenReturn(new EduStudentBindingAccess(11L, CLASS_ID, COURSE_ID, USER_ID,
                        true, true, true, JOINED, null));

        assertThrows(IllegalStateException.class, () -> adapter.resolve(BINDING_ID, USER_ID, AT));
    }

    @Test
    void resolveRejectsActiveSnapshotWithoutJoinedAt()
    {
        when(timelineService.studentInBinding(BINDING_ID, USER_ID, POINT))
                .thenReturn(binding(true, true, true, null, null));

        assertThrows(IllegalStateException.class, () -> adapter.resolve(BINDING_ID, USER_ID, AT));
    }

    @Test
    void listActiveBindingsMapsAllAndKeepsClassStatus()
    {
        when(timelineService.studentBindings(USER_ID, POINT)).thenReturn(List.of(
                binding(true, true, true, JOINED, null),
                new EduStudentBindingAccess(12L, 21L, COURSE_ID, USER_ID, true, false, true,
                        JOINED, LEFT)));

        List<StudentAnnouncementAccess> accesses = adapter.listActiveBindings(USER_ID, AT);

        assertEquals(2, accesses.size());
        assertTrue(accesses.get(0).authority().active());
        assertFalse(accesses.get(1).authority().teachingClassActive());
        assertFalse(accesses.get(1).authority().active());
        assertEquals(LocalDateTime.ofInstant(LEFT, ZoneOffset.UTC), accesses.get(1).leftAt());
        verify(timelineService).studentBindings(USER_ID, POINT);
    }

    @Test
    void listActiveBindingsRejectsNullResult()
    {
        when(timelineService.studentBindings(USER_ID, POINT)).thenReturn(null);

        assertThrows(IllegalStateException.class, () -> adapter.listActiveBindings(USER_ID, AT));
    }

    @Test
    void listActiveStudentsMapsMembersWithOneCurrentClassProbe()
    {
        EduStudentMemberSegment first = member(101L, USER_ID, JOINED, null);
        EduStudentMemberSegment second = member(102L, 8L, JOINED, LEFT);
        when(timelineService.studentsInBinding(BINDING_ID, POINT))
                .thenReturn(List.of(first, second));
        when(timelineService.studentInBinding(BINDING_ID, USER_ID, POINT))
                .thenReturn(binding(true, true, false, JOINED, null));

        List<StudentAnnouncementMember> members = adapter.listActiveStudents(BINDING_ID, AT);

        assertEquals(2, members.size());
        assertFalse(members.get(0).authority().teachingClassActive());
        assertFalse(members.get(0).authority().active());
        assertFalse(members.get(1).authority().active());
        assertEquals(BINDING_ID, members.get(1).bindingId());
        assertEquals(CLASS_ID, members.get(1).teachingClassId());
        assertEquals(LocalDateTime.ofInstant(LEFT, ZoneOffset.UTC), members.get(1).leftAt());
        verify(timelineService).studentsInBinding(BINDING_ID, POINT);
        verify(timelineService).studentInBinding(BINDING_ID, USER_ID, POINT);
    }

    @Test
    void listActiveStudentsEmptyDoesNotProbeClassStatus()
    {
        when(timelineService.studentsInBinding(BINDING_ID, POINT)).thenReturn(List.of());

        assertTrue(adapter.listActiveStudents(BINDING_ID, AT).isEmpty());
        verify(timelineService).studentsInBinding(BINDING_ID, POINT);
        verify(timelineService, never()).studentInBinding(anyLong(), anyLong(), any());
    }

    @Test
    void listActiveStudentsRejectsInconsistentBinding()
    {
        when(timelineService.studentsInBinding(BINDING_ID, POINT))
                .thenReturn(List.of(member(101L, USER_ID, JOINED, null)));
        when(timelineService.studentInBinding(BINDING_ID, USER_ID, POINT))
                .thenReturn(binding(true, false, true, JOINED, null));

        assertThrows(IllegalStateException.class,
                () -> adapter.listActiveStudents(BINDING_ID, AT));
    }

    @Test
    void listActiveStudentsRejectsClassMismatch()
    {
        when(timelineService.studentsInBinding(BINDING_ID, POINT))
                .thenReturn(List.of(member(101L, USER_ID, JOINED, null)));
        when(timelineService.studentInBinding(BINDING_ID, USER_ID, POINT))
                .thenReturn(new EduStudentBindingAccess(BINDING_ID, 99L, COURSE_ID, USER_ID,
                        true, true, true, JOINED, null));

        assertThrows(IllegalStateException.class,
                () -> adapter.listActiveStudents(BINDING_ID, AT));
    }

    @Test
    void invalidIdsFailBeforeTimelineCall()
    {
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(0L, USER_ID, AT));
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(BINDING_ID, 0L, AT));
        assertThrows(IllegalArgumentException.class, () -> adapter.listActiveBindings(0L, AT));
        assertThrows(IllegalArgumentException.class, () -> adapter.listActiveStudents(0L, AT));
        verify(timelineService, never()).studentInBinding(anyLong(), anyLong(), any());
        verify(timelineService, never()).studentBindings(anyLong(), any());
        verify(timelineService, never()).studentsInBinding(anyLong(), any());
    }

    private static EduStudentBindingAccess binding(boolean active, boolean bindingActive,
            boolean classOpen, Instant joinedAt, Instant leftAt)
    {
        return new EduStudentBindingAccess(BINDING_ID, CLASS_ID, COURSE_ID, USER_ID,
                bindingActive, classOpen, active, joinedAt, leftAt);
    }

    private static EduStudentMemberSegment member(Long memberId, Long userId, Instant joinedAt,
            Instant leftAt)
    {
        return new EduStudentMemberSegment(memberId, CLASS_ID, userId, "student" + userId,
                "学生" + userId, joinedAt, leftAt);
    }
}
