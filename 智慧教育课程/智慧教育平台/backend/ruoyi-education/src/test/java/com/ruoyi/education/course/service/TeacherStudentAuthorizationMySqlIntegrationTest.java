package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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
import java.util.List;
import java.util.Set;
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
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.impl.EduMembershipTimelineServiceImpl;
import com.ruoyi.education.course.authorization.MembershipTimelineStudentAnnouncementAuthorizationAdapter;
import com.ruoyi.education.course.authorization.MembershipTimelineTeachingClassAuthorizationAdapter;
import com.ruoyi.education.course.authorization.StudentAnnouncementAuthorizationPort;
import com.ruoyi.education.course.authorization.TeachingClassAuthorizationPort;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.exception.AnnouncementAccessDeniedException;
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.mapper.CourseAnnouncementMapper;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;
import com.ruoyi.education.mapper.EduMembershipTimelineMapper;
import com.ruoyi.education.mapper.EduPortalMapper;

/**
 * 教师公告授权与学生通知授权的真实 MySQL 组合测试。
 *
 * <p>装配链路（除固定时钟和测试身份外均为真实实现，不使用 FakePort、Mockito 或 H2）：
 * 第 2 组 {@link EduMembershipTimelineServiceImpl} 及其 MyBatis Mapper/XML
 * → {@link MembershipTimelineTeachingClassAuthorizationAdapter}
 * → 第 6 组 {@link CourseAnnouncementService}；公告发布后
 * → {@link MembershipTimelineStudentAnnouncementAuthorizationAdapter}
 * → {@link AnnouncementNoticeFanoutService} → 真实 {@link StudentNoticeService}
 * → 真实 MySQL。</p>
 *
 * <p>默认跳过；通过系统属性提供可丢弃的 MySQL 服务地址后执行：</p>
 *
 * <pre>
 * -Dnotice.mysql.url=jdbc:mysql://127.0.0.1:56353/teacher_auth_it?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC&amp;connectionTimeZone=UTC
 * -Dnotice.mysql.user=root
 * -Dnotice.mysql.password=
 * </pre>
 *
 * <p>每个测试创建随机数据库，加载学生通知精简 schema 和本测试的教师补充 schema，
 * 结束后删除。固定 {@link Clock} 和 SecurityContext 用户只是测试夹具，不代表
 * 真实登录、HTTP、角色拦截或审计联调。</p>
 */
class TeacherStudentAuthorizationMySqlIntegrationTest
{
    private static final Long BINDING_ID = 100L;

    private static final Long CLASS_ID = 20L;

    private static final Long COURSE_SPACE_ID = 10L;

    private static final Long ORGANIZATION_ID = 1L;

    private static final Long TEACHER_ID = 8001L;

    private static final Long LIMITED_TEACHER_ID = 8002L;

    private static final Long NON_TEACHER_ID = 8003L;

    private static final Long TEAM_TEACHER_ID = 8004L;

    private static final Long STUDENT_ONE = 9001L;

    private static final Long STUDENT_TWO = 9002L;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 20, 9, 0, 0);

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
                "notice.mysql.url not set; skipping real MySQL teacher/student authorization test");

        username = System.getProperty("notice.mysql.user", "root");
        password = System.getProperty("notice.mysql.password", "");
        serverUrl = deriveServerUrl(configuredUrl);
        database = "teacher_auth_it_" + Long.toHexString(System.nanoTime());

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
            ScriptUtils.executeSqlScript(connection,
                    new ClassPathResource("mysql/teacher-auth-extra-schema.sql"));
        }

        harness = buildHarness(dataSource);
        seedBaseRows();
        assertEquals("+00:00", harness.jdbc.queryForObject(
                "SELECT @@session.time_zone", String.class));
        System.out.printf("MYSQL_IT teacher-student db=%s version=%s session_tz=%s%n",
                database,
                harness.jdbc.queryForObject("SELECT VERSION()", String.class),
                harness.jdbc.queryForObject("SELECT @@session.time_zone", String.class));
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
     * 合法教师创建并发布公告后，学生适配器按发布时点的在班成员生成通知；
     * 学生列表、未读数、详情和标记已读使用同一成员范围，读后状态保持一致。
     */
    @Test
    void authorizedTeacherPublishesAndStudentsSeeConsistentNotices()
    {
        // 直接班级任课的 CO_TEACHER 且 canPublish=true：按契约值保留发布权限。
        seedTeacher(TEACHER_ID, "CO_TEACHER", true);
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        seedStudent(STUDENT_TWO, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        authenticate(TEACHER_ID);

        CourseAnnouncement draft = harness.tx.execute(status ->
                harness.announcementService.createDraft(BINDING_ID, "期中公告", "正文", null, null));
        assertNotNull(draft.getId());
        assertEquals(AnnouncementStatus.DRAFT, draft.getStatus());

        CourseAnnouncement published = harness.tx.execute(status ->
                harness.announcementService.publish(BINDING_ID, draft.getId()));
        assertEquals(AnnouncementStatus.PUBLISHED, published.getStatus());
        assertEquals(2, noticeCount());

        Long firstNotice = noticeId(STUDENT_ONE, draft.getId());
        assertNotNull(firstNotice);
        assertNotNull(noticeId(STUDENT_TWO, draft.getId()));

        authenticate(STUDENT_ONE);
        StudentNoticeService noticeService = harness.noticeService(NOW.plusMinutes(5));
        StudentNoticePage page = noticeService.list(BINDING_ID, null, false, 1, 10);
        assertEquals(1, page.total());
        assertEquals("期中公告", page.rows().get(0).getTitle());
        assertEquals("期中公告", noticeService.get(firstNotice).getTitle());
        assertEquals(1, noticeService.unreadCount(BINDING_ID).unreadCount());

        StudentNoticeReadResult firstRead = harness.tx.execute(status ->
                noticeService.markRead(firstNotice));
        assertFalse(firstRead.alreadyRead());
        assertEquals(NOW.plusMinutes(5), firstRead.readAt());
        assertEquals(0, noticeService.unreadCount(BINDING_ID).unreadCount());
        assertEquals(0, noticeService.list(BINDING_ID, null, true, 1, 10).total());
        assertNotNull(harness.jdbc.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE id = ?", LocalDateTime.class,
                firstNotice));

        authenticate(STUDENT_TWO);
        StudentNoticeService secondStudent = harness.noticeService(NOW.plusMinutes(5));
        assertEquals(1, secondStudent.list(BINDING_ID, null, false, 1, 10).total());
        assertEquals(1, secondStudent.unreadCount(BINDING_ID).unreadCount());
    }

    /** 无发布权限的受限教师不能创建草稿，且不产生公告或通知写入。 */
    @Test
    void limitedTeacherCannotCreateDraftOrWriteAnything()
    {
        seedTeacher(LIMITED_TEACHER_ID, "CO_TEACHER", false);
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        authenticate(LIMITED_TEACHER_ID);

        assertThrows(AnnouncementAccessDeniedException.class, () -> harness.tx.execute(status ->
                harness.announcementService.createDraft(BINDING_ID, "标题", "正文", null, null)));

        assertEquals(0, announcementCount());
        assertEquals(0, noticeCount());
    }

    /** 课程团队 fallback 的 canPublish=false：真实 SQL 返回团队教师，但仍无发布权限。 */
    @Test
    void teamFallbackCannotPublishThroughRealSchema()
    {
        seedTeamTeacher(TEAM_TEACHER_ID);
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        authenticate(TEAM_TEACHER_ID);

        assertThrows(AnnouncementAccessDeniedException.class, () -> harness.tx.execute(status ->
                harness.announcementService.createDraft(BINDING_ID, "团队草稿", "正文", null, null)));

        assertEquals(0, announcementCount());
        assertEquals(0, noticeCount());
    }

    /** 非任课教师不能发布已有草稿，公告状态不变且没有通知写入。 */
    @Test
    void nonTeacherCannotPublishExistingDraft()
    {
        seedTeacher(TEACHER_ID, "LEAD", true);
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        insertDraft(3001L, "教师草稿", TEACHER_ID);
        authenticate(NON_TEACHER_ID);

        assertThrows(AnnouncementAccessDeniedException.class, () -> harness.tx.execute(status ->
                harness.announcementService.publish(BINDING_ID, 3001L)));

        assertEquals("DRAFT", statusOf(3001L));
        assertEquals(0, noticeCount());
    }

    /** 绑定已结束或教学班已关闭时，合法拒绝由既有公告服务返回 403，不产生状态变更或通知。 */
    @Test
    void endedBindingAndClosedClassAreDeniedWithoutWrites()
    {
        seedTeacher(TEACHER_ID, "LEAD", true);
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        insertDraft(3002L, "待发布草稿", TEACHER_ID);
        authenticate(TEACHER_ID);

        harness.jdbc.update("UPDATE edu_course_class_binding SET ended_at = ? WHERE id = ?",
                NOW.minusDays(1), BINDING_ID);
        assertThrows(AnnouncementAccessDeniedException.class, () -> harness.tx.execute(status ->
                harness.announcementService.publish(BINDING_ID, 3002L)));
        assertEquals("DRAFT", statusOf(3002L));
        assertEquals(0, noticeCount());

        harness.jdbc.update("UPDATE edu_course_class_binding SET ended_at = NULL WHERE id = ?",
                BINDING_ID);
        harness.jdbc.update("UPDATE edu_teaching_class SET status = 'ARCHIVED' WHERE id = ?",
                CLASS_ID);
        assertThrows(AnnouncementAccessDeniedException.class, () -> harness.tx.execute(status ->
                harness.announcementService.publish(BINDING_ID, 3002L)));
        assertEquals("DRAFT", statusOf(3002L));
        assertEquals(0, noticeCount());
    }

    /** 教师来源查询故障保持既有 503，失败发生在公告状态更新之前，无部分写入。 */
    @Test
    void teacherSourceFailureMapsTo503WithoutPartialWrites() throws Exception
    {
        seedTeacher(TEACHER_ID, "LEAD", true);
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        insertDraft(3003L, "教师来源故障草稿", TEACHER_ID);
        authenticate(TEACHER_ID);

        harness.jdbc.execute(
                "RENAME TABLE edu_class_teacher TO edu_class_teacher_unavailable");
        try
        {
            assertThrows(AnnouncementAuthorizationUnavailableException.class,
                    () -> harness.tx.execute(status ->
                            harness.announcementService.publish(BINDING_ID, 3003L)));
        }
        finally
        {
            harness.jdbc.execute(
                    "RENAME TABLE edu_class_teacher_unavailable TO edu_class_teacher");
        }

        assertEquals("DRAFT", statusOf(3003L));
        assertEquals(0, noticeCount());
    }

    /**
     * 学生来源查询在发布事务中的 fan-out 阶段故障：返回既有 503，
     * 公告状态更新随事务回滚，通知表无部分写入。
     */
    @Test
    void studentSourceFailureMapsTo503AndRollsBackPublishedState() throws Exception
    {
        seedTeacher(TEACHER_ID, "LEAD", true);
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        insertDraft(3004L, "学生来源故障草稿", TEACHER_ID);
        authenticate(TEACHER_ID);

        harness.jdbc.execute(
                "RENAME TABLE edu_class_student TO edu_class_student_unavailable");
        try
        {
            assertThrows(NoticeAuthorizationUnavailableException.class,
                    () -> harness.tx.execute(status ->
                            harness.announcementService.publish(BINDING_ID, 3004L)));
        }
        finally
        {
            harness.jdbc.execute(
                    "RENAME TABLE edu_class_student_unavailable TO edu_class_student");
        }

        assertEquals("DRAFT", statusOf(3004L));
        assertEquals(0, noticeCount());
    }

    /**
     * 重复发布走既有幂等返回，不重复 fan-out；重复投递依赖通知唯一键，
     * 通知行数不增且保留已有 read_at。
     */
    @Test
    void duplicatePublishAndDeliveryPreserveIdempotencyAndReadAt()
    {
        seedTeacher(TEACHER_ID, "LEAD", true);
        seedStudent(STUDENT_ONE, LocalDateTime.of(2026, 11, 10, 8, 0), null);
        authenticate(TEACHER_ID);

        CourseAnnouncement draft = harness.tx.execute(status ->
                harness.announcementService.createDraft(BINDING_ID, "幂等公告", "正文", null, null));
        harness.tx.execute(status -> harness.announcementService.publish(BINDING_ID, draft.getId()));
        Long noticeId = noticeId(STUDENT_ONE, draft.getId());
        assertNotNull(noticeId);
        assertEquals(1, noticeCount());

        authenticate(STUDENT_ONE);
        StudentNoticeService noticeService = harness.noticeService(NOW.plusMinutes(5));
        harness.tx.execute(status -> noticeService.markRead(noticeId));
        LocalDateTime readAt = harness.jdbc.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE id = ?", LocalDateTime.class, noticeId);
        assertNotNull(readAt);

        authenticate(TEACHER_ID);
        harness.tx.execute(status -> harness.announcementService.publish(BINDING_ID, draft.getId()));
        CourseAnnouncement published = harness.announcementMapper.selectById(draft.getId());
        harness.tx.execute(status -> harness.fanout.fanOutAnnouncement(published, NOW));

        assertEquals("PUBLISHED", statusOf(draft.getId()));
        assertEquals(1, noticeCount());
        assertEquals(readAt, harness.jdbc.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE id = ?", LocalDateTime.class, noticeId));
    }

    private Harness buildHarness(DataSource dataSource) throws Exception
    {
        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        Resource[] timelineMappers = resolver.getResources(
                "classpath*:mapper/education/EduMembershipTimelineMapper.xml");
        Resource[] portalMappers = resolver.getResources(
                "classpath*:mapper/education/EduPortalMapper.xml");
        // EduPortalMapper.xml 复用该 XML 的 activeTeacherAccount 片段，必须一起加载。
        Resource[] courseManagementMappers = resolver.getResources(
                "classpath*:mapper/education/EduCourseManagementMapper.xml");
        Resource[] announcementMappers = resolver.getResources(
                "classpath*:mapper/education/CourseAnnouncementMapper.xml");
        Resource[] noticeMappers = resolver.getResources(
                "classpath*:mapper/education/StudentNoticeMapper.xml");
        Resource[] mapperLocations = new Resource[timelineMappers.length + portalMappers.length
                + courseManagementMappers.length + announcementMappers.length
                + noticeMappers.length];
        int index = 0;
        for (Resource[] group : List.of(timelineMappers, portalMappers, courseManagementMappers,
                announcementMappers, noticeMappers))
        {
            System.arraycopy(group, 0, mapperLocations, index, group.length);
            index += group.length;
        }
        factory.setMapperLocations(mapperLocations);
        SqlSessionTemplate sqlSession = new SqlSessionTemplate(factory.getObject());

        EduMembershipTimelineServiceImpl timelineService = new EduMembershipTimelineServiceImpl();
        ReflectionTestUtils.setField(timelineService, "mapper",
                sqlSession.getMapper(EduMembershipTimelineMapper.class));
        ReflectionTestUtils.setField(timelineService, "portalMapper",
                sqlSession.getMapper(EduPortalMapper.class));

        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerSingleton("timelineService", timelineService);
        TeachingClassAuthorizationPort teacherPort =
                new MembershipTimelineTeachingClassAuthorizationAdapter(
                        beanFactory.getBeanProvider(EduMembershipTimelineService.class));
        StudentAnnouncementAuthorizationPort studentPort =
                new MembershipTimelineStudentAnnouncementAuthorizationAdapter(
                        beanFactory.getBeanProvider(EduMembershipTimelineService.class));

        CourseAnnouncementMapper announcementMapper =
                sqlSession.getMapper(CourseAnnouncementMapper.class);
        StudentNoticeMapper noticeMapper = sqlSession.getMapper(StudentNoticeMapper.class);
        AnnouncementNoticeFanoutService fanout =
                new AnnouncementNoticeFanoutService(studentPort, noticeMapper);
        CourseAnnouncementService announcementService = new CourseAnnouncementService(
                announcementMapper, teacherPort, fanout,
                Clock.fixed(NOW.toInstant(ZoneOffset.UTC), ZoneOffset.UTC));

        return new Harness(new JdbcTemplate(dataSource), announcementMapper, noticeMapper,
                studentPort, fanout, announcementService,
                new TransactionTemplate(new DataSourceTransactionManager(dataSource)));
    }

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
        seedUser(TEACHER_ID, "teacher8001", "任课教师");
        seedUser(LIMITED_TEACHER_ID, "teacher8002", "受限教师");
        seedUser(NON_TEACHER_ID, "teacher8003", "非任课教师");
        seedUser(TEAM_TEACHER_ID, "teacher8004", "团队成员");
        seedUser(STUDENT_ONE, "student9001", "学生一");
        seedUser(STUDENT_TWO, "student9002", "学生二");
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

    private void seedUser(Long userId, String userName, String nickName)
    {
        harness.jdbc.update("INSERT INTO sys_user (user_id, dept_id, user_name, nick_name, "
                + "phonenumber, status, del_flag) VALUES (?, NULL, ?, ?, '', '0', '0')",
                userId, userName, nickName);
    }

    private void seedTeacher(Long userId, String role, boolean canPublish)
    {
        harness.jdbc.update("INSERT INTO edu_class_teacher (teaching_class_id, user_id, "
                + "teacher_role, can_publish, can_grade, can_export, valid_from, valid_to, "
                + "granted_by_user_id) VALUES (?, ?, ?, ?, 1, 1, ?, NULL, 1)",
                CLASS_ID, userId, role, canPublish ? 1 : 0, BASE_PUBLISHED);
    }

    /** 教师角色 + 课程团队段：用于验证真实团队 fallback 的 canPublish=false。 */
    private void seedTeamTeacher(Long userId)
    {
        harness.jdbc.update("INSERT INTO sys_role (role_id, role_name, role_key, status, del_flag) "
                + "VALUES (2, '教师', 'teacher', '0', '0')");
        harness.jdbc.update("INSERT INTO sys_user_role (user_id, role_id) VALUES (?, 2)", userId);
        harness.jdbc.update("INSERT INTO edu_course_teacher (course_space_id, user_id, team_role, "
                + "joined_at, added_by_user_id) VALUES (?, ?, 'MEMBER', ?, 1)",
                COURSE_SPACE_ID, userId, BASE_PUBLISHED);
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

    private void insertDraft(long id, String title, Long createdBy)
    {
        harness.jdbc.update("INSERT INTO edu_course_announcement (id, binding_id, title, body, "
                + "status, created_by_user_id, created_at) VALUES (?, ?, ?, 'body', 'DRAFT', ?, ?)",
                id, BINDING_ID, title, createdBy, NOW.minusMinutes(1));
    }

    private String statusOf(long id)
    {
        return harness.jdbc.queryForObject(
                "SELECT status FROM edu_course_announcement WHERE id = ?", String.class, id);
    }

    private long announcementCount()
    {
        return harness.jdbc.queryForObject(
                "SELECT COUNT(*) FROM edu_course_announcement", Long.class);
    }

    private long noticeCount()
    {
        return harness.jdbc.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice", Long.class);
    }

    private Long noticeId(Long recipientUserId, long sourceId)
    {
        return harness.jdbc.queryForObject("SELECT id FROM edu_user_notice "
                + "WHERE recipient_user_id = ? AND notice_kind = 'ANNOUNCEMENT' AND source_id = ?",
                Long.class, recipientUserId, sourceId);
    }

    private void authenticate(Long userId)
    {
        SysUser sysUser = new SysUser(userId);
        LoginUser loginUser = new LoginUser(userId, null, sysUser, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
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

        private final CourseAnnouncementMapper announcementMapper;

        private final StudentNoticeMapper noticeMapper;

        private final StudentAnnouncementAuthorizationPort studentPort;

        private final AnnouncementNoticeFanoutService fanout;

        private final CourseAnnouncementService announcementService;

        private final TransactionTemplate tx;

        private Harness(JdbcTemplate jdbc, CourseAnnouncementMapper announcementMapper,
                StudentNoticeMapper noticeMapper, StudentAnnouncementAuthorizationPort studentPort,
                AnnouncementNoticeFanoutService fanout,
                CourseAnnouncementService announcementService, TransactionTemplate tx)
        {
            this.jdbc = jdbc;
            this.announcementMapper = announcementMapper;
            this.noticeMapper = noticeMapper;
            this.studentPort = studentPort;
            this.fanout = fanout;
            this.announcementService = announcementService;
            this.tx = tx;
        }

        private StudentNoticeService noticeService(LocalDateTime at)
        {
            return new StudentNoticeService(noticeMapper, studentPort,
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
