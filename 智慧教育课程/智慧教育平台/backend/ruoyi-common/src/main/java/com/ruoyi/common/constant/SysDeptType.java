package com.ruoyi.common.constant;

import java.util.Locale;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.exception.ServiceException;

/** 组织节点类型。行政班用于组织归属，课程教学班单独管理。 */
public enum SysDeptType
{
    SCHOOL("学校"),
    COLLEGE("学院"),
    MAJOR("专业"),
    CLASS("班级"),
    OTHER("其他");

    private final String label;

    SysDeptType(String label)
    {
        this.label = label;
    }

    public String getLabel()
    {
        return label;
    }

    public static SysDeptType parse(String value)
    {
        if (value == null || value.isBlank()) return null;
        String text = value.trim();
        for (SysDeptType type : values())
        {
            if (type.label.equals(text) || type.name().equals(text.toUpperCase(Locale.ROOT))) return type;
        }
        throw new ServiceException("组织类型请填写学校、学院、专业、班级或其他", 400);
    }

    /** 旧数据中只识别顶级学校和学校直属的学院/系，其余保持未分类。 */
    public static SysDeptType effectiveType(SysDept dept, SysDept parent)
    {
        if (dept == null) return null;
        SysDeptType type = parse(dept.getDeptType());
        if (type != null) return type;
        if (Long.valueOf(0L).equals(dept.getParentId())) return SCHOOL;
        if (parent == null || effectiveType(parent, null) != SCHOOL || dept.getDeptName() == null) return null;
        String name = dept.getDeptName().trim();
        return name.endsWith("学院") || name.endsWith("系") ? COLLEGE : null;
    }
}
