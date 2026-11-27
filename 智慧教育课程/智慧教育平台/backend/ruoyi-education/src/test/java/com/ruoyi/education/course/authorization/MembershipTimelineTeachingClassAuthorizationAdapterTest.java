package com.ruoyi.education.course.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.course.api.EduStudentMemberSegment;
import com.ruoyi.education.course.api.EduTeacherBindingAccess;
import com.ruoyi.education.course.domain.TeacherAnnouncementAuthority;

/**
 * 教师公告授权适配器的消费端测试。
 *
 * <p>来源服务使用手工测试替身，不连接第 2 组 Mapper、数据库或 HTTP；
 * 重点验证字段/时间校验、权限组合保留和失败关闭语义。</p>
 */
class MembershipTimelineTeachingClassAuthorizationAdapterTest
{
    private static final Long BINDING_ID = 100L;

    private static final Long USER_ID = 8001L;

    private static final Long CLASS_ID = 1001L;

    private static final Long SPACE_ID = 2001L;

    private static final Instant PUBLISHED_AT = Instant.parse("2026-11-01T00:00:00Z");

    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 27, 12, 0);

    private static final Instant AT_INSTANT = AT.toInstant(ZoneOffset.UTC);

    private RecordingTimelineService timeline;

    private MembershipTimelineTeachingClassAuthorizationAdapter adapter;

    @BeforeEach
    void setUp()
    {
        timeline = new RecordingTimelineService();
        StaticListableBeanFactory beanFactory = new StaticListableBeanFactory();
        beanFactory.addBean("timelineService", timeline);
        adapter = new MembershipTimelineTeachingClassAuthorizationAdapter(
                beanFactory.getBeanProvider(EduMembershipTimelineService.class));
    }

    @Test
    void mapsOrdinaryTeacherWithPublishPermission()
    {
        timeline.returns(access(true, Boolean.TRUE, true, true));

        TeacherAnnouncementAccess result = adapter.resolve(BINDING_ID, USER_ID, AT);

        assertEquals(BINDING_ID, result.bindingId());
        assertEquals(CLASS_ID, result.teachingClassId());
        assertEquals(new TeacherAnnouncementAuthority(true, true, true, true), result.authority());
        assertEquals(BINDING_ID, timeline.lastBindingId);
        assertEquals(USER_ID, timeline.lastUserId);
    }

    @Test
    void mapsLimitedTeacherWithoutPublishPermission()
    {
        timeline.returns(access(true, Boolean.FALSE, true, true));

        TeacherAnnouncementAccess result = adapter.resolve(BINDING_ID, USER_ID, AT);

        assertEquals(new TeacherAnnouncementAuthority(true, false, true, true), result.authority());
    }

    @Test
    void mapsTeamFallbackCoTeacherWithoutPublishPermission()
    {
        timeline.returns(access(BINDING_ID, USER_ID, CLASS_ID, SPACE_ID, PUBLISHED_AT, null,
                true, Boolean.FALSE, true, true, "CO_TEACHER"));

        TeacherAnnouncementAccess result = adapter.resolve(BINDING_ID, USER_ID, AT);

        assertEquals("CO_TEACHER", timeline.result.teacherRole());
        assertEquals(new TeacherAnnouncementAuthority(true, false, true, true), result.authority());
    }

    @Test
    void mapsNonTeacherToNoPublishPermissionInsteadOf503()
    {
        // 非任课教师是合法结果：适配器返回快照，由公告服务按现有规则拒绝。
        timeline.returns(access(false, null, true, true));

        TeacherAnnouncementAccess result = adapter.resolve(BINDING_ID, USER_ID, AT);

        assertEquals(new TeacherAnnouncementAuthority(false, false, true, true), result.authority());
    }

    @Test
    void preservesDirectCoTeacherPublishPermission()
    {
        // 直接班级任课的 CO_TEACHER 可以 canPublish=true，不能按角色名降权。
        timeline.returns(access(BINDING_ID, USER_ID, CLASS_ID, SPACE_ID, PUBLISHED_AT, null,
                true, Boolean.TRUE, true, true, "CO_TEACHER"));

        TeacherAnnouncementAccess result = adapter.resolve(BINDING_ID, USER_ID, AT);

        assertTrue(result.authority().canPublish());
    }

    @Test
    void keepsEndedBindingAsLegalDenial()
    {
        Instant endedAt = Instant.parse("2026-11-20T00:00:00Z");
        timeline.returns(access(BINDING_ID, USER_ID, CLASS_ID, SPACE_ID, PUBLISHED_AT, endedAt,
                true, Boolean.TRUE, false, true, "LEAD"));

        TeacherAnnouncementAccess result = adapter.resolve(BINDING_ID, USER_ID, AT);

        // bindingActive 与 currentTeacher 独立保留，不因绑定结束清除任课标志。
        assertEquals(new TeacherAnnouncementAuthority(true, true, false, true), result.authority());
    }

    @Test
    void keepsClosedClassAsLegalDenial()
    {
        timeline.returns(access(true, Boolean.TRUE, true, false));

        TeacherAnnouncementAccess result = adapter.resolve(BINDING_ID, USER_ID, AT);

        assertEquals(new TeacherAnnouncementAuthority(true, true, true, false), result.authority());
    }

    @Test
    void rejectsNullSourceResult()
    {
        timeline.returns(null);

        assertThrows(IllegalStateException.class, () -> adapter.resolve(BINDING_ID, USER_ID, AT));
    }

    @Test
    void rejectsCrossUserResult()
    {
        timeline.returns(access(BINDING_ID, USER_ID + 1, CLASS_ID, SPACE_ID, PUBLISHED_AT, null,
                true, Boolean.TRUE, true, true, "LEAD"));

        assertThrows(IllegalStateException.class, () -> adapter.resolve(BINDING_ID, USER_ID, AT));
    }

    @Test
    void rejectsCrossBindingResult()
    {
        timeline.returns(access(BINDING_ID + 1, USER_ID, CLASS_ID, SPACE_ID, PUBLISHED_AT, null,
                true, Boolean.TRUE, true, true, "LEAD"));

        assertThrows(IllegalStateException.class, () -> adapter.resolve(BINDING_ID, USER_ID, AT));
    }

    @Test
    void rejectsInvalidInputIdentifiersAndTime()
    {
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(null, USER_ID, AT));
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(0L, USER_ID, AT));
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(-1L, USER_ID, AT));
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(BINDING_ID, null, AT));
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(BINDING_ID, 0L, AT));
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(BINDING_ID, -1L, AT));
        assertThrows(IllegalArgumentException.class, () -> adapter.resolve(BINDING_ID, USER_ID, null));
        assertEquals(0, timeline.calls);
    }

    @Test
    void rejectsMissingOrNonPositiveReturnedIdentifiers()
    {
        assertRejected(access(BINDING_ID, USER_ID, null, SPACE_ID, PUBLISHED_AT, null,
                true, Boolean.TRUE, true, true, "LEAD"));
        assertRejected(access(BINDING_ID, USER_ID, 0L, SPACE_ID, PUBLISHED_AT, null,
                true, Boolean.TRUE, true, true, "LEAD"));
        assertRejected(access(BINDING_ID, USER_ID, CLASS_ID, null, PUBLISHED_AT, null,
                true, Boolean.TRUE, true, true, "LEAD"));
        assertRejected(access(BINDING_ID, USER_ID, CLASS_ID, -1L, PUBLISHED_AT, null,
                true, Boolean.TRUE, true, true, "LEAD"));
    }

    @Test
    void rejectsMissingPublishedAt()
    {
        timeline.returns(access(BINDING_ID, USER_ID, CLASS_ID, SPACE_ID, null, null,
                true, Boolean.TRUE, false, true, "LEAD"));

        assertThrows(IllegalStateException.class, () -> adapter.resolve(BINDING_ID, USER_ID, AT));
    }

    @Test
    void rejectsEndedAtNotAfterPublishedAt()
    {
        assertRejected(access(BINDING_ID, USER_ID, CLASS_ID, SPACE_ID, PUBLISHED_AT, PUBLISHED_AT,
                true, Boolean.TRUE, false, true, "LEAD"));
        assertRejected(access(BINDING_ID, USER_ID, CLASS_ID, SPACE_ID, PUBLISHED_AT,
                PUBLISHED_AT.minusSeconds(1), true, Boolean.TRUE, false, true, "LEAD"));
    }

    @Test
    void rejectsBindingActiveContradictingHalfOpenInterval()
    {
        // 返回 true，但 at 早于发布时间，半开区间判定为未生效。
        assertRejected(access(BINDING_ID, USER_ID, CLASS_ID, SPACE_ID, AT_INSTANT.plusSeconds(1), null,
                true, Boolean.TRUE, true, true, "LEAD"));
        // 返回 false，但区间覆盖 at。
        assertRejected(access(BINDING_ID, USER_ID, CLASS_ID, SPACE_ID, PUBLISHED_AT, null,
                true, Boolean.TRUE, false, true, "LEAD"));
    }

    @Test
    void rejectsCurrentTeacherWithoutPublishFlag()
    {
        timeline.returns(access(true, null, true, true));

        assertThrows(IllegalStateException.class, () -> adapter.resolve(BINDING_ID, USER_ID, AT));
    }

    @Test
    void rejectsNonTeacherWithPublishFlag()
    {
        timeline.returns(access(false, Boolean.TRUE, true, true));

        assertThrows(IllegalStateException.class, () -> adapter.resolve(BINDING_ID, USER_ID, AT));
    }

    @Test
    void propagatesSourceServiceException()
    {
        ServiceException failure = new ServiceException("课程绑定不存在");
        timeline.fails(failure);

        ServiceException thrown = assertThrows(ServiceException.class,
                () -> adapter.resolve(BINDING_ID, USER_ID, AT));
        assertSame(failure, thrown);
    }

    @Test
    void propagatesOverlappingRecordException()
    {
        ServiceException failure = new ServiceException("教师在该绑定下存在多条重叠任课记录");
        timeline.fails(failure);

        assertSame(failure, assertThrows(ServiceException.class,
                () -> adapter.resolve(BINDING_ID, USER_ID, AT)));
    }

    @Test
    void passesServerAtAsUtcMilliseconds()
    {
        timeline.returns(access(true, Boolean.TRUE, true, true));
        LocalDateTime withNanos = LocalDateTime.of(2026, 11, 27, 12, 0, 0, 123_456_789);

        adapter.resolve(BINDING_ID, USER_ID, withNanos);

        // 先按 DatabaseTimePrecision 四舍五入到毫秒，再按 UTC 转 Instant。
        assertEquals(Instant.parse("2026-11-27T12:00:00.123Z"), timeline.lastAt);
    }

    @Test
    void contextStartsWithoutTimelineSourceAndFailsClosed()
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.register(MembershipTimelineTeachingClassAuthorizationAdapter.class);
            context.refresh();

            assertEquals(1,
                    context.getBeanNamesForType(TeachingClassAuthorizationPort.class).length);
            TeachingClassAuthorizationPort port = context.getBean(TeachingClassAuthorizationPort.class);
            assertThrows(IllegalStateException.class,
                    () -> port.resolve(BINDING_ID, USER_ID, AT));
        }
    }

    @Test
    void contextWithTimelineSourceHasSingleProductionPortBean()
    {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext())
        {
            context.registerBean("timelineService", EduMembershipTimelineService.class, () -> timeline);
            context.register(MembershipTimelineTeachingClassAuthorizationAdapter.class);
            context.refresh();

            assertEquals(1,
                    context.getBeanNamesForType(TeachingClassAuthorizationPort.class).length);
            timeline.returns(access(true, Boolean.TRUE, true, true));
            TeachingClassAuthorizationPort port = context.getBean(TeachingClassAuthorizationPort.class);
            TeacherAnnouncementAccess result = port.resolve(BINDING_ID, USER_ID, AT);
            assertTrue(result.authority().canPublish());
        }
    }

    private void assertRejected(EduTeacherBindingAccess result)
    {
        timeline.returns(result);
        assertThrows(IllegalStateException.class, () -> adapter.resolve(BINDING_ID, USER_ID, AT));
    }

    private static EduTeacherBindingAccess access(boolean currentTeacher, Boolean canPublish,
            boolean bindingActive, boolean classOpen)
    {
        return access(BINDING_ID, USER_ID, CLASS_ID, SPACE_ID, PUBLISHED_AT, null,
                currentTeacher, canPublish, bindingActive, classOpen,
                currentTeacher ? "LEAD" : null);
    }

    private static EduTeacherBindingAccess access(Long bindingId, Long userId, Long classId,
            Long spaceId, Instant publishedAt, Instant endedAt, boolean currentTeacher,
            Boolean canPublish, boolean bindingActive, boolean classOpen, String teacherRole)
    {
        return new EduTeacherBindingAccess(bindingId, classId, "教学班", "C-1", spaceId,
                publishedAt, endedAt, userId, "teacher1", "教师一",
                bindingActive, classOpen, currentTeacher, teacherRole, canPublish);
    }

    /**
     * 第 2 组契约的手工测试替身：记录调用参数并返回预设结果或抛出预设异常。
     */
    static class RecordingTimelineService implements EduMembershipTimelineService
    {
        private EduTeacherBindingAccess result;

        private RuntimeException failure;

        private int calls;

        private Long lastBindingId;

        private Long lastUserId;

        private Instant lastAt;

        void returns(EduTeacherBindingAccess result)
        {
            this.result = result;
            this.failure = null;
        }

        void fails(RuntimeException failure)
        {
            this.failure = failure;
        }

        @Override
        public EduTeacherBindingAccess teacherInBinding(Long bindingId, Long userId, Instant at)
        {
            calls++;
            lastBindingId = bindingId;
            lastUserId = userId;
            lastAt = at;
            if (failure != null)
            {
                throw failure;
            }
            return result;
        }

        @Override
        public EduStudentBindingAccess studentInBinding(Long bindingId, Long userId, Instant at)
        {
            throw new UnsupportedOperationException("本测试只验证教师授权");
        }

        @Override
        public List<EduStudentBindingAccess> studentBindings(Long userId, Instant at)
        {
            throw new UnsupportedOperationException("本测试只验证教师授权");
        }

        @Override
        public List<EduStudentMemberSegment> studentsInBinding(Long bindingId, Instant at)
        {
            throw new UnsupportedOperationException("本测试只验证教师授权");
        }
    }
}
