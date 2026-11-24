package com.ruoyi.education.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.impl.EduMembershipTimelineServiceImpl;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
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
import org.springframework.test.util.ReflectionTestUtils;

/**
 * 时点成员查询的 SQL 语义验证（H2 隔离）。
 *
 * <p>核心场景（第 6 组通知投递）：事件 9:00 发生、学生 10:00 退班、通知 11:00 投递，
 * at=09:00 必须命中该生，at=11:00 必须不命中；半开区间边界（left_at = at 不算在班）、
 * 绑定发布/结束时间的覆盖判定一并验证。</p>
 */
class EduMembershipTimelineMapperTest
{
    private static final String JDBC_URL =
            "jdbc:h2:mem:edu_membership_timeline;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpFactory() throws Exception
    {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        String resource = "mapper/education/EduMembershipTimelineMapper.xml";
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
                statement.execute("DROP TABLE IF EXISTS edu_class_student");
                statement.execute("DROP TABLE IF EXISTS edu_course_class_binding");
                statement.execute("DROP TABLE IF EXISTS edu_teaching_class");
                statement.execute("DROP TABLE IF EXISTS v_identity_user");
                statement.execute("""
                        CREATE TABLE edu_teaching_class (
                            id BIGINT PRIMARY KEY,
                            status VARCHAR(16) NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE edu_course_class_binding (
                            id BIGINT PRIMARY KEY,
                            teaching_class_id BIGINT NOT NULL,
                            course_space_id BIGINT NOT NULL,
                            published_at TIMESTAMP(3) NOT NULL,
                            ended_at TIMESTAMP(3)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE edu_class_student (
                            id BIGINT PRIMARY KEY,
                            teaching_class_id BIGINT NOT NULL,
                            user_id BIGINT NOT NULL,
                            joined_at TIMESTAMP(3) NOT NULL,
                            left_at TIMESTAMP(3)
                        )
                        """);
                statement.execute("""
                        CREATE TABLE v_identity_user (
                            user_id BIGINT PRIMARY KEY,
                            user_name VARCHAR(64),
                            nick_name VARCHAR(64)
                        )
                        """);
            }
            insertClass(connection, 1L, "ACTIVE");
            insertClass(connection, 2L, "DRAFT");
            insertBinding(connection, 100L, 1L, 10L, ts(8, 0), null);
            insertBinding(connection, 101L, 1L, 11L, ts(10, 0), null);
            insertBinding(connection, 102L, 2L, 12L, ts(8, 0), ts(9, 0));
            insertBinding(connection, 103L, 2L, 13L, ts(8, 0), null);
            insertStudent(connection, 1L, 1L, 9001L, ts(7, 0), ts(10, 0));
            insertStudent(connection, 2L, 1L, 9002L, ts(8, 30), null);
            insertStudent(connection, 3L, 2L, 9001L, ts(7, 0), null);
            insertUser(connection, 9001L, "u9001", "学生一");
            insertUser(connection, 9002L, "u9002", "学生二");
        }
    }

    private static Timestamp ts(int hour, int minute)
    {
        return Timestamp.from(LocalDateTime.of(2026, 11, 20, hour, minute).toInstant(ZoneOffset.UTC));
    }

    private static void insertClass(Connection connection, Long id, String status) throws Exception
    {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO edu_teaching_class(id, status) VALUES (?, ?)"))
        {
            ps.setLong(1, id);
            ps.setString(2, status);
            ps.executeUpdate();
        }
    }

    private static void insertBinding(Connection connection, Long id, Long classId, Long courseId,
            Timestamp publishedAt, Timestamp endedAt) throws Exception
    {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO edu_course_class_binding(id, teaching_class_id, course_space_id, published_at, ended_at)"
                        + " VALUES (?, ?, ?, ?, ?)"))
        {
            ps.setLong(1, id);
            ps.setLong(2, classId);
            ps.setLong(3, courseId);
            ps.setTimestamp(4, publishedAt);
            ps.setTimestamp(5, endedAt);
            ps.executeUpdate();
        }
    }

    private static void insertStudent(Connection connection, Long id, Long classId, Long userId,
            Timestamp joinedAt, Timestamp leftAt) throws Exception
    {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO edu_class_student(id, teaching_class_id, user_id, joined_at, left_at)"
                        + " VALUES (?, ?, ?, ?, ?)"))
        {
            ps.setLong(1, id);
            ps.setLong(2, classId);
            ps.setLong(3, userId);
            ps.setTimestamp(4, joinedAt);
            ps.setTimestamp(5, leftAt);
            ps.executeUpdate();
        }
    }

    private static void insertUser(Connection connection, Long userId, String userName, String nickName)
            throws Exception
    {
        try (PreparedStatement ps = connection.prepareStatement(
                "INSERT INTO v_identity_user(user_id, user_name, nick_name) VALUES (?, ?, ?)"))
        {
            ps.setLong(1, userId);
            ps.setString(2, userName);
            ps.setString(3, nickName);
            ps.executeUpdate();
        }
    }

    @Test
    void selectStudentsInBindingUsesOccurredAtNotDeliveryTime()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            EduMembershipTimelineMapper mapper = session.getMapper(EduMembershipTimelineMapper.class);

            List<Map<String, Object>> atNine = mapper.selectStudentsInBinding(100L, ts(9, 0));
            assertEquals(2, atNine.size());
            assertEquals(9001L, atNine.get(0).get("user_id"));
            assertEquals(9002L, atNine.get(1).get("user_id"));
            assertEquals("学生一", atNine.get(0).get("nick_name"));

            List<Map<String, Object>> atEleven = mapper.selectStudentsInBinding(100L, ts(11, 0));
            assertEquals(1, atEleven.size());
            assertEquals(9002L, atEleven.get(0).get("user_id"));
        }
    }

    @Test
    void selectStudentsInBindingHonorsBindingPublishedAndEnded()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            EduMembershipTimelineMapper mapper = session.getMapper(EduMembershipTimelineMapper.class);

            assertEquals(0, mapper.selectStudentsInBinding(101L, ts(9, 0)).size());
            assertEquals(1, mapper.selectStudentsInBinding(101L, ts(10, 0)).size());
            assertEquals(0, mapper.selectStudentsInBinding(102L, ts(9, 0)).size());
        }
    }

    @Test
    void selectStudentSegmentsInBindingReturnsCoveringSegmentOnly()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            EduMembershipTimelineMapper mapper = session.getMapper(EduMembershipTimelineMapper.class);

            List<Map<String, Object>> atNine = mapper.selectStudentSegmentsInBinding(100L, 9001L, ts(9, 0));
            assertEquals(1, atNine.size());
            assertEquals(1L, atNine.get(0).get("member_id"));
            assertEquals(ts(7, 0), atNine.get(0).get("joined_at"));
            assertEquals(ts(10, 0), atNine.get(0).get("left_at"));

            assertEquals(0, mapper.selectStudentSegmentsInBinding(100L, 9001L, ts(10, 0)).size());
            assertEquals(0, mapper.selectStudentSegmentsInBinding(100L, 9001L, ts(11, 0)).size());
        }
    }

    @Test
    void selectStudentSegmentsInBindingReturnsAllOverlappingRows() throws Exception
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            insertStudent(session.getConnection(), 4L, 1L, 9001L, ts(8, 45), ts(12, 0));
            EduMembershipTimelineMapper mapper = session.getMapper(EduMembershipTimelineMapper.class);

            List<Map<String, Object>> segments = mapper.selectStudentSegmentsInBinding(100L, 9001L, ts(9, 0));
            assertEquals(2, segments.size());
        }
    }

    @Test
    void serviceFailsClosedWhenMemberSegmentsOverlap() throws Exception
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            insertStudent(session.getConnection(), 4L, 1L, 9001L, ts(8, 45), ts(12, 0));
            EduMembershipTimelineServiceImpl service = new EduMembershipTimelineServiceImpl();
            ReflectionTestUtils.setField(service, "mapper", session.getMapper(EduMembershipTimelineMapper.class));
            Instant at = Instant.parse("2026-11-20T09:00:00Z");

            assertThrows(ServiceException.class, () -> service.studentInBinding(100L, 9001L, at));
            assertThrows(ServiceException.class, () -> service.studentsInBinding(100L, at));
            assertThrows(ServiceException.class, () -> service.studentBindings(9001L, at));
        }
    }

    @Test
    void selectBindingWithClassReturnsCurrentStatus()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            EduMembershipTimelineMapper mapper = session.getMapper(EduMembershipTimelineMapper.class);

            Map<String, Object> binding = mapper.selectBindingWithClass(100L);
            assertEquals(100L, binding.get("binding_id"));
            assertEquals("ACTIVE", binding.get("teaching_class_status"));
            assertEquals("DRAFT", mapper.selectBindingWithClass(103L).get("teaching_class_status"));
            assertNull(mapper.selectBindingWithClass(999L));
        }
    }

    @Test
    void selectStudentBindingsReturnsBindingsCoveringAt()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            EduMembershipTimelineMapper mapper = session.getMapper(EduMembershipTimelineMapper.class);

            List<Map<String, Object>> atNine = mapper.selectStudentBindings(9001L, ts(9, 0));
            assertEquals(2, atNine.size());
            assertEquals(100L, atNine.get(0).get("binding_id"));
            assertEquals(103L, atNine.get(1).get("binding_id"));

            List<Map<String, Object>> atEleven = mapper.selectStudentBindings(9001L, ts(11, 0));
            assertEquals(1, atEleven.size());
            assertEquals(103L, atEleven.get(0).get("binding_id"));
        }
    }
}
