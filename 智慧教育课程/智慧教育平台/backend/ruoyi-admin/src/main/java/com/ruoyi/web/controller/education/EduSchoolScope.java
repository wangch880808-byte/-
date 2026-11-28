package com.ruoyi.web.controller.education;

import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import com.ruoyi.common.constant.SysDeptType;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.framework.security.context.PermissionContextHolder;
import com.ruoyi.system.service.ISysDeptService;
import org.springframework.stereotype.Component;

@Component
class EduSchoolScope {
    private final ISysDeptService departments;

    EduSchoolScope(ISysDeptService departments) {
        this.departments = departments;
    }

    List<SysDept> visibleDepartments(String permission) {
        String previous = PermissionContextHolder.getContext();
        try {
            PermissionContextHolder.setContext(permission);
            return departments.selectDeptList(new SysDept());
        } finally {
            PermissionContextHolder.setContext(previous);
        }
    }

    List<SysDept> schools(Collection<SysDept> visible) {
        Map<Long, SysDept> byId = new LinkedHashMap<>();
        visible.forEach(department -> byId.putIfAbsent(department.getDeptId(), department));
        return schoolIds(visible).stream()
                .map(id -> byId.computeIfAbsent(id, departments::selectDeptById))
                .filter(EduSchoolScope::activeSchool)
                .sorted(Comparator.comparing(SysDept::getOrderNum, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(SysDept::getDeptId))
                .toList();
    }

    static Set<Long> schoolIds(Collection<SysDept> visible) {
        return visible.stream().map(EduSchoolScope::rootDepartmentId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
    }

    Long schoolIdOf(Long deptId) {
        if (deptId == null) {
            return null;
        }
        SysDept department = departments.selectDeptById(deptId);
        return department == null ? null : rootDepartmentId(department);
    }

    private static Long rootDepartmentId(SysDept department) {
        if (Long.valueOf(0).equals(department.getParentId())) {
            return department.getDeptId();
        }
        String ancestors = department.getAncestors();
        if (ancestors == null || !ancestors.startsWith("0,")) {
            return null;
        }
        String[] path = ancestors.split(",", 3);
        try {
            return Long.valueOf(path[1]);
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private static boolean activeSchool(SysDept department) {
        return department != null && "0".equals(department.getDelFlag())
                && UserConstants.DEPT_NORMAL.equals(department.getStatus())
                && Long.valueOf(0).equals(department.getParentId())
                && SysDeptType.effectiveType(department, null) == SysDeptType.SCHOOL;
    }
}
