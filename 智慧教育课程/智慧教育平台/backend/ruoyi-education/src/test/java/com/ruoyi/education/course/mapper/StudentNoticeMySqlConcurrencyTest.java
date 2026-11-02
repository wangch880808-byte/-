package com.ruoyi.education.course.mapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;

/**
 * 真实 MySQL 并发集成测试。
 *
 * <p>默认跳过；通过系统属性提供可丢弃的 MySQL 测试库后执行：</p>
 *
 * <pre>
 * -Dnotice.mysql.url=jdbc:mysql://127.0.0.1:3399/notice_repro?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC
 * -Dnotice.mysql.user=root
 * -Dnotice.mysql.password=
 * </pre>
 *
 * <p>测试只创建带随机后缀的临时表，结束后删除；不会修改项目数据库配置。</p>
 */
class StudentNoticeMySqlConcurrencyTest
{
    private static final LocalDateTime AT = LocalDateTime.of(2026, 10, 28, 12, 0, 0, 123_000_000);

    private static final LocalDateTime JOINED_AT = AT.minusDays(5);

    private static final LocalDateTime VISIBLE_AT = AT.minusHours(1);

    private static final LocalDateTime CREATED_AT = AT.minusHours(2);

    private static final LocalDateTime B_READ_AT = AT.minusMinutes(1);

    private final String url = System.getProperty("notice.mysql.url", "");

    private final String user = System.getProperty("notice.mysql.user", "root");

    private final String password = System.getProperty("notice.mysql.password", "");

    @Test
    void plainCountIsStaleAndCurrentReadIsFreshUnderRepeatableRead() throws Exception
    {
        Assumptions.assumeTrue(!url.isBlank(),
                "notice.mysql.url not set; skipping real MySQL concurrency test");

        String suffix = Long.toHexString(System.nanoTime());
        String noticeTable = "it_notice_" + suffix;
        String announcementTable = "it_announcement_" + suffix;

        try (Connection setup = open();
                Connection a = open();
                Connection b = open();
                Connection c = open())
        {
            createTables(setup, noticeTable, announcementTable);
            seed(setup, noticeTable);

            a.setAutoCommit(false);
            a.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);
            b.setAutoCommit(false);
            b.setTransactionIsolation(Connection.TRANSACTION_REPEATABLE_READ);

            // A 的第一条普通一致性读模拟生产授权适配器，建立 REPEATABLE READ 快照。
            assertEquals(4, count(a, noticeTable, announcementTable, true));

            // B 在 A 快照之后标记一条通知已读并提交。
            try (PreparedStatement update = b.prepareStatement(
                    "UPDATE `" + noticeTable + "` SET read_at = ? WHERE id = 1 AND read_at IS NULL"))
            {
                update.setObject(1, B_READ_AT);
                assertEquals(1, update.executeUpdate());
            }
            b.commit();

            // A 的条件更新是当前读，应跳过 B 已读的 id=1，只标记 3 条。
            try (PreparedStatement update = a.prepareStatement(updateSql(noticeTable, announcementTable)))
            {
                update.setObject(1, AT);
                update.setObject(2, AT);
                update.setObject(3, JOINED_AT);
                update.setObject(4, AT);
                update.setObject(5, AT);
                assertEquals(3, update.executeUpdate(), "markedCount must reflect the current read");
            }

            // 旧实现的普通 COUNT 仍使用 A 的早期快照：B 的 id=1 被继续算作未读。
            assertEquals(1, count(a, noticeTable, announcementTable, true),
                    "plain COUNT under REPEATABLE READ is expected to be stale");

            // 新实现的当前读 ID 列表应看到 B 已提交的 id=1，返回空集合。
            List<Long> remaining = idsForUpdate(a, noticeTable, announcementTable);
            assertTrue(remaining.isEmpty(),
                    "current-read ID list must not reuse the transaction snapshot");

            // A 的条件更新不覆盖 B 已写入的 readAt。
            assertEquals(B_READ_AT, readAt(c, noticeTable, 1L));

            a.commit();
            assertEquals(0, count(c, noticeTable, announcementTable, true));
            assertEquals(AT, readAt(c, noticeTable, 2L));
            assertEquals(AT, readAt(c, noticeTable, 3L));
            assertEquals(AT, readAt(c, noticeTable, 4L));
        }
        finally
        {
            try (Connection cleanup = open(); Statement statement = cleanup.createStatement())
            {
                statement.execute("DROP TABLE IF EXISTS `" + noticeTable + "`");
                statement.execute("DROP TABLE IF EXISTS `" + announcementTable + "`");
            }
        }
    }

    private Connection open() throws Exception
    {
        return DriverManager.getConnection(url, user, password);
    }

    private void createTables(Connection connection, String noticeTable, String announcementTable)
            throws Exception
    {
        try (Statement statement = connection.createStatement())
        {
            statement.execute("DROP TABLE IF EXISTS `" + noticeTable + "`");
            statement.execute("DROP TABLE IF EXISTS `" + announcementTable + "`");
            statement.execute("""
                    CREATE TABLE `%s` (
                        id BIGINT PRIMARY KEY,
                        binding_id BIGINT NOT NULL,
                        status VARCHAR(16) NOT NULL,
                        opens_at DATETIME(3) NULL,
                        closes_at DATETIME(3) NULL
                    ) ENGINE=InnoDB
                    """.formatted(announcementTable));
            statement.execute("""
                    CREATE TABLE `%s` (
                        id BIGINT PRIMARY KEY,
                        recipient_user_id BIGINT NOT NULL,
                        binding_id BIGINT NULL,
                        notice_kind VARCHAR(24) NOT NULL,
                        source_id BIGINT NOT NULL,
                        title VARCHAR(200) NOT NULL,
                        visible_at DATETIME(3) NOT NULL,
                        read_at DATETIME(3) NULL,
                        created_at DATETIME(3) NOT NULL
                    ) ENGINE=InnoDB
                    """.formatted(noticeTable));
        }
    }

    private void seed(Connection connection, String noticeTable) throws Exception
    {
        try (PreparedStatement insert = connection.prepareStatement(
                "INSERT INTO `" + noticeTable
                        + "` (id, recipient_user_id, binding_id, notice_kind, source_id,"
                        + " title, visible_at, read_at, created_at)"
                        + " VALUES (?, 9001, 100, 'GRADE', ?, ?, ?, NULL, ?)"))
        {
            for (long id = 1; id <= 4; id++)
            {
                insert.setLong(1, id);
                insert.setLong(2, 900L + id);
                insert.setString(3, "n" + id);
                insert.setObject(4, VISIBLE_AT);
                insert.setObject(5, CREATED_AT);
                insert.addBatch();
            }
            insert.executeBatch();
        }
    }

    private long count(Connection connection, String noticeTable, String announcementTable,
            boolean unreadOnly) throws Exception
    {
        String sql = "SELECT COUNT(*) FROM `" + noticeTable + "` n WHERE "
                + visibility(noticeTable, announcementTable)
                + (unreadOnly ? " AND n.read_at IS NULL" : "");
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            bindVisibility(statement);
            try (ResultSet result = statement.executeQuery())
            {
                result.next();
                return result.getLong(1);
            }
        }
    }

    private List<Long> idsForUpdate(Connection connection, String noticeTable,
            String announcementTable) throws Exception
    {
        String sql = "SELECT n.id FROM `" + noticeTable + "` n WHERE "
                + visibility(noticeTable, announcementTable)
                + " AND n.read_at IS NULL FOR UPDATE";
        try (PreparedStatement statement = connection.prepareStatement(sql))
        {
            bindVisibility(statement);
            try (ResultSet result = statement.executeQuery())
            {
                List<Long> ids = new ArrayList<>();
                while (result.next())
                {
                    ids.add(result.getLong(1));
                }
                return ids;
            }
        }
    }

    private String updateSql(String noticeTable, String announcementTable)
    {
        return "UPDATE `" + noticeTable + "` n SET read_at = ? WHERE "
                + visibility(noticeTable, announcementTable)
                + " AND n.read_at IS NULL";
    }

    private String visibility(String noticeTable, String announcementTable)
    {
        return "n.recipient_user_id = 9001"
                + " AND n.binding_id IS NOT NULL"
                + " AND n.visible_at <= ?"
                + " AND (n.binding_id = 100 AND n.visible_at >= ?)"
                + " AND (n.notice_kind <> 'ANNOUNCEMENT' OR EXISTS ("
                + " SELECT 1 FROM `" + announcementTable + "` a"
                + " WHERE a.id = n.source_id AND a.binding_id = n.binding_id"
                + " AND a.status = 'PUBLISHED' AND a.opens_at IS NOT NULL"
                + " AND a.opens_at <= ? AND (a.closes_at IS NULL OR a.closes_at > ?)))";
    }

    private void bindVisibility(PreparedStatement statement) throws Exception
    {
        statement.setObject(1, AT);
        statement.setObject(2, JOINED_AT);
        statement.setObject(3, AT);
        statement.setObject(4, AT);
    }

    private LocalDateTime readAt(Connection connection, String noticeTable, long id) throws Exception
    {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT read_at FROM `" + noticeTable + "` WHERE id = ?"))
        {
            statement.setLong(1, id);
            try (ResultSet result = statement.executeQuery())
            {
                assertTrue(result.next());
                return result.getObject(1, LocalDateTime.class);
            }
        }
    }
}
