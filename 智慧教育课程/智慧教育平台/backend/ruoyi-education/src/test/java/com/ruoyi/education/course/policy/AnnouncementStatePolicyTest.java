package com.ruoyi.education.course.policy;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.AnnouncementVisibility;
import com.ruoyi.education.course.domain.TeacherAnnouncementAuthority;

class AnnouncementStatePolicyTest
{
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 24, 12, 0);
    private static final TeacherAnnouncementAuthority AUTHORIZED =
            new TeacherAnnouncementAuthority(true, true, true, true);
    private static final TeacherAnnouncementAuthority READ_ONLY =
            new TeacherAnnouncementAuthority(true, false, true, true);
    private static final TeacherAnnouncementAuthority NOT_TEACHER =
            new TeacherAnnouncementAuthority(false, false, true, true);

    @Test
    void authorizedTeacherCanCreateDraft()
    {
        assertTrue(AnnouncementStatePolicy.canCreateDraft(AUTHORIZED));
    }

    @Test
    void authorizedTeacherCanEditDraft()
    {
        assertTrue(AnnouncementStatePolicy.canEditDraft(AUTHORIZED, AnnouncementStatus.DRAFT));
    }

    @Test
    void readOnlyTeacherCanEditOwnDraftInCreatorAwarePolicy()
    {
        assertTrue(AnnouncementStatePolicy.canEditDraft(
                READ_ONLY, AnnouncementStatus.DRAFT, 1L, 1L));
        assertFalse(AnnouncementStatePolicy.canEditDraft(
                READ_ONLY, AnnouncementStatus.DRAFT, 2L, 1L));
    }

    @Test
    void managerCanEditAnotherTeachersDraftInCreatorAwarePolicy()
    {
        assertTrue(AnnouncementStatePolicy.canEditDraft(
                AUTHORIZED, AnnouncementStatus.DRAFT, 2L, 1L));
    }

    @Test
    void creatorAwarePolicyRejectsMissingUserIdentity()
    {
        assertFalse(AnnouncementStatePolicy.canEditDraft(
                READ_ONLY, AnnouncementStatus.DRAFT, 1L, null));
        assertFalse(AnnouncementStatePolicy.canEditDraft(
                READ_ONLY, AnnouncementStatus.DRAFT, null, 1L));
    }

    @Test
    void authorizedTeacherCanPublishDraft()
    {
        assertTrue(AnnouncementStatePolicy.canPublish(AUTHORIZED, AnnouncementStatus.DRAFT));
    }

    @Test
    void authorizedTeacherCanArchivePublishedAnnouncement()
    {
        assertTrue(AnnouncementStatePolicy.canArchive(AUTHORIZED, AnnouncementStatus.PUBLISHED));
    }

    @Test
    void authorizedTeacherCanReadClassAnnouncements()
    {
        assertTrue(AnnouncementStatePolicy.canReadTeacher(AUTHORIZED));
    }

    @Test
    void teacherWithoutPublishPermissionCanReadButCannotWrite()
    {
        assertTrue(AnnouncementStatePolicy.canReadTeacher(READ_ONLY));
        assertFalse(AnnouncementStatePolicy.canCreateDraft(READ_ONLY));
        assertFalse(AnnouncementStatePolicy.canPublish(READ_ONLY, AnnouncementStatus.DRAFT));
    }

    @Test
    void nonTeacherCannotReadOrWrite()
    {
        assertFalse(AnnouncementStatePolicy.canReadTeacher(NOT_TEACHER));
        assertFalse(AnnouncementStatePolicy.canCreateDraft(NOT_TEACHER));
    }

    @Test
    void nullAuthorityIsDenied()
    {
        assertFalse(AnnouncementStatePolicy.canReadTeacher(null));
        assertFalse(AnnouncementStatePolicy.canCreateDraft(null));
    }

    @Test
    void publishedAnnouncementCannotBeEdited()
    {
        assertFalse(AnnouncementStatePolicy.canEditDraft(AUTHORIZED, AnnouncementStatus.PUBLISHED));
    }

    @Test
    void archivedAnnouncementCannotBePublished()
    {
        assertFalse(AnnouncementStatePolicy.canPublish(AUTHORIZED, AnnouncementStatus.ARCHIVED));
    }

    @Test
    void draftCannotBeArchived()
    {
        assertFalse(AnnouncementStatePolicy.canArchive(AUTHORIZED, AnnouncementStatus.DRAFT));
    }

    @Test
    void draftIsNotVisibleToStudents()
    {
        assertEquals(AnnouncementVisibility.DRAFT,
                AnnouncementStatePolicy.visibility(AnnouncementStatus.DRAFT, null, null, NOW));
        assertFalse(AnnouncementStatePolicy.canStudentRead(AnnouncementStatus.DRAFT, null, null, NOW));
    }

    @Test
    void archivedAnnouncementIsNotVisibleToStudents()
    {
        assertEquals(AnnouncementVisibility.ARCHIVED,
                AnnouncementStatePolicy.visibility(AnnouncementStatus.ARCHIVED, NOW.minusDays(1), null, NOW));
        assertFalse(AnnouncementStatePolicy.canStudentRead(
                AnnouncementStatus.ARCHIVED, NOW.minusDays(1), null, NOW));
    }

    @Test
    void publishedAnnouncementIsNotVisibleBeforeOpenTime()
    {
        LocalDateTime opensAt = NOW.plusMinutes(1);
        assertEquals(AnnouncementVisibility.NOT_YET_OPEN,
                AnnouncementStatePolicy.visibility(AnnouncementStatus.PUBLISHED, opensAt, null, NOW));
        assertFalse(AnnouncementStatePolicy.canStudentRead(
                AnnouncementStatus.PUBLISHED, opensAt, null, NOW));
    }

    @Test
    void publishedAnnouncementIsVisibleAtOpenBoundary()
    {
        assertEquals(AnnouncementVisibility.OPEN,
                AnnouncementStatePolicy.visibility(AnnouncementStatus.PUBLISHED, NOW, null, NOW));
        assertTrue(AnnouncementStatePolicy.canStudentRead(
                AnnouncementStatus.PUBLISHED, NOW, null, NOW));
    }

    @Test
    void publishedAnnouncementIsClosedAtCloseBoundary()
    {
        assertEquals(AnnouncementVisibility.CLOSED,
                AnnouncementStatePolicy.visibility(AnnouncementStatus.PUBLISHED, NOW.minusHours(1), NOW, NOW));
        assertFalse(AnnouncementStatePolicy.canStudentRead(
                AnnouncementStatus.PUBLISHED, NOW.minusHours(1), NOW, NOW));
    }

    @Test
    void publishedAnnouncementWithoutCloseTimeStaysOpen()
    {
        assertEquals(AnnouncementVisibility.OPEN,
                AnnouncementStatePolicy.visibility(
                        AnnouncementStatus.PUBLISHED, NOW.minusDays(1), null, NOW));
    }

    @Test
    void publishedAnnouncementWithoutOpenTimeIsNotVisible()
    {
        assertEquals(AnnouncementVisibility.NOT_YET_OPEN,
                AnnouncementStatePolicy.visibility(AnnouncementStatus.PUBLISHED, null, null, NOW));
    }

    @Test
    void nullStatusIsRejected()
    {
        assertThrows(NullPointerException.class,
                () -> AnnouncementStatePolicy.visibility(null, null, null, NOW));
    }

    @Test
    void nullNowIsRejected()
    {
        assertThrows(NullPointerException.class,
                () -> AnnouncementStatePolicy.visibility(AnnouncementStatus.PUBLISHED, NOW, null, null));
    }
}
