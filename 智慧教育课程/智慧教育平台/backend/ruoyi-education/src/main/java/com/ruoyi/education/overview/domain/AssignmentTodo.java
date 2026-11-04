package com.ruoyi.education.overview.domain;

import java.time.Instant;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 预习/作业待办聚合。
 *
 * <p>学生维度表示本人待完成项；教师/绑定维度表示班级任务汇总。
 * {@code pendingCount} 只做展示用途，不替代提交状态或正式成绩。</p>
 *
 * @param bindingId      课程空间与教学班绑定 ID
 * @param assignmentId   任务 ID
 * @param title          任务标题
 * @param assignmentKind PREVIEW/CLASSWORK/HOMEWORK
 * @param opensAt        开放时间
 * @param closesAt       截止时间
 * @param targetCount    有效目标人数
 * @param submittedCount 已完成/已提交人数
 */
public record AssignmentTodo(
        Long bindingId,
        Long assignmentId,
        String title,
        String assignmentKind,
        Instant opensAt,
        Instant closesAt,
        long targetCount,
        long submittedCount)
{
    /**
     * 校验字段与计数。
     *
     * @throws OverviewValidationException 必要字段为空或计数为负数
     */
    public AssignmentTodo
    {
        if (bindingId == null || assignmentId == null)
        {
            throw new OverviewValidationException("bindingId/assignmentId 不能为空");
        }
        if (title == null || title.isBlank())
        {
            throw new OverviewValidationException("title 不能为空");
        }
        if (assignmentKind == null || assignmentKind.isBlank())
        {
            throw new OverviewValidationException("assignmentKind 不能为空");
        }
        if (opensAt == null || closesAt == null)
        {
            throw new OverviewValidationException("opensAt/closesAt 不能为空");
        }
        if (targetCount < 0 || submittedCount < 0)
        {
            throw new OverviewValidationException("目标/已提交人数不能为负数");
        }
    }

    /**
     * 计算待处理人数。
     *
     * <p>目标调整、豁免或重复提交可能使已提交人数大于目标人数，因此下限取 0，
     * 不用负数表示异常。</p>
     *
     * @return 待处理人数，最小 0
     */
    public long pendingCount()
    {
        return Math.max(0L, targetCount - submittedCount);
    }
}
