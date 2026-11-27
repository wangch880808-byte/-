package com.ruoyi.education.group3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduClassBindingRef;
import com.ruoyi.education.course.api.EduClassMembership;
import com.ruoyi.education.course.api.EduClassRef;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.domain.EduTerm;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.service.IEduPortalService;
import com.ruoyi.education.service.IEduTermService;

class Group3AccessServiceTest
{
    private static final Long USER_ID = 9001L;
    private static final Long CLASS_ID = 20001L;
    private static final Long COURSE_ID = 30001L;
    private static final Long BINDING_ID = 40001L;

    private Group3Mapper mapper;
    private EduCourseAccessService access;
    private IEduPortalService portal;
    private Group3AccessService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(Group3Mapper.class);
        access = mock(EduCourseAccessService.class);
        portal = mock(IEduPortalService.class);
        IEduTermService terms = mock(IEduTermService.class);
        EduTerm term = new EduTerm();
        term.setStartsOn(java.sql.Date.valueOf("2026-09-01"));
        term.setEndsOn(java.sql.Date.valueOf("2027-01-15"));
        when(terms.getById(5001L)).thenReturn(term);
        service = new Group3AccessService(mapper, access, portal, terms);
    }

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void teacherContextsReturnPublishableBindingsOnly()
    {
        authenticate("teacher");
        EduClassRef openClass = classRef(true);
        when(access.classesOfTeacher(USER_ID)).thenReturn(List.of(openClass));
        when(access.activeBindings(CLASS_ID)).thenReturn(List.of(new EduClassBindingRef(BINDING_ID, CLASS_ID, COURSE_ID, null)));
        when(access.membership(CLASS_ID, USER_ID)).thenReturn(membership(true, true));
        when(portal.courseList(null, null)).thenReturn(List.of(course()));

        List<Map<String, Object>> contexts = service.contexts();

        assertEquals(1, contexts.size());
        Map<String, Object> ctx = contexts.get(0);
        assertEquals(BINDING_ID, ctx.get("bindingId"));
        assertEquals(COURSE_ID, ctx.get("courseSpaceId"));
        assertEquals("课程A", ctx.get("courseTitle"));
        assertEquals("简介A", ctx.get("courseSummary"));
        assertEquals(CLASS_ID, ctx.get("teachingClassId"));
        assertEquals("班级A", ctx.get("className"));
        assertEquals("LEAD", ctx.get("teacherRole"));
        assertTrue((Boolean) ctx.get("canPublish"));
    }

    @Test
    void teacherContextsSkipClosedClassesAndNonPublishable()
    {
        authenticate("teacher");
        when(access.classesOfTeacher(USER_ID)).thenReturn(List.of(classRef(false)));

        assertTrue(service.contexts().isEmpty());
    }

    @Test
    void requireTeacherBindingForbiddenWhenCannotPublish()
    {
        authenticate("teacher");
        stubBinding(true);
        when(access.membership(CLASS_ID, USER_ID)).thenReturn(membership(true, false));

        assertThrows(EduForbiddenException.class, () -> service.requireTeacherBinding(BINDING_ID));
    }

    @Test
    void requireTeacherBindingReturnsMapWhenAuthorized()
    {
        authenticate("teacher");
        stubBinding(true);
        when(access.membership(CLASS_ID, USER_ID)).thenReturn(membership(true, true));
        when(portal.courseDetail(COURSE_ID)).thenReturn(course());

        Map<String, Object> binding = service.requireTeacherBinding(BINDING_ID);

        assertEquals(BINDING_ID, binding.get("bindingId"));
        assertEquals("课程A", binding.get("courseTitle"));
        assertEquals("ACTIVE", binding.get("courseStatus"));
        assertEquals("TRIAL", binding.get("classKind"));
        assertEquals("2026-09-01", binding.get("termStartsOn"));
    }

    @Test
    void requireStudentBindingForbiddenWhenNotInClass()
    {
        authenticate("student");
        stubBinding(false);
        when(access.isActiveStudent(CLASS_ID, USER_ID)).thenReturn(false);

        assertThrows(EduForbiddenException.class, () -> service.requireStudentBinding(BINDING_ID));
    }

    @Test
    void requireStudentBindingReturnsMapWhenInClass()
    {
        authenticate("student");
        stubBinding(false);
        when(access.isActiveStudent(CLASS_ID, USER_ID)).thenReturn(true);
        when(portal.courseDetail(COURSE_ID)).thenReturn(course());

        Map<String, Object> binding = service.requireStudentBinding(BINDING_ID);

        assertEquals(BINDING_ID, binding.get("bindingId"));
        assertNull(binding.get("teacherRole"));
        assertFalse((Boolean) binding.get("canPublish"));
    }

    @Test
    void studentCannotReuseBindingAfterCourseMovesToDraft()
    {
        authenticate("student");
        stubBinding(false);
        when(access.isActiveStudent(CLASS_ID, USER_ID)).thenReturn(true);
        when(portal.courseDetail(COURSE_ID)).thenReturn(Map.of("status", "DRAFT"));

        assertThrows(EduForbiddenException.class, () -> service.requireStudentBinding(BINDING_ID));
    }

    @Test
    void teacherCannotUseBindingAfterItEnds()
    {
        authenticate("teacher");
        when(access.classesOfTeacher(USER_ID)).thenReturn(List.of(classRef(true)));
        when(access.activeBindings(CLASS_ID)).thenReturn(List.of());

        assertThrows(EduForbiddenException.class, () -> service.requireTeacherBinding(BINDING_ID));
    }

    @Test
    void publishingPermissionDoesNotGrantBulkExport()
    {
        authenticate("teacher");
        stubBinding(true);
        when(access.membership(CLASS_ID, USER_ID)).thenReturn(membership(true, true));
        when(portal.courseDetail(COURSE_ID)).thenReturn(course());

        assertThrows(EduForbiddenException.class, () -> service.requireTeacherExportBinding(BINDING_ID));
    }

    @Test
    void teacherVersionReturnsDecoratedVersion()
    {
        authenticate("teacher");
        stubBinding(true);
        when(access.membership(CLASS_ID, USER_ID)).thenReturn(membership(true, true));
        when(portal.courseDetail(COURSE_ID)).thenReturn(course());
        when(mapper.selectTeacherVersion(anyLong(), eq(COURSE_ID)))
                .thenReturn(new java.util.LinkedHashMap<>(Map.of("versionId", 7L, "title", "课件")));

        Map<String, Object> version = service.teacherVersion(BINDING_ID, 7L);

        assertEquals(7L, version.get("versionId"));
        assertEquals("课件", version.get("title"));
        assertEquals(BINDING_ID, version.get("bindingId"));
        assertEquals("课程A", version.get("courseTitle"));
    }

    @Test
    void teacherVersionThrowsWhenVersionMissing()
    {
        authenticate("teacher");
        stubBinding(true);
        when(access.membership(CLASS_ID, USER_ID)).thenReturn(membership(true, true));
        when(portal.courseDetail(COURSE_ID)).thenReturn(course());
        when(mapper.selectTeacherVersion(anyLong(), eq(COURSE_ID))).thenReturn(null);

        assertThrows(ServiceException.class, () -> service.teacherVersion(BINDING_ID, 7L));
    }

    private void authenticate(String roleKey)
    {
        SysUser user = new SysUser(USER_ID);
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        role.setStatus("0");
        user.setRoles(List.of(role));
        LoginUser loginUser = new LoginUser(USER_ID, null, user, java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    private EduClassRef classRef(boolean active)
    {
        return new EduClassRef(CLASS_ID, "班级A", "C001", 5001L, "2026秋",
                100L, active ? "ACTIVE" : "INACTIVE", "TRIAL");
    }

    private Map<String, Object> course()
    {
        return Map.of("id", COURSE_ID, "title", "课程A", "summary", "简介A", "status", "ACTIVE");
    }

    private void stubBinding(boolean teacher)
    {
        if (teacher)
        {
            when(access.classesOfTeacher(USER_ID)).thenReturn(List.of(classRef(true)));
        }
        else
        {
            when(access.classesOfStudent(USER_ID)).thenReturn(List.of(classRef(true)));
        }
        when(access.activeBindings(CLASS_ID)).thenReturn(List.of(new EduClassBindingRef(BINDING_ID, CLASS_ID, COURSE_ID, null)));
    }

    private EduClassMembership membership(boolean teacher, boolean canPublish)
    {
        return new EduClassMembership(CLASS_ID, USER_ID, teacher, false, teacher ? "LEAD" : null,
                canPublish, false, false, null);
    }
}
