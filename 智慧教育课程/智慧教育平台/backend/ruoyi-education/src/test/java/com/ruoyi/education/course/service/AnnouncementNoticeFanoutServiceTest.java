package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
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
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;

class AnnouncementNoticeFanoutServiceTest
{
    private static final String JDBC_URL =
            "jdbc:h2:mem:announcement_fanout;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 30, 12, 0, 0, 123_000_000);

    private static final Long ANNOUNCEMENT_ID = 20001L;

    private static final Long BINDING_ID = 100L;

    private static final Long CLASS_ID = 1001L;

    private JdbcTemplate jdbcTemplate;

    private StudentNoticeMapper mapper;

    private TransactionTemplate transactionTemplate;

    private FakeStudentPort port;

    private AnnouncementNoticeFanoutService service;

    @BeforeEach
    void setUp() throws Exception
    {
        DataSource dataSource = new DriverManagerDataSource(JDBC_URL, "sa", "");
        resetSchema(dataSource);

        SqlSessionFactoryBean factory = new SqlSessionFactoryBean();
        factory.setDataSource(dataSource);
        factory.setMapperLocations(new PathMatchingResourcePatternResolver()
                .getResources("classpath*:mapper/education/StudentNoticeMapper.xml"));
        mapper = new SqlSessionTemplate(factory.getObject()).getMapper(StudentNoticeMapper.class);
        transactionTemplate = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        jdbcTemplate = new JdbcTemplate(dataSource);

        port = new FakeStudentPort();
        service = new AnnouncementNoticeFanoutService(port, mapper);
    }

    @Test
    void fanOutWritesOnlyMembersWhoseIntervalContainsPublishedAt()
    {
        port.members = List.of(
                member(9001L, BINDING_ID, AT.minusDays(2), null, true),
                member(9002L, BINDING_ID, AT.minusDays(2), AT.minusDays(1), true),
                member(9003L, BINDING_ID, AT.plusDays(1), null, true),
                member(9004L, BINDING_ID, AT.minusDays(2), AT.plusHours(1), true),
                member(9005L, BINDING_ID, AT.minusDays(2), null, false));

        Integer inserted = transactionTemplate.execute(
                status -> service.fanOutAnnouncement(announcement(), AT));

        assertEquals(2, inserted);
        assertEquals(2, countRows());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice WHERE recipient_user_id IN (9001, 9004)", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice WHERE recipient_user_id IN (9002, 9003, 9005)", Integer.class));
        assertEquals("ANNOUNCEMENT", jdbcTemplate.queryForObject(
                "SELECT notice_kind FROM edu_user_notice WHERE recipient_user_id = 9001", String.class));
        assertEquals(ANNOUNCEMENT_ID, jdbcTemplate.queryForObject(
                "SELECT source_id FROM edu_user_notice WHERE recipient_user_id = 9001", Long.class));
        assertEquals(AT.plusMinutes(30), jdbcTemplate.queryForObject(
                "SELECT visible_at FROM edu_user_notice WHERE recipient_user_id = 9001", LocalDateTime.class));
        assertNull(jdbcTemplate.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE recipient_user_id = 9001", LocalDateTime.class));
    }

    @Test
    void fanOutIsIdempotentAndDoesNotOverwriteExistingReadAt()
    {
        port.members = List.of(member(9001L, BINDING_ID, AT.minusDays(2), null, true));

        Integer first = transactionTemplate.execute(
                status -> service.fanOutAnnouncement(announcement(), AT));
        Integer second = transactionTemplate.execute(
                status -> service.fanOutAnnouncement(announcement(), AT));

        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(1, countRows());

        // 人为写入已读时间和旧标题，再次 fan-out 不得覆盖。
        jdbcTemplate.update("UPDATE edu_user_notice SET read_at = ?, title = '旧标题' WHERE recipient_user_id = 9001",
                AT.minusHours(1));
        Integer third = transactionTemplate.execute(
                status -> service.fanOutAnnouncement(announcement(), AT));

        assertEquals(0, third);
        assertEquals(1, countRows());
        assertEquals(AT.minusHours(1), jdbcTemplate.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE recipient_user_id = 9001", LocalDateTime.class));
        assertEquals("旧标题", jdbcTemplate.queryForObject(
                "SELECT title FROM edu_user_notice WHERE recipient_user_id = 9001", String.class));
    }

    @Test
    void fanOutFailsClosedWhenAuthorizationPortIsMissing()
    {
        AnnouncementNoticeFanoutService withoutPort =
                new AnnouncementNoticeFanoutService((StudentAnnouncementAuthorizationPort) null, mapper);

        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> transactionTemplate.execute(
                        status -> withoutPort.fanOutAnnouncement(announcement(), AT)));
        assertEquals(0, countRows());
    }

    @Test
    void fanOutFailsClosedWhenAuthorizationThrowsOrReturnsNull()
    {
        port.failure = new IllegalStateException("upstream down");
        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> transactionTemplate.execute(
                        status -> service.fanOutAnnouncement(announcement(), AT)));

        port.failure = null;
        port.nullMembers = true;
        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> transactionTemplate.execute(
                        status -> service.fanOutAnnouncement(announcement(), AT)));
        assertEquals(0, countRows());
    }

    @Test
    void fanOutFailsClosedForOtherBindingAndIncompleteMember()
    {
        port.members = List.of(member(9001L, 999L, AT.minusDays(2), null, true));
        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> transactionTemplate.execute(
                        status -> service.fanOutAnnouncement(announcement(), AT)));

        port.members = List.of(new StudentAnnouncementMember(
                9001L, BINDING_ID, CLASS_ID, null, null, activeAuthority()));
        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> transactionTemplate.execute(
                        status -> service.fanOutAnnouncement(announcement(), AT)));
        assertEquals(0, countRows());
    }

    @Test
    void fanOutSplitsBatchesWithoutDroppingRecipients()
    {
        List<StudentAnnouncementMember> members = new ArrayList<>();
        for (long userId = 1; userId <= 501; userId++)
        {
            members.add(member(userId, BINDING_ID, AT.minusDays(2), null, true));
        }
        port.members = members;

        Integer inserted = transactionTemplate.execute(
                status -> service.fanOutAnnouncement(announcement(), AT));

        assertEquals(501, inserted);
        assertEquals(501, countRows());
    }

    @Test
    void fanOutWithNoMembersReturnsZero()
    {
        port.members = List.of();

        Integer inserted = transactionTemplate.execute(
                status -> service.fanOutAnnouncement(announcement(), AT));

        assertEquals(0, inserted);
        assertEquals(0, countRows());
    }

    private void resetSchema(DataSource dataSource) throws Exception
    {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement())
        {
            statement.execute("DROP TABLE IF EXISTS edu_user_notice");
            statement.execute("DROP TABLE IF EXISTS edu_course_announcement");
            statement.execute("""
                    CREATE TABLE edu_course_announcement (
                        id BIGINT PRIMARY KEY,
                        binding_id BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        opens_at TIMESTAMP(3) NULL,
                        closes_at TIMESTAMP(3) NULL
                    )
                    """);
            statement.execute("""
                    CREATE TABLE edu_user_notice (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        recipient_user_id BIGINT NOT NULL,
                        binding_id BIGINT NULL,
                        notice_kind VARCHAR(24) NOT NULL,
                        source_id BIGINT NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        visible_at TIMESTAMP(3) NOT NULL,
                        read_at TIMESTAMP(3) NULL,
                        created_at TIMESTAMP(3) NOT NULL,
                        CONSTRAINT uq_edu_notice_recipient_source
                            UNIQUE (recipient_user_id, notice_kind, source_id)
                    )
                    """);
        }

    }

    private long countRows()
    {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_user_notice", Long.class);
    }

    private CourseAnnouncement announcement()
    {
        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setId(ANNOUNCEMENT_ID);
        announcement.setBindingId(BINDING_ID);
        announcement.setTitle("公告标题");
        announcement.setStatus(AnnouncementStatus.PUBLISHED);
        announcement.setOpensAt(AT.plusMinutes(30));
        announcement.setCreatedAt(AT.minusHours(2));
        return announcement;
    }

    private StudentAnnouncementMember member(long userId, long bindingId,
            LocalDateTime joinedAt, LocalDateTime leftAt, boolean active)
    {
        return new StudentAnnouncementMember(userId, bindingId, CLASS_ID, joinedAt, leftAt,
                new StudentAnnouncementAuthority(active, true, true));
    }

    private StudentAnnouncementAuthority activeAuthority()
    {
        return new StudentAnnouncementAuthority(true, true, true);
    }

    static class FakeStudentPort implements StudentAnnouncementAuthorizationPort
    {
        private List<StudentAnnouncementMember> members = List.of();

        private boolean nullMembers;

        private RuntimeException failure;

        @Override
        public StudentAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at)
        {
            throw new UnsupportedOperationException("not used by fan-out");
        }

        @Override
        public List<StudentAnnouncementAccess> listActiveBindings(Long userId, LocalDateTime at)
        {
            throw new UnsupportedOperationException("not used by fan-out");
        }

        @Override
        public List<StudentAnnouncementMember> listActiveStudents(Long bindingId, LocalDateTime at)
        {
            if (failure != null)
            {
                throw failure;
            }
            return nullMembers ? null : members;
        }
    }
}
