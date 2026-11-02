package com.ruoyi.education.course.service;

import java.util.List;
import com.ruoyi.education.course.domain.CourseAnnouncement;

/**
 * 教师公告分页结果。total 是过滤后的总数，rows 是当前页。
 */
public record CourseAnnouncementPage(long total, List<CourseAnnouncement> rows)
{
    public CourseAnnouncementPage
    {
        rows = List.copyOf(rows);
    }
}
