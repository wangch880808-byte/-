package com.ruoyi.education.overview.domain;

import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 待批/待发布成绩汇总。
 *
 * <p>只返回计数，不返回学生成绩明细；正式成绩的口径由第 5 组负责，
 * 本模块不重算、不覆盖。</p>
 *
 * @param bindingId           课程空间与教学班绑定 ID
 * @param assignmentId        任务 ID
 * @param pendingGradeCount   待批改/待确认数量
 * @param pendingPublishCount 待发布数量
 */
public record GradeTodo(
        Long bindingId,
        Long assignmentId,
        long pendingGradeCount,
        long pendingPublishCount)
{
    /**
     * 校验标识与计数。
     *
     * @throws OverviewValidationException 标识为空或计数为负数
     */
    public GradeTodo
    {
        if (bindingId == null || assignmentId == null)
        {
            throw new OverviewValidationException("bindingId/assignmentId 不能为空");
        }
        if (pendingGradeCount < 0 || pendingPublishCount < 0)
        {
            throw new OverviewValidationException("待批/待发布计数不能为负数");
        }
    }
}
