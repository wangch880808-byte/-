package com.ruoyi.system.service;

import java.util.Objects;
import java.util.List;
import java.util.Map;
import java.util.function.UnaryOperator;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;
import com.ruoyi.common.constant.SysDeptType;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.system.mapper.SysDeptMapper;

@Component
public class SysDeptHierarchy
{
    private final SysDeptMapper mapper;

    public SysDeptHierarchy(SysDeptMapper mapper)
    {
        this.mapper = mapper;
    }

    public void validatePlacement(SysDept dept, boolean hasParent, SysDeptType parentType)
    {
        if (parentType == SysDeptType.CLASS)
            throw invalid("行政班不能设下级组织");
        SysDeptType type = SysDeptType.parse(dept.getDeptType());
        if (type == null) return;
        switch (type)
        {
            case SCHOOL -> {
                if (hasParent) throw invalid("学校必须为顶级组织");
            }
            case COLLEGE -> {
                if (parentType != SysDeptType.SCHOOL) throw invalid("学院的上级必须是学校");
            }
            case MAJOR -> {
                if (parentType != SysDeptType.COLLEGE) throw invalid("专业的上级必须是学院");
            }
            case CLASS -> {
                if (parentType != SysDeptType.COLLEGE && parentType != SysDeptType.MAJOR)
                    throw invalid("行政班的上级必须是学院或专业");
            }
            case OTHER -> { }
        }
    }

    public void validateTypeChange(SysDept current, SysDeptType before, SysDept after, SysDeptType afterType,
            UnaryOperator<SysDept> finalChild)
    {
        if (before == afterType) return;
        checkTeachers(current, before, afterType);
        Map<Long, List<SysDept>> children = mapper.selectChildrenDeptById(current.getDeptId()).stream()
                .filter(child -> "0".equals(child.getDelFlag()) && child.getParentId() != null)
                .collect(Collectors.groupingBy(SysDept::getParentId));
        validateDescendants(current, after, afterType, children, finalChild);
    }

    private void validateDescendants(SysDept current, SysDept after, SysDeptType afterType,
            Map<Long, List<SysDept>> children, UnaryOperator<SysDept> finalChild)
    {
        for (SysDept child : children.getOrDefault(current.getDeptId(), List.of()))
        {
            SysDept planned = finalChild.apply(child);
            validatePlacement(planned, true, afterType);
            SysDeptType previous = SysDeptType.effectiveType(child, current);
            SysDeptType next = SysDeptType.effectiveType(planned, after);
            checkTeachers(child, previous, next);
            if (!Objects.equals(previous, next))
                validateDescendants(child, planned, next, children, finalChild);
        }
    }

    private void checkTeachers(SysDept dept, SysDeptType before, SysDeptType after)
    {
        if (before == after || after == SysDeptType.COLLEGE) return;
        if (mapper.checkDeptExistTeacher(dept.getDeptId()) > 0)
            throw invalid("该组织仍有教师账号，请先调整教师所属学院");
    }

    private static ServiceException invalid(String message)
    {
        return new ServiceException(message, 400);
    }
}
