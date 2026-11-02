package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.transaction.support.TransactionTemplate;
import com.ruoyi.education.course.authorization.StudentAnnouncementAccess;
import com.ruoyi.education.course.authorization.StudentAnnouncementAuthorizationPort;
import com.ruoyi.education.course.authorization.StudentAnnouncementMember;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;
import com.ruoyi.education.course.exception.NoticePersistenceException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;

/**
 * 真实 MySQL 写入与并发加固测试。
 *
 * <p>默认跳过；通过系统属性提供可丢弃的 MySQL 服务地址后执行：</p>
 *
 * <pre>
 * -Dnotice.mysql.url=jdbc:mysql://127.0.0.1:3399/notice_repro?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC
 * -Dnotice.mysql.user=root
 * -Dnotice.mysql.password=
 * </pre>
 *
 * <p>测试会创建随机数据库和临时表，结束后删除；不会修改项目数据库。</p>
 */
class NoticeMySqlHardeningTest
{
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 30, 12, 0, 0, 123_000_000);

    private static final Long BINDING_ID = 100L;

    private static final Long CLASS_ID = 1000L;

    private String serverUrl;

    private String database;

    private String username;

    private String password;

    private DataSource dataSource;

    private StudentNoticeMapper mapper;

    private TransactionTemplate transactionTemplate;

    private JdbcTemplate jdbcTemplate;

    private FakePort port;

    private AnnouncementNoticeFanoutService fanoutService;

    private UpstreamNoticeEventService eventService;

    private StudentNoticeService noticeService;

    private DataSource shortTimeoutDataSource;

    private StudentNoticeMapper shortTimeoutMapper;

    private TransactionTemplate shortTimeoutTransaction;

    @BeforeEach
    void setUp() throws Exception
    {
        String configuredUrl = System.getProperty("notice.mysql.url", "");
        Assumptions.assumeTrue(!configuredUrl.isBlank(),
                "notice.mysql.url not set; skipping real MySQL hardening test");

        username = System.getProperty("notice.mysql.user", "root");
        password = System.getProperty("notice.mysql.password", "");
        serverUrl = deriveServerUrl(configuredUrl);
        database = "notice_hardening_" + Long.toHexString(System.nanoTime());

        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                Statement statement = connection.createStatement())
        {
            statement.execute("CREATE DATABASE `" + database
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        String testUrl = withDatabase(serverUrl, database);
        dataSource = new DriverManagerDataSource(testUrl, username, password);
        createTables(dataSource);
        mapper = buildMapper(dataSource);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbcTemplate = new JdbcTemplate(dataSource);

        shortTimeoutDataSource = new DriverManagerDataSource(
                appendParameter(testUrl, "sessionVariables=innodb_lock_wait_timeout=2"),
                username, password);
        shortTimeoutMapper = buildMapper(shortTimeoutDataSource);
        shortTimeoutTransaction = new TransactionTemplate(
                new DataSourceTransactionManager(shortTimeoutDataSource));
        // 先确认会话变量生效，避免并发超时阈值不明确。
        assertEquals(2, new JdbcTemplate(shortTimeoutDataSource)
                .queryForObject("SELECT @@innodb_lock_wait_timeout", Integer.class));

        port = new FakePort();
        fanoutService = new AnnouncementNoticeFanoutService(port, mapper);
        eventService = new UpstreamNoticeEventService(port, mapper);
        noticeService = new StudentNoticeService(mapper, port, Clock.systemUTC());
    }

    @AfterEach
    void tearDown() throws Exception
    {
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

    @Test
    void fanOutInsertIsIdempotentPreservesReadAtAndUsesAutoIncrement()
    {
        port.members = List.of(member(9001L), member(9002L));
        CourseAnnouncement announcement = announcement(20001L, "fanout-title");

        assertEquals(Integer.valueOf(2), transactionTemplate.execute(
                status -> fanoutService.fanOutAnnouncement(announcement, AT)));
        assertNotNull(transactionTemplate.execute(
                status -> fanoutService.fanOutAnnouncement(announcement, AT)));
        assertEquals(2, countRows());
        List<Long> firstIds = jdbcTemplate.queryForList(
                "SELECT id FROM edu_user_notice ORDER BY id", Long.class);
        assertEquals(2, firstIds.size());
        assertTrue(firstIds.get(1) > firstIds.get(0));

        jdbcTemplate.update("UPDATE edu_user_notice SET read_at = ?, title = '旧标题' "
                + "WHERE recipient_user_id = 9001", AT.minusHours(1));
        assertNotNull(transactionTemplate.execute(
                status -> fanoutService.fanOutAnnouncement(announcement, AT)));
        assertEquals(AT.minusHours(1), jdbcTemplate.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE recipient_user_id = 9001",
                LocalDateTime.class));
        assertEquals("旧标题", jdbcTemplate.queryForObject(
                "SELECT title FROM edu_user_notice WHERE recipient_user_id = 9001", String.class));

        CourseAnnouncement secondAnnouncement = announcement(20002L, "fanout-title-2");
        assertEquals(Integer.valueOf(2), transactionTemplate.execute(
                status -> fanoutService.fanOutAnnouncement(secondAnnouncement, AT)));
        List<Long> allIds = jdbcTemplate.queryForList(
                "SELECT id FROM edu_user_notice ORDER BY id", Long.class);
        assertTrue(allIds.size() == 4);
        assertTrue(allIds.get(3) > firstIds.get(1));
    }

    @Test
    void upstreamFourKindsAreIdempotentAndBatchFailureRollsBack()
    {
        for (NoticeKind kind : List.of(NoticeKind.PREVIEW, NoticeKind.ACTIVITY,
                NoticeKind.ASSIGNMENT, NoticeKind.GRADE))
        {
            UpstreamNoticeEvent event = event(kind, 3000L + kind.ordinal(), List.of(9001L));
            assertEquals(Integer.valueOf(1), transactionTemplate.execute(status -> eventService.receive(event)),
                    kind.name());
            assertNotNull(transactionTemplate.execute(status -> eventService.receive(event)),
                    kind.name());
        }
        assertEquals(4, countRows());

        jdbcTemplate.update("UPDATE edu_user_notice SET read_at = ?, title = '旧标题' "
                + "WHERE recipient_user_id = 9001 AND notice_kind = 'GRADE'", AT.minusHours(1));
        UpstreamNoticeEvent grade = event(NoticeKind.GRADE,
                3000L + NoticeKind.GRADE.ordinal(), List.of(9001L));
        assertNotNull(transactionTemplate.execute(status -> eventService.receive(grade)));
        assertEquals(AT.minusHours(1), jdbcTemplate.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE recipient_user_id = 9001 "
                        + "AND notice_kind = 'GRADE'", LocalDateTime.class));
        assertEquals("旧标题", jdbcTemplate.queryForObject(
                "SELECT title FROM edu_user_notice WHERE recipient_user_id = 9001 "
                        + "AND notice_kind = 'GRADE'", String.class));

        // 用触发器让第二批失败：第一批 500 条已写入，第二批次必须随事务回滚。
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_notice");
        jdbcTemplate.execute("""
                CREATE TRIGGER trg_fail_notice BEFORE INSERT ON edu_user_notice
                FOR EACH ROW SET NEW.recipient_user_id =
                    IF(NEW.recipient_user_id = 999999, NULL, NEW.recipient_user_id)
                """);
        List<Long> recipients = new ArrayList<>();
        for (long userId = 1; userId <= 500; userId++)
        {
            recipients.add(userId);
        }
        recipients.add(999999L);
        UpstreamNoticeEvent failing = new UpstreamNoticeEvent("evt-rollback", NoticeKind.ASSIGNMENT,
                BINDING_ID, 3004L, "rollback", null, AT, AT, recipients);

        assertThrows(NoticePersistenceException.class,
                () -> transactionTemplate.execute(status -> eventService.receive(failing)));
        assertEquals(4, countRows());
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice WHERE notice_kind = 'ASSIGNMENT' "
                        + "AND source_id = 3004", Integer.class));
        jdbcTemplate.execute("DROP TRIGGER IF EXISTS trg_fail_notice");
    }

    @Test
    void readAllCurrentReadBlocksFanoutAndEventInsertThenRecoversAfterCommit() throws Exception
    {
        seedFourUnreadNotices();
        port.bindings = List.of(access());
        port.members = List.of(member(9001L));

        CountDownLatch locked = new CountDownLatch(1);
        AtomicReference<Throwable> transactionFailure = new AtomicReference<>();
        Thread readAllThread = new Thread(() ->
        {
            try
            {
                // StudentNoticeService 从 SecurityContext 读取当前用户；线程不继承默认 ThreadLocal。
                com.ruoyi.common.core.domain.entity.SysUser sysUser =
                        new com.ruoyi.common.core.domain.entity.SysUser(9001L);
                com.ruoyi.common.core.domain.model.LoginUser loginUser =
                        new com.ruoyi.common.core.domain.model.LoginUser(9001L, null, sysUser, java.util.Set.of());
                org.springframework.security.core.context.SecurityContextHolder.getContext()
                        .setAuthentication(new org.springframework.security.authentication
                                .UsernamePasswordAuthenticationToken(loginUser, null, java.util.List.of()));
                transactionTemplate.execute(status ->
                {
                    noticeService.markAllRead();
                    locked.countDown();
                    try
                    {
                        Thread.sleep(5000L);
                    }
                    catch (InterruptedException e)
                    {
                        Thread.currentThread().interrupt();
                    }
                    return null;
                });
            }
            catch (Throwable t)
            {
                transactionFailure.set(t);
                locked.countDown();
            }
        });
        readAllThread.start();
        assertTrue(locked.await(5, TimeUnit.SECONDS));

        boolean fanoutBlocked = false;
        boolean eventBlocked = false;
        long fanoutElapsedMs;
        long eventElapsedMs;
        try
        {
            long fanoutStart = System.nanoTime();
            try
            {
                shortTimeoutTransaction.execute(status -> fanoutServiceShort()
                        .fanOutAnnouncement(announcement(40001L, "during-read-all"), AT));
            }
            catch (NoticePersistenceException e)
            {
                fanoutBlocked = true;
                assertTrue(rootMessage(e).contains("Lock wait timeout"), rootMessage(e));
            }
            fanoutElapsedMs = (System.nanoTime() - fanoutStart) / 1_000_000L;

            long eventStart = System.nanoTime();
            try
            {
                shortTimeoutTransaction.execute(status -> eventServiceShort().receive(
                        event(NoticeKind.ASSIGNMENT, 40002L, List.of(9001L))));
            }
            catch (NoticePersistenceException e)
            {
                eventBlocked = true;
                assertTrue(rootMessage(e).contains("Lock wait timeout"), rootMessage(e));
            }
            eventElapsedMs = (System.nanoTime() - eventStart) / 1_000_000L;
        }
        finally
        {
            readAllThread.join(10000L);
        }
        assertNull(transactionFailure.get());
        System.out.printf("CONCURRENCY read_all_hold_ms=5000 fanout_blocked=%s fanout_ms=%d "
                        + "event_blocked=%s event_ms=%d%n",
                fanoutBlocked, fanoutElapsedMs, eventBlocked, eventElapsedMs);

        assertEquals(Integer.valueOf(1), transactionTemplate.execute(
                status -> fanoutService.fanOutAnnouncement(announcement(40003L, "after-commit"), AT)));
        assertEquals(Integer.valueOf(1), transactionTemplate.execute(
                status -> eventService.receive(event(NoticeKind.ASSIGNMENT, 40004L, List.of(9001L)))));
        int successfulDuringHold = (fanoutBlocked ? 0 : 1) + (eventBlocked ? 0 : 1);
        assertEquals(4 + successfulDuringHold + 2, countRows());
    }

    @Test
    void syntheticFanoutAndNoticeQueriesRecordTimings()
    {
        for (int size : new int[] { 500, 5000 })
        {
            jdbcTemplate.update("DELETE FROM edu_user_notice");
            port.members = members(size);
            long fanoutStart = System.nanoTime();
            int inserted = transactionTemplate.execute(status -> fanoutService.fanOutAnnouncement(
                    announcement(50000L + size, "synthetic-" + size), AT));
            long fanoutMs = (System.nanoTime() - fanoutStart) / 1_000_000L;
            assertEquals(size, inserted);
            assertEquals(size, countRows());

            // 为单个学生制造同等规模历史通知，测量列表/未读数查询。
            List<Object[]> heavyRows = new ArrayList<>(size);
            for (long sourceId = 1; sourceId <= size; sourceId++)
            {
                heavyRows.add(new Object[] { sourceId, "heavy", AT, AT });
            }
            jdbcTemplate.batchUpdate("""
                    INSERT INTO edu_user_notice
                        (recipient_user_id, binding_id, notice_kind, source_id, title,
                         visible_at, read_at, created_at)
                    VALUES (9001, 100, 'GRADE', ?, ?, ?, NULL, ?)
                    """, heavyRows);

            StudentNoticeQuery query = new StudentNoticeQuery();
            query.setRecipientUserId(9001L);
            query.setAt(AT);
            query.setBindings(List.of(new NoticeBindingScope(BINDING_ID, AT.minusDays(5), null)));
            query.setUnreadOnly(false);
            query.setLimit(100);
            query.setOffset(0);
            long listStart = System.nanoTime();
            long total = mapper.countVisible(query);
            int pageRows = mapper.selectVisiblePage(query).size();
            long listMs = (System.nanoTime() - listStart) / 1_000_000L;
            query.setUnreadOnly(true);
            long unreadStart = System.nanoTime();
            long unread = mapper.countVisible(query);
            long unreadMs = (System.nanoTime() - unreadStart) / 1_000_000L;

            assertTrue(total >= size);
            assertTrue(pageRows > 0);
            assertTrue(unread >= size);
            System.out.printf("METRIC fanout_size=%d fanout_ms=%d list_ms=%d unread_ms=%d total=%d unread=%d%n",
                    size, fanoutMs, listMs, unreadMs, total, unread);
        }
    }

    private AnnouncementNoticeFanoutService fanoutServiceShort()
    {
        return new AnnouncementNoticeFanoutService(port, shortTimeoutMapper);
    }

    private UpstreamNoticeEventService eventServiceShort()
    {
        return new UpstreamNoticeEventService(port, shortTimeoutMapper);
    }

    private void seedFourUnreadNotices()
    {
        for (long id = 1; id <= 4; id++)
        {
            jdbcTemplate.update("""
                    INSERT INTO edu_user_notice
                        (recipient_user_id, binding_id, notice_kind, source_id, title,
                         visible_at, read_at, created_at)
                    VALUES (?, ?, 'GRADE', ?, ?, ?, NULL, ?)
                    """, 9001L, BINDING_ID, 60000L + id, "seed-" + id,
                    AT.minusHours(1), AT.minusHours(1));
        }
    }

    private long countRows()
    {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_user_notice", Long.class);
    }

    private CourseAnnouncement announcement(long id, String title)
    {
        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setId(id);
        announcement.setBindingId(BINDING_ID);
        announcement.setTitle(title);
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setOpensAt(AT.plusMinutes(30));
        announcement.setCreatedAt(AT);
        return announcement;
    }

    private UpstreamNoticeEvent event(NoticeKind kind, long sourceId, List<Long> recipients)
    {
        return new UpstreamNoticeEvent("evt-" + kind.name() + "-" + sourceId, kind,
                BINDING_ID, sourceId, kind.name() + "-title", "content", AT, AT.plusMinutes(5),
                recipients);
    }

    private StudentAnnouncementMember member(long userId)
    {
        return new StudentAnnouncementMember(userId, BINDING_ID, CLASS_ID,
                AT.minusDays(2), null, new StudentAnnouncementAuthority(true, true, true));
    }

    private List<StudentAnnouncementMember> members(int size)
    {
        List<StudentAnnouncementMember> members = new ArrayList<>(size);
        for (long userId = 1; userId <= size; userId++)
        {
            members.add(member(userId));
        }
        return members;
    }

    private StudentAnnouncementAccess access()
    {
        return new StudentAnnouncementAccess(BINDING_ID, CLASS_ID, AT.minusDays(5), null,
                new StudentAnnouncementAuthority(true, true, true));
    }

    private String rootMessage(Throwable throwable)
    {
        Throwable current = throwable;
        while (current.getCause() != null)
        {
            current = current.getCause();
        }
        return current.getMessage() == null ? "" : current.getMessage();
    }

    private void createTables(DataSource dataSource) throws Exception
    {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute("""
                    CREATE TABLE edu_course_announcement (
                        id BIGINT PRIMARY KEY,
                        binding_id BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        opens_at DATETIME(3) NULL,
                        closes_at DATETIME(3) NULL
                    ) ENGINE=InnoDB
                    """);
            statement.execute("""
                    CREATE TABLE edu_user_notice (
                        id BIGINT NOT NULL AUTO_INCREMENT,
                        recipient_user_id BIGINT NOT NULL,
                        binding_id BIGINT NULL,
                        notice_kind VARCHAR(24) NOT NULL,
                        source_id BIGINT NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        visible_at DATETIME(3) NOT NULL,
                        read_at DATETIME(3) NULL,
                        created_at DATETIME(3) NOT NULL,
                        PRIMARY KEY (id),
                        UNIQUE KEY uq_edu_notice_recipient_source
                            (recipient_user_id, notice_kind, source_id),
                        KEY idx_edu_notice_recipient_read
                            (recipient_user_id, read_at, visible_at),
                        KEY idx_edu_notice_binding (binding_id)
                    ) ENGINE=InnoDB
                    """);
        }
    }

    private StudentNoticeMapper buildMapper(DataSource dataSource) throws Exception
    {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/education/StudentNoticeMapper.xml"));
        return new SqlSessionTemplate(factory.getObject()).getMapper(StudentNoticeMapper.class);
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

    private String withDatabase(String serverUrl, String database)
    {
        int queryStart = serverUrl.indexOf('?');
        String base = queryStart >= 0 ? serverUrl.substring(0, queryStart) : serverUrl;
        String query = queryStart >= 0 ? serverUrl.substring(queryStart) : "";
        return base + database + query;
    }

    private String appendParameter(String url, String parameter)
    {
        return url + (url.contains("?") ? "&" : "?") + parameter;
    }

    static class FakePort implements StudentAnnouncementAuthorizationPort
    {
        private List<StudentAnnouncementMember> members = List.of();

        private List<StudentAnnouncementAccess> bindings = List.of();

        @Override
        public StudentAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at)
        {
            return bindings.isEmpty() ? null : bindings.get(0);
        }

        @Override
        public List<StudentAnnouncementAccess> listActiveBindings(Long userId, LocalDateTime at)
        {
            return bindings;
        }

        @Override
        public List<StudentAnnouncementMember> listActiveStudents(Long bindingId, LocalDateTime at)
        {
            return members;
        }
    }
}
