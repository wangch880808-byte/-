package com.ruoyi.education.course.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

class AdminOrganizationScopeMapperTest
{
    private static final String JDBC_URL =
            "jdbc:h2:mem:admin_org_scope;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
    private static final LocalDateTime AT = LocalDateTime.of(2026, 11, 28, 0, 0);
    private static SqlSessionFactory sqlSessionFactory;

    @BeforeAll
    static void setUpFactory() throws Exception
    {
        DataSource dataSource = new UnpooledDataSource("org.h2.Driver", JDBC_URL, "sa", "");
        Environment environment = new Environment("test", new JdbcTransactionFactory(), dataSource);
        Configuration configuration = new Configuration(environment);
        String resource = "mapper/education/AdminOrganizationScopeMapper.xml";
        try (InputStream inputStream = Resources.getResourceAsStream(resource))
        {
            new XMLMapperBuilder(inputStream, configuration, resource,
                    configuration.getSqlFragments()).parse();
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
                statement.execute("DROP TABLE IF EXISTS edu_course_space");
                statement.execute("DROP TABLE IF EXISTS edu_teaching_class");
                statement.execute("""
                        CREATE TABLE edu_teaching_class (
                            id BIGINT PRIMARY KEY,
                            organization_id BIGINT NOT NULL,
                            name VARCHAR(160) NOT NULL,
                            code VARCHAR(60) NOT NULL,
                            status VARCHAR(16) NOT NULL
                        )
                        """);
                statement.execute("""
                        CREATE TABLE edu_course_space (
                            id BIGINT PRIMARY KEY,
                            title VARCHAR(160) NOT NULL,
                            deleted_at TIMESTAMP(3)
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
                            teaching_class_id BIGINT NOT NULL,
                            joined_at TIMESTAMP(3) NOT NULL,
                            left_at TIMESTAMP(3)
                        )
                        """);
            }
            insertClass(connection, 100L, 10L, "班级 A", "A", "ACTIVE");
            insertClass(connection, 101L, 10L, "班级 B", "B", "ACTIVE");
            insertClass(connection, 102L, 10L, "草稿班", "D", "DRAFT");
            insertClass(connection, 103L, 20L, "其他学校班", "O", "ACTIVE");
            insertCourse(connection, 1L, "课程 1");
            insertCourse(connection, 2L, "课程 2");
            insertCourse(connection, 3L, "未来课程");
            insertBinding(connection, 1L, 100L, 1L, AT.minusDays(2), null);
            insertBinding(connection, 2L, 100L, 2L, AT.minusDays(1), AT.plusDays(1));
            insertBinding(connection, 3L, 100L, 3L, AT.plusDays(1), null);
            insertBinding(connection, 4L, 102L, 1L, AT.minusDays(1), null);
            insertBinding(connection, 5L, 103L, 1L, AT.minusDays(1), null);
            insertStudent(connection, 100L, AT.minusDays(10), null);
            insertStudent(connection, 100L, AT.minusDays(5), AT.plusDays(1));
            insertStudent(connection, 100L, AT.minusDays(1), AT.minusHours(2));
            insertStudent(connection, 100L, AT.plusDays(1), null);
            insertStudent(connection, 101L, AT.minusDays(3), null);
            insertStudent(connection, 103L, AT.minusDays(1), null);
        }
    }

    @Test
    void expandsActiveBindingsAndCountsEffectiveStudents()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            List<AdminOrganizationClassRangeRow> rows = session
                    .getMapper(AdminOrganizationScopeMapper.class)
                    .selectAuthorizedClassRanges(List.of(10L), AT);

            assertEquals(3, rows.size());
            assertEquals(100L, rows.get(0).getTeachingClassId());
            assertEquals(1L, rows.get(0).getBindingId());
            assertEquals(2L, rows.get(0).getStudentCount());
            assertEquals(100L, rows.get(1).getTeachingClassId());
            assertEquals(2L, rows.get(1).getBindingId());
            assertEquals(2L, rows.get(1).getStudentCount());
            assertEquals(101L, rows.get(2).getTeachingClassId());
            assertNull(rows.get(2).getBindingId());
            assertNull(rows.get(2).getCourseSpaceId());
            assertEquals(1L, rows.get(2).getStudentCount());
        }
    }

    @Test
    void filtersByAuthorizedOrganizationAndActiveClass()
    {
        try (SqlSession session = sqlSessionFactory.openSession())
        {
            List<AdminOrganizationClassRangeRow> rows = session
                    .getMapper(AdminOrganizationScopeMapper.class)
                    .selectAuthorizedClassRanges(List.of(20L), AT);

            assertEquals(1, rows.size());
            assertEquals(103L, rows.get(0).getTeachingClassId());
            assertEquals(1L, rows.get(0).getStudentCount());
        }
    }

    private void insertClass(Connection connection, long id, long organizationId,
            String name, String code, String status) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO edu_teaching_class(id, organization_id, name, code, status) VALUES (?,?,?,?,?)"))
        {
            statement.setLong(1, id);
            statement.setLong(2, organizationId);
            statement.setString(3, name);
            statement.setString(4, code);
            statement.setString(5, status);
            statement.executeUpdate();
        }
    }

    private void insertCourse(Connection connection, long id, String title) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO edu_course_space(id, title, deleted_at) VALUES (?,?,NULL)"))
        {
            statement.setLong(1, id);
            statement.setString(2, title);
            statement.executeUpdate();
        }
    }

    private void insertBinding(Connection connection, long id, long classId, long courseId,
            LocalDateTime publishedAt, LocalDateTime endedAt) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO edu_course_class_binding(id, teaching_class_id, course_space_id, published_at, ended_at) VALUES (?,?,?,?,?)"))
        {
            statement.setLong(1, id);
            statement.setLong(2, classId);
            statement.setLong(3, courseId);
            statement.setTimestamp(4, Timestamp.valueOf(publishedAt));
            statement.setTimestamp(5, endedAt == null ? null : Timestamp.valueOf(endedAt));
            statement.executeUpdate();
        }
    }

    private void insertStudent(Connection connection, long classId, LocalDateTime joinedAt,
            LocalDateTime leftAt) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "INSERT INTO edu_class_student(teaching_class_id, joined_at, left_at) VALUES (?,?,?)"))
        {
            statement.setLong(1, classId);
            statement.setTimestamp(2, Timestamp.valueOf(joinedAt));
            statement.setTimestamp(3, leftAt == null ? null : Timestamp.valueOf(leftAt));
            statement.executeUpdate();
        }
    }
}
