package com.ruoyi.education.overview.adapter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.education.course.service.StudentNoticeService;
import com.ruoyi.education.course.service.StudentNoticeUnreadCountResult;
import com.ruoyi.education.overview.domain.NoticeSummary;
import com.ruoyi.education.overview.exception.OverviewAccessDeniedException;

/**
 * 通知未读汇总生产适配测试。
 *
 * <p>来源服务自身的学生授权、可见性和时间语义由 6-02 测试覆盖；本测试只验证
 * 适配器复核当前登录人、把 6-02 结果映射为端口记录，以及教师来源不可用时
 * 明确失败而不是返回假零。</p>
 */
class NoticeSummaryPortAdapterTest
{
    private static final long USER_ID = 930000000101L;

    private static final LocalDateTime AS_OF = LocalDateTime.of(2026, 11, 28, 3, 0, 0);

    private StudentNoticeService studentNoticeService;

    private NoticeSummaryPortAdapter adapter;

    @BeforeEach
    void setUp()
    {
        studentNoticeService = mock(StudentNoticeService.class);
        adapter = new NoticeSummaryPortAdapter(studentNoticeService);
        authenticate(USER_ID, "student");
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void mapsStudentUnreadCountAndUtcAsOf()
    {
        when(studentNoticeService.unreadCount(null))
                .thenReturn(new StudentNoticeUnreadCountResult(3L, AS_OF));

        Map<Long, NoticeSummary> result = adapter.findUnreadSummaries(List.of(USER_ID));

        assertEquals(Set.of(USER_ID), result.keySet());
        NoticeSummary summary = result.get(USER_ID);
        assertEquals(USER_ID, summary.recipientUserId());
        assertEquals(3L, summary.unreadCount());
        assertEquals(InstantHolder.AS_OF, summary.asOf());
        verify(studentNoticeService).unreadCount(null);
    }

    @Test
    void rejectsRequestForAnotherRecipient()
    {
        assertThrows(OverviewAccessDeniedException.class,
                () -> adapter.findUnreadSummaries(List.of(USER_ID + 1L)));
    }

    @Test
    void rejectsEmptyRecipients()
    {
        assertThrows(Exception.class,
                () -> adapter.findUnreadSummaries(Collections.emptyList()));
    }

    @Test
    void teacherNoticeSourceIsUnavailableInsteadOfFakeZero()
    {
        authenticate(USER_ID, "teacher");

        assertThrows(UnsupportedOperationException.class,
                () -> adapter.findUnreadSummaries(List.of(USER_ID)));
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

    /** 避免在测试中散落时区转换：6-02 的 asOf 是 UTC DATETIME。 */
    private static final class InstantHolder
    {
        private static final java.time.Instant AS_OF =
                LocalDateTime.of(2026, 11, 28, 3, 0, 0)
                        .toInstant(java.time.ZoneOffset.UTC);
    }
}
