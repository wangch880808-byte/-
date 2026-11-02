package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.education.course.authorization.StudentAnnouncementAccess;
import com.ruoyi.education.course.authorization.StudentAnnouncementAuthorizationPort;
import com.ruoyi.education.course.authorization.StudentAnnouncementMember;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;
import com.ruoyi.education.course.exception.AnnouncementAccessDeniedException;
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.AnnouncementNotFoundException;
import com.ruoyi.education.course.exception.AnnouncementValidationException;
import com.ruoyi.education.course.mapper.CourseAnnouncementMapper;

class StudentAnnouncementServiceTest
{
    private static final Long STUDENT_ID = 930000000001L;
    private static final Long BINDING_ID = 100L;
    private static final Long OTHER_BINDING_ID = 200L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);

    private CourseAnnouncementMapper mapper;

    private FakeStudentAuthorizationPort authorizationPort;

    private StudentAnnouncementService service;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.clearContext();
        authenticate(STUDENT_ID);
        mapper = mock(CourseAnnouncementMapper.class);
        authorizationPort = new FakeStudentAuthorizationPort();
        service = new StudentAnnouncementService(mapper, authorizationPort,
                Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listWithBindingIdQueriesOnlyThatBindingAndUsesOneTime()
    {
        authorizationPort.allow(BINDING_ID);
        when(mapper.countVisibleByBindingIds(anyList(), any())).thenReturn(1L);
        when(mapper.selectVisibleByBindingIdsPage(anyList(), any(), eq(0L), eq(10)))
                .thenReturn(List.of(row(1L, BINDING_ID)));

        CourseAnnouncementPage page = service.list(BINDING_ID, 1, 10);

        assertEquals(1, page.total());
        assertEquals(List.of(1L), page.rows().stream().map(CourseAnnouncement::getId).toList());
        assertEquals(STUDENT_ID, authorizationPort.lastUserId);

        ArgumentCaptor<List<Long>> ids = listCaptor();
        ArgumentCaptor<LocalDateTime> at = timeCaptor();
        verify(mapper).countVisibleByBindingIds(ids.capture(), at.capture());
        assertEquals(List.of(BINDING_ID), ids.getValue());
        assertEquals(authorizationPort.lastAt, at.getValue());

        ArgumentCaptor<List<Long>> pageIds = listCaptor();
        verify(mapper).selectVisibleByBindingIdsPage(pageIds.capture(), eq(at.getValue()), eq(0L), eq(10));
        assertEquals(List.of(BINDING_ID), pageIds.getValue());
    }

    @Test
    void listWithoutBindingIdDeduplicatesActiveBindings()
    {
        StudentAnnouncementAccess first = access(BINDING_ID, true);
        StudentAnnouncementAccess duplicate = access(BINDING_ID, true);
        StudentAnnouncementAccess second = access(OTHER_BINDING_ID, true);
        authorizationPort.setActive(List.of(first, duplicate, second));
        when(mapper.countVisibleByBindingIds(anyList(), any())).thenReturn(2L);
        when(mapper.selectVisibleByBindingIdsPage(anyList(), any(), eq(0L), eq(10)))
                .thenReturn(List.of(row(1L, BINDING_ID), row(2L, OTHER_BINDING_ID)));

        CourseAnnouncementPage page = service.list(null, 1, 10);

        assertEquals(2, page.total());
        ArgumentCaptor<List<Long>> ids = listCaptor();
        verify(mapper).countVisibleByBindingIds(ids.capture(), any());
        assertEquals(List.of(BINDING_ID, OTHER_BINDING_ID), ids.getValue());

        ArgumentCaptor<List<Long>> pageIds = listCaptor();
        verify(mapper).selectVisibleByBindingIdsPage(pageIds.capture(), any(), eq(0L), eq(10));
        assertEquals(List.of(BINDING_ID, OTHER_BINDING_ID), pageIds.getValue());
    }

    @Test
    void listWithoutBindingIdAndNoActiveClassesReturnsEmptyWithoutMapper()
    {
        authorizationPort.setActive(List.of());

        CourseAnnouncementPage page = service.list(null, 1, 10);

        assertEquals(0, page.total());
        assertEquals(List.of(), page.rows());
        verifyNoInteractions(mapper);
    }

    @Test
    void listWithoutBindingIdNullAuthorizationResultReturns503()
    {
        authorizationPort.setNullActiveList(true);

        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> service.list(null, 1, 10));
        verifyNoInteractions(mapper);
    }

    @Test
    void listWithoutBindingIdIncompleteAuthorizationResultReturns503()
    {
        authorizationPort.setActive(List.of(
                new StudentAnnouncementAccess(null, 1001L, activeAuthority())));

        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> service.list(null, 1, 10));
        verifyNoInteractions(mapper);
    }

    @Test
    void listWithoutBindingIdNonPositiveIdsReturns503()
    {
        authorizationPort.setActive(List.of(
                new StudentAnnouncementAccess(0L, 1001L, activeAuthority())));
        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> service.list(null, 1, 10));

        authorizationPort.setActive(List.of(
                new StudentAnnouncementAccess(100L, 0L, activeAuthority())));
        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> service.list(null, 1, 10));
        verifyNoInteractions(mapper);
    }

    @Test
    void authorizationFailureFailsClosed()
    {
        authorizationPort.setFail(true);
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(row(1L, BINDING_ID));

        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> service.list(BINDING_ID, 1, 10));
        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> service.list(null, 1, 10));
        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> service.get(1L));
    }

    @Test
    void listWithBindingIdAndInactiveMembershipReturns403()
    {
        authorizationPort.setResolved(access(BINDING_ID, false));

        assertThrows(AnnouncementAccessDeniedException.class,
                () -> service.list(BINDING_ID, 1, 10));
        verifyNoInteractions(mapper);
    }

    @Test
    void missingAuthorizationPortFailsClosed()
    {
        StudentAnnouncementService unconfigured = new StudentAnnouncementService(mapper, null,
                Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(row(1L, BINDING_ID));

        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> unconfigured.list(BINDING_ID, 1, 10));
        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> unconfigured.get(1L));
    }

    @Test
    void getUsesSameRoundedTimeForVisibilityAndMembership()
    {
        StudentAnnouncementService subMillisecondService = new StudentAnnouncementService(
                mapper, authorizationPort,
                Clock.fixed(Instant.parse("2026-10-24T12:00:00.000600Z"), ZoneOffset.UTC));
        authorizationPort.allow(BINDING_ID);
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(row(1L, BINDING_ID));

        subMillisecondService.get(1L);

        LocalDateTime expected = LocalDateTime.of(2026, 10, 24, 12, 0, 0, 1_000_000);
        assertEquals(expected, authorizationPort.lastAt);
        verify(mapper).selectVisibleById(eq(1L), eq(expected));
    }

    @Test
    void getNonVisibleRowReturnedByMapperReturns404()
    {
        authorizationPort.allow(BINDING_ID);
        CourseAnnouncement draft = row(1L, BINDING_ID);
        draft.setStatus(AnnouncementStatus.DRAFT);
        draft.setOpensAt(null);
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(draft);

        assertThrows(AnnouncementNotFoundException.class, () -> service.get(1L));
    }

    @Test
    void getAuthorizationExceptionFailsClosed()
    {
        authorizationPort.setResolveFailure(new AnnouncementAccessDeniedException());
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(row(1L, BINDING_ID));

        assertThrows(AnnouncementAuthorizationUnavailableException.class, () -> service.get(1L));
    }

    @Test
    void getVisibleAnnouncementForNonMemberReturns404()
    {
        authorizationPort.setResolved(access(BINDING_ID, false));
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(row(1L, BINDING_ID));

        assertThrows(AnnouncementNotFoundException.class, () -> service.get(1L));
    }

    @Test
    void getMissingAnnouncementReturns404()
    {
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(null);

        assertThrows(AnnouncementNotFoundException.class, () -> service.get(1L));
    }

    @Test
    void invalidPageReturns400()
    {
        assertThrows(AnnouncementValidationException.class, () -> service.list(BINDING_ID, 0, 10));
        assertThrows(AnnouncementValidationException.class, () -> service.list(BINDING_ID, 1, 0));
        assertThrows(AnnouncementValidationException.class, () -> service.list(BINDING_ID, 1, 101));
    }

    private CourseAnnouncement row(long id, long bindingId)
    {
        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setId(id);
        announcement.setBindingId(bindingId);
        announcement.setTitle("title");
        announcement.setBody(null);
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setOpensAt(NOW.minusDays(1));
        announcement.setCreatedAt(NOW.minusDays(2));
        return announcement;
    }

    private StudentAnnouncementAccess access(long bindingId, boolean active)
    {
        StudentAnnouncementAuthority authority = active
                ? activeAuthority() : new StudentAnnouncementAuthority(false, true, true);
        return new StudentAnnouncementAccess(bindingId, bindingId + 1000L, authority);
    }

    private StudentAnnouncementAuthority activeAuthority()
    {
        return new StudentAnnouncementAuthority(true, true, true);
    }

    @SuppressWarnings("unchecked")
    private ArgumentCaptor<List<Long>> listCaptor()
    {
        return ArgumentCaptor.forClass(List.class);
    }

    private ArgumentCaptor<LocalDateTime> timeCaptor()
    {
        return ArgumentCaptor.forClass(LocalDateTime.class);
    }

    private void authenticate(Long userId)
    {
        LoginUser loginUser = new LoginUser(userId, null, new SysUser(userId), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    static class FakeStudentAuthorizationPort implements StudentAnnouncementAuthorizationPort
    {
        private final Map<Long, StudentAnnouncementAccess> resolved = new HashMap<>();

        private List<StudentAnnouncementAccess> active = new ArrayList<>();

        private boolean nullActiveList;

        private boolean fail;

        private RuntimeException resolveFailure;

        private Long lastUserId;

        private LocalDateTime lastAt;

        void allow(Long bindingId)
        {
            resolved.put(bindingId, new StudentAnnouncementAccess(
                    bindingId, bindingId + 1000L, activeAuthorityStatic()));
        }

        void setResolved(StudentAnnouncementAccess access)
        {
            resolved.put(access.bindingId(), access);
        }

        void setActive(List<StudentAnnouncementAccess> access)
        {
            active = new ArrayList<>(access);
        }

        void setNullActiveList(boolean nullActiveList)
        {
            this.nullActiveList = nullActiveList;
        }

        void setFail(boolean fail)
        {
            this.fail = fail;
        }

        void setResolveFailure(RuntimeException resolveFailure)
        {
            this.resolveFailure = resolveFailure;
        }

        @Override
        public StudentAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at)
        {
            lastUserId = userId;
            lastAt = at;
            if (resolveFailure != null)
            {
                throw resolveFailure;
            }
            if (fail)
            {
                throw new IllegalStateException("authorization unavailable");
            }
            return resolved.get(bindingId);
        }

        @Override
        public List<StudentAnnouncementAccess> listActiveBindings(Long userId, LocalDateTime at)
        {
            lastUserId = userId;
            lastAt = at;
            if (fail)
            {
                throw new IllegalStateException("authorization unavailable");
            }
            return nullActiveList ? null : active;
        }

        @Override
        public List<StudentAnnouncementMember> listActiveStudents(Long bindingId, LocalDateTime at)
        {
            // 公告测试不触发 fan-out；fan-out 使用独立的成员测试替身。
            return List.of();
        }

        private static StudentAnnouncementAuthority activeAuthorityStatic()
        {
            return new StudentAnnouncementAuthority(true, true, true);
        }
    }
}
