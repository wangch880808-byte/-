package com.ruoyi.education.course.api.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import com.ruoyi.education.course.api.EduAdminOrganizationScopeResult;
import com.ruoyi.education.course.api.EduAdminOrganizationScopeStatus;
import com.ruoyi.education.course.mapper.AdminOrganizationClassRangeRow;
import com.ruoyi.education.course.mapper.AdminOrganizationScopeMapper;

class EduAdminOrganizationScopeServiceTest
{
    private static final Instant AS_OF = Instant.parse("2026-11-28T00:00:00Z");

    private static final Clock CLOCK = Clock.fixed(AS_OF, ZoneOffset.UTC);

    @Test
    void returnsRangesAndRepeatedStudentCountsWithoutDedup()
    {
        AdminOrganizationScopeMapper mapper = mock(AdminOrganizationScopeMapper.class);
        when(mapper.selectAuthorizedClassRanges(anyCollection(), any()))
                .thenReturn(List.of(
                        row(10L, 100L, 1000L, 3L),
                        row(10L, 100L, 1001L, 3L),
                        row(10L, 101L, null, 0L)));

        EduAdminOrganizationScopeResult result = new EduAdminOrganizationScopeServiceImpl(mapper, CLOCK)
                .listAuthorizedRanges(List.of(10L, 10L));

        assertEquals(EduAdminOrganizationScopeStatus.OK, result.status());
        assertEquals(3, result.rangeCount());
        assertEquals(6L, result.studentCount());
        assertEquals(3, result.ranges().size());
        assertEquals(AS_OF, result.asOf());
        assertNull(result.reason());
        verify(mapper).selectAuthorizedClassRanges(Set.of(10L),
                LocalDateTime.ofInstant(AS_OF, ZoneOffset.UTC));
    }

    @Test
    void emptyAuthorizedOrganizationsIsNoDataWithoutQuery()
    {
        AdminOrganizationScopeMapper mapper = mock(AdminOrganizationScopeMapper.class);

        EduAdminOrganizationScopeResult result = new EduAdminOrganizationScopeServiceImpl(mapper, CLOCK)
                .listAuthorizedRanges(List.of());

        assertEquals(EduAdminOrganizationScopeStatus.NO_DATA, result.status());
        assertNull(result.rangeCount());
        assertNull(result.studentCount());
        assertTrue(result.ranges().isEmpty());
        verify(mapper, never()).selectAuthorizedClassRanges(anyCollection(), any());
    }

    @Test
    void nullOrganizationsIsProgrammerError()
    {
        AdminOrganizationScopeMapper mapper = mock(AdminOrganizationScopeMapper.class);
        EduAdminOrganizationScopeServiceImpl service =
                new EduAdminOrganizationScopeServiceImpl(mapper, CLOCK);

        assertThrows(IllegalArgumentException.class, () -> service.listAuthorizedRanges(null));
        verify(mapper, never()).selectAuthorizedClassRanges(anyCollection(), any());
    }

    @Test
    void mapperFailureIsUnavailableNotNoData()
    {
        AdminOrganizationScopeMapper mapper = mock(AdminOrganizationScopeMapper.class);
        when(mapper.selectAuthorizedClassRanges(anyCollection(), any()))
                .thenThrow(new IllegalStateException("database down"));

        EduAdminOrganizationScopeResult result = new EduAdminOrganizationScopeServiceImpl(mapper, CLOCK)
                .listAuthorizedRanges(List.of(10L));

        assertEquals(EduAdminOrganizationScopeStatus.UNAVAILABLE, result.status());
        assertNull(result.rangeCount());
        assertNull(result.studentCount());
        assertTrue(result.reason().contains("暂不可用"));
    }

    @Test
    void emptyRowsIsNoData()
    {
        AdminOrganizationScopeMapper mapper = mock(AdminOrganizationScopeMapper.class);
        when(mapper.selectAuthorizedClassRanges(anyCollection(), any())).thenReturn(List.of());

        EduAdminOrganizationScopeResult result = new EduAdminOrganizationScopeServiceImpl(mapper, CLOCK)
                .listAuthorizedRanges(List.of(10L));

        assertEquals(EduAdminOrganizationScopeStatus.NO_DATA, result.status());
        assertNull(result.rangeCount());
        assertNull(result.studentCount());
    }

    @Test
    void incompleteRowIsUnavailable()
    {
        AdminOrganizationScopeMapper mapper = mock(AdminOrganizationScopeMapper.class);
        AdminOrganizationClassRangeRow row = row(10L, 100L, 1000L, 3L);
        row.setStudentCount(null);
        when(mapper.selectAuthorizedClassRanges(anyCollection(), any())).thenReturn(List.of(row));

        EduAdminOrganizationScopeResult result = new EduAdminOrganizationScopeServiceImpl(mapper, CLOCK)
                .listAuthorizedRanges(List.of(10L));

        assertEquals(EduAdminOrganizationScopeStatus.UNAVAILABLE, result.status());
        assertNull(result.rangeCount());
        assertNull(result.studentCount());
    }

    private AdminOrganizationClassRangeRow row(Long organizationId, Long teachingClassId,
            Long bindingId, Long studentCount)
    {
        AdminOrganizationClassRangeRow row = new AdminOrganizationClassRangeRow();
        row.setOrganizationId(organizationId);
        row.setTeachingClassId(teachingClassId);
        row.setClassName("教学班" + teachingClassId);
        row.setClassCode("C" + teachingClassId);
        row.setClassStatus("ACTIVE");
        row.setBindingId(bindingId);
        row.setCourseSpaceId(bindingId == null ? null : 200L + bindingId);
        row.setCourseName(bindingId == null ? null : "课程" + bindingId);
        row.setStudentCount(studentCount);
        return row;
    }
}
