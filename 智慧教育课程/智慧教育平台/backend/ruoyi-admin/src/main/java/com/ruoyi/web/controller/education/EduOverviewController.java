package com.ruoyi.web.controller.education;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import com.github.pagehelper.PageHelper;
import com.ruoyi.common.constant.SysDeptType;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ForbiddenException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.service.IEduPortalService;
import com.ruoyi.system.service.ISysUserService;
import com.ruoyi.system.service.SysAdministrativeClassService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class EduOverviewController {
    private static final String DIRECTORY_LIST = "education:directory:list";
    private static final String DEPARTMENT_LIST = "system:dept:list";
    private static final String CLASS_LIST = "education:class:list";
    private static final String TERM_LIST = "education:term:list";

    private final IEduPortalService portal;
    private final ISysUserService users;
    private final EduSchoolScope scope;
    private final SysAdministrativeClassService classes;

    public EduOverviewController(IEduPortalService portal, ISysUserService users,
            EduSchoolScope scope, SysAdministrativeClassService classes) {
        this.portal = portal;
        this.users = users;
        this.scope = scope;
        this.classes = classes;
    }

    @GetMapping("/education/overview")
    public AjaxResult overview(@RequestParam(required = false) Long schoolId) {
        if (!portal.isAdmin()) {
            throw new ForbiddenException("只有管理员可以查看管理总览");
        }
        Map<String, Set<Long>> schoolScopes = new LinkedHashMap<>();
        Map<Long, SysDept> departmentIndex = new LinkedHashMap<>();
        List<SysDept> organizationDepartments = List.of();
        for (String permission : List.of(DIRECTORY_LIST, DEPARTMENT_LIST, CLASS_LIST, TERM_LIST)) {
            if (!SecurityUtils.hasPermi(permission)) {
                continue;
            }
            List<SysDept> visible = scope.visibleDepartments(permission);
            if (DEPARTMENT_LIST.equals(permission)) {
                organizationDepartments = visible;
            }
            visible.forEach(dept -> departmentIndex.putIfAbsent(dept.getDeptId(), dept));
            schoolScopes.put(permission, EduSchoolScope.schoolIds(visible));
        }
        List<SysDept> schools = scope.schools(departmentIndex.values());
        Map<Long, SysDept> schoolIndex = schools.stream().collect(Collectors.toMap(SysDept::getDeptId, school -> school));
        Set<Long> visibleSchoolIds = schoolIndex.keySet();
        if (schoolId != null && !visibleSchoolIds.contains(schoolId)) {
            throw new ForbiddenException("没有权限查看该学校的管理总览");
        }

        Map<String, Object> totals = new LinkedHashMap<>();
        if (canRead(schoolScopes, DIRECTORY_LIST, schoolId)) {
            totals.put("teacherCount", countDirectory(schoolId, false, portal::teacherList));
            totals.put("studentCount", countDirectory(schoolId, false, users::selectStudentAccounts));
            totals.put("unassignedTeacherCount", countDirectory(schoolId, true, portal::teacherList));
            totals.put("unassignedStudentCount", countDirectory(schoolId, true, users::selectStudentAccounts));
        }
        if (canRead(schoolScopes, DEPARTMENT_LIST, schoolId)) {
            long colleges = organizationDepartments.stream()
                    .filter(dept -> visibleSchoolIds.contains(dept.getParentId()))
                    .filter(dept -> schoolId == null || schoolId.equals(dept.getParentId()))
                    .filter(dept -> UserConstants.DEPT_NORMAL.equals(dept.getStatus()))
                    .filter(dept -> SysDeptType.effectiveType(dept, schoolIndex.get(dept.getParentId()))
                            == SysDeptType.COLLEGE)
                    .count();
            totals.put("collegeCount", colleges);
        }
        if (canRead(schoolScopes, CLASS_LIST, schoolId)) {
            SysDept filter = new SysDept();
            filter.setParentId(schoolId);
            totals.put("administrativeClassCount", PageHelper.count(() -> classes.list(filter)));
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("schools", schools.stream()
                .map(school -> Map.of("deptId", school.getDeptId(), "deptName", school.getDeptName()))
                .toList());
        data.put("schoolId", schoolId);
        data.put("totals", totals);
        Long termSchoolId = schoolId == null && schools.size() == 1 ? schools.get(0).getDeptId() : schoolId;
        if (termSchoolId != null && canRead(schoolScopes, TERM_LIST, termSchoolId)) {
            data.put("currentTerm", currentTerm(termSchoolId));
        }
        return AjaxResult.success(data);
    }

    private static boolean canRead(Map<String, Set<Long>> scopes, String permission, Long schoolId) {
        Set<Long> schools = scopes.get(permission);
        return schools != null && (schoolId == null || schools.contains(schoolId));
    }

    private long countDirectory(Long schoolId, boolean unassigned, Consumer<SysUser> lookup) {
        SysUser filter = new SysUser();
        filter.getParams().put("schoolId", schoolId);
        filter.getParams().put("unassigned", unassigned);
        return PageHelper.count(() -> lookup.accept(filter));
    }

    private Map<String, Object> currentTerm(Long schoolId) {
        return portal.termList(null, Set.of(schoolId)).stream()
                .filter(row -> Boolean.TRUE.equals(row.get("current")) || "1".equals(String.valueOf(row.get("current"))))
                .findFirst()
                .map(row -> Map.of("id", row.get("id"), "name", row.get("name"),
                        "startDate", row.get("startsOn"), "endDate", row.get("endsOn")))
                .orElse(null);
    }
}
