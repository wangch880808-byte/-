package com.ruoyi.admin.notice;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.lang.reflect.Constructor;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Statement;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.env.YamlPropertySourceLoader;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.PropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ScriptUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import com.alibaba.druid.pool.DruidDataSource;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.impl.EduMembershipTimelineServiceImpl;
import com.ruoyi.education.course.authorization.MembershipTimelineStudentAnnouncementAuthorizationAdapter;
import com.ruoyi.education.course.authorization.StudentAnnouncementAccess;
import com.ruoyi.education.course.authorization.StudentAnnouncementAuthorizationPort;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.exception.NoticeAccessDeniedException;
import com.ruoyi.education.course.exception.NoticeNotFoundException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;
import com.ruoyi.education.course.service.AnnouncementNoticeFanoutService;
import com.ruoyi.education.course.service.StudentNoticePage;
import com.ruoyi.education.course.service.StudentNoticeReadResult;
import com.ruoyi.education.course.service.StudentNoticeService;
import com.ruoyi.education.course.service.UpstreamNoticeEvent;
import com.ruoyi.education.course.service.UpstreamNoticeEventService;
import com.ruoyi.education.mapper.EduMembershipTimelineMapper;
import com.ruoyi.framework.config.DruidConfig;
import com.ruoyi.framework.config.properties.DruidProperties;

/**
 * 当前仓库 Druid 数据源配置下的学生公告/通知时间语义验证。
 *
 * <p>装配方式：加载实际 {@code application-druid.yml}，通过真实
 * {@link DruidProperties} 与 {@link DruidConfig} 创建 {@code masterDataSource} 和
 * {@code dynamicDataSource}，再以实际 {@code dynamicDataSource} 组装 MyBatis Mapper、
 * 第 2 组时点 Service、#40 授权适配器和第 6 组通知服务。A 组不执行
 * {@code SET time_zone}，不添加 {@code connectionTimeZone} /
 * {@code forceConnectionTimeZoneToSession}，只替换隔离实例的主机、端口、库和临时凭据。</p>
 *
 * <p>本测试不启动 Web、Redis 或真实登录，不代表完整应用启动验证。固定 Clock 和
 * SecurityContext 用户只用于可重复测试。schema 为按正式 DDL 精简的测试夹具，
 * 不进入生产资源。默认未提供 {@code notice.mysql.url} 时跳过；提供后连接或装配
 * 失败直接报错，不转成跳过。</p>
 */
class NoticeDruidTimezoneIntegrationTest
{
    private static final Long BINDING_ID = 100L;

    private static final Long CLASS_ID = 20L;

    private static final Long COURSE_SPACE_ID = 10L;

    private static final Long ORGANIZATION_ID = 1L;

    private static final Long STUDENT_ID = 9001L;

    private static final LocalDateTime BINDING_PUBLISHED =
            LocalDateTime.of(2026, 11, 1, 0, 0, 0, 123_000_000);

    private static final LocalDateTime JOINED =
            LocalDateTime.of(2026, 11, 20, 8, 0, 0, 123_000_000);

    private static final LocalDateTime LEFT =
            LocalDateTime.of(2026, 11, 20, 10, 0, 0, 456_000_000);

    private static final LocalDateTime NINE_AM = LocalDateTime.of(2026, 11, 20, 9, 0);

    private static final LocalDateTime NINE_THIRTY = LocalDateTime.of(2026, 11, 20, 9, 30);

    private static final LocalDateTime ELEVEN_AM = LocalDateTime.of(2026, 11, 20, 11, 0);

    private String serverUrl;

    private String database;

    private String username;

    private String password;

    private String isolatedUrl;

    private String defaultDruidUrl;

    private AnnotationConfigApplicationContext context;

    private DataSource dynamicDataSource;

    private DruidDataSource masterDataSource;

    private JdbcTemplate jdbc;

    private StudentNoticeMapper noticeMapper;

    private EduMembershipTimelineMapper timelineMapper;

    private StudentAnnouncementAuthorizationPort authorizationPort;

    private AnnouncementNoticeFanoutService fanoutService;

    private UpstreamNoticeEventService eventService;

    private StudentNoticeService noticeService;

    private TransactionTemplate transactionTemplate;

    /**
     * 创建随机数据库、构建真实 Druid Spring 上下文并加载测试 schema。
     *
     * @throws Exception 数据库或 Spring 装配失败时直接失败，不转成跳过
     */
    @BeforeEach
    void setUp() throws Exception
    {
        String configuredUrl = System.getProperty("notice.mysql.url", "");
        Assumptions.assumeTrue(!configuredUrl.isBlank(),
                "notice.mysql.url not set; skipping Druid timezone validation");
        username = System.getProperty("notice.mysql.user", "root");
        password = System.getProperty("notice.mysql.password", "");

        String endpoint = endpointOf(configuredUrl);
        serverUrl = "jdbc:mysql://" + endpoint + "/";
        database = "notice_druid_it_" + Long.toHexString(System.nanoTime());
        try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                Statement statement = connection.createStatement())
        {
            statement.execute("CREATE DATABASE `" + database
                    + "` CHARACTER SET utf8mb4 COLLATE utf8mb4_0900_ai_ci");
        }

        defaultDruidUrl = defaultDruidUrl();
        isolatedUrl = replaceEndpoint(defaultDruidUrl, endpoint, database);
        StandardEnvironment environment = buildEnvironment();

        context = new AnnotationConfigApplicationContext();
        context.setEnvironment(environment);
        context.register(DruidProperties.class, DruidConfig.class, TestConfiguration.class);
        context.refresh();

        dynamicDataSource = context.getBean("dynamicDataSource", DataSource.class);
        DataSource master = context.getBean("masterDataSource", DataSource.class);
        assertTrue(master instanceof DruidDataSource,
                "DruidConfig should expose a DruidDataSource");
        masterDataSource = (DruidDataSource) master;
        jdbc = new JdbcTemplate(dynamicDataSource);
        try (Connection connection = dynamicDataSource.getConnection())
        {
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("mysql/notice-druid-minimal-schema.sql"));
        }
        seedBaseRows();

        noticeMapper = context.getBean(StudentNoticeMapper.class);
        timelineMapper = context.getBean(EduMembershipTimelineMapper.class);
        authorizationPort = context.getBean(StudentAnnouncementAuthorizationPort.class);
        fanoutService = context.getBean(AnnouncementNoticeFanoutService.class);
        eventService = context.getBean(UpstreamNoticeEventService.class);
        noticeService = context.getBean(StudentNoticeService.class);
        transactionTemplate = context.getBean(TransactionTemplate.class);
        logDruidConfiguration();
    }

    /**
     * 关闭 Spring 上下文和 Druid 池，删除本轮随机库；不触碰共享库。
     *
     * @throws Exception 清理连接失败时报告
     */
    @AfterEach
    void tearDown() throws Exception
    {
        SecurityContextHolder.clearContext();
        try
        {
            if (masterDataSource != null)
            {
                masterDataSource.close();
            }
        }
        finally
        {
            if (context != null)
            {
                context.close();
            }
        }
        if (serverUrl != null && database != null)
        {
            try (Connection connection = DriverManager.getConnection(serverUrl, username, password);
                    Statement statement = connection.createStatement())
            {
                statement.execute("DROP DATABASE IF EXISTS `" + database + "`");
            }
        }
    }

    /**
     * 直接以 SQL 字面量写入 DATETIME(3)，再从真实 Druid 连接读回并验证
     * joinedAt 包含、leftAt 不包含和绑定发布/结束边界。
     */
    @Test
    void knownDatetime3ValuesAndHalfOpenBoundaries()
    {
        seedStudent(STUDENT_ID, "'2026-11-20 08:00:00.123'",
                "'2026-11-20 10:00:00.456'");
        assertEquals(JOINED, jdbc.queryForObject(
                "SELECT joined_at FROM edu_class_student WHERE user_id = ?",
                LocalDateTime.class, STUDENT_ID));
        assertEquals(LEFT, jdbc.queryForObject(
                "SELECT left_at FROM edu_class_student WHERE user_id = ?",
                LocalDateTime.class, STUDENT_ID));
        assertEquals(BINDING_PUBLISHED, jdbc.queryForObject(
                "SELECT published_at FROM edu_course_class_binding WHERE id = ?",
                LocalDateTime.class, BINDING_ID));

        assertFalse(authorizationPort.resolve(BINDING_ID, STUDENT_ID,
                JOINED.minusNanos(1_000_000L)).authority().currentStudent());
        assertTrue(authorizationPort.resolve(BINDING_ID, STUDENT_ID, JOINED)
                .authority().currentStudent());
        assertTrue(authorizationPort.resolve(BINDING_ID, STUDENT_ID,
                LEFT.minusNanos(1_000_000L)).authority().currentStudent());
        assertFalse(authorizationPort.resolve(BINDING_ID, STUDENT_ID, LEFT)
                .authority().currentStudent());
        assertEquals(1, authorizationPort.listActiveStudents(BINDING_ID, JOINED).size());
        assertTrue(authorizationPort.listActiveStudents(BINDING_ID, LEFT).isEmpty());

        jdbc.update("UPDATE edu_course_class_binding SET ended_at = "
                + "'2026-12-01 00:00:00.456' WHERE id = ?", BINDING_ID);
        LocalDateTime ended = LocalDateTime.of(2026, 12, 1, 0, 0, 0, 456_000_000);
        assertTrue(authorizationPort.resolve(BINDING_ID, STUDENT_ID,
                ended.minusNanos(1_000_000L)).authority().bindingActive());
        assertFalse(authorizationPort.resolve(BINDING_ID, STUDENT_ID, ended)
                .authority().bindingActive());
    }

    /**
     * 事件 occurredAt 与读取 now 分开：事件 9 点发生、学生 10 点退班后投递，
     * 接收人仍按 9 点确定；11 点读取按当前成员段拒绝。
     */
    @Test
    void eventOccurredAtAndReadNowAreSeparated()
    {
        seedStudent(STUDENT_ID, "'2026-11-20 08:00:00.000'",
                "'2026-11-20 10:00:00.000'");
        UpstreamNoticeEvent event = new UpstreamNoticeEvent("druid-evt-9",
                NoticeKind.ASSIGNMENT, BINDING_ID, 5001L, "九点事件", null, NINE_AM,
                NINE_AM, null);

        assertEquals(Integer.valueOf(1),
                transactionTemplate.execute(status -> eventService.receive(event)));
        assertEquals(1, noticeCount());
        Long noticeId = noticeId(STUDENT_ID, NoticeKind.ASSIGNMENT, 5001L);
        assertEquals(NINE_AM, jdbc.queryForObject(
                "SELECT visible_at FROM edu_user_notice WHERE id = ?",
                LocalDateTime.class, noticeId));

        authenticate(STUDENT_ID);
        StudentNoticePage atNineThirty = noticeServiceAt(NINE_THIRTY)
                .list(BINDING_ID, null, false, 1, 10);
        assertEquals(1, atNineThirty.total());

        StudentNoticeService atEleven = noticeServiceAt(ELEVEN_AM);
        assertThrows(NoticeAccessDeniedException.class,
                () -> atEleven.list(BINDING_ID, null, false, 1, 10));
        assertThrows(NoticeNotFoundException.class, () -> atEleven.get(noticeId));
        assertEquals(1, noticeCount());
    }

    /**
     * 公告 fan-out 的 visibleAt 和已读 readAt 保留毫秒值，两个 JVM 时区下
     * 均应落在同一 UTC 时间，不出现 8 小时偏移。
     */
    @Test
    void announcementVisibleAtAndReadAtKeepUtcMilliseconds()
    {
        seedStudent(STUDENT_ID, "'2026-11-20 08:00:00.000'", "NULL");
        LocalDateTime opensAt = LocalDateTime.of(2026, 11, 20, 9, 0, 0, 123_000_000);
        jdbc.update("INSERT INTO edu_course_announcement (id, binding_id, title, body, "
                + "status, opens_at, closes_at, published_by_user_id, created_by_user_id) "
                + "VALUES (20001, ?, '公告A', 'body', 'PUBLISHED', "
                + "'2026-11-20 09:00:00.123', NULL, 1, 1)", BINDING_ID);
        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setId(20001L);
        announcement.setBindingId(BINDING_ID);
        announcement.setTitle("公告A");
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setOpensAt(opensAt);
        announcement.setCreatedAt(NINE_AM);

        assertEquals(Integer.valueOf(1), transactionTemplate.execute(status ->
                fanoutService.fanOutAnnouncement(announcement, NINE_AM)));
        Long noticeId = noticeId(STUDENT_ID, NoticeKind.ANNOUNCEMENT, 20001L);
        assertEquals(opensAt, jdbc.queryForObject(
                "SELECT visible_at FROM edu_user_notice WHERE id = ?",
                LocalDateTime.class, noticeId));

        authenticate(STUDENT_ID);
        StudentNoticeService atNineThirty = noticeServiceAt(NINE_THIRTY);
        assertEquals(1, atNineThirty.list(BINDING_ID, null, false, 1, 10).total());
        StudentNoticeReadResult read = transactionTemplate.execute(status ->
                atNineThirty.markRead(noticeId));
        assertFalse(read.alreadyRead());
        assertEquals(NINE_THIRTY, read.readAt());
        assertEquals(NINE_THIRTY, jdbc.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE id = ?",
                LocalDateTime.class, noticeId));
        assertEquals(0, atNineThirty.unreadCount(BINDING_ID).unreadCount());
    }

    /**
     * 通过实际 dynamicDataSource 同时借出两个物理连接，归还后重新借用，
     * 记录 CONNECTION_ID、会话时区和池状态，并验证业务查询仍返回同一 UTC 时间。
     */
    @Test
    void druidPoolReuseKeepsBusinessTimeSemantics() throws Exception
    {
        seedStudent(STUDENT_ID, "'2026-11-20 08:00:00.123'", "NULL");
        assertEquals(isolatedUrl, masterDataSource.getUrl());
        assertEquals(username, masterDataSource.getUsername());
        assertEquals(5, masterDataSource.getInitialSize());
        assertEquals(10, masterDataSource.getMinIdle());
        assertEquals(20, masterDataSource.getMaxActive());
        assertEquals("SELECT 1 FROM DUAL", masterDataSource.getValidationQuery());

        long firstId;
        long secondId;
        try (Connection first = dynamicDataSource.getConnection();
                Connection second = dynamicDataSource.getConnection())
        {
            firstId = connectionId(first);
            secondId = connectionId(second);
            assertNotEquals(firstId, secondId,
                    "two simultaneously borrowed connections must be physical");
        }

        long reusedId;
        String sessionTimeZone;
        String utcOffset;
        try (Connection connection = dynamicDataSource.getConnection())
        {
            assertTrue(connection.isValid(3));
            reusedId = connectionId(connection);
            sessionTimeZone = single(connection, "SELECT @@session.time_zone");
            utcOffset = single(connection, "SELECT TIMEDIFF(NOW(), UTC_TIMESTAMP())");
            assertEquals(1, singleInt(connection, "SELECT 1"));
        }

        StudentAnnouncementAccess access =
                authorizationPort.resolve(BINDING_ID, STUDENT_ID, NINE_AM);
        assertTrue(access.authority().active());
        assertEquals(JOINED, access.joinedAt());
        assertEquals(JOINED, jdbc.queryForObject(
                "SELECT joined_at FROM edu_class_student WHERE user_id = ?",
                LocalDateTime.class, STUDENT_ID));
        assertEquals(1, timelineMapper.selectStudentsInBinding(BINDING_ID,
                java.sql.Timestamp.from(NINE_AM.toInstant(ZoneOffset.UTC))).size());

        System.out.printf("DRUID_POOL jvm=%s session_tz=%s offset=%s conn_ids=%d,%d,reused=%d "
                        + "active=%d pooling=%d create=%d%n",
                java.util.TimeZone.getDefault().getID(), sessionTimeZone, utcOffset,
                firstId, secondId, reusedId, masterDataSource.getActiveCount(),
                masterDataSource.getPoolingCount(), masterDataSource.getCreateCount());
    }

    private StandardEnvironment buildEnvironment() throws Exception
    {
        StandardEnvironment environment = new StandardEnvironment();
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (String file : List.of("application.yml", "application-druid.yml"))
        {
            for (PropertySource<?> source : loader.load(file,
                    new ClassPathResource(file)))
            {
                environment.getPropertySources().addLast(source);
            }
        }
        Map<String, Object> overrides = new HashMap<>();
        overrides.put("spring.datasource.druid.master.url", isolatedUrl);
        overrides.put("spring.datasource.druid.master.username", username);
        overrides.put("spring.datasource.druid.master.password", password);
        environment.getPropertySources().addFirst(
                new MapPropertySource("notice-druid-test-overrides", overrides));
        return environment;
    }

    /**
     * 从实际 application-druid.yml 读取默认 JDBC URL 的占位符默认值，
     * 只替换隔离实例的 host/port/database，保留其余连接参数。
     */
    private String defaultDruidUrl() throws Exception
    {
        YamlPropertySourceLoader loader = new YamlPropertySourceLoader();
        for (PropertySource<?> source : loader.load("application-druid.yml",
                new ClassPathResource("application-druid.yml")))
        {
            Object value = source.getProperty("spring.datasource.druid.master.url");
            if (value != null)
            {
                String text = value.toString();
                if (text.startsWith("${") && text.contains(":"))
                {
                    return text.substring(text.indexOf(':') + 1,
                            text.lastIndexOf('}'));
                }
                return text;
            }
        }
        throw new IllegalStateException("application-druid.yml 缺少 master url");
    }

    private String replaceEndpoint(String url, String endpoint, String databaseName)
    {
        String prefix = "jdbc:mysql://";
        if (!url.startsWith(prefix))
        {
            throw new IllegalStateException("A 组只支持实际配置中的 MySQL JDBC URL");
        }
        int pathStart = url.indexOf('/', prefix.length());
        String query = pathStart >= 0 && url.indexOf('?', pathStart) >= 0
                ? url.substring(url.indexOf('?', pathStart)) : "";
        return prefix + endpoint + "/" + databaseName + query;
    }

    private String endpointOf(String url)
    {
        String prefix = "jdbc:mysql://";
        if (!url.startsWith(prefix))
        {
            throw new IllegalArgumentException("notice.mysql.url must be a MySQL JDBC URL");
        }
        String remainder = url.substring(prefix.length());
        int slash = remainder.indexOf('/');
        return slash >= 0 ? remainder.substring(0, slash) : remainder;
    }

    private void seedBaseRows()
    {
        jdbc.update("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, "
                + "phonenumber, status, del_flag) VALUES (1, NULL, 'owner', '负责人', '', '0', '0')");
        jdbc.update("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, "
                + "phonenumber, status, del_flag) VALUES (?, NULL, 'student9001', '学生一', '', '0', '0')",
                STUDENT_ID);
        jdbc.update("INSERT INTO edu_term (id, organization_id, code, name, starts_on, "
                + "ends_on, is_current) VALUES (1, ?, 'T-2026', '2026 秋', '2026-09-01', "
                + "'2027-01-31', 0)", ORGANIZATION_ID);
        jdbc.update("INSERT INTO edu_official_course (id, organization_id, code, name, "
                + "status) VALUES (1, ?, 'OC-1', '测试课程', 'ACTIVE')", ORGANIZATION_ID);
        jdbc.update("INSERT INTO edu_course_space (id, organization_id, "
                + "official_course_id, owner_user_id, title, status) VALUES (?, ?, 1, 1, "
                + "'测试课程空间', 'ACTIVE')", COURSE_SPACE_ID, ORGANIZATION_ID);
        jdbc.update("INSERT INTO edu_teaching_class (id, organization_id, term_id, "
                + "official_course_id, code, name, class_kind, status) VALUES (?, ?, 1, 1, "
                + "'TC-1', '测试教学班', 'FORMAL', 'ACTIVE')", CLASS_ID, ORGANIZATION_ID);
        jdbc.update("INSERT INTO edu_course_class_binding (id, organization_id, "
                + "teaching_class_id, course_space_id, bound_by_user_id, published_at, ended_at) "
                + "VALUES (?, ?, ?, ?, 1, '2026-11-01 00:00:00.123', NULL)", BINDING_ID,
                ORGANIZATION_ID, CLASS_ID, COURSE_SPACE_ID);
    }

    private void seedStudent(Long userId, String joinedLiteral, String leftLiteral)
    {
        jdbc.update("INSERT INTO edu_class_student (teaching_class_id, user_id, joined_at, "
                + "left_at, recorded_by_user_id) VALUES (?, ?, " + joinedLiteral + ", "
                + leftLiteral + ", 1)", CLASS_ID, userId);
    }

    private long noticeCount()
    {
        return jdbc.queryForObject("SELECT COUNT(*) FROM edu_user_notice", Long.class);
    }

    private Long noticeId(Long recipientUserId, NoticeKind kind, long sourceId)
    {
        return jdbc.queryForObject("SELECT id FROM edu_user_notice WHERE recipient_user_id = ? "
                + "AND notice_kind = ? AND source_id = ?", Long.class, recipientUserId,
                kind.name(), sourceId);
    }

    private StudentNoticeService noticeServiceAt(LocalDateTime at)
    {
        return newStudentNoticeService(noticeMapper, authorizationPort,
                Clock.fixed(at.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));
    }

    private static StudentNoticeService newStudentNoticeService(StudentNoticeMapper mapper,
            StudentAnnouncementAuthorizationPort port, Clock clock)
    {
        try
        {
            Constructor<StudentNoticeService> constructor = StudentNoticeService.class
                    .getDeclaredConstructor(StudentNoticeMapper.class,
                            StudentAnnouncementAuthorizationPort.class, Clock.class);
            constructor.setAccessible(true);
            return constructor.newInstance(mapper, port, clock);
        }
        catch (ReflectiveOperationException e)
        {
            throw new IllegalStateException("无法装配 StudentNoticeService 测试夹具", e);
        }
    }

    private void authenticate(Long userId)
    {
        SysUser sysUser = new SysUser(userId);
        LoginUser loginUser = new LoginUser(userId, null, sysUser, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    private void logDruidConfiguration()
    {
        String version = jdbc.queryForObject("SELECT VERSION()", String.class);
        String globalTimeZone = jdbc.queryForObject("SELECT @@global.time_zone", String.class);
        String sessionTimeZone = jdbc.queryForObject("SELECT @@session.time_zone", String.class);
        String systemTimeZone = jdbc.queryForObject("SELECT @@system_time_zone", String.class);
        String isolation = jdbc.queryForObject("SELECT @@transaction_isolation", String.class);
        String utcOffset = jdbc.queryForObject(
                "SELECT TIMEDIFF(NOW(), UTC_TIMESTAMP())", String.class);
        System.out.printf("DRUID_TZ jvm=%s db=%s version=%s global_tz=%s session_tz=%s "
                        + "system_tz=%s offset=%s isolation=%s url=%s initialSize=%d "
                        + "minIdle=%d maxActive=%d%n",
                java.util.TimeZone.getDefault().getID(), database, version, globalTimeZone,
                sessionTimeZone, systemTimeZone, utcOffset, isolation, maskedUrl(),
                masterDataSource.getInitialSize(), masterDataSource.getMinIdle(),
                masterDataSource.getMaxActive());
    }

    private String maskedUrl()
    {
        return isolatedUrl;
    }

    private static long connectionId(Connection connection) throws Exception
    {
        return singleLong(connection, "SELECT CONNECTION_ID()");
    }

    private static String single(Connection connection, String sql) throws Exception
    {
        try (Statement statement = connection.createStatement();
                java.sql.ResultSet result = statement.executeQuery(sql))
        {
            assertTrue(result.next());
            return result.getString(1);
        }
    }

    private static int singleInt(Connection connection, String sql) throws Exception
    {
        try (Statement statement = connection.createStatement();
                java.sql.ResultSet result = statement.executeQuery(sql))
        {
            assertTrue(result.next());
            return result.getInt(1);
        }
    }

    private static long singleLong(Connection connection, String sql) throws Exception
    {
        try (Statement statement = connection.createStatement();
                java.sql.ResultSet result = statement.executeQuery(sql))
        {
            assertTrue(result.next());
            return result.getLong(1);
        }
    }

    /**
     * 最小 Spring 配置：只启用 Boot 配置绑定、真实 DruidConfig/DruidProperties、
     * MyBatis Mapper 和第 6 组真实服务；不加载 Web/Redis/Controller。
     */
    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties
    static class TestConfiguration
    {
        @Bean
        @ConfigurationProperties("spring.datasource")
        DataSourceProperties dataSourceProperties()
        {
            return new DataSourceProperties();
        }

        @Bean
        static org.springframework.context.support.PropertySourcesPlaceholderConfigurer
                propertySourcesPlaceholderConfigurer()
        {
            return new org.springframework.context.support.PropertySourcesPlaceholderConfigurer();
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(
                @Qualifier("dynamicDataSource") DataSource dataSource) throws Exception
        {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            PathMatchingResourcePatternResolver resolver =
                    new PathMatchingResourcePatternResolver();
            Resource[] timeline = resolver.getResources(
                    "classpath*:mapper/education/EduMembershipTimelineMapper.xml");
            Resource[] notice = resolver.getResources(
                    "classpath*:mapper/education/StudentNoticeMapper.xml");
            Resource[] all = Arrays.copyOf(timeline, timeline.length + notice.length);
            System.arraycopy(notice, 0, all, timeline.length, notice.length);
            factory.setMapperLocations(all);
            return factory.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory factory)
        {
            return new SqlSessionTemplate(factory);
        }

        @Bean
        StudentNoticeMapper studentNoticeMapper(SqlSessionTemplate template)
        {
            return template.getMapper(StudentNoticeMapper.class);
        }

        @Bean
        EduMembershipTimelineMapper eduMembershipTimelineMapper(SqlSessionTemplate template)
        {
            return template.getMapper(EduMembershipTimelineMapper.class);
        }

        @Bean
        EduMembershipTimelineService eduMembershipTimelineService(
                EduMembershipTimelineMapper mapper)
        {
            EduMembershipTimelineServiceImpl service = new EduMembershipTimelineServiceImpl();
            ReflectionTestUtils.setField(service, "mapper", mapper);
            return service;
        }

        @Bean
        StudentAnnouncementAuthorizationPort studentAnnouncementAuthorizationPort(
                ObjectProvider<EduMembershipTimelineService> provider)
        {
            return new MembershipTimelineStudentAnnouncementAuthorizationAdapter(provider);
        }

        @Bean
        AnnouncementNoticeFanoutService announcementNoticeFanoutService(
                ObjectProvider<StudentAnnouncementAuthorizationPort> provider,
                StudentNoticeMapper mapper)
        {
            return new AnnouncementNoticeFanoutService(provider, mapper);
        }

        @Bean
        UpstreamNoticeEventService upstreamNoticeEventService(
                ObjectProvider<StudentAnnouncementAuthorizationPort> provider,
                StudentNoticeMapper mapper)
        {
            return new UpstreamNoticeEventService(provider, mapper);
        }

        @Bean
        Clock fixedClock()
        {
            return Clock.fixed(Instant.parse("2026-11-20T09:30:00Z"), ZoneOffset.UTC);
        }

        @Bean
        StudentNoticeService studentNoticeService(StudentNoticeMapper mapper,
                StudentAnnouncementAuthorizationPort port, Clock clock)
        {
            return newStudentNoticeService(mapper, port, clock);
        }

        @Bean
        TransactionTemplate transactionTemplate(
                @Qualifier("dynamicDataSource") DataSource dataSource)
        {
            return new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        }
    }
}
