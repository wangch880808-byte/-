package com.ruoyi.education.enums;

import com.ruoyi.common.utils.StringUtils;

/**
 * 课堂快照视图。
 */
public enum SnapshotView
{
    TEACHER,
    PUBLIC,
    STUDENT;

    public static SnapshotView fromParam(String view)
    {
        if (StringUtils.isEmpty(view))
        {
            return null;
        }
        return SnapshotView.valueOf(view.trim().toUpperCase());
    }
}
