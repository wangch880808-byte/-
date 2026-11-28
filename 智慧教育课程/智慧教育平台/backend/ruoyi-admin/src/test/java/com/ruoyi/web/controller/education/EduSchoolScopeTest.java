package com.ruoyi.web.controller.education;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.system.service.ISysDeptService;

class EduSchoolScopeTest
{
    @AfterEach
    void tearDown()
    {
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void authorizedSchoolsKeepsOnlyExplicitSchoolRows()
    {
        ISysDeptService departments = mock(ISysDeptService.class);
        when(departments.selectDeptList(any())).thenReturn(List.of(
                dept(10L, 0L, "学校", "SCHOOL"),
                dept(20L, 10L, "学院", "COLLEGE"),
                dept(30L, 20L, "专业", "MAJOR")));
        bindRequest();
        EduSchoolScope scope = new EduSchoolScope(departments);

        List<SysDept> schools = scope.authorizedSchools("education:overview:admin");

        assertEquals(1, schools.size());
        assertEquals(10L, schools.get(0).getDeptId());
    }

    @Test
    void collegeOnlyVisibilityDoesNotExpandToSchool()
    {
        ISysDeptService departments = mock(ISysDeptService.class);
        when(departments.selectDeptList(any())).thenReturn(List.of(
                dept(20L, 10L, "学院", "COLLEGE")));
        bindRequest();
        EduSchoolScope scope = new EduSchoolScope(departments);

        List<SysDept> schools = scope.authorizedSchools("education:overview:admin");

        assertTrue(schools.isEmpty());
    }

    @Test
    void disabledSchoolIsNotAuthorized()
    {
        ISysDeptService departments = mock(ISysDeptService.class);
        SysDept disabled = dept(10L, 0L, "学校", "SCHOOL");
        disabled.setStatus("1");
        when(departments.selectDeptList(any())).thenReturn(List.of(disabled));
        bindRequest();
        EduSchoolScope scope = new EduSchoolScope(departments);

        assertTrue(scope.authorizedSchools("education:overview:admin").isEmpty());
    }

    private void bindRequest()
    {
        RequestContextHolder.setRequestAttributes(
                new ServletRequestAttributes(new MockHttpServletRequest()));
    }

    private SysDept dept(Long id, Long parentId, String name, String type)
    {
        SysDept dept = new SysDept();
        dept.setDeptId(id);
        dept.setParentId(parentId);
        dept.setDeptName(name);
        dept.setDeptType(type);
        dept.setStatus("0");
        dept.setDelFlag("0");
        dept.setOrderNum(1);
        return dept;
    }
}
