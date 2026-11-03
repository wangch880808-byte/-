package com.ruoyi.education.course.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DataIntegrityViolationException;
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
import com.ruoyi.education.course.authorization.TeacherAnnouncementAccess;
import com.ruoyi.education.course.authorization.TeachingClassAuthorizationPort;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.domain.TeacherAnnouncementAuthority;
import com.ruoyi.education.course.exception.AnnouncementAccessDeniedException;
import com.ruoyi.education.course.exception.AnnouncementAuthorizationUnavailableException;
import com.ruoyi.education.course.exception.AnnouncementConcurrentUpdateException;
import com.ruoyi.education.course.exception.AnnouncementNotFoundException;
import com.ruoyi.education.course.exception.AnnouncementPersistenceException;
import com.ruoyi.education.course.exception.AnnouncementStateConflictException;
import com.ruoyi.education.course.exception.AnnouncementValidationException;
import com.ruoyi.education.course.mapper.CourseAnnouncementMapper;
import com.ruoyi.education.course.service.CourseAnnouncementPage;

@SpringJUnitConfig(CourseAnnouncementServiceTest.TestConfig.class)
class CourseAnnouncementServiceTest
{
    private static final Long TEACHER_ID = 930000000001L;
    private static final Long OTHER_TEACHER_ID = 930000000002L;
    private static final Long BINDING_ID = 100L;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);

    @Autowired
    private CourseAnnouncementService service;

    @Autowired
    private TestMapper testMapper;

    @Autowired
    private FakeAuthorizationPort authorizationPort;

    @Autowired
    private FakeFanoutPort fanoutPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private int nextId;

    @BeforeEach
    void setUp()
    {
        SecurityContextHolder.clearContext();
        authenticate(TEACHER_ID);
        fanoutPort.reset();
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
        testMapper.reset();
        authorizationPort.reset();
    }

    @AfterEach
    void tearDown()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void authorizedTeacherCanCreateDraft()
    {
        authorizationPort.allow(BINDING_ID, true);

        CourseAnnouncement created = service.createDraft(BINDING_ID, "title", "body", null, null);

        assertNotNull(created.getId());
        assertEquals("title", created.getTitle());
        assertEquals(AnnouncementStatus.DRAFT, created.getStatus());
        assertEquals(TEACHER_ID, created.getCreatedByUserId());
        assertNull(created.getPublishedByUserId());
        assertEquals(AnnouncementStatus.DRAFT, statusOf(created.getId()));
        assertEquals(1, countRows());
    }

    @Test
    void createDraftAllowsCloseTimeWithoutOpenTime()
    {
        authorizationPort.allow(BINDING_ID, true);
        LocalDateTime closesAt = NOW.plusHours(1);

        CourseAnnouncement created = service.createDraft(BINDING_ID, "title", "body", null, closesAt);

        assertNull(created.getOpensAt());
        assertEquals(closesAt, created.getClosesAt());
        assertEquals(AnnouncementStatus.DRAFT, statusOf(created.getId()));
    }

    @Test
    void createDraftRejectsBodyOverCharacterLimit()
    {
        authorizationPort.allow(BINDING_ID, true);
        String body = "a".repeat(20_001);

        assertThrows(AnnouncementValidationException.class,
                () -> service.createDraft(BINDING_ID, "title", body, null, null));
        assertEquals(0, countRows());
    }

    @Test
    void createDraftRejectsBodyOverUtf8ByteLimit()
    {
        authorizationPort.allow(BINDING_ID, true);
        String body = "😀".repeat(16_384);

        assertThrows(AnnouncementValidationException.class,
                () -> service.createDraft(BINDING_ID, "title", body, null, null));
        assertEquals(0, countRows());
    }

    @Test
    void createDraftAcceptsBodyAtCharacterLimit()
    {
        authorizationPort.allow(BINDING_ID, true);
        String body = "a".repeat(20_000);

        CourseAnnouncement created = service.createDraft(BINDING_ID, "title", body, null, null);

        assertEquals(20_000, created.getBody().length());
    }

    @Test
    void createDraftAcceptsBodyAtUtf8ByteLimit()
    {
        authorizationPort.allow(BINDING_ID, true);
        String body = "😀".repeat(16_383);

        CourseAnnouncement created = service.createDraft(BINDING_ID, "title", body, null, null);

        assertEquals(16_383, created.getBody().codePointCount(0, created.getBody().length()));
    }

    @Test
    void createDraftRejectsWindowThatCollapsesAtDatabasePrecision()
    {
        authorizationPort.allow(BINDING_ID, true);
        LocalDateTime opensAt = NOW.plusNanos(600_000);
        LocalDateTime closesAt = NOW.plusNanos(1_400_000);

        assertThrows(AnnouncementValidationException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", opensAt, closesAt));
        assertEquals(0, countRows());
    }

    @Test
    void teacherWithoutPublishPermissionCannotCreateDraft()
    {
        authorizationPort.allow(BINDING_ID, false);

        assertThrows(AnnouncementAccessDeniedException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertEquals(0, countRows());
    }

    @Test
    void nonTeacherCannotCreateDraft()
    {
        authorizationPort.setAccess(BINDING_ID, false, true);

        assertThrows(AnnouncementAccessDeniedException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertEquals(0, countRows());
    }

    @Test
    void creatorCanEditOwnDraftWithoutPublishPermission()
    {
        long id = insertRow(BINDING_ID, "old", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, false);

        CourseAnnouncement updated = service.updateDraft(BINDING_ID, id, "new", "new body", null, null);

        assertEquals("new", updated.getTitle());
        assertEquals("new body", updated.getBody());
        assertEquals(AnnouncementStatus.DRAFT, statusOf(id));
    }

    @Test
    void managerCanEditAnotherTeachersDraft()
    {
        long id = insertRow(BINDING_ID, "old", "DRAFT", OTHER_TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);

        CourseAnnouncement updated = service.updateDraft(BINDING_ID, id, "managed", "body", null, null);

        assertEquals("managed", updated.getTitle());
    }

    @Test
    void teacherWithoutPublishPermissionCannotEditAnotherTeachersDraft()
    {
        long id = insertRow(BINDING_ID, "old", "DRAFT", OTHER_TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, false);

        assertThrows(AnnouncementAccessDeniedException.class,
                () -> service.updateDraft(BINDING_ID, id, "denied", "body", null, null));
        assertEquals("old", titleOf(id));
    }

    @Test
    void publishedAnnouncementCannotBeEditedAsDraft()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementStateConflictException.class,
                () -> service.updateDraft(BINDING_ID, id, "changed", "body", null, null));
        assertEquals("published", titleOf(id));
    }

    @Test
    void unchangedDraftUpdateReturnsCurrentWithoutConcurrentConflict()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.NO_CHANGE;

        CourseAnnouncement result = service.updateDraft(BINDING_ID, id, "draft", "body", null, null);

        assertEquals(id, result.getId());
        assertEquals("draft", result.getTitle());
        assertEquals(AnnouncementStatus.DRAFT, statusOf(id));
    }

    @Test
    void updateDraftRejectsBodyOverCharacterLimit()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        String body = "a".repeat(20_001);

        assertThrows(AnnouncementValidationException.class,
                () -> service.updateDraft(BINDING_ID, id, "draft", body, null, null));
        assertEquals("draft", titleOf(id));
    }

    @Test
    void updateDraftRejectsBodyOverUtf8ByteLimit()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        String body = "😀".repeat(16_384);

        assertThrows(AnnouncementValidationException.class,
                () -> service.updateDraft(BINDING_ID, id, "draft", body, null, null));
        assertEquals("draft", titleOf(id));
    }

    @Test
    void updateDraftAcceptsBodyAtCharacterLimit()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        String body = "a".repeat(20_000);

        CourseAnnouncement updated = service.updateDraft(BINDING_ID, id, "draft", body, null, null);

        assertEquals(20_000, updated.getBody().length());
    }

    @Test
    void updateDraftAcceptsBodyAtUtf8ByteLimit()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        String body = "😀".repeat(16_383);

        CourseAnnouncement updated = service.updateDraft(BINDING_ID, id, "draft", body, null, null);

        assertEquals(16_383, updated.getBody().codePointCount(0, updated.getBody().length()));
    }

    @Test
    void publishDraftSetsPublishedFields()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);

        CourseAnnouncement published = service.publish(BINDING_ID, id);

        assertEquals(AnnouncementStatus.PUBLISHED, published.getStatus());
        assertEquals(TEACHER_ID, published.getPublishedByUserId());
        assertEquals(NOW, published.getOpensAt());
        assertEquals(AnnouncementStatus.PUBLISHED, statusOf(id));
    }

    @Test
    void teacherWithoutPublishPermissionCannotPublish()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, false);

        assertThrows(AnnouncementAccessDeniedException.class, () -> service.publish(BINDING_ID, id));
        assertEquals(AnnouncementStatus.DRAFT, statusOf(id));
    }

    @Test
    void repeatPublishIsIdempotentSuccess()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        CourseAnnouncement first = service.publish(BINDING_ID, id);

        CourseAnnouncement second = service.publish(BINDING_ID, id);

        assertEquals(AnnouncementStatus.PUBLISHED, second.getStatus());
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getPublishedByUserId(), second.getPublishedByUserId());
        assertEquals(first.getOpensAt(), second.getOpensAt());
        assertEquals(AnnouncementStatus.PUBLISHED, statusOf(id));
        // 重复发布只走幂等返回，不重复 fan-out。
        assertEquals(1, fanoutPort.calls);
    }

    @Test
    void fanoutFailureRollsBackAnnouncementPublish()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        fanoutPort.failure = new com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException(
                "学生教学班授权服务尚未接入");

        assertThrows(com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException.class,
                () -> service.publish(BINDING_ID, id));

        // fan-out 与发布同事务：授权 503 必须回滚公告状态。
        assertEquals(AnnouncementStatus.DRAFT, statusOf(id));
        assertEquals(1, fanoutPort.calls);
    }

    @Test
    void archivedAnnouncementCannotBePublished()
    {
        long id = insertRow(BINDING_ID, "archived", "ARCHIVED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementStateConflictException.class, () -> service.publish(BINDING_ID, id));
        assertEquals(AnnouncementStatus.ARCHIVED, statusOf(id));
    }

    @Test
    void creatorWithoutPublishPermissionCannotPublishOwnDraft()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, false);

        assertThrows(AnnouncementAccessDeniedException.class, () -> service.publish(BINDING_ID, id));
        assertEquals(AnnouncementStatus.DRAFT, statusOf(id));
    }

    @Test
    void publishRejectsDraftWhoseCloseTimeIsNotAfterPublishTime()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        jdbcTemplate.update("update edu_course_announcement set closes_at = ? where id = ?",
                Timestamp.valueOf(NOW.minusMinutes(1)), id);
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementValidationException.class, () -> service.publish(BINDING_ID, id));
        assertEquals(AnnouncementStatus.DRAFT, statusOf(id));
    }

    @Test
    void publishDraftWithFutureCloseTimeUsesServerOpenTime()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        LocalDateTime closesAt = NOW.plusHours(1);
        jdbcTemplate.update("update edu_course_announcement set closes_at = ? where id = ?",
                Timestamp.valueOf(closesAt), id);
        authorizationPort.allow(BINDING_ID, true);

        CourseAnnouncement published = service.publish(BINDING_ID, id);

        assertEquals(AnnouncementStatus.PUBLISHED, published.getStatus());
        assertEquals(NOW, published.getOpensAt());
        assertEquals(closesAt, published.getClosesAt());
    }

    @Test
    void publishRejectsDraftWhoseCloseTimeIsNotAfterExistingOpenTime()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        jdbcTemplate.update("update edu_course_announcement set opens_at = ?, closes_at = ? where id = ?",
                Timestamp.valueOf(NOW.minusHours(1)), Timestamp.valueOf(NOW.minusHours(2)), id);
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementValidationException.class, () -> service.publish(BINDING_ID, id));
        assertEquals(AnnouncementStatus.DRAFT, statusOf(id));
    }

    @Test
    void unauthorizedRepeatPublishIsDeniedBeforeIdempotentReturn()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, false);

        assertThrows(AnnouncementAccessDeniedException.class, () -> service.publish(BINDING_ID, id));
        assertEquals(AnnouncementStatus.PUBLISHED, statusOf(id));
    }

    @Test
    void unauthorizedCannotProbeMissingAnnouncementByPublish()
    {
        authorizationPort.allow(BINDING_ID, false);

        assertThrows(AnnouncementAccessDeniedException.class, () -> service.publish(BINDING_ID, 9999L));
    }

    @Test
    void unauthorizedRepeatArchiveIsDeniedBeforeIdempotentReturn()
    {
        long id = insertRow(BINDING_ID, "archived", "ARCHIVED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, false);

        assertThrows(AnnouncementAccessDeniedException.class, () -> service.archive(BINDING_ID, id));
        assertEquals(AnnouncementStatus.ARCHIVED, statusOf(id));
    }

    @Test
    void archivePublishedAnnouncement()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, true);

        CourseAnnouncement archived = service.archive(BINDING_ID, id);

        assertEquals(AnnouncementStatus.ARCHIVED, archived.getStatus());
        assertEquals(AnnouncementStatus.ARCHIVED, statusOf(id));
    }

    @Test
    void draftCannotBeArchived()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementStateConflictException.class, () -> service.archive(BINDING_ID, id));
        assertEquals(AnnouncementStatus.DRAFT, statusOf(id));
    }

    @Test
    void repeatArchiveIsIdempotentSuccess()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, true);
        CourseAnnouncement first = service.archive(BINDING_ID, id);

        CourseAnnouncement second = service.archive(BINDING_ID, id);

        assertEquals(AnnouncementStatus.ARCHIVED, second.getStatus());
        assertEquals(first.getId(), second.getId());
        assertEquals(first.getPublishedByUserId(), second.getPublishedByUserId());
        assertEquals(first.getOpensAt(), second.getOpensAt());
        assertEquals(AnnouncementStatus.ARCHIVED, statusOf(id));
    }

    @Test
    void operationOnAnotherBindingIsNotFound()
    {
        long id = insertRow(200L, "other class", "DRAFT", OTHER_TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementNotFoundException.class,
                () -> service.updateDraft(BINDING_ID, id, "denied", "body", null, null));
        assertEquals("other class", titleOf(id));
    }

    @Test
    void missingAnnouncementIsNotFound()
    {
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementNotFoundException.class,
                () -> service.updateDraft(BINDING_ID, 9999L, "title", "body", null, null));
    }

    @Test
    void publishZeroRowTargetStateReturnsCurrentAnnouncement()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.TARGET_STATE;

        CourseAnnouncement result = service.publish(BINDING_ID, id);

        assertEquals(AnnouncementStatus.PUBLISHED, result.getStatus());
        assertEquals(TEACHER_ID, result.getPublishedByUserId());
        assertNotNull(result.getOpensAt());
        assertEquals(AnnouncementStatus.PUBLISHED, statusOf(id));
    }

    @Test
    void publishZeroRowDeletedAnnouncementIsNotFound()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.DELETE;

        assertThrows(AnnouncementNotFoundException.class, () -> service.publish(BINDING_ID, id));
    }

    @Test
    void publishZeroRowReboundAnnouncementIsNotFound()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.REBIND;

        assertThrows(AnnouncementNotFoundException.class, () -> service.publish(BINDING_ID, id));
    }

    @Test
    void publishZeroRowIllegalStateIsStateConflict()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.ILLEGAL_STATE;

        assertThrows(AnnouncementStateConflictException.class, () -> service.publish(BINDING_ID, id));
    }

    @Test
    void publishZeroRowUnchangedIsConcurrentConflict()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.NO_CHANGE;

        assertThrows(AnnouncementConcurrentUpdateException.class, () -> service.publish(BINDING_ID, id));
        assertEquals(AnnouncementStatus.DRAFT, statusOf(id));
    }

    @Test
    void updateZeroRowUnchangedIsConcurrentConflict()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.NO_CHANGE;

        assertThrows(AnnouncementConcurrentUpdateException.class,
                () -> service.updateDraft(BINDING_ID, id, "changed", "body", null, null));
        assertEquals("draft", titleOf(id));
    }

    @Test
    void archiveZeroRowTargetStateReturnsCurrentAnnouncement()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.TARGET_STATE;

        CourseAnnouncement result = service.archive(BINDING_ID, id);

        assertEquals(AnnouncementStatus.ARCHIVED, result.getStatus());
        assertEquals(AnnouncementStatus.ARCHIVED, statusOf(id));
    }

    @Test
    void archiveZeroRowUnchangedIsConcurrentConflict()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.NO_CHANGE;

        assertThrows(AnnouncementConcurrentUpdateException.class, () -> service.archive(BINDING_ID, id));
        assertEquals(AnnouncementStatus.PUBLISHED, statusOf(id));
    }

    @Test
    void archiveZeroRowIllegalStateIsStateConflict()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.zeroRowMode = ZeroRowMode.ILLEGAL_STATE;

        assertThrows(AnnouncementStateConflictException.class, () -> service.archive(BINDING_ID, id));
        assertEquals(AnnouncementStatus.PUBLISHED, statusOf(id));
    }

    @Test
    void authorizationFailureDoesNotCreateDraft()
    {
        authorizationPort.fail = true;

        assertThrows(AnnouncementAuthorizationUnavailableException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertEquals(0, countRows());
    }

    @Test
    void transactionRollsBackWhenInsertFailsAfterWrite()
    {
        authorizationPort.allow(BINDING_ID, true);
        testMapper.failAfterInsert = true;

        assertThrows(IllegalStateException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertEquals(0, countRows());
    }

    @Test
    void transactionRollsBackWhenDataAccessFailsAfterInsert()
    {
        authorizationPort.allow(BINDING_ID, true);
        testMapper.failAfterInsertWithDataAccess();

        AnnouncementPersistenceException error = assertThrows(AnnouncementPersistenceException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertSame(testMapper.dataAccessFailure, error.getCause());
        assertEquals(0, countRows());
    }

    @Test
    void createDraftWrapsDataAccessException()
    {
        authorizationPort.allow(BINDING_ID, true);
        testMapper.failWritesWithDataAccess();

        AnnouncementPersistenceException error = assertThrows(AnnouncementPersistenceException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", null, null));
        assertSame(testMapper.dataAccessFailure, error.getCause());
    }

    @Test
    void updateDraftWrapsDataAccessException()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.failWritesWithDataAccess();

        AnnouncementPersistenceException error = assertThrows(AnnouncementPersistenceException.class,
                () -> service.updateDraft(BINDING_ID, id, "changed", "body", null, null));
        assertSame(testMapper.dataAccessFailure, error.getCause());
    }

    @Test
    void publishWrapsDataAccessException()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.failWritesWithDataAccess();

        AnnouncementPersistenceException error = assertThrows(AnnouncementPersistenceException.class,
                () -> service.publish(BINDING_ID, id));
        assertSame(testMapper.dataAccessFailure, error.getCause());
    }

    @Test
    void archiveWrapsDataAccessException()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, true);
        testMapper.failWritesWithDataAccess();

        AnnouncementPersistenceException error = assertThrows(AnnouncementPersistenceException.class,
                () -> service.archive(BINDING_ID, id));
        assertSame(testMapper.dataAccessFailure, error.getCause());
    }

    @Test
    void getTeacherAnnouncementWrapsDataAccessException()
    {
        authorizationPort.allow(BINDING_ID, true);
        testMapper.failReadsWithDataAccess();

        AnnouncementPersistenceException error = assertThrows(AnnouncementPersistenceException.class,
                () -> service.getTeacherAnnouncement(BINDING_ID, 1L));
        assertSame(testMapper.dataAccessFailure, error.getCause());
    }

    @Test
    void listTeacherAnnouncementsWrapsDataAccessException()
    {
        authorizationPort.allow(BINDING_ID, true);
        testMapper.failReadsWithDataAccess();

        AnnouncementPersistenceException error = assertThrows(AnnouncementPersistenceException.class,
                () -> service.listTeacherAnnouncements(BINDING_ID, null));
        assertSame(testMapper.dataAccessFailure, error.getCause());
    }

    @Test
    void draftReadHidesOtherTeachersDraftAsNotFound()
    {
        long otherDraft = insertRow(BINDING_ID, "other draft", "DRAFT", OTHER_TEACHER_ID, null);
        long ownDraft = insertRow(BINDING_ID, "own draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, false);

        assertThrows(AnnouncementNotFoundException.class,
                () -> service.getTeacherAnnouncement(BINDING_ID, otherDraft));
        assertNotNull(service.getTeacherAnnouncement(BINDING_ID, ownDraft));

        authorizationPort.allow(BINDING_ID, true);
        assertNotNull(service.getTeacherAnnouncement(BINDING_ID, otherDraft));
    }

    @Test
    void teacherAnnouncementCanBeReadByAnnouncementId()
    {
        long id = insertRow(BINDING_ID, "read by id", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, false);

        CourseAnnouncement announcement = service.getTeacherAnnouncement(id);

        assertEquals(id, announcement.getId());
        assertEquals("read by id", announcement.getTitle());
    }

    @Test
    void missingAnnouncementByIdIsNotFound()
    {
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementNotFoundException.class,
                () -> service.getTeacherAnnouncement(9999L));
    }

    @Test
    void draftByIdHidesOtherTeachersDraftAsNotFound()
    {
        long id = insertRow(BINDING_ID, "other draft", "DRAFT", OTHER_TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, false);

        assertThrows(AnnouncementNotFoundException.class,
                () -> service.getTeacherAnnouncement(id));
    }

    @Test
    void updateDraftByAnnouncementIdResolvesBinding()
    {
        long id = insertRow(BINDING_ID, "old", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, false);

        CourseAnnouncement updated = service.updateDraft(id, "new", "body", null, null);

        assertEquals("new", titleOf(id));
        assertEquals("new", updated.getTitle());
    }

    @Test
    void publishByAnnouncementIdResolvesBinding()
    {
        long id = insertRow(BINDING_ID, "draft", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);

        CourseAnnouncement published = service.publish(id);

        assertEquals(AnnouncementStatus.PUBLISHED, published.getStatus());
        assertEquals(AnnouncementStatus.PUBLISHED, statusOf(id));
    }

    @Test
    void archiveByAnnouncementIdResolvesBinding()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, true);

        CourseAnnouncement archived = service.archive(id);

        assertEquals(AnnouncementStatus.ARCHIVED, archived.getStatus());
        assertEquals(AnnouncementStatus.ARCHIVED, statusOf(id));
    }

    @Test
    void publishedAnnouncementCanBeReadWithoutPublishPermission()
    {
        long id = insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, false);

        assertNotNull(service.getTeacherAnnouncement(BINDING_ID, id));
    }

    @Test
    void listHidesOtherTeachersDraftsWithoutPublishPermission()
    {
        insertRow(BINDING_ID, "own draft", "DRAFT", TEACHER_ID, null);
        insertRow(BINDING_ID, "other draft", "DRAFT", OTHER_TEACHER_ID, null);
        insertRow(BINDING_ID, "published", "PUBLISHED", OTHER_TEACHER_ID, 90001L);
        authorizationPort.allow(BINDING_ID, false);

        List<CourseAnnouncement> all = service.listTeacherAnnouncements(BINDING_ID, null);
        assertEquals(List.of("own draft", "published"),
                all.stream().map(CourseAnnouncement::getTitle).toList());
        assertEquals(List.of("own draft"),
                service.listTeacherAnnouncements(BINDING_ID, AnnouncementStatus.DRAFT)
                        .stream().map(CourseAnnouncement::getTitle).toList());

        authorizationPort.allow(BINDING_ID, true);
        assertEquals(2, service.listTeacherAnnouncements(BINDING_ID, AnnouncementStatus.DRAFT).size());
    }

    @Test
    void listTeacherAnnouncementsPageReturnsTotalAndRows()
    {
        insertRow(BINDING_ID, "one", "DRAFT", TEACHER_ID, null);
        insertRow(BINDING_ID, "two", "PUBLISHED", TEACHER_ID, 90001L);
        insertRow(BINDING_ID, "three", "ARCHIVED", TEACHER_ID, 90001L);
        insertRow(BINDING_ID, "four", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);

        CourseAnnouncementPage page = service.listTeacherAnnouncementsPage(BINDING_ID, null, 1, 2);

        assertEquals(4, page.total());
        assertEquals(List.of("one", "two"),
                page.rows().stream().map(CourseAnnouncement::getTitle).toList());
        page.rows().forEach(row -> assertNull(row.getBody()));
    }

    @Test
    void listTeacherAnnouncementsPageReturnsEmptyRowsBeyondLastPage()
    {
        insertRow(BINDING_ID, "one", "DRAFT", TEACHER_ID, null);
        insertRow(BINDING_ID, "two", "DRAFT", TEACHER_ID, null);
        authorizationPort.allow(BINDING_ID, true);

        CourseAnnouncementPage page = service.listTeacherAnnouncementsPage(BINDING_ID, null, 3, 2);

        assertEquals(2, page.total());
        assertEquals(List.of(), page.rows());
    }

    @Test
    void listTeacherAnnouncementsPageRejectsInvalidPageNum()
    {
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementValidationException.class,
                () -> service.listTeacherAnnouncementsPage(BINDING_ID, null, 0, 10));
    }

    @Test
    void listTeacherAnnouncementsPageRejectsInvalidPageSize()
    {
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementValidationException.class,
                () -> service.listTeacherAnnouncementsPage(BINDING_ID, null, 1, 0));
        assertThrows(AnnouncementValidationException.class,
                () -> service.listTeacherAnnouncementsPage(BINDING_ID, null, 1, 101));
    }

    @Test
    void invalidDraftFieldsAreRejected()
    {
        authorizationPort.allow(BINDING_ID, true);

        assertThrows(AnnouncementValidationException.class,
                () -> service.createDraft(BINDING_ID, " ", "body", null, null));
        assertThrows(AnnouncementValidationException.class,
                () -> service.createDraft(BINDING_ID, "title", " ", null, null));
        assertThrows(AnnouncementValidationException.class,
                () -> service.createDraft(BINDING_ID, "title", "body", NOW, NOW));
        assertEquals(0, countRows());
    }

    private void authenticate(Long userId)
    {
        LoginUser loginUser = new LoginUser(userId, null, new SysUser(userId), Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, Collections.emptyList()));
    }

    private long insertRow(Long bindingId, String title, String status, Long createdBy, Long publishedBy)
    {
        long id = nextId++;
        jdbcTemplate.update("""
                INSERT INTO edu_course_announcement
                    (id, binding_id, title, body, status, opens_at, closes_at,
                     published_by_user_id, created_by_user_id, created_at)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                id, bindingId, title, "body", status,
                status.equals("DRAFT") ? null : Timestamp.valueOf(NOW.minusDays(1)),
                null,
                status.equals("DRAFT") ? null : publishedBy,
                createdBy, Timestamp.valueOf(NOW.minusMinutes(id)));
        return id;
    }

    private AnnouncementStatus statusOf(long id)
    {
        String status = jdbcTemplate.queryForObject(
                "select status from edu_course_announcement where id = ?", String.class, id);
        return AnnouncementStatus.valueOf(status);
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

    static class FakeAuthorizationPort implements TeachingClassAuthorizationPort
    {
        private final Map<Long, TeacherAnnouncementAccess> access = new HashMap<>();

        private boolean fail;

        void reset()
        {
            access.clear();
            fail = false;
        }

        void allow(Long bindingId, boolean canPublish)
        {
            access.put(bindingId, new TeacherAnnouncementAccess(
                    bindingId, bindingId + 1000L,
                    new TeacherAnnouncementAuthority(true, canPublish, true, true)));
        }

        void setAccess(Long bindingId, boolean currentTeacher, boolean canPublish)
        {
            access.put(bindingId, new TeacherAnnouncementAccess(
                    bindingId, bindingId + 1000L,
                    new TeacherAnnouncementAuthority(currentTeacher, canPublish, true, true)));
        }

        @Override
        public TeacherAnnouncementAccess resolve(Long bindingId, Long userId, LocalDateTime at)
        {
            if (fail)
            {
                throw new IllegalStateException("authorization unavailable");
            }
            return access.get(bindingId);
        }
    }

    enum ZeroRowMode
    {
        NONE,
        NO_CHANGE,
        TARGET_STATE,
        DELETE,
        REBIND,
        ILLEGAL_STATE
    }

    static class TestMapper implements CourseAnnouncementMapper
    {
        private final CourseAnnouncementMapper delegate;

        private final JdbcTemplate jdbcTemplate;

        private boolean failAfterInsert;

        private boolean failAfterInsertWithDataAccess;

        DataAccessException dataAccessFailure;

        private boolean failReadsWithDataAccess;

        ZeroRowMode zeroRowMode = ZeroRowMode.NONE;

        TestMapper(CourseAnnouncementMapper delegate, JdbcTemplate jdbcTemplate)
        {
            this.delegate = delegate;
            this.jdbcTemplate = jdbcTemplate;
        }

        void reset()
        {
            failAfterInsert = false;
            failAfterInsertWithDataAccess = false;
            dataAccessFailure = null;
            failReadsWithDataAccess = false;
            zeroRowMode = ZeroRowMode.NONE;
        }

        void failWritesWithDataAccess()
        {
            dataAccessFailure = new DataIntegrityViolationException("simulated persistence failure");
        }

        void failReadsWithDataAccess()
        {
            failReadsWithDataAccess = true;
            dataAccessFailure = new DataIntegrityViolationException("simulated read failure");
        }

        void failAfterInsertWithDataAccess()
        {
            failAfterInsertWithDataAccess = true;
            dataAccessFailure = new DataIntegrityViolationException("simulated failure after insert");
        }

        @Override
        public CourseAnnouncement selectById(Long id)
        {
            if (failReadsWithDataAccess)
            {
                throw dataAccessFailure;
            }
            return delegate.selectById(id);
        }

        @Override
        public CourseAnnouncement selectByIdForUpdate(Long id)
        {
            if (failReadsWithDataAccess)
            {
                throw dataAccessFailure;
            }
            return delegate.selectByIdForUpdate(id);
        }

        @Override
        public List<CourseAnnouncement> selectByBindingId(Long bindingId, AnnouncementStatus status)
        {
            return delegate.selectByBindingId(bindingId, status);
        }

        @Override
        public List<CourseAnnouncement> selectVisibleByBindingId(Long bindingId, LocalDateTime now)
        {
            return delegate.selectVisibleByBindingId(bindingId, now);
        }

        @Override
        public CourseAnnouncement selectVisibleById(Long id, LocalDateTime now)
        {
            return delegate.selectVisibleById(id, now);
        }

        @Override
        public List<CourseAnnouncement> selectTeacherVisibleByBindingId(Long bindingId,
                AnnouncementStatus status, Long userId, boolean canReadAllDrafts)
        {
            if (failReadsWithDataAccess)
            {
                throw dataAccessFailure;
            }
            return delegate.selectTeacherVisibleByBindingId(bindingId, status, userId, canReadAllDrafts);
        }

        @Override
        public long countTeacherVisibleByBindingId(Long bindingId,
                AnnouncementStatus status, Long userId, boolean canReadAllDrafts)
        {
            if (failReadsWithDataAccess)
            {
                throw dataAccessFailure;
            }
            return delegate.countTeacherVisibleByBindingId(bindingId, status, userId, canReadAllDrafts);
        }

        @Override
        public List<CourseAnnouncement> selectTeacherVisibleByBindingIdPage(Long bindingId,
                AnnouncementStatus status, Long userId, boolean canReadAllDrafts, long offset, int limit)
        {
            if (failReadsWithDataAccess)
            {
                throw dataAccessFailure;
            }
            return delegate.selectTeacherVisibleByBindingIdPage(
                    bindingId, status, userId, canReadAllDrafts, offset, limit);
        }

        @Override
        public long countVisibleByBindingIds(List<Long> bindingIds, LocalDateTime at)
        {
            return delegate.countVisibleByBindingIds(bindingIds, at);
        }

        @Override
        public List<CourseAnnouncement> selectVisibleByBindingIdsPage(List<Long> bindingIds,
                LocalDateTime at, long offset, int limit)
        {
            return delegate.selectVisibleByBindingIdsPage(bindingIds, at, offset, limit);
        }

        @Override
        public int insertDraft(CourseAnnouncement announcement)
        {
            if (dataAccessFailure != null)
            {
                throw dataAccessFailure;
            }
            int rows = delegate.insertDraft(announcement);
            if (failAfterInsert)
            {
                throw new IllegalStateException("simulated failure after insert");
            }
            if (failAfterInsertWithDataAccess)
            {
                throw dataAccessFailure;
            }
            return rows;
        }

        @Override
        public int updateDraft(CourseAnnouncement announcement)
        {
            if (dataAccessFailure != null)
            {
                throw dataAccessFailure;
            }
            if (zeroRowMode == ZeroRowMode.NONE)
            {
                return delegate.updateDraft(announcement);
            }
            switch (zeroRowMode)
            {
                case TARGET_STATE -> delegate.updateDraft(announcement);
                case DELETE -> deleteRow(announcement.getId());
                case REBIND -> rebindRow(announcement.getId());
                case ILLEGAL_STATE -> setStatus(announcement.getId(), "PUBLISHED");
                case NO_CHANGE -> { }
                case NONE -> { }
            }
            return 0;
        }

        @Override
        public int publishDraft(Long id, Long bindingId, Long publishedByUserId, LocalDateTime now)
        {
            if (dataAccessFailure != null)
            {
                throw dataAccessFailure;
            }
            if (zeroRowMode == ZeroRowMode.NONE)
            {
                return delegate.publishDraft(id, bindingId, publishedByUserId, now);
            }
            switch (zeroRowMode)
            {
                case TARGET_STATE -> delegate.publishDraft(id, bindingId, publishedByUserId, now);
                case DELETE -> deleteRow(id);
                case REBIND -> rebindRow(id);
                case ILLEGAL_STATE -> setStatus(id, "ARCHIVED");
                case NO_CHANGE -> { }
                case NONE -> { }
            }
            return 0;
        }

        @Override
        public int archivePublished(Long id, Long bindingId)
        {
            if (dataAccessFailure != null)
            {
                throw dataAccessFailure;
            }
            if (zeroRowMode == ZeroRowMode.NONE)
            {
                return delegate.archivePublished(id, bindingId);
            }
            switch (zeroRowMode)
            {
                case TARGET_STATE -> delegate.archivePublished(id, bindingId);
                case DELETE -> deleteRow(id);
                case REBIND -> rebindRow(id);
                case ILLEGAL_STATE -> setStatus(id, "DRAFT");
                case NO_CHANGE -> { }
                case NONE -> { }
            }
            return 0;
        }

        private void deleteRow(Long id)
        {
            jdbcTemplate.update("delete from edu_course_announcement where id = ?", id);
        }

        private void rebindRow(Long id)
        {
            jdbcTemplate.update("update edu_course_announcement set binding_id = 200 where id = ?", id);
        }

        private void setStatus(Long id, String status)
        {
            jdbcTemplate.update("update edu_course_announcement set status = ? where id = ?", status, id);
        }
    }

    static class FakeFanoutPort implements AnnouncementNoticeFanoutPort
    {
        int calls;

        CourseAnnouncement lastAnnouncement;

        LocalDateTime lastPublishedAt;

        RuntimeException failure;

        void reset()
        {
            calls = 0;
            lastAnnouncement = null;
            lastPublishedAt = null;
            failure = null;
        }

        @Override
        public int fanOutAnnouncement(CourseAnnouncement announcement, LocalDateTime publishedAt)
        {
            calls++;
            lastAnnouncement = announcement;
            lastPublishedAt = publishedAt;
            if (failure != null)
            {
                throw failure;
            }
            return 0;
        }
    }

    @Configuration
    @EnableTransactionManagement
    static class TestConfig
    {
        private static final String JDBC_URL =
                "jdbc:h2:mem:announcement_service;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";

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
        CourseAnnouncementMapper realMapper(SqlSessionTemplate sqlSessionTemplate)
        {
            return sqlSessionTemplate.getMapper(CourseAnnouncementMapper.class);
        }

        @Bean
        TestMapper testMapper(@Qualifier("realMapper") CourseAnnouncementMapper realMapper,
                JdbcTemplate jdbcTemplate)
        {
            return new TestMapper(realMapper, jdbcTemplate);
        }

        @Bean
        @Primary
        CourseAnnouncementMapper announcementMapper(TestMapper testMapper)
        {
            return testMapper;
        }

        @Bean
        FakeAuthorizationPort authorizationPort()
        {
            return new FakeAuthorizationPort();
        }

        @Bean
        FakeFanoutPort fanoutService()
        {
            return new FakeFanoutPort();
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
                FakeAuthorizationPort authorizationPort,
                AnnouncementNoticeFanoutPort fanoutService)
        {
            return new CourseAnnouncementService(announcementMapper, authorizationPort,
                    fanoutService, Clock.fixed(Instant.parse("2026-10-24T12:00:00Z"), ZoneOffset.UTC));
        }
    }
}
