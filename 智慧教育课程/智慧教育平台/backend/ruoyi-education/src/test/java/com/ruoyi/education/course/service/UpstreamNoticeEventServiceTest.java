package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;
import com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.NoticeValidationException;
import com.ruoyi.education.course.mapper.StudentNoticeMapper;

class UpstreamNoticeEventServiceTest
{
    private static final String JDBC_URL =
            "jdbc:h2:mem:upstream_notice_event;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 30, 12, 0, 0, 123_000_000);

    private static final Long BINDING_ID = 100L;

    private static final Long CLASS_ID = 1001L;

    private JdbcTemplate jdbcTemplate;

    private StudentNoticeMapper mapper;

    private TransactionTemplate transactionTemplate;

    private FakeStudentPort port;

    private UpstreamNoticeEventService service;

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
        service = new UpstreamNoticeEventService(port, mapper);
    }

    @Test
    void receivesAllFourSupportedEventKinds()
    {
        int sourceId = 1000;
        for (NoticeKind kind : List.of(NoticeKind.PREVIEW, NoticeKind.ACTIVITY,
                NoticeKind.ASSIGNMENT, NoticeKind.GRADE))
        {
            sourceId++;
            int eventSourceId = sourceId;
            Integer inserted = transactionTemplate.execute(status -> service.receive(
                    event(kind, eventSourceId, List.of(9001L))));
            assertEquals(1, inserted, kind.name());
        }

        assertEquals(4, countRows());
        assertEquals(4, jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT notice_kind) FROM edu_user_notice", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice WHERE notice_kind = 'PREVIEW'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice WHERE notice_kind = 'ACTIVITY'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice WHERE notice_kind = 'ASSIGNMENT'", Integer.class));
        assertEquals(1, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice WHERE notice_kind = 'GRADE'", Integer.class));
    }

    @Test
    void classWideEventFiltersMembersByOccurredAtInterval()
    {
        port.members = List.of(
                member(9001L, AT.minusDays(2), null, true),
                member(9002L, AT.minusDays(2), AT.minusDays(1), true),
                member(9003L, AT.plusDays(1), null, true),
                member(9004L, AT.minusDays(2), null, false),
                member(9005L, AT.minusDays(2), AT.plusHours(1), true));

        Integer inserted = transactionTemplate.execute(status -> service.receive(
                event(NoticeKind.PREVIEW, 1001L, null)));

        assertEquals(2, inserted);
        assertEquals(2, countRows());
        assertEquals(2, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice WHERE recipient_user_id IN (9001, 9005)", Integer.class));
        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM edu_user_notice WHERE recipient_user_id IN (9002, 9003, 9004)", Integer.class));
        assertEquals("PREVIEW", jdbcTemplate.queryForObject(
                "SELECT notice_kind FROM edu_user_notice WHERE recipient_user_id = 9001", String.class));
        assertEquals(AT.plusMinutes(5), jdbcTemplate.queryForObject(
                "SELECT visible_at FROM edu_user_notice WHERE recipient_user_id = 9001", LocalDateTime.class));
    }

    @Test
    void explicitRecipientsDoNotRequireAuthorizationPort()
    {
        UpstreamNoticeEventService withoutPort =
                new UpstreamNoticeEventService((StudentAnnouncementAuthorizationPort) null, mapper);
        UpstreamNoticeEvent event = new UpstreamNoticeEvent("evt-explicit", NoticeKind.ASSIGNMENT,
                BINDING_ID, 1002L, "作业已发布", "content", AT, null, List.of(9001L, 9002L, 9002L));

        Integer inserted = transactionTemplate.execute(status -> withoutPort.receive(event));

        assertEquals(2, inserted);
        assertEquals(2, countRows());
        assertEquals(AT, jdbcTemplate.queryForObject(
                "SELECT visible_at FROM edu_user_notice WHERE recipient_user_id = 9001", LocalDateTime.class));
    }

    @Test
    void duplicateDeliveryIsIdempotentAndDoesNotOverwriteReadAt()
    {
        UpstreamNoticeEvent event = event(NoticeKind.GRADE, 1003L, List.of(9001L));
        Integer first = transactionTemplate.execute(status -> service.receive(event));
        Integer second = transactionTemplate.execute(status -> service.receive(event));

        assertEquals(1, first);
        assertEquals(0, second);
        assertEquals(1, countRows());

        jdbcTemplate.update("UPDATE edu_user_notice SET read_at = ?, title = '旧标题' WHERE recipient_user_id = 9001",
                AT.minusHours(1));
        Integer third = transactionTemplate.execute(status -> service.receive(event));

        assertEquals(0, third);
        assertEquals(1, countRows());
        assertEquals(AT.minusHours(1), jdbcTemplate.queryForObject(
                "SELECT read_at FROM edu_user_notice WHERE recipient_user_id = 9001", LocalDateTime.class));
        assertEquals("旧标题", jdbcTemplate.queryForObject(
                "SELECT title FROM edu_user_notice WHERE recipient_user_id = 9001", String.class));
    }

    @Test
    void incompleteOrUnsupportedEventsAreRejected()
    {
        List<UpstreamNoticeEvent> invalid = List.of(
                new UpstreamNoticeEvent("", NoticeKind.PREVIEW, BINDING_ID, 1001L, "t", null, AT, null, List.of(9001L)),
                new UpstreamNoticeEvent("evt", null, BINDING_ID, 1001L, "t", null, AT, null, List.of(9001L)),
                new UpstreamNoticeEvent("evt", NoticeKind.ANNOUNCEMENT, BINDING_ID, 1001L, "t", null, AT, null, List.of(9001L)),
                new UpstreamNoticeEvent("evt", NoticeKind.PREVIEW, null, 1001L, "t", null, AT, null, List.of(9001L)),
                new UpstreamNoticeEvent("evt", NoticeKind.PREVIEW, BINDING_ID, null, "t", null, AT, null, List.of(9001L)),
                new UpstreamNoticeEvent("evt", NoticeKind.PREVIEW, BINDING_ID, 1001L, " ", null, AT, null, List.of(9001L)),
                new UpstreamNoticeEvent("evt", NoticeKind.PREVIEW, BINDING_ID, 1001L, "t", null, null, null, List.of(9001L)),
                new UpstreamNoticeEvent("evt", NoticeKind.PREVIEW, BINDING_ID, 1001L, "t", null, AT, null, List.of(0L)));

        for (UpstreamNoticeEvent event : invalid)
        {
            assertThrows(NoticeValidationException.class,
                    () -> transactionTemplate.execute(status -> service.receive(event)));
        }
        assertEquals(0, countRows());
    }

    @Test
    void classWideAuthorizationUnavailableReturns503()
    {
        UpstreamNoticeEventService withoutPort =
                new UpstreamNoticeEventService((StudentAnnouncementAuthorizationPort) null, mapper);
        UpstreamNoticeEvent event = event(NoticeKind.ACTIVITY, 1004L, null);
        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> transactionTemplate.execute(status -> withoutPort.receive(event)));

        port.failure = new IllegalStateException("upstream down");
        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> transactionTemplate.execute(status -> service.receive(event)));

        port.failure = null;
        port.nullMembers = true;
        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> transactionTemplate.execute(status -> service.receive(event)));

        port.nullMembers = false;
        port.members = List.of(member(9001L, 999L, AT.minusDays(2), null, true));
        assertThrows(NoticeAuthorizationUnavailableException.class,
                () -> transactionTemplate.execute(status -> service.receive(event)));
        assertEquals(0, countRows());
    }

    @Test
    void explicitRecipientListSplitsBatchesWithoutDroppingRecipients()
    {
        List<Long> recipients = new ArrayList<>();
        for (long userId = 1; userId <= 501; userId++)
        {
            recipients.add(userId);
        }
        UpstreamNoticeEvent event = new UpstreamNoticeEvent("evt-batch", NoticeKind.ASSIGNMENT,
                BINDING_ID, 1005L, "批量作业", null, AT, AT, recipients);

        Integer inserted = transactionTemplate.execute(status -> service.receive(event));

        assertEquals(501, inserted);
        assertEquals(501, countRows());
    }

    @Test
    void emptyExplicitRecipientListInsertsNothing()
    {
        UpstreamNoticeEvent event = new UpstreamNoticeEvent("evt-empty", NoticeKind.PREVIEW,
                BINDING_ID, 1006L, "无接收人", null, AT, AT, List.of());

        Integer inserted = transactionTemplate.execute(status -> service.receive(event));

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

    private UpstreamNoticeEvent event(NoticeKind kind, long sourceId, List<Long> recipients)
    {
        return new UpstreamNoticeEvent("evt-" + kind.name() + "-" + sourceId, kind, BINDING_ID,
                sourceId, kind.name() + " 标题", "content", AT, AT.plusMinutes(5), recipients);
    }

    private long countRows()
    {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM edu_user_notice", Long.class);
    }

    private StudentAnnouncementMember member(long userId, LocalDateTime joinedAt,
            LocalDateTime leftAt, boolean active)
    {
        return member(userId, BINDING_ID, joinedAt, leftAt, active);
    }

    private StudentAnnouncementMember member(long userId, long bindingId,
            LocalDateTime joinedAt, LocalDateTime leftAt, boolean active)
    {
        return new StudentAnnouncementMember(userId, bindingId, CLASS_ID, joinedAt, leftAt,
                new StudentAnnouncementAuthority(active, true, true));
    }

    static class FakeStudentPort implements StudentAnnouncementAuthorizationPort
    {
        private List<StudentAnnouncementMember> members = List.of();

        private boolean nullMembers;

        private RuntimeException failure;

        @Override
        public StudentAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at)
        {
            throw new UnsupportedOperationException("not used by event receive");
        }

        @Override
        public List<StudentAnnouncementAccess> listActiveBindings(Long userId, LocalDateTime at)
        {
            throw new UnsupportedOperationException("not used by event receive");
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
