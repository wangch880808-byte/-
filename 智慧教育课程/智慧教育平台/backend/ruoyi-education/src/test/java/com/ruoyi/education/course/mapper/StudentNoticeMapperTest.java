package com.ruoyi.education.course.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.List;
import javax.sql.DataSource;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.io.Resources;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.domain.StudentNotice;
import com.ruoyi.education.course.service.NoticeBindingScope;
import com.ruoyi.education.course.service.StudentNoticeQuery;

class StudentNoticeMapperTest
{
    private static final String JDBC_URL =
            "jdbc:h2:mem:student_notice_mapper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 28, 12, 0);
    private static final LocalDateTime JOINED_AT = NOW.minusDays(5);
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpFactory() throws Exception
    {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        String resource = "mapper/education/StudentNoticeMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(inputStream, configuration, resource, configuration.getSqlFragments()).parse();
        }
        sqlSessionFactory = new SqlSessionFactoryBuilder().build(configuration);
    }

    @BeforeEach
    void resetDatabase() throws Exception
    {
        try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", ""))
        {
            try (Statement statement = connection.createStatement())
            {
                statement.execute("DROP TABLE IF EXISTS edu_user_notice");
                statement.execute("DROP TABLE IF EXISTS edu_course_announcement");
                statement.execute("""
                        CREATE TABLE edu_course_announcement (
                            id BIGINT PRIMARY KEY,
                            binding_id BIGINT NOT NULL,
                            title VARCHAR(200) NOT NULL,
                            status VARCHAR(16) NOT NULL,
                            opens_at TIMESTAMP(3),
                            closes_at TIMESTAMP(3)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE edu_user_notice (
                            id BIGINT PRIMARY KEY,
                            recipient_user_id BIGINT NOT NULL,
                            binding_id BIGINT,
                            notice_kind VARCHAR(24) NOT NULL,
                            source_id BIGINT NOT NULL,
                            title VARCHAR(200) NOT NULL,
                            visible_at TIMESTAMP(3) NOT NULL,
                            read_at TIMESTAMP(3),
                            created_at TIMESTAMP(3) NOT NULL
                        )
                        """);
            }
            insertAnnouncement(connection, 1L, 100L, "PUBLISHED", NOW.minusDays(1), null);
            insertAnnouncement(connection, 2L, 100L, "ARCHIVED", NOW.minusDays(2), null);
            insertAnnouncement(connection, 3L, 200L, "PUBLISHED", NOW.minusDays(1), null);
            insertAnnouncement(connection, 4L, 100L, "PUBLISHED", NOW.plusHours(1), null);

            insertNotice(connection, 1L, 9001L, 100L, "ANNOUNCEMENT", 1L,
                    "open-announcement", NOW.minusHours(1), null, NOW.minusHours(2));
            insertNotice(connection, 2L, 9001L, 100L, "ANNOUNCEMENT", 2L,
                    "archived-source", NOW.minusHours(1), null, NOW.minusHours(2));
            insertNotice(connection, 3L, 9001L, 100L, "ANNOUNCEMENT", 3L,
                    "cross-binding-source", NOW.minusHours(1), null, NOW.minusHours(2));
            insertNotice(connection, 4L, 9001L, 100L, "GRADE", 900L,
                    "grade-unread", NOW.minusHours(2), null, NOW.minusHours(3));
            insertNotice(connection, 5L, 9001L, 100L, "GRADE", 901L,
                    "future", NOW.plusHours(1), null, NOW.minusHours(1));
            insertNotice(connection, 6L, 9002L, 100L, "GRADE", 902L,
                    "other-user", NOW.minusHours(3), null, NOW.minusHours(4));
            insertNotice(connection, 7L, 9001L, 200L, "GRADE", 903L,
                    "other-binding", NOW.minusHours(4), null, NOW.minusHours(5));
            insertNotice(connection, 8L, 9001L, 100L, "GRADE", 904L,
                    "already-read", NOW.minusHours(5), NOW.minusHours(4), NOW.minusHours(6));
            insertNotice(connection, 9L, 9001L, 100L, "GRADE", 905L,
                    "before-join", NOW.minusDays(10), null, NOW.minusDays(11));
            insertNotice(connection, 10L, 9001L, 100L, "ANNOUNCEMENT", 4L,
                    "future-announcement", NOW.minusHours(1), null, NOW.minusHours(2));
        }
    }

    @Test
    void countVisibleUsesRecipientMembershipWindowAndAnnouncementSource()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);
            StudentNoticeQuery query = query(false, null);

            assertEquals(3, mapper.countVisible(query));
        }
    }

    @Test
    void countVisibleAppliesKindAndUnreadFilters()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);

            StudentNoticeQuery gradeQuery = query(false, NoticeKind.GRADE);
            assertEquals(2, mapper.countVisible(gradeQuery));

            StudentNoticeQuery unreadGradeQuery = query(true, NoticeKind.GRADE);
            assertEquals(1, mapper.countVisible(unreadGradeQuery));
        }
    }

    @Test
    void selectVisiblePageSortsByVisibleAtDescendingThenIdDescending()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);
            StudentNoticeQuery query = query(false, null);
            query.setLimit(2);
            query.setOffset(0);

            List<StudentNotice> firstPage = mapper.selectVisiblePage(query);
            assertEquals(List.of(1L, 4L), firstPage.stream().map(StudentNotice::getId).toList());

            query.setOffset(2);
            List<StudentNotice> secondPage = mapper.selectVisiblePage(query);
            assertEquals(List.of(8L), secondPage.stream().map(StudentNotice::getId).toList());
        }
    }

    @Test
    void selectVisibleByIdHidesOtherUsersOtherBindingsAndInvisibleNotices()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);
            StudentNoticeQuery query = query(false, null);

            assertNotNull(mapper.selectVisibleById(1L, query));
            assertNotNull(mapper.selectVisibleById(8L, query));
            assertNull(mapper.selectVisibleById(2L, query));
            assertNull(mapper.selectVisibleById(3L, query));
            assertNull(mapper.selectVisibleById(5L, query));
            assertNull(mapper.selectVisibleById(6L, query));
            assertNull(mapper.selectVisibleById(7L, query));
            assertNull(mapper.selectVisibleById(9L, query));
            assertNull(mapper.selectVisibleById(10L, query));
        }
    }

    @Test
    void selectByIdAndRecipientReturnsOnlyOwnedRow()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);

            assertNotNull(mapper.selectByIdAndRecipient(1L, 9001L));
            assertNull(mapper.selectByIdAndRecipient(1L, 9002L));
            assertNull(mapper.selectByIdAndRecipient(999L, 9001L));
        }
    }

    @Test
    void selectVisibleByIdForUpdateReturnsVisibleRow()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);

            assertNotNull(mapper.selectVisibleByIdForUpdate(1L, query(false, null)));
            assertNull(mapper.selectVisibleByIdForUpdate(2L, query(false, null)));
        }
    }

    @Test
    void markReadOnlyUpdatesVisibleOwnedUnreadRow()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);
            StudentNoticeQuery query = query(false, null);
            LocalDateTime readAt = NOW.minusMinutes(1);

            assertEquals(1, mapper.markRead(1L, readAt, query));
            assertEquals(0, mapper.markRead(1L, readAt, query));
            assertEquals(0, mapper.markRead(8L, readAt, query));
            assertEquals(0, mapper.markRead(2L, readAt, query));
            assertEquals(0, mapper.markRead(6L, readAt, query));
            session.commit();

            StudentNotice notice = mapper.selectByIdAndRecipient(1L, 9001L);
            assertEquals(readAt, notice.getReadAt());
            assertEquals(NOW.minusHours(4), mapper.selectByIdAndRecipient(8L, 9001L).getReadAt());
        }
    }

    @Test
    void markAllReadUpdatesOnlyVisibleUnreadRowsAndReportsActualCount()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);
            StudentNoticeQuery query = query(true, null);
            LocalDateTime operatedAt = NOW.minusMinutes(1);

            assertEquals(2, mapper.markAllRead(operatedAt, query));
            assertEquals(0, mapper.markAllRead(operatedAt, query));
            session.commit();

            assertEquals(operatedAt, mapper.selectByIdAndRecipient(1L, 9001L).getReadAt());
            assertEquals(operatedAt, mapper.selectByIdAndRecipient(4L, 9001L).getReadAt());
            assertNull(mapper.selectByIdAndRecipient(6L, 9002L).getReadAt());
            assertEquals(NOW.minusHours(4), mapper.selectByIdAndRecipient(8L, 9001L).getReadAt());
            assertEquals(0, mapper.selectVisibleUnreadIdsForUpdate(query).size());
        }
    }

    @Test
    void selectVisibleUnreadIdsForUpdateReturnsVisibleOwnedUnreadRows()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);
            List<Long> ids = mapper.selectVisibleUnreadIdsForUpdate(query(true, null));

            assertEquals(2, ids.size());
            assertEquals(true, ids.contains(1L));
            assertEquals(true, ids.contains(4L));
        }
    }

    @Test
    void noticeWithNullBindingIsExcluded()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);
            StudentNoticeQuery query = query(false, null);
            assertEquals(3, mapper.countVisible(query));

            try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", ""))
            {
                insertNotice(connection, 11L, 9001L, null, "GRADE", 906L,
                        "null-binding", NOW.minusMinutes(30), null, NOW.minusHours(1));
            }

            assertEquals(3, mapper.countVisible(query));
            assertNull(mapper.selectVisibleById(11L, query));
            assertNotNull(mapper.selectByIdAndRecipient(11L, 9001L));

            assertEquals(0, mapper.markRead(11L, NOW, query));
            assertEquals(2, mapper.markAllRead(NOW, query));
            session.commit();
            assertNull(mapper.selectByIdAndRecipient(11L, 9001L).getReadAt());
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    @Test
    void membershipLeftAtIsExclusive()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            StudentNoticeMapper mapper = session.getMapper(StudentNoticeMapper.class);
            StudentNoticeQuery query = query(false, null);
            query.setBindings(List.of(new NoticeBindingScope(100L, JOINED_AT, NOW.minusDays(3))));

            try (Connection connection = DriverManager.getConnection(JDBC_URL, "sa", ""))
            {
                insertNotice(connection, 12L, 9001L, 100L, "GRADE", 907L,
                        "inside-window", NOW.minusDays(4), null, NOW.minusDays(5));
                insertNotice(connection, 13L, 9001L, 100L, "GRADE", 908L,
                        "after-window", NOW.minusDays(2), null, NOW.minusDays(3));
            }

            assertEquals(1, mapper.countVisible(query));
            assertNotNull(mapper.selectVisibleById(12L, query));
            assertNull(mapper.selectVisibleById(13L, query));
        }
        catch (Exception e)
        {
            throw new IllegalStateException(e);
        }
    }

    private static StudentNoticeQuery query(boolean unreadOnly, NoticeKind kind)
    {
        StudentNoticeQuery query = new StudentNoticeQuery();
        query.setRecipientUserId(9001L);
        query.setAt(NOW);
        query.setBindings(List.of(new NoticeBindingScope(100L, JOINED_AT, null)));
        query.setKind(kind);
        query.setUnreadOnly(unreadOnly);
        query.setLimit(10);
        query.setOffset(0);
        return query;
    }

    private static void insertAnnouncement(Connection connection, long id, long bindingId,
            String status, LocalDateTime opensAt, LocalDateTime closesAt) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO edu_course_announcement
                    (id, binding_id, title, status, opens_at, closes_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """))
        {
            statement.setLong(1, id);
            statement.setLong(2, bindingId);
            statement.setString(3, "announcement-" + id);
            statement.setString(4, status);
            statement.setTimestamp(5, Timestamp.valueOf(opensAt));
            statement.setTimestamp(6, closesAt == null ? null : Timestamp.valueOf(closesAt));
            statement.executeUpdate();
        }
    }

    private static void insertNotice(Connection connection, long id, long recipientUserId,
            Long bindingId, String kind, long sourceId, String title, LocalDateTime visibleAt,
            LocalDateTime readAt, LocalDateTime createdAt) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement("""
                INSERT INTO edu_user_notice
                    (id, recipient_user_id, binding_id, notice_kind, source_id, title,
                     visible_at, read_at, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                """))
        {
            statement.setLong(1, id);
            statement.setLong(2, recipientUserId);
            if (bindingId == null)
            {
                statement.setNull(3, java.sql.Types.BIGINT);
            }
            else
            {
                statement.setLong(3, bindingId);
            }
            statement.setString(4, kind);
            statement.setLong(5, sourceId);
            statement.setString(6, title);
            statement.setTimestamp(7, Timestamp.valueOf(visibleAt));
            statement.setTimestamp(8, readAt == null ? null : Timestamp.valueOf(readAt));
            statement.setTimestamp(9, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        }
    }
}
