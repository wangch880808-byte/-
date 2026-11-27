package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduMembershipTimelineService;
import com.ruoyi.education.course.api.EduStudentBindingAccess;
import com.ruoyi.education.course.api.EduStudentMemberSegment;
import com.ruoyi.education.course.api.EduTeacherBindingAccess;
import com.ruoyi.education.course.authorization.MembershipTimelineTeachingClassAuthorizationAdapter;
import com.ruoyi.education.course.authorization.TeachingClassAuthorizationPort;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.exception.AnnouncementAccessDeniedException;
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.mapper.CourseAnnouncementMapper;

/**
 * 真实教师授权适配器与 {@link CourseAnnouncementService} 的共同装配测试。
 *
 * <p>公告 Mapper 使用 H2 内存库，第 2 组任课时间线服务使用手工测试替身，
 * 不连接真实 MySQL、真实登录或 HTTP；用于证明适配器接入后正常流程、
 * 403/503 映射和创建者编辑规则保持不变。</p>
 */
@SpringJUnitConfig(TeacherAnnouncementAuthorizationIntegrationTest.TestConfig.class)
class TeacherAnnouncementAuthorizationIntegrationTest
{
    private static final Long TEACHER_ID = 930000000001L;

    private static final Long BINDING_ID = 100L;

    private static final Long CLASS_ID = 1001L;

    private static final Long SPACE_ID = 2001L;

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);

    private static final Instant PUBLISHED_AT = Instant.parse("2026-10-01T00:00:00Z");

    private static final Instant ENDED_AT = Instant.parse("2026-10-20T00:00:00Z");

    @Autowired
    private CourseAnnouncementService service;

    @Autowired
    private StubTimelineService timeline;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int nextId;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.clearContext();
        authenticate(TEACHER_ID);
        timeline.reset();
        jdbcTemplate.execute("DROP TABLE IF EXISTS edu_course_announcement");
        jdbcTemplate.execute("""
                CREATE TABLE edu_course_announcement (
                    id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    binding_id BIGINT NOT NULL,
                    title VARCHAR(200) NOT NULL,
                    body TEXT NOT NULL,
                    status VARCHAR(16) NOT NULL,
                    opens_at TIMESTAMP(3),
                    closes_at TIMESTAMP(3),
                    published_by_user_id BIGINT,
                    created_by_user_id BIGINT NOT NULL,
                    created_at TIMESTAMP(3) NOT NULL
                )
                """);
        jdbcTemplate.execute("ALTER TABLE edu_course_announcement ALTER COLUMN id RESTART WITH 100");
        nextId = 1;
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizedTeacherCanCreateDraftThroughRealAdapter()
    {
        timeline.returns(access(true, Boolean.TRUE, true, true, null));

        CourseAnnouncement created = service.createDraft(BINDING_ID, "title", "body", null, null);

        assertNotNull(created.getId());
        assertEquals("title", titleOf(created.getId()));
        assertEquals(1, countRows());
    }

    @Test
    void nonTeacherResultRemains403WithoutWrites()
    {
        timeline.returns(access(false, null, true, true, null));

        assertThrows(AnnouncementAccessDeniedException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertEquals(0, countRows());
    }

    @Test
    void limitedTeacherCannotCreateDraftAndWritesNothing()
    {
        timeline.returns(access(true, Boolean.FALSE, true, true, null));

        assertThrows(AnnouncementAccessDeniedException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertEquals(0, countRows());
    }

    @Test
    void endedBindingDenialRemains403WithoutWrites()
    {
        timeline.returns(access(true, Boolean.TRUE, false, true, ENDED_AT));

        assertThrows(AnnouncementAccessDeniedException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertEquals(0, countRows());
    }

    @Test
    void sourceFailureRemains503WithoutWrites()
    {
        timeline.fails(new ServiceException("教师任课记录重叠，无法判定唯一授权"));

        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertEquals(0, countRows());
    }

    @Test
    void creatorCanEditOwnDraftWhenPublishPermissionIsFalse()
    {
        long id = insertDraft(TEACHER_ID);
        timeline.returns(access(true, Boolean.FALSE, true, true, null));

        service.updateDraft(BINDING_ID, id, "updated", "body", null, null);

        assertEquals("updated", titleOf(id));
        assertEquals(1, countRows());
    }

    private static EduTeacherBindingAccess access(boolean currentTeacher, Boolean canPublish,
            boolean bindingActive, boolean classOpen, Instant endedAt)
    {
        return new EduTeacherBindingAccess(BINDING_ID, CLASS_ID, "教学班", "C-1", SPACE_ID,
                PUBLISHED_AT, endedAt, TEACHER_ID, "teacher1", "教师一",
                bindingActive, classOpen, currentTeacher, currentTeacher ? "LEAD" : null, canPublish);
    }

    private void authenticate(Long userId)
    {
        LoginUser loginUser = new LoginUser(userId, null, new SysUser(userId), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private long insertDraft(Long createdBy)
    {
        long id = nextId++;
        jdbcTemplate.update("""
                INSERT INTO edu_course_announcement
                    (id, binding_id, title, body, status, created_by_user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                id, BINDING_ID, "draft", "body", "DRAFT", createdBy,
                Timestamp.valueOf(NOW.minusMinutes(id)));
        return id;
    }

    private String titleOf(long id)
    {
        return jdbcTemplate.queryForObject(
                "select title from edu_course_announcement where id = ?", String.class, id);
    }

    private int countRows()
    {
        Integer count = jdbcTemplate.queryForObject(
                "select count(*) from edu_course_announcement", Integer.class);
        return count == null ? 0 : count;
    }

    /**
     * 第 2 组契约的手工测试替身：不访问真实 Mapper 或数据库。
     */
    static class StubTimelineService implements EduMembershipTimelineService
    {
        private EduTeacherBindingAccess result;

        private RuntimeException failure;

        void returns(EduTeacherBindingAccess result)
        {
            this.result = result;
            this.failure = null;
        }

        void fails(RuntimeException failure)
        {
            this.failure = failure;
        }

        void reset()
        {
            result = null;
            failure = null;
        }

        @Override
        public EduTeacherBindingAccess teacherInBinding(Long bindingId, Long userId, Instant at)
        {
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

    @Configuration
    @EnableTransactionManagement
    static class TestConfig
    {
        private static final String JDBC_URL =
                "jdbc:h2:mem:teacher_auth_adapter;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

        @Bean
        DataSource dataSource()
        {
            DriverManagerDataSource dataSource = new DriverManagerDataSource(JDBC_URL, "sa", "");
            dataSource.setDriverClassName("org.h2.Driver");
            return dataSource;
        }

        @Bean
        SqlSessionFactory sqlSessionFactory(DataSource dataSource) throws Exception
        {
            SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
            factory.setDataSource(dataSource);
            factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                    .getResources("classpath*:mapper/education/CourseAnnouncementMapper.xml"));
            return factory.getObject();
        }

        @Bean
        SqlSessionTemplate sqlSessionTemplate(SqlSessionFactory sqlSessionFactory)
        {
            return new SqlSessionTemplate(sqlSessionFactory);
        }

        @Bean
        CourseAnnouncementMapper announcementMapper(SqlSessionTemplate sqlSessionTemplate)
        {
            return sqlSessionTemplate.getMapper(CourseAnnouncementMapper.class);
        }

        @Bean
        StubTimelineService timelineService()
        {
            return new StubTimelineService();
        }

        @Bean
        TeachingClassAuthorizationPort authorizationPort(
                ObjectProvider<EduMembershipTimelineService> timelineServiceProvider)
        {
            return new MembershipTimelineTeachingClassAuthorizationAdapter(timelineServiceProvider);
        }

        @Bean
        AnnouncementNoticeFanoutPort fanoutService()
        {
            return (announcement, publishedAt) -> 0;
        }

        @Bean
        PlatformTransactionManager transactionManager(DataSource dataSource)
        {
            return new DataSourceTransactionManager(dataSource);
        }

        @Bean
        JdbcTemplate jdbcTemplate(DataSource dataSource)
        {
            return new JdbcTemplate(dataSource);
        }

        @Bean
        CourseAnnouncementService announcementService(CourseAnnouncementMapper announcementMapper,
                TeachingClassAuthorizationPort authorizationPort,
                AnnouncementNoticeFanoutPort fanoutService)
        {
            return new CourseAnnouncementService(announcementMapper, authorizationPort,
                    fanoutService, Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));
        }
    }
}
