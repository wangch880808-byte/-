package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
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
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;
import com.ruoyi.education.course.domain.StudentNotice;
import com.ruoyi.education.course.exception.NoticeAccessDeniedException;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.NoticeConcurrentUpdateException;
import com.ruoyi.education.course.exception.NoticeNotFoundException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;

class StudentNoticeServiceTest
{
    private static final long USER_ID = 9001L;

    private static final long BINDING_ID = 10001L;

    private static final long OTHER_BINDING_ID = 10002L;

    private static final long TEACHING_CLASS_ID = 20001L;

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 28, 12, 0, 0, 123_000_000);

    private static final LocalDateTime JOINED_AT = AT.minusDays(5);

    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-10-28T12:00:00.123Z"), ZoneOffset.UTC);

    private StudentNoticeMapper mapper;

    private StudentAnnouncementAuthorizationPort port;

    private StudentNoticeService service;

    @BeforeEach
    void setUp()
    {
        mapper = org.mockito.Mockito.mock(StudentNoticeMapper.class);
        port = org.mockito.Mockito.mock(StudentAnnouncementAuthorizationPort.class);
        service = new StudentNoticeService(mapper, port, CLOCK);
        authenticate(USER_ID);
    }

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listWithoutBindingUsesActiveBindingsAndSharedQuery()
    {
        when(port.listActiveBindings(USER_ID, AT))
                .thenReturn(List.of(access(BINDING_ID, JOINED_AT, true)));
        when(mapper.countVisible(any())).thenReturn(1L);
        when(mapper.selectVisiblePage(any())).thenReturn(List.of(notice(1L, null)));

        StudentNoticePage page = service.list(null, NoticeKind.GRADE, true, 2, 10);

        assertEquals(1, page.total());
        assertEquals(1, page.rows().size());

        StudentNoticeQuery query = captureQuery(mapper);
        assertEquals(USER_ID, query.getRecipientUserId());
        assertEquals(AT, query.getAt());
        assertEquals(NoticeKind.GRADE, query.getKind());
        assertTrue(query.isUnreadOnly());
        assertEquals(10, query.getOffset());
        assertEquals(10, query.getLimit());
        assertEquals(1, query.getBindings().size());
        assertEquals(BINDING_ID, query.getBindings().get(0).getBindingId());
        assertEquals(JOINED_AT, query.getBindings().get(0).getJoinedAt());
    }

    @Test
    void listWithExplicitBindingNonMemberReturns403()
    {
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, false));

        assertThrows(NoticeAccessDeniedException.class,
                () -> service.list(BINDING_ID, null, false, 1, 10));

        verifyNoInteractions(mapper);
    }

    @Test
    void listWithExplicitBindingNonMemberMissingJoinedAtReturns403()
    {
        when(port.resolve(BINDING_ID, USER_ID, AT)).thenReturn(new StudentAnnouncementAccess(
                BINDING_ID, TEACHING_CLASS_ID, null, null, inactiveAuthority()));

        assertThrows(NoticeAccessDeniedException.class,
                () -> service.list(BINDING_ID, null, false, 1, 10));

        verifyNoInteractions(mapper);
    }

    @Test
    void listWithoutAuthorizationPortFailsClosedWith503()
    {
        StudentNoticeService withoutPort = new StudentNoticeService(mapper, null, CLOCK);

        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> withoutPort.list(null, null, false, 1, 10));
        verifyNoInteractions(mapper);
    }

    @Test
    void listWithPortExceptionReturns503()
    {
        when(port.listActiveBindings(USER_ID, AT)).thenThrow(new RuntimeException("upstream down"));

        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> service.list(null, null, false, 1, 10));
        verifyNoInteractions(mapper);
    }

    @Test
    void listWithNullActiveBindingsReturns503()
    {
        when(port.listActiveBindings(USER_ID, AT)).thenReturn(null);

        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> service.list(null, null, false, 1, 10));
    }

    @Test
    void listWithMissingJoinedAtReturns503()
    {
        when(port.listActiveBindings(USER_ID, AT))
                .thenReturn(List.of(new StudentAnnouncementAccess(
                        BINDING_ID, TEACHING_CLASS_ID, null, null, activeAuthority())));

        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> service.list(null, null, false, 1, 10));
    }

    @Test
    void listWithNullAccessElementReturns503()
    {
        when(port.listActiveBindings(USER_ID, AT))
                .thenReturn(Arrays.asList((StudentAnnouncementAccess) null));

        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> service.list(null, null, false, 1, 10));
    }

    @Test
    void listWithDuplicateBindingDifferentWindowReturns503()
    {
        when(port.listActiveBindings(USER_ID, AT)).thenReturn(List.of(
                access(BINDING_ID, JOINED_AT, true),
                access(BINDING_ID, JOINED_AT.minusDays(1), true)));

        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> service.list(null, null, false, 1, 10));
    }

    @Test
    void listWithNoActiveBindingsReturnsEmptyWithoutMapper()
    {
        when(port.listActiveBindings(USER_ID, AT)).thenReturn(List.of());

        StudentNoticePage page = service.list(null, null, false, 1, 10);

        assertEquals(0, page.total());
        assertTrue(page.rows().isEmpty());
        verifyNoInteractions(mapper);
    }

    @Test
    void listSkipsInactiveAccessWhenBindingIsNotExplicit()
    {
        when(port.listActiveBindings(USER_ID, AT))
                .thenReturn(List.of(access(BINDING_ID, JOINED_AT, false)));

        StudentNoticePage page = service.list(null, null, false, 1, 10);

        assertEquals(0, page.total());
        verifyNoInteractions(mapper);
    }

    @Test
    void getNonOwnerReturns404WithoutAuthorizationLookup()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(null);

        assertThrows(NoticeNotFoundException.class, () -> service.get(1L));
        verifyNoInteractions(port);
    }

    @Test
    void getNoticeWithNullBindingReturns404()
    {
        StudentNotice notice = notice(1L, null);
        notice.setBindingId(null);
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice);

        assertThrows(NoticeNotFoundException.class, () -> service.get(1L));
        verifyNoInteractions(port);
    }

    @Test
    void getInactiveMembershipReturns404()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, false));

        assertThrows(NoticeNotFoundException.class, () -> service.get(1L));
        verify(mapper, never()).selectVisibleById(anyLong(), any());
    }

    @Test
    void getInactiveMembershipMissingJoinedAtReturns404()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT)).thenReturn(new StudentAnnouncementAccess(
                BINDING_ID, TEACHING_CLASS_ID, null, null, inactiveAuthority()));

        assertThrows(NoticeNotFoundException.class, () -> service.get(1L));
        verify(mapper, never()).selectVisibleById(anyLong(), any());
    }

    @Test
    void getPortFailureReturns503()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT)).thenThrow(new RuntimeException("upstream down"));

        assertThrows(NoticeAuthorizationUnavailableException.class, () -> service.get(1L));
    }

    @Test
    void getMismatchedBindingFromPortReturns503()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(OTHER_BINDING_ID, JOINED_AT, true));

        assertThrows(NoticeAuthorizationUnavailableException.class, () -> service.get(1L));
    }

    @Test
    void getLeftBeforeJoinedReturns503()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT)).thenReturn(new StudentAnnouncementAccess(
                BINDING_ID, TEACHING_CLASS_ID, JOINED_AT, JOINED_AT, activeAuthority()));

        assertThrows(NoticeAuthorizationUnavailableException.class, () -> service.get(1L));
    }

    @Test
    void getVisibleNoticeReturnsIt()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, true));
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(notice(1L, null));

        StudentNotice result = service.get(1L);

        assertEquals(1L, result.getId());
        ArgumentCaptor<StudentNoticeQuery> captor = ArgumentCaptor.forClass(StudentNoticeQuery.class);
        verify(mapper).selectVisibleById(eq(1L), captor.capture());
        StudentNoticeQuery query = captor.getValue();
        assertEquals(1, query.getBindings().size());
        assertEquals(BINDING_ID, query.getBindings().get(0).getBindingId());
    }

    @Test
    void markReadFirstTimeReturnsAlreadyReadFalse()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, true));
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(notice(1L, null));
        when(mapper.markRead(eq(1L), eq(AT), any())).thenReturn(1);

        StudentNoticeReadResult result = service.markRead(1L);

        assertEquals(1L, result.id());
        assertEquals(AT, result.readAt());
        assertFalse(result.alreadyRead());
    }

    @Test
    void markReadAlreadyReadReturnsExistingTimeWithoutUpdate()
    {
        LocalDateTime existing = AT.minusHours(1);
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, existing));
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, true));
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(notice(1L, existing));

        StudentNoticeReadResult result = service.markRead(1L);

        assertEquals(existing, result.readAt());
        assertTrue(result.alreadyRead());
        verify(mapper, never()).markRead(anyLong(), any(), any());
    }

    @Test
    void markReadAlreadyReadButNowInvisibleReturns404()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID))
                .thenReturn(notice(1L, AT.minusHours(1)));
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, true));
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(null);

        assertThrows(NoticeNotFoundException.class, () -> service.markRead(1L));
        verify(mapper, never()).markRead(anyLong(), any(), any());
    }

    @Test
    void unreadCountWithExplicitBindingNonMemberReturns403()
    {
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, false));

        assertThrows(NoticeAccessDeniedException.class, () -> service.unreadCount(BINDING_ID));
        verifyNoInteractions(mapper);
    }

    @Test
    void markReadZeroRowsCurrentReadAlreadyReadReturnsTrue()
    {
        LocalDateTime existing = AT.minusHours(1);
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, true));
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(notice(1L, null));
        when(mapper.markRead(eq(1L), eq(AT), any())).thenReturn(0);
        when(mapper.selectVisibleByIdForUpdate(eq(1L), any()))
                .thenReturn(notice(1L, existing));

        StudentNoticeReadResult result = service.markRead(1L);

        assertEquals(existing, result.readAt());
        assertTrue(result.alreadyRead());
    }

    @Test
    void markReadZeroRowsStillVisibleAndUnreadReturns409()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, true));
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(notice(1L, null));
        when(mapper.markRead(eq(1L), eq(AT), any())).thenReturn(0);
        when(mapper.selectVisibleByIdForUpdate(eq(1L), any())).thenReturn(notice(1L, null));

        assertThrows(NoticeConcurrentUpdateException.class, () -> service.markRead(1L));
    }

    @Test
    void markReadZeroRowsNowInvisibleReturns404()
    {
        when(mapper.selectByIdAndRecipient(1L, USER_ID)).thenReturn(notice(1L, null));
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, true));
        when(mapper.selectVisibleById(eq(1L), any())).thenReturn(notice(1L, null));
        when(mapper.markRead(eq(1L), eq(AT), any())).thenReturn(0);
        when(mapper.selectVisibleByIdForUpdate(eq(1L), any())).thenReturn(null);

        assertThrows(NoticeNotFoundException.class, () -> service.markRead(1L));
    }

    @Test
    void readAllMarksVisibleUnreadAndCountsRemaining()
    {
        when(port.listActiveBindings(USER_ID, AT)).thenReturn(List.of(
                access(BINDING_ID, JOINED_AT, true),
                access(OTHER_BINDING_ID, JOINED_AT, true)));
        when(mapper.markAllRead(eq(AT), any())).thenReturn(3);
        when(mapper.selectVisibleUnreadIdsForUpdate(any())).thenReturn(List.of(5L));

        StudentNoticeReadAllResult result = service.markAllRead();

        assertEquals(3, result.markedCount());
        assertEquals(1, result.unreadCount());
        assertEquals(AT, result.operatedAt());

        ArgumentCaptor<StudentNoticeQuery> captor = ArgumentCaptor.forClass(StudentNoticeQuery.class);
        verify(mapper).markAllRead(eq(AT), captor.capture());
        StudentNoticeQuery query = captor.getValue();
        assertEquals(2, query.getBindings().size());
        assertEquals(USER_ID, query.getRecipientUserId());
        assertEquals(AT, query.getAt());
        assertTrue(query.isUnreadOnly());
        verify(mapper).selectVisibleUnreadIdsForUpdate(any());
        verify(mapper, never()).countVisible(any());
    }

    @Test
    void readAllWithoutActiveBindingsReturnsZeroWithoutMapper()
    {
        when(port.listActiveBindings(USER_ID, AT)).thenReturn(List.of());

        StudentNoticeReadAllResult result = service.markAllRead();

        assertEquals(0, result.markedCount());
        assertEquals(0, result.unreadCount());
        assertEquals(AT, result.operatedAt());
        verifyNoInteractions(mapper);
    }

    @Test
    void readAllWithNoUnreadReturnsZeroSuccess()
    {
        when(port.listActiveBindings(USER_ID, AT))
                .thenReturn(List.of(access(BINDING_ID, JOINED_AT, true)));
        when(mapper.markAllRead(eq(AT), any())).thenReturn(0);
        when(mapper.selectVisibleUnreadIdsForUpdate(any())).thenReturn(List.of());

        StudentNoticeReadAllResult result = service.markAllRead();

        assertEquals(0, result.markedCount());
        assertEquals(0, result.unreadCount());
        assertEquals(AT, result.operatedAt());
    }

    @Test
    void readAllMissingJoinedAtReturns503()
    {
        when(port.listActiveBindings(USER_ID, AT))
                .thenReturn(List.of(new StudentAnnouncementAccess(
                        BINDING_ID, TEACHING_CLASS_ID, null, null, activeAuthority())));

        assertThrows(NoticeAuthorizationUnavailableException.class, () -> service.markAllRead());
        verifyNoInteractions(mapper);
    }

    @Test
    void unreadCountUsesUnreadOnlyQuery()
    {
        when(port.resolve(BINDING_ID, USER_ID, AT))
                .thenReturn(access(BINDING_ID, JOINED_AT, true));
        when(mapper.countVisible(any())).thenReturn(2L);

        StudentNoticeUnreadCountResult result = service.unreadCount(BINDING_ID);

        assertEquals(2, result.unreadCount());
        assertEquals(AT, result.asOf());

        ArgumentCaptor<StudentNoticeQuery> captor = ArgumentCaptor.forClass(StudentNoticeQuery.class);
        verify(mapper).countVisible(captor.capture());
        assertTrue(captor.getValue().isUnreadOnly());
    }

    @Test
    void unreadCountWithNullBindingUsesAllActiveBindings()
    {
        when(port.listActiveBindings(USER_ID, AT))
                .thenReturn(List.of(access(BINDING_ID, JOINED_AT, true)));
        when(mapper.countVisible(any())).thenReturn(0L);

        StudentNoticeUnreadCountResult result = service.unreadCount(null);

        assertEquals(0, result.unreadCount());
        assertEquals(AT, result.asOf());
    }

    @Test
    void timeIsTruncatedToMilliseconds()
    {
        Clock nanoClock = Clock.fixed(Instant.parse("2026-10-28T12:00:00.123456789Z"), ZoneOffset.UTC);
        StudentNoticeService nanoService = new StudentNoticeService(mapper, port, nanoClock);
        when(port.listActiveBindings(USER_ID, AT))
                .thenReturn(List.of(access(BINDING_ID, JOINED_AT, true)));
        when(mapper.countVisible(any())).thenReturn(0L);

        nanoService.list(null, null, false, 1, 10);

        StudentNoticeQuery query = captureQuery(mapper);
        assertEquals(123_000_000, query.getAt().getNano());
    }

    private StudentNoticeQuery captureQuery(StudentNoticeMapper target)
    {
        ArgumentCaptor<StudentNoticeQuery> captor = ArgumentCaptor.forClass(StudentNoticeQuery.class);
        verify(target).countVisible(captor.capture());
        return captor.getValue();
    }

    private StudentAnnouncementAccess access(long bindingId, LocalDateTime joinedAt, boolean currentStudent)
    {
        return new StudentAnnouncementAccess(bindingId, bindingId + 1000L, joinedAt, null,
                new StudentAnnouncementAuthority(currentStudent, true, true));
    }

    private StudentAnnouncementAuthority activeAuthority()
    {
        return new StudentAnnouncementAuthority(true, true, true);
    }

    private StudentAnnouncementAuthority inactiveAuthority()
    {
        return new StudentAnnouncementAuthority(false, true, true);
    }

    private StudentNotice notice(long id, LocalDateTime readAt)
    {
        StudentNotice notice = new StudentNotice();
        notice.setId(id);
        notice.setRecipientUserId(USER_ID);
        notice.setBindingId(BINDING_ID);
        notice.setNoticeKind(NoticeKind.GRADE);
        notice.setSourceId(30001L);
        notice.setTitle("title");
        notice.setVisibleAt(AT.minusHours(1));
        notice.setReadAt(readAt);
        notice.setCreatedAt(AT.minusHours(2));
        return notice;
    }

    private void authenticate(Long userId)
    {
        LoginUser loginUser = new LoginUser(userId, null, new SysUser(userId), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }
}
