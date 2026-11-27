package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Types;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TimeZone;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.AbstractDataSource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.impl.EduMembershipTimelineServiceImpl;
import com.ruoyi.education.course.authorization.MembershipTimelineStudentAnnouncementAuthorizationAdapter;
import com.ruoyi.education.course.authorization.StudentAnnouncementAccess;
import com.ruoyi.education.course.authorization.StudentAnnouncementAuthorizationPort;
import com.ruoyi.education.course.authorization.StudentAnnouncementMember;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.exception.NoticeAccessDeniedException;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.NoticeNotFoundException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;
import com.ruoyi.education.mapper.EduMembershipTimelineMapper;

/**
 * 学生公告/通知授权链路的真实 MySQL 集成测试。
 *
 * <p>装配链路（除固定时钟和测试身份外均为真实实现，不使用 FakePort、Mockito 或 H2）：
 * 第 2 组 {@link EduMembershipTimelineServiceImpl} 及其 MyBatis Mapper/XML
 * → {@link MembershipTimelineStudentAnnouncementAuthorizationAdapter}
 * → 第 6 组 {@link AnnouncementNoticeFanoutService}、{@link UpstreamNoticeEventService}、
 * {@link StudentNoticeService} → 真实 MySQL。</p>
 *
 * <p>默认跳过；通过系统属性提供可丢弃的 MySQL 服务地址后执行：</p>
 *
 * <pre>
 * -Dnotice.mysql.url=jdbc:mysql://127.0.0.1:56350/notice_auth?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC&amp;connectionTimeZone=UTC
 * -Dnotice.mysql.user=root
 * -Dnotice.mysql.password=
 * </pre>
 *
 * <p>每个测试创建随机数据库并加载
 * {@code src/test/resources/mysql/notice-auth-minimal-schema.sql}，结束后删除；
 * 不会修改项目数据库。JDBC 会话显式执行 {@code SET time_zone = '+00:00'}，
 * 不把 {@code serverTimezone=UTC} 当作会话时区已生效的证据。</p>
 *
 * <p>固定 {@link Clock} 只用于让读取/已读时点可重复；SecurityContext 中的用户
 * 只是测试身份，不代表真实登录、角色拦截或 HTTP 鉴权。</p>
 */
class NoticeAuthorizationMySqlIntegrationTest
{
    private static final Long BINDING_ID = 100L;

    private static final Long CLASS_ID = 20L;

    private static final Long COURSE_SPACE_ID = 10L;

    private static final Long ORGANIZATION_ID = 1L;

    private static final Long STUDENT_ONE = 9001L;

    private static final Long STUDENT_TWO = 9002L;

    private static final Long STUDENT_THREE = 9003L;

    private static final LocalDateTime ELEVEN_AM = LocalDateTime.of(2026, 11, 20, 11, 0, 0);

    private static final LocalDateTime NINE_AM = LocalDateTime.of(2026, 11, 20, 9, 0, 0);

    private static final LocalDateTime NINE_THIRTY = LocalDateTime.of(2026, 11, 20, 9, 30, 0);

    private static final LocalDateTime TEN_AM = LocalDateTime.of(2026, 11, 20, 10, 0, 0);

    private static final LocalDateTime BASE_PUBLISHED =
            LocalDateTime.of(2026, 11, 1, 0, 0, 0, 123_000_000);

    private String serverUrl;

    private String database;

    private String username;

    private String password;

    private Harness harness;

    @BeforeEach
    void setUp() throws Exception
    {
        String configuredUrl = System.getProperty("notice.mysql.url", "");
        Assumptions.assumeTrue(!configuredUrl.isBlank(),
                "notice.mysql.url not set; skipping real MySQL notice authorization test");

        username = System.getProperty("notice.mysql.user", "root");
        password = System.getProperty("notice.mysql.password", "");
        serverUrl = deriveServerUrl(configuredUrl);
        database = "notice_auth_it_" + Long.toHexString(System.nanoTime());

        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                Statement statement = connection.createStatement())
        {
            statement.execute("CREATE DATABASE `" + database
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        DataSource dataSource = new UtcSessionDataSource(newDriverManagerDataSource());
        try (Connection connection = dataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("mysql/notice-auth-minimal-schema.sql"));
        }

        harness = buildHarness(dataSource);
        seedBaseRows();
        logDatabaseSettings(harness.jdbc);
    }

    @AfterEach
    void tearDown() throws Exception
    {
        SecurityContextHolder.clearContext();
        if (serverUrl == null || database == null)
        {
            return;
        }
        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                Statement statement = connection.createStatement())
        {
            statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
        }
    }

    /**
     * 事件在 9 点发生、学生 10 点退班、11 点才投递：接收人按 occurredAt=9 点确定；
     * 11 点读取是否允许另行按当前成员段判断。
     */
    @Test
    void eventRecipientsUseOccurredAtWhileReadUsesCurrentMembership()
    {
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 20, 8, 0),
                LocalDateTime.of(2026, 11, 20, 10, 0));
        UpstreamNoticeEvent event = new UpstreamNoticeEvent("evt-nine", NoticeKind.ASSIGNMENT,
                BINDING_ID, 5001L, "九点事件", null, NINE_AM, NINE_AM, null);

        // 投递动作发生在退班之后，但事件 occurredAt 仍是 9 点。
        assertEquals(Integer.valueOf(1), harness.tx.execute(status -> harness.events.receive(event)));
        assertEquals(1, noticeCount());
        Long noticeId = noticeId(STUDENT_ONE, NoticeKind.ASSIGNMENT, 5001L);
        assertEquals(NINE_AM, harness.jdbc.queryForObject(
                "SELECT visible_at FROM edu_user_notice WHERE id = ?", LocalDateTime.class,
                noticeId));

        authenticate(STUDENT_ONE);
        StudentNoticePage atNineThirty = harness.noticeService(NINE_THIRTY)
                .list(BINDING_ID, null, false, 1, 10);
        assertEquals(1, atNineThirty.total());
        assertEquals("九点事件", atNineThirty.rows().get(0).getTitle());

        StudentNoticeService atEleven = harness.noticeService(ELEVEN_AM);
        assertThrows(NoticeAccessDeniedException.class,
                () -> atEleven.list(BINDING_ID, null, false, 1, 10));
        assertThrows(NoticeNotFoundException.class, () -> atEleven.get(noticeId));

        // 读取被拒绝不代表删除或改写已投递通知。
        assertEquals(1, noticeCount());
    }

    /**
     * 半开区间与毫秒边界：joinedAt 包含、leftAt 不包含，绑定 published_at/ended_at
     * 同样是半开边界；全部时间按 DATETIME(3) 毫秒比较。
     */
    @Test
    void halfOpenMembershipAndBindingIntervalsRespectMilliseconds()
    {
        LocalDateTime published = LocalDateTime.of(2026, 11, 1, 0, 0, 0, 123_000_000);
        LocalDateTime ended = LocalDateTime.of(2026, 12, 1, 0, 0, 0, 456_000_000);
        LocalDateTime joined = LocalDateTime.of(2026, 11, 10, 9, 0, 0, 234_000_000);
        LocalDateTime left = LocalDateTime.of(2026, 11, 20, 10, 0, 0, 567_000_000);
        harness.jdbc.update("UPDATE edu_course_class_binding SET published_at = ?, ended_at = ? "
                + "WHERE id = ?", published, ended, BINDING_ID);
        seedStudent(STUDENT_ONE, joined, left);

        assertFalse(harness.port.resolve(BINDING_ID, STUDENT_ONE, published.minusNanos(1_000_000L))
                .authority().bindingActive());
        assertTrue(harness.port.resolve(BINDING_ID, STUDENT_ONE, published)
                .authority().bindingActive());
        assertFalse(harness.port.resolve(BINDING_ID, STUDENT_ONE, joined.minusNanos(1_000_000L))
                .authority().currentStudent());
        assertTrue(harness.port.resolve(BINDING_ID, STUDENT_ONE, joined)
                .authority().currentStudent());
        assertTrue(harness.port.resolve(BINDING_ID, STUDENT_ONE, left.minusNanos(1_000_000L))
                .authority().currentStudent());
        assertFalse(harness.port.resolve(BINDING_ID, STUDENT_ONE, left)
                .authority().currentStudent());
        assertTrue(harness.port.resolve(BINDING_ID, STUDENT_ONE, ended.minusNanos(1_000_000L))
                .authority().bindingActive());
        assertFalse(harness.port.resolve(BINDING_ID, STUDENT_ONE, ended)
                .authority().bindingActive());

        assertTrue(harness.port.listActiveStudents(BINDING_ID, published.minusNanos(1_000_000L))
                .isEmpty());
        assertTrue(harness.port.listActiveStudents(BINDING_ID, published).isEmpty());
        assertEquals(1, harness.port.listActiveStudents(BINDING_ID, joined).size());
        assertEquals(1, harness.port.listActiveStudents(BINDING_ID, left.minusNanos(1_000_000L)).size());
        assertTrue(harness.port.listActiveStudents(BINDING_ID, left).isEmpty());
        assertTrue(harness.port.listActiveStudents(BINDING_ID, ended).isEmpty());

        StudentAnnouncementMember atJoin = harness.port.listActiveStudents(BINDING_ID, joined).get(0);
        assertEquals(joined, atJoin.joinedAt());
        assertEquals(left, atJoin.leftAt());
        assertTrue(atJoin.authority().teachingClassActive());
    }

    /**
     * 退班后重新入班：通知可见性使用当前成员段。旧通知是否可见沿用既有
     * visible_at 必须落入当前 [joinedAt, leftAt) 的政策，本测试只验证不改变它。
     */
    @Test
    void rejoinedStudentUsesCurrentMembershipSegmentForNoticeVisibility()
    {
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 1, 8, 0),
                LocalDateTime.of(2026, 11, 10, 10, 0));
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 15, 8, 0), null);
        insertNotice(STUDENT_ONE, NoticeKind.ASSIGNMENT, 6001L, "第一次在班通知",
                LocalDateTime.of(2026, 11, 5, 9, 0), null);
        insertNotice(STUDENT_ONE, NoticeKind.ASSIGNMENT, 6002L, "重新入班后通知",
                LocalDateTime.of(2026, 11, 16, 9, 0), null);

        authenticate(STUDENT_ONE);
        StudentNoticePage firstStint = harness.noticeService(LocalDateTime.of(2026, 11, 5, 12, 0))
                .list(BINDING_ID, null, false, 1, 10);
        assertEquals(1, firstStint.total());
        assertEquals(6001L, firstStint.rows().get(0).getSourceId());

        StudentNoticePage secondStint = harness.noticeService(LocalDateTime.of(2026, 11, 16, 12, 0))
                .list(BINDING_ID, null, false, 1, 10);
        assertEquals(1, secondStint.total());
        assertEquals(6002L, secondStint.rows().get(0).getSourceId());
    }

    /**
     * 教学班 status 只有当前值：当前关闭时，即使成员和绑定在该历史时点有效，
     * classOpen 仍为 false；不伪造历史教学班状态。
     */
    @Test
    void closedTeachingClassKeepsCurrentStatusWithoutHistoricalBackfill()
    {
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        harness.jdbc.update("UPDATE edu_teaching_class SET status = 'ARCHIVED' WHERE id = ?",
                CLASS_ID);

        StudentAnnouncementAccess access = harness.port.resolve(BINDING_ID, STUDENT_ONE, NINE_AM);
        assertTrue(access.authority().currentStudent());
        assertTrue(access.authority().bindingActive());
        assertFalse(access.authority().teachingClassActive());
        assertFalse(access.authority().active());

        List<StudentAnnouncementMember> members =
                harness.port.listActiveStudents(BINDING_ID, NINE_AM);
        assertEquals(1, members.size());
        assertFalse(members.get(0).authority().active());

        CourseAnnouncement announcement = announcement(4001L, "关闭班级公告");
        assertEquals(Integer.valueOf(0), harness.tx.execute(status ->
                harness.fanout.fanOutAnnouncement(announcement, NINE_AM)));
        assertEquals(0, noticeCount());

        UpstreamNoticeEvent event = new UpstreamNoticeEvent("evt-closed-class",
                NoticeKind.ASSIGNMENT, BINDING_ID, 4002L, "关闭班级事件", null, NINE_AM,
                NINE_AM, null);
        assertEquals(Integer.valueOf(0), harness.tx.execute(status -> harness.events.receive(event)));
        assertEquals(0, noticeCount());

        authenticate(STUDENT_ONE);
        assertThrows(NoticeAccessDeniedException.class,
                () -> harness.noticeService(NINE_THIRTY)
                        .list(BINDING_ID, null, false, 1, 10));

        harness.jdbc.update("UPDATE edu_teaching_class SET status = 'ACTIVE' WHERE id = ?",
                CLASS_ID);
        assertTrue(harness.port.resolve(BINDING_ID, STUDENT_ONE, NINE_AM)
                .authority().active());
    }

    /**
     * 重复投递由数据库唯一键保持幂等；最终以行数、read_at 和标题不变证明，
     * 不把 MySQL 影响行数当作接收人数。
     */
    @Test
    void duplicateDeliveryIsIdempotentAndPreservesReadAt()
    {
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        UpstreamNoticeEvent event = new UpstreamNoticeEvent("evt-idempotent",
                NoticeKind.ASSIGNMENT, BINDING_ID, 7001L, "原始标题", null, NINE_AM,
                NINE_AM, null);
        assertEquals(Integer.valueOf(1), harness.tx.execute(status -> harness.events.receive(event)));
        Long noticeId = noticeId(STUDENT_ONE, NoticeKind.ASSIGNMENT, 7001L);

        authenticate(STUDENT_ONE);
        StudentNoticeService noticeService = harness.noticeService(NINE_THIRTY);
        StudentNoticeReadResult read = harness.tx.execute(status -> noticeService.markRead(noticeId));
        assertFalse(read.alreadyRead());
        assertEquals(NINE_THIRTY, read.readAt());
        harness.jdbc.update("UPDATE edu_user_notice SET title = '保留标题' WHERE id = ?", noticeId);

        UpstreamNoticeEvent duplicate = new UpstreamNoticeEvent("evt-idempotent",
                NoticeKind.ASSIGNMENT, BINDING_ID, 7001L, "重复投递标题", null, TEN_AM,
                TEN_AM, null);
        // 返回值可能因驱动把重复键计为匹配行而不同，不用它计算接收人数。
        assertNotNull(harness.tx.execute(status -> harness.events.receive(duplicate)));

        assertEquals(1, noticeCount());
        assertEquals("保留标题", harness.jdbc.queryForObject(
                "SELECT title FROM edu_user_notice WHERE id = ?", String.class, noticeId));
        assertEquals(NINE_AM, harness.jdbc.queryForObject(
                "SELECT visible_at FROM edu_user_notice WHERE id = ?", LocalDateTime.class,
                noticeId));
        assertEquals(NINE_THIRTY, harness.jdbc.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE id = ?", LocalDateTime.class,
                noticeId));
    }

    /**
     * 通知列表、未读数、单条已读和全部已读使用同一成员范围；其他学生、其他绑定
     * 的通知不可见、不可标记。
     */
    @Test
    void noticeListUnreadAndReadStateStayWithinMembershipScope()
    {
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        seedStudent(STUDENT_TWO, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        insertAnnouncement(20001L, "公告A", NINE_AM);
        CourseAnnouncement announcement = announcement(20001L, "公告A");

        assertEquals(Integer.valueOf(2), harness.tx.execute(status ->
                harness.fanout.fanOutAnnouncement(announcement, NINE_AM)));
        // 重复 fan-out 不新增通知。
        harness.tx.execute(status -> harness.fanout.fanOutAnnouncement(announcement, NINE_AM));
        assertEquals(2, noticeCount());

        UpstreamNoticeEvent event = new UpstreamNoticeEvent("evt-scope", NoticeKind.ASSIGNMENT,
                BINDING_ID, 8001L, "仅九零零一", null, NINE_AM,
                LocalDateTime.of(2026, 11, 20, 9, 5), List.of(STUDENT_ONE));
        assertEquals(Integer.valueOf(1), harness.tx.execute(status -> harness.events.receive(event)));
        assertEquals(3, noticeCount());
        Long eventNoticeId = noticeId(STUDENT_ONE, NoticeKind.ASSIGNMENT, 8001L);

        authenticate(STUDENT_ONE);
        StudentNoticeService studentOne = harness.noticeService(TEN_AM);
        StudentNoticePage pageOne = studentOne.list(BINDING_ID, null, false, 1, 10);
        assertEquals(2, pageOne.total());
        assertTrue(pageOne.rows().stream().allMatch(row -> STUDENT_ONE.equals(row.getRecipientUserId())));
        assertEquals(2, studentOne.unreadCount(BINDING_ID).unreadCount());

        authenticate(STUDENT_TWO);
        StudentNoticeService studentTwo = harness.noticeService(TEN_AM);
        StudentNoticePage pageTwo = studentTwo.list(BINDING_ID, null, false, 1, 10);
        assertEquals(1, pageTwo.total());
        assertEquals(NoticeKind.ANNOUNCEMENT, pageTwo.rows().get(0).getNoticeKind());
        assertEquals(1, studentTwo.unreadCount(BINDING_ID).unreadCount());
        assertThrows(NoticeNotFoundException.class, () -> studentTwo.get(eventNoticeId));

        authenticate(STUDENT_ONE);
        Long announcementNoticeId = noticeId(STUDENT_ONE, NoticeKind.ANNOUNCEMENT, 20001L);
        StudentNoticeReadResult firstRead = harness.tx.execute(status ->
                studentOne.markRead(announcementNoticeId));
        assertFalse(firstRead.alreadyRead());
        assertEquals(TEN_AM, firstRead.readAt());
        assertEquals(1, studentOne.unreadCount(BINDING_ID).unreadCount());
        StudentNoticeReadResult secondRead = harness.tx.execute(status ->
                studentOne.markRead(announcementNoticeId));
        assertTrue(secondRead.alreadyRead());
        assertEquals(TEN_AM, secondRead.readAt());

        StudentNoticeReadAllResult allRead = harness.tx.execute(status -> studentOne.markAllRead());
        assertEquals(1, allRead.markedCount());
        assertEquals(0, allRead.unreadCount());
        assertEquals(TEN_AM, allRead.operatedAt());
        assertEquals(0, studentOne.list(BINDING_ID, null, true, 1, 10).total());
        assertEquals(2, studentOne.list(BINDING_ID, null, false, 1, 10).total());
        assertNotNull(harness.jdbc.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE id = ?", LocalDateTime.class,
                eventNoticeId));

        // 别人的通知不能被单条已读。
        Long studentTwoAnnouncement = noticeId(STUDENT_TWO, NoticeKind.ANNOUNCEMENT, 20001L);
        authenticate(STUDENT_ONE);
        assertThrows(NoticeNotFoundException.class,
                () -> harness.tx.execute(status -> studentOne.markRead(studentTwoAnnouncement)));
        assertNull(harness.jdbc.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE id = ?", LocalDateTime.class,
                studentTwoAnnouncement));
    }

    /** 重叠成员段是跨行脏数据：授权链路失败关闭，且不产生部分通知写入。 */
    @Test
    void overlappingMembershipSegmentsFailClosedWithoutPartialNoticeWrites()
    {
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 1, 8, 0),
                LocalDateTime.of(2026, 12, 1, 0, 0));
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 15, 8, 0),
                LocalDateTime.of(2026, 11, 30, 0, 0));

        assertThrows(ServiceException.class,
                () -> harness.port.resolve(BINDING_ID, STUDENT_ONE, NINE_AM));
        UpstreamNoticeEvent event = new UpstreamNoticeEvent("evt-overlap",
                NoticeKind.ASSIGNMENT, BINDING_ID, 9001L, "重叠成员事件", null, NINE_AM,
                NINE_AM, null);
        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> harness.tx.execute(status -> harness.events.receive(event)));
        assertEquals(0, noticeCount());
    }

    /** 真实查询失败（表不可用）保持 503，不返回空名单，也不产生部分通知。 */
    @Test
    void authorizationQueryFailureFailsClosedWithoutPartialNoticeWrites() throws Exception
    {
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        harness.jdbc.execute("RENAME TABLE edu_class_student TO edu_class_student_unavailable");
        try
        {
            UpstreamNoticeEvent failing = new UpstreamNoticeEvent("evt-db-down",
                    NoticeKind.ASSIGNMENT, BINDING_ID, 9101L, "查询失败事件", null, NINE_AM,
                    NINE_AM, null);
            assertThrows(NoticeAuthorizationUnavailableException.class,
                    () -> harness.tx.execute(status -> harness.events.receive(failing)));
            assertEquals(0, noticeCount());
        }
        finally
        {
            harness.jdbc.execute(
                    "RENAME TABLE edu_class_student_unavailable TO edu_class_student");
        }

        UpstreamNoticeEvent recovered = new UpstreamNoticeEvent("evt-recovered",
                NoticeKind.ASSIGNMENT, BINDING_ID, 9102L, "恢复后事件", null, NINE_AM,
                NINE_AM, null);
        assertEquals(Integer.valueOf(1), harness.tx.execute(status ->
                harness.events.receive(recovered)));
        assertEquals(1, noticeCount());
    }

    /**
     * 同一批 UTC 测试时间在 JVM 默认时区 UTC 与 Asia/Shanghai 下必须得到一致结果。
     *
     * <p>每个时区使用新 DataSource/连接，并在会话内显式设置 time_zone='+00:00'，
     * 避免复用连接时区缓存或把 serverTimezone=UTC 当成会话已生效。</p>
     */
    @Test
    void sameUtcTimesAreStableUnderUtcAndShanghaiJvmTimeZones() throws Exception
    {
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0, 0, 123_000_000), null);
        List<String> signatures = new ArrayList<>();
        String[] zones = { "UTC", "Asia/Shanghai" };
        TimeZone previous = TimeZone.getDefault();
        try
        {
            for (int index = 0; index < zones.length; index++)
            {
                TimeZone.setDefault(TimeZone.getTimeZone(zones[index]));
                Harness zoneHarness = buildHarness(newDataSource());
                assertEquals("+00:00", zoneHarness.jdbc.queryForObject(
                        "SELECT @@session.time_zone", String.class));
                zoneHarness.jdbc.update("DELETE FROM edu_user_notice");

                StudentAnnouncementAccess access =
                        zoneHarness.port.resolve(BINDING_ID, STUDENT_ONE, NINE_AM);
                List<StudentAnnouncementMember> members =
                        zoneHarness.port.listActiveStudents(BINDING_ID, NINE_AM);
                UpstreamNoticeEvent event = new UpstreamNoticeEvent("evt-zone-" + index,
                        NoticeKind.ASSIGNMENT, BINDING_ID, 9200L + index, "时区事件", null,
                        NINE_AM, NINE_AM, null);
                Integer inserted = zoneHarness.tx.execute(status ->
                        zoneHarness.events.receive(event));

                authenticate(STUDENT_ONE);
                StudentNoticePage page = zoneHarness.noticeService(NINE_THIRTY)
                        .list(BINDING_ID, null, false, 1, 10);
                LocalDateTime persistedVisibleAt = zoneHarness.jdbc.queryForObject(
                        "SELECT visible_at FROM edu_user_notice WHERE source_id = ?",
                        LocalDateTime.class, 9200L + index);
                signatures.add(String.join("|",
                        zones[index],
                        String.valueOf(access.authority().active()),
                        access.joinedAt().toString(),
                        access.leftAt() == null ? "null" : access.leftAt().toString(),
                        String.valueOf(members.size()),
                        String.valueOf(inserted),
                        persistedVisibleAt.toString(),
                        String.valueOf(page.total())));
                System.out.printf("MYSQL_IT_TIMEZONE jvm=%s session=%s signature=%s%n",
                        zones[index], zoneHarness.jdbc.queryForObject(
                                "SELECT @@session.time_zone", String.class),
                        signatures.get(signatures.size() - 1));
            }
        }
        finally
        {
            TimeZone.setDefault(previous);
        }

        assertEquals(2, signatures.size());
        assertEquals(signatures.get(0).substring(signatures.get(0).indexOf('|')),
                signatures.get(1).substring(signatures.get(1).indexOf('|')));
        assertTrue(signatures.get(0).contains("2026-11-10T08:00:00.123"));
    }

    private Harness buildHarness(DataSource dataSource) throws Exception
    {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] timelineMappers = resolver.getResources(
                "classpath*:mapper/education/EduMembershipTimelineMapper.xml");
        Resource[] noticeMappers = resolver.getResources(
                "classpath*:mapper/education/StudentNoticeMapper.xml");
        Resource[] mapperLocations = new Resource[timelineMappers.length + noticeMappers.length];
        System.arraycopy(timelineMappers, 0, mapperLocations, 0, timelineMappers.length);
        System.arraycopy(noticeMappers, 0, mapperLocations, timelineMappers.length,
                noticeMappers.length);
        factory.setMapperLocations(mapperLocations);
        SqlSessionTemplate sqlSession = new SqlSessionTemplate(factory.getObject());
        EduMembershipTimelineMapper timelineMapper =
                sqlSession.getMapper(EduMembershipTimelineMapper.class);
        StudentNoticeMapper noticeMapper = sqlSession.getMapper(StudentNoticeMapper.class);

        EduMembershipTimelineServiceImpl timelineService = new EduMembershipTimelineServiceImpl();
        ReflectionTestUtils.setField(timelineService, "mapper", timelineMapper);
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("timelineService", timelineService);
        StudentAnnouncementAuthorizationPort port =
                new MembershipTimelineStudentAnnouncementAuthorizationAdapter(
                        beanFactory.getBeanProvider(EduMembershipTimelineService.class));

        return new Harness(new JdbcTemplate(dataSource), noticeMapper, port,
                new AnnouncementNoticeFanoutService(port, noticeMapper),
                new UpstreamNoticeEventService(port, noticeMapper),
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

    private DataSource newDataSource()
    {
        return new UtcSessionDataSource(newDriverManagerDataSource());
    }

    /**
     * 显式声明驱动连接时区为 UTC，与生产 JDBC URL 的 serverTimezone=UTC 语义一致。
     * 仅设置会话 time_zone 不足以固定 Connector/J 对 java.util.Date 的格式化时区。
     */
    private DriverManagerDataSource newDriverManagerDataSource()
    {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                withDatabase(serverUrl, database), username, password);
        java.util.Properties properties = new java.util.Properties();
        properties.setProperty("connectionTimeZone", "UTC");
        properties.setProperty("forceConnectionTimeZoneToSession", "true");
        dataSource.setConnectionProperties(properties);
        return dataSource;
    }

    private void seedBaseRows()
    {
        harness.jdbc.update("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, "
                + "phonenumber, status, del_flag) VALUES (1, NULL, 'owner', '负责人', '', '0', '0')");
        harness.jdbc.update("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, "
                + "phonenumber, status, del_flag) VALUES (?, NULL, ?, ?, '', '0', '0')",
                STUDENT_ONE, "student9001", "学生一");
        harness.jdbc.update("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, "
                + "phonenumber, status, del_flag) VALUES (?, NULL, ?, ?, '', '0', '0')",
                STUDENT_TWO, "student9002", "学生二");
        harness.jdbc.update("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, "
                + "phonenumber, status, del_flag) VALUES (?, NULL, ?, ?, '', '0', '0')",
                STUDENT_THREE, "student9003", "非成员");
        harness.jdbc.update("INSERT INTO edu_term (id, organization_id, code, name, starts_on, "
                + "ends_on, is_current) VALUES (1, ?, 'T-2026', '2026 秋', '2026-09-01', "
                + "'2027-01-31', 0)", ORGANIZATION_ID);
        harness.jdbc.update("INSERT INTO edu_official_course (id, organization_id, code, name, "
                + "status) VALUES (1, ?, 'OC-1', '测试课程', 'ACTIVE')", ORGANIZATION_ID);
        harness.jdbc.update("INSERT INTO edu_course_space (id, organization_id, "
                + "official_course_id, owner_user_id, title, status) VALUES (?, ?, 1, 1, "
                + "'测试课程空间', 'ACTIVE')", COURSE_SPACE_ID, ORGANIZATION_ID);
        harness.jdbc.update("INSERT INTO edu_teaching_class (id, organization_id, term_id, "
                + "official_course_id, code, name, class_kind, status) VALUES (?, ?, 1, 1, "
                + "'TC-1', '测试教学班', 'FORMAL', 'ACTIVE')", CLASS_ID, ORGANIZATION_ID);
        harness.jdbc.update("INSERT INTO edu_course_class_binding (id, organization_id, "
                + "teaching_class_id, course_space_id, bound_by_user_id, published_at, ended_at) "
                + "VALUES (?, ?, ?, ?, 1, ?, NULL)", BINDING_ID, ORGANIZATION_ID, CLASS_ID,
                COURSE_SPACE_ID, BASE_PUBLISHED);
    }

    private void seedStudent(Long userId, LocalDateTime joinedAt, LocalDateTime leftAt)
    {
        harness.jdbc.update("INSERT INTO edu_class_student (teaching_class_id, user_id, "
                + "joined_at, left_at, recorded_by_user_id) VALUES (?, ?, ?, ?, 1)",
                ps -> {
                    ps.setLong(1, CLASS_ID);
                    ps.setLong(2, userId);
                    ps.setObject(3, joinedAt);
                    if (leftAt == null)
                    {
                        ps.setNull(4, Types.TIMESTAMP);
                    }
                    else
                    {
                        ps.setObject(4, leftAt);
                    }
                });
    }

    private void insertNotice(Long recipientUserId, NoticeKind kind, long sourceId, String title,
            LocalDateTime visibleAt, LocalDateTime readAt)
    {
        harness.jdbc.update("INSERT INTO edu_user_notice (recipient_user_id, binding_id, "
                + "notice_kind, source_id, title, visible_at, read_at, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?)", ps -> {
                    ps.setLong(1, recipientUserId);
                    ps.setLong(2, BINDING_ID);
                    ps.setString(3, kind.name());
                    ps.setLong(4, sourceId);
                    ps.setString(5, title);
                    ps.setObject(6, visibleAt);
                    if (readAt == null)
                    {
                        ps.setNull(7, Types.TIMESTAMP);
                    }
                    else
                    {
                        ps.setObject(7, readAt);
                    }
                    ps.setObject(8, visibleAt);
                });
    }

    private void insertAnnouncement(long id, String title, LocalDateTime opensAt)
    {
        harness.jdbc.update("INSERT INTO edu_course_announcement (id, binding_id, title, body, "
                + "status, opens_at, closes_at, published_by_user_id, created_by_user_id) "
                + "VALUES (?, ?, ?, 'body', 'PUBLISHED', ?, NULL, 1, 1)",
                id, BINDING_ID, title, opensAt);
    }

    private CourseAnnouncement announcement(long id, String title)
    {
        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setId(id);
        announcement.setBindingId(BINDING_ID);
        announcement.setTitle(title);
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setOpensAt(NINE_AM);
        announcement.setCreatedAt(NINE_AM);
        return announcement;
    }

    private long noticeCount()
    {
        return harness.jdbc.queryForObject("SELECT COUNT(*) FROM edu_user_notice", Long.class);
    }

    private Long noticeId(Long recipientUserId, NoticeKind kind, long sourceId)
    {
        return harness.jdbc.queryForObject("SELECT id FROM edu_user_notice "
                + "WHERE recipient_user_id = ? AND notice_kind = ? AND source_id = ?",
                Long.class, recipientUserId, kind.name(), sourceId);
    }

    private void authenticate(Long userId)
    {
        SysUser sysUser = new SysUser(userId);
        LoginUser loginUser = new LoginUser(userId, null, sysUser, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    private void logDatabaseSettings(JdbcTemplate jdbcTemplate)
    {
        System.out.printf("MYSQL_IT db=%s version=%s global_tz=%s session_tz=%s "
                        + "system_tz=%s isolation=%s jvm_tz=%s%n",
                database,
                jdbcTemplate.queryForObject("SELECT VERSION()", String.class),
                jdbcTemplate.queryForObject("SELECT @@global.time_zone", String.class),
                jdbcTemplate.queryForObject("SELECT @@session.time_zone", String.class),
                jdbcTemplate.queryForObject("SELECT @@system_time_zone", String.class),
                jdbcTemplate.queryForObject("SELECT @@transaction_isolation", String.class),
                TimeZone.getDefault().getID());
    }

    private String deriveServerUrl(String url)
    {
        int schemeEnd = url.indexOf("//");
        int pathStart = url.indexOf('/', schemeEnd + 2);
        if (pathStart < 0)
        {
            return url;
        }
        int queryStart = url.indexOf('?', pathStart);
        String base = url.substring(0, pathStart + 1);
        return queryStart >= 0 ? base + url.substring(queryStart) : base;
    }

    private String withDatabase(String serverUrl, String databaseName)
    {
        int queryStart = serverUrl.indexOf('?');
        String base = queryStart >= 0 ? serverUrl.substring(0, queryStart) : serverUrl;
        String query = queryStart >= 0 ? serverUrl.substring(queryStart) : "";
        return base + databaseName + query;
    }

    private static final class Harness
    {
        private final JdbcTemplate jdbc;

        private final StudentNoticeMapper noticeMapper;

        private final StudentAnnouncementAuthorizationPort port;

        private final AnnouncementNoticeFanoutService fanout;

        private final UpstreamNoticeEventService events;

        private final TransactionTemplate tx;

        private Harness(JdbcTemplate jdbc, StudentNoticeMapper noticeMapper,
                StudentAnnouncementAuthorizationPort port,
                AnnouncementNoticeFanoutService fanout, UpstreamNoticeEventService events,
                TransactionTemplate tx)
        {
            this.jdbc = jdbc;
            this.noticeMapper = noticeMapper;
            this.port = port;
            this.fanout = fanout;
            this.events = events;
            this.tx = tx;
        }

        private StudentNoticeService noticeService(LocalDateTime at)
        {
            return new StudentNoticeService(noticeMapper, port,
                    Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
        }
    }

    /**
     * 每个连接显式设置 UTC 会话时区；测试不依赖 serverTimezone 参数自动设置会话。
     */
    private static final class UtcSessionDataSource extends AbstractDataSource
    {
        private final DataSource delegate;

        private UtcSessionDataSource(DataSource delegate)
        {
            this.delegate = delegate;
        }

        @Override
        public Connection getConnection() throws SQLException
        {
            return initialize(delegate.getConnection());
        }

        @Override
        public Connection getConnection(String username, String password) throws SQLException
        {
            return initialize(delegate.getConnection(username, password));
        }

        private Connection initialize(Connection connection) throws SQLException
        {
            try (Statement statement = connection.createStatement())
            {
                statement.execute("SET time_zone = '+00:00'");
                return connection;
            }
            catch (SQLException e)
            {
                connection.close();
                throw e;
            }
        }
    }
}
