package com.ruoyi.education.course.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;

class CourseAnnouncementMapperTest
{
    private static final String JDBC_URL =
            "jdbc:h2:mem:announcement_mapper;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpFactory() throws Exception
    {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        String resource = "mapper/education/CourseAnnouncementMapper.xml";
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
                statement.execute("DROP TABLE IF EXISTS edu_course_announcement");
                statement.execute("""
                        CREATE TABLE edu_course_announcement (
                            id BIGINT AUTO_INCREMENT PRIMARY KEY,
                            binding_id BIGINT NOT NULL,
                            title VARCHAR(200) NOT NULL,
                            body VARCHAR(4000) NOT NULL,
                            status VARCHAR(16) NOT NULL,
                            opens_at TIMESTAMP(3),
                            closes_at TIMESTAMP(3),
                            published_by_user_id BIGINT,
                            created_by_user_id BIGINT NOT NULL,
                            created_at TIMESTAMP(3) NOT NULL
                        )
                        """);
            }
            insert(connection, 1L, 100L, "visible", "PUBLISHED",
                    NOW.minusDays(1), null, NOW.minusDays(4));
            insert(connection, 2L, 100L, "future", "PUBLISHED",
                    NOW.plusDays(1), null, NOW.minusDays(3));
            insert(connection, 3L, 100L, "closed", "PUBLISHED",
                    NOW.minusHours(2), NOW, NOW.minusDays(2));
            insert(connection, 4L, 100L, "draft", "DRAFT",
                    null, null, NOW.minusDays(1));
            insert(connection, 5L, 200L, "other-binding", "PUBLISHED",
                    NOW.minusDays(1), null, NOW);
            try (Statement statement = connection.createStatement())
            {
                statement.execute("ALTER TABLE edu_course_announcement ALTER COLUMN id RESTART WITH 100");
            }
        }
    }

    @Test
    void selectVisibleByBindingIdReturnsOnlyOpenPublishedAnnouncements()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            List<CourseAnnouncement> announcements = session.getMapper(CourseAnnouncementMapper.class)
                    .selectVisibleByBindingId(100L, NOW);

            assertEquals(1, announcements.size());
            assertEquals(1L, announcements.get(0).getId());
            assertEquals(AnnouncementStatus.PUBLISHED, announcements.get(0).getStatus());
        }
    }

    @Test
    void selectVisibleByBindingIdReturnsEmptyWhenNothingIsVisible()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            List<CourseAnnouncement> announcements = session.getMapper(CourseAnnouncementMapper.class)
                    .selectVisibleByBindingId(300L, NOW);

            assertTrue(announcements.isEmpty());
        }
    }

    @Test
    void selectVisibleByIdHidesFutureAndClosedAnnouncements()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);

            assertNotNull(mapper.selectVisibleById(1L, NOW));
            assertNull(mapper.selectVisibleById(2L, NOW));
            assertNull(mapper.selectVisibleById(3L, NOW));
            assertNull(mapper.selectVisibleById(4L, NOW));
        }
    }

    @Test
    void selectByBindingIdReturnsTeacherRowsNewestFirst()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            List<CourseAnnouncement> announcements = session.getMapper(CourseAnnouncementMapper.class)
                    .selectByBindingId(100L, null);

            assertEquals(List.of(4L, 3L, 2L, 1L),
                    announcements.stream().map(CourseAnnouncement::getId).toList());
        }
    }

    @Test
    void selectByBindingIdFiltersByStatus()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);

            assertEquals(3, mapper.selectByBindingId(100L, AnnouncementStatus.PUBLISHED).size());
            assertEquals(1, mapper.selectByBindingId(100L, AnnouncementStatus.DRAFT).size());
        }
    }

    @Test
    void selectByIdReturnsDraftForTeacherView()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncement announcement = session.getMapper(CourseAnnouncementMapper.class)
                    .selectById(4L);

            assertNotNull(announcement);
            assertEquals("draft", announcement.getTitle());
            assertEquals(AnnouncementStatus.DRAFT, announcement.getStatus());
        }
    }

    @Test
    void selectTeacherVisibleByBindingIdHidesOtherTeachersDraftsWhenNotManager()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);

            assertEquals(List.of(3L, 2L, 1L), mapper
                    .selectTeacherVisibleByBindingId(100L, null, 90002L, false)
                    .stream().map(CourseAnnouncement::getId).toList());

            assertEquals(List.of(4L, 3L, 2L, 1L), mapper
                    .selectTeacherVisibleByBindingId(100L, null, 90001L, false)
                    .stream().map(CourseAnnouncement::getId).toList());

            assertEquals(List.of(4L, 3L, 2L, 1L), mapper
                    .selectTeacherVisibleByBindingId(100L, null, 90002L, true)
                    .stream().map(CourseAnnouncement::getId).toList());
        }
    }

    @Test
    void countTeacherVisibleByBindingIdMatchesVisibilityRules()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);

            assertEquals(4, mapper.countTeacherVisibleByBindingId(100L, null, 90001L, false));
            assertEquals(3, mapper.countTeacherVisibleByBindingId(100L, null, 90002L, false));
            assertEquals(4, mapper.countTeacherVisibleByBindingId(100L, null, 90002L, true));
            assertEquals(3, mapper.countTeacherVisibleByBindingId(
                    100L, AnnouncementStatus.PUBLISHED, 90001L, false));
        }
    }

    @Test
    void pageTeacherVisibleByBindingIdAppliesStableOrderAndLimits()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);

            List<CourseAnnouncement> firstPage = mapper
                    .selectTeacherVisibleByBindingIdPage(100L, null, 90001L, false, 0, 2);
            assertEquals(List.of(4L, 3L),
                    firstPage.stream().map(CourseAnnouncement::getId).toList());
            firstPage.forEach(row -> assertNull(row.getBody()));

            List<CourseAnnouncement> secondPage = mapper
                    .selectTeacherVisibleByBindingIdPage(100L, null, 90001L, false, 2, 2);
            assertEquals(List.of(2L, 1L),
                    secondPage.stream().map(CourseAnnouncement::getId).toList());
            secondPage.forEach(row -> assertNull(row.getBody()));

            assertEquals(List.of(), mapper
                    .selectTeacherVisibleByBindingIdPage(100L, null, 90001L, false, 4, 2)
                    .stream().map(CourseAnnouncement::getId).toList());
        }
    }

    @Test
    void countVisibleByBindingIdsAppliesVisibilityAndBindingScope()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);

            assertEquals(1, mapper.countVisibleByBindingIds(List.of(100L), NOW));
            assertEquals(2, mapper.countVisibleByBindingIds(List.of(100L, 200L), NOW));
            assertEquals(1, mapper.countVisibleByBindingIds(List.of(200L), NOW));
            assertEquals(0, mapper.countVisibleByBindingIds(List.of(300L), NOW));
        }
    }

    @Test
    void selectVisibleByBindingIdsPageUsesOpenOrderAndSummaryFields()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);

            List<CourseAnnouncement> firstPage = mapper
                    .selectVisibleByBindingIdsPage(List.of(100L, 200L), NOW, 0, 1);
            assertEquals(List.of(5L),
                    firstPage.stream().map(CourseAnnouncement::getId).toList());
            firstPage.forEach(row -> assertNull(row.getBody()));

            List<CourseAnnouncement> secondPage = mapper
                    .selectVisibleByBindingIdsPage(List.of(100L, 200L), NOW, 1, 1);
            assertEquals(List.of(1L),
                    secondPage.stream().map(CourseAnnouncement::getId).toList());
            secondPage.forEach(row -> assertNull(row.getBody()));
        }
    }

    @Test
    void insertDraftPersistsDraftAndGeneratedId()
    {
        CourseAnnouncement announcement = new CourseAnnouncement();
        announcement.setBindingId(100L);
        announcement.setTitle("new draft");
        announcement.setBody("body");
        announcement.setOpensAt(NOW.plusDays(1));
        announcement.setClosesAt(NOW.plusDays(2));
        announcement.setCreatedByUserId(90001L);
        announcement.setCreatedAt(NOW);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);
            assertEquals(1, mapper.insertDraft(announcement));
            session.commit();

            assertNotNull(announcement.getId());
            CourseAnnouncement saved = mapper.selectById(announcement.getId());
            assertNotNull(saved);
            assertEquals(AnnouncementStatus.DRAFT, saved.getStatus());
            assertEquals(90001L, saved.getCreatedByUserId());
            assertNull(saved.getPublishedByUserId());
        }
    }

    @Test
    void updateDraftOnlyUpdatesDraftRows()
    {
        CourseAnnouncement draft = new CourseAnnouncement();
        draft.setId(4L);
        draft.setBindingId(100L);
        draft.setTitle("updated draft");
        draft.setBody("updated body");
        draft.setOpensAt(NOW.plusDays(1));
        draft.setClosesAt(null);

        CourseAnnouncement published = new CourseAnnouncement();
        published.setId(1L);
        published.setBindingId(100L);
        published.setTitle("cannot overwrite");
        published.setBody("cannot overwrite");
        published.setOpensAt(null);
        published.setClosesAt(null);

        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);
            assertEquals(1, mapper.updateDraft(draft));
            assertEquals(0, mapper.updateDraft(published));
            session.commit();

            CourseAnnouncement updated = mapper.selectById(4L);
            assertEquals("updated draft", updated.getTitle());
            assertEquals(AnnouncementStatus.DRAFT, updated.getStatus());

            CourseAnnouncement untouched = mapper.selectById(1L);
            assertEquals("visible", untouched.getTitle());
            assertEquals(AnnouncementStatus.PUBLISHED, untouched.getStatus());
        }
    }

    @Test
    void publishDraftSetsPublishedFieldsAndOnlyAcceptsDraft()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);

            assertEquals(1, mapper.publishDraft(4L, 100L, 90001L, NOW));
            assertEquals(0, mapper.publishDraft(4L, 100L, 90001L, NOW));

            CourseAnnouncement published = mapper.selectById(4L);
            assertEquals(AnnouncementStatus.PUBLISHED, published.getStatus());
            assertEquals(90001L, published.getPublishedByUserId());
            assertNotNull(published.getOpensAt());
        }
    }

    @Test
    void archivePublishedOnlyAcceptsPublishedRows()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            CourseAnnouncementMapper mapper = session.getMapper(CourseAnnouncementMapper.class);

            assertEquals(1, mapper.archivePublished(1L, 100L));
            assertEquals(0, mapper.archivePublished(1L, 100L));
            assertEquals(0, mapper.archivePublished(4L, 100L));

            CourseAnnouncement archived = mapper.selectById(1L);
            assertEquals(AnnouncementStatus.ARCHIVED, archived.getStatus());
            assertEquals(90001L, archived.getPublishedByUserId());
        }
    }

    private static void insert(Connection connection, long id, long bindingId, String title,
            String status, LocalDateTime opensAt, LocalDateTime closesAt, LocalDateTime createdAt) throws Exception
    {
        String sql = """
                INSERT INTO edu_course_announcement
                    (id, binding_id, title, body, status, opens_at, closes_at,
                     published_by_user_id, created_by_user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            statement.setLong(1, id);
            statement.setLong(2, bindingId);
            statement.setString(3, title);
            statement.setString(4, "body");
            statement.setString(5, status);
            statement.setTimestamp(6, opensAt == null ? null : Timestamp.valueOf(opensAt));
            statement.setTimestamp(7, closesAt == null ? null : Timestamp.valueOf(closesAt));
            if ("DRAFT".equals(status))
            {
                statement.setNull(8, java.sql.Types.BIGINT);
            }
            else
            {
                statement.setLong(8, 90001L);
            }
            statement.setLong(9, 90001L);
            statement.setTimestamp(10, Timestamp.valueOf(createdAt));
            statement.executeUpdate();
        }
    }
}
