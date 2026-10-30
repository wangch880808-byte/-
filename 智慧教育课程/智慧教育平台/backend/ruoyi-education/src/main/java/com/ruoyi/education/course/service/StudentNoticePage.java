package com.ruoyi.education.course.service;

import java.util.List;
import com.ruoyi.education.course.domain.StudentNotice;

/**
 * 学生通知分页结果。
 */
public record StudentNoticePage(long total, List<StudentNotice> rows)
{
}
