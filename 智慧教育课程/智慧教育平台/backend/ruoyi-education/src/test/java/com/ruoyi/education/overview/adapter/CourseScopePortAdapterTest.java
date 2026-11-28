package com.ruoyi.education.overview.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.concurrent.DelegatingSecurityContextExecutor;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.education.course.api.EduClassBindingRef;
import com.ruoyi.education.course.api.EduClassRef;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.course.api.EduMemberRef;
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.overview.domain.CourseScope;
import com.ruoyi.education.overview.dto.OverviewStatus;
import com.ruoyi.education.overview.exception.OverviewAccessDeniedException;
import com.ruoyi.education.overview.exception.OverviewValidationException;
import com.ruoyi.education.overview.support.OverviewSourceGuard;
import com.ruoyi.education.overview.support.OverviewSourceResult;

/**
 * 课程范围生产适配测试。
 *
 * <p>只验证适配器对第 2 组公开契约的映射、身份复核和三态输入；不连接数据库，
 * 不宣称真实课程数据已经联调。</p>
 */
class CourseScopePortAdapterTest
{
    private static final long USER_ID = 930000000001L;

    private static final Instant NOW = Instant.parse("2026-11-28T03:00:00Z");

    private EduCourseAccessService courseAccess;

    private EduMembershipTimelineService membershipTimeline;

    private CourseScopePortAdapter adapter;

    @BeforeEach
    void setUp()
    {
        courseAccess = mock(EduCourseAccessService.class);
        membershipTimeline = mock(EduMembershipTimelineService.class);
        adapter = new CourseScopePortAdapter(courseAccess, membershipTimeline,
                Clock.fixed(NOW, ZoneOffset.UTC));
        authenticate(USER_ID, "teacher");
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherScopesMapBindingsAndActiveMemberCounts()
    {
        EduClassRef classA = new EduClassRef(11L, "演示班 A", "A", 1L, "学期",
                9001L, "ACTIVE", "FORMAL");
        EduClassRef classB = new EduClassRef(12L, "演示班 B", "B", 1L, "学期",
                9001L, "ACTIVE", "FORMAL");
        when(courseAccess.classesOfTeacher(USER_ID)).thenReturn(List.of(classA, classB));
        when(courseAccess.activeBindings(11L)).thenReturn(List.of(
                new EduClassBindingRef(501L, 11L, 701L, "2026-11-01")));
        when(courseAccess.activeBindings(12L)).thenReturn(List.of(
                new EduClassBindingRef(502L, 12L, 702L, "2026-11-01")));
        when(courseAccess.activeMembers(11L)).thenReturn(List.of(
                member(11L, 101L), member(11L, 102L), member(11L, 103L)));
        when(courseAccess.activeMembers(12L)).thenReturn(List.of(member(12L, 201L)));

        Map<Long, List<CourseScope>> result = adapter.findTeacherScopes(List.of(USER_ID));

        assertEquals(Set.of(USER_ID), result.keySet());
        List<CourseScope> scopes = result.get(USER_ID);
        assertEquals(2, scopes.size());
        assertEquals(11L, scopes.get(0).teachingClassId());
        assertEquals(501L, scopes.get(0).bindingId());
        assertEquals(701L, scopes.get(0).courseSpaceId());
        assertEquals(9001L, scopes.get(0).organizationId());
        assertEquals("演示班 A", scopes.get(0).className());
        assertEquals(3L, scopes.get(0).studentCount());
        assertEquals(1L, scopes.get(1).studentCount());
        verify(courseAccess).classesOfTeacher(USER_ID);
        verify(courseAccess).activeMembers(11L);
        verify(courseAccess).activeMembers(12L);
    }

    @Test
    void teacherClassWithoutBindingStillCountsAsScope()
    {
        EduClassRef classA = new EduClassRef(11L, "未绑定班", "A", 1L, "学期",
                9001L, "ACTIVE", "FORMAL");
        when(courseAccess.classesOfTeacher(USER_ID)).thenReturn(List.of(classA));
        when(courseAccess.activeBindings(11L)).thenReturn(Collections.emptyList());
        when(courseAccess.activeMembers(11L)).thenReturn(List.of(member(11L, 101L)));

        List<CourseScope> scopes = adapter.findTeacherScopes(List.of(USER_ID)).get(USER_ID);

        assertEquals(1, scopes.size());
        assertNull(scopes.get(0).bindingId());
        assertNull(scopes.get(0).courseSpaceId());
        assertEquals(1L, scopes.get(0).studentCount());
    }

    @Test
    void studentScopesUseActiveTimelineBindingsOnly()
    {
        authenticate(USER_ID, "student");
        EduClassRef classA = new EduClassRef(11L, "演示班 A", "A", 1L, "学期",
                9001L, "ACTIVE", "FORMAL");
        when(courseAccess.classesOfStudent(USER_ID)).thenReturn(List.of(classA));
        when(membershipTimeline.studentBindings(USER_ID, NOW)).thenReturn(List.of(
                access(501L, 11L, 701L, true, true),
                access(502L, 11L, 702L, true, false),
                access(503L, 11L, 703L, false, true)));

        List<CourseScope> scopes = adapter.findStudentScopes(List.of(USER_ID)).get(USER_ID);

        assertEquals(1, scopes.size());
        assertEquals(501L, scopes.get(0).bindingId());
        assertEquals(701L, scopes.get(0).courseSpaceId());
        assertEquals(9001L, scopes.get(0).organizationId());
        assertNull(scopes.get(0).studentCount());
    }

    @Test
    void studentClassWithoutActiveBindingStillCounts()
    {
        authenticate(USER_ID, "student");
        when(courseAccess.classesOfStudent(USER_ID)).thenReturn(List.of(
                new EduClassRef(11L, "演示班 A", "A", 1L, "学期", 9001L, "ACTIVE", "FORMAL")));
        when(membershipTimeline.studentBindings(USER_ID, NOW)).thenReturn(Collections.emptyList());

        List<CourseScope> scopes = adapter.findStudentScopes(List.of(USER_ID)).get(USER_ID);

        assertEquals(1, scopes.size());
        assertNull(scopes.get(0).bindingId());
    }

    @Test
    void studentBindingsFromAnotherUserFailClosed()
    {
        authenticate(USER_ID, "student");
        when(courseAccess.classesOfStudent(USER_ID)).thenReturn(List.of(
                new EduClassRef(11L, "演示班 A", "A", 1L, "学期", 9001L, "ACTIVE", "FORMAL")));
        when(membershipTimeline.studentBindings(USER_ID, NOW)).thenReturn(List.of(
                accessForUser(501L, 11L, 701L, USER_ID + 1L, true, true)));

        assertThrows(IllegalStateException.class,
                () -> adapter.findStudentScopes(List.of(USER_ID)));
    }

    @Test
    void nullStudentBindingElementFailsClosed()
    {
        authenticate(USER_ID, "student");
        when(courseAccess.classesOfStudent(USER_ID)).thenReturn(List.of(
                new EduClassRef(11L, "演示班 A", "A", 1L, "学期", 9001L, "ACTIVE", "FORMAL")));
        when(membershipTimeline.studentBindings(USER_ID, NOW)).thenReturn(Arrays.asList(
                access(501L, 11L, 701L, true, true), null));

        assertThrows(IllegalStateException.class,
                () -> adapter.findStudentScopes(List.of(USER_ID)));
    }

    @Test
    void activeMembersFromAnotherClassFailClosed()
    {
        when(courseAccess.classesOfTeacher(USER_ID)).thenReturn(List.of(
                new EduClassRef(11L, "演示班 A", "A", 1L, "学期", 9001L, "ACTIVE", "FORMAL")));
        when(courseAccess.activeBindings(11L)).thenReturn(Collections.emptyList());
        when(courseAccess.activeMembers(11L)).thenReturn(List.of(member(12L, 101L)));

        assertThrows(IllegalStateException.class,
                () -> adapter.findTeacherScopes(List.of(USER_ID)));
    }

    @Test
    void nullActiveMemberFailsClosed()
    {
        when(courseAccess.classesOfTeacher(USER_ID)).thenReturn(List.of(
                new EduClassRef(11L, "演示班 A", "A", 1L, "学期", 9001L, "ACTIVE", "FORMAL")));
        when(courseAccess.activeBindings(11L)).thenReturn(Collections.emptyList());
        when(courseAccess.activeMembers(11L)).thenReturn(Arrays.asList(member(11L, 101L), null));

        assertThrows(IllegalStateException.class,
                () -> adapter.findTeacherScopes(List.of(USER_ID)));
    }

    @Test
    void duplicateActiveMemberFailsClosed()
    {
        when(courseAccess.classesOfTeacher(USER_ID)).thenReturn(List.of(
                new EduClassRef(11L, "演示班 A", "A", 1L, "学期", 9001L, "ACTIVE", "FORMAL")));
        when(courseAccess.activeBindings(11L)).thenReturn(Collections.emptyList());
        when(courseAccess.activeMembers(11L)).thenReturn(List.of(
                memberWithIds(11L, 900L, 101L), memberWithIds(11L, 901L, 101L)));

        assertThrows(IllegalStateException.class,
                () -> adapter.findTeacherScopes(List.of(USER_ID)));
    }

    @Test
    void sourceViolationBecomesUnavailableInsteadOfNormalNumbers()
    {
        when(courseAccess.classesOfTeacher(USER_ID)).thenReturn(List.of(
                new EduClassRef(11L, "演示班 A", "A", 1L, "学期", 9001L, "ACTIVE", "FORMAL")));
        when(courseAccess.activeBindings(11L)).thenReturn(Collections.emptyList());
        when(courseAccess.activeMembers(11L)).thenReturn(List.of(member(12L, 101L)));

        ExecutorService executor = Executors.newSingleThreadExecutor();
        try
        {
            OverviewSourceGuard guard = new OverviewSourceGuard(
                    new DelegatingSecurityContextExecutor(executor), Duration.ofSeconds(1));
            OverviewSourceResult<Map<Long, List<CourseScope>>> result = guard.query("course",
                    () -> adapter.findTeacherScopes(List.of(USER_ID)), value -> false);

            assertEquals(OverviewStatus.UNAVAILABLE, result.status());
            assertNull(result.value());
        }
        finally
        {
            executor.shutdownNow();
        }
    }

    @Test
    void teacherClassWithZeroMembersKeepsRealZero()
    {
        when(courseAccess.classesOfTeacher(USER_ID)).thenReturn(List.of(
                new EduClassRef(11L, "空班", "A", 1L, "学期", 9001L, "ACTIVE", "FORMAL")));
        when(courseAccess.activeBindings(11L)).thenReturn(Collections.emptyList());
        when(courseAccess.activeMembers(11L)).thenReturn(Collections.emptyList());

        List<CourseScope> scopes = adapter.findTeacherScopes(List.of(USER_ID)).get(USER_ID);

        assertEquals(1, scopes.size());
        assertEquals(0L, scopes.get(0).studentCount());
    }

    @Test
    void rejectsRequestForAnotherUser()
    {
        assertThrows(OverviewAccessDeniedException.class,
                () -> adapter.findTeacherScopes(List.of(USER_ID + 1L)));
    }

    @Test
    void rejectsEmptyUserIds()
    {
        assertThrows(OverviewValidationException.class,
                () -> adapter.findTeacherScopes(Collections.emptyList()));
    }

    @Test
    void adminOrganizationScopeIsNotConnectedYet()
    {
        assertThrows(UnsupportedOperationException.class,
                () -> adapter.findOrganizationScopes(9001L));
    }

    private void authenticate(long userId, String roleKey)
    {
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        role.setStatus("0");
        SysUser user = new SysUser(userId);
        user.setRoles(List.of(role));
        LoginUser loginUser = new LoginUser(userId, 9001L, user, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    private static EduMemberRef member(long classId, long userId)
    {
        return memberWithIds(classId, userId, userId);
    }

    private static EduMemberRef memberWithIds(long classId, long memberId, long userId)
    {
        return new EduMemberRef(classId, memberId, userId, "s" + userId,
                "学生" + userId, "2026-11-01");
    }

    private static EduStudentBindingAccess access(long bindingId, long classId,
            long courseSpaceId, boolean active, boolean classOpen)
    {
        return accessForUser(bindingId, classId, courseSpaceId, USER_ID, active, classOpen);
    }

    private static EduStudentBindingAccess accessForUser(long bindingId, long classId,
            long courseSpaceId, long userId, boolean active, boolean classOpen)
    {
        return new EduStudentBindingAccess(bindingId, classId, courseSpaceId, userId,
                true, classOpen, active, NOW, null);
    }
}
