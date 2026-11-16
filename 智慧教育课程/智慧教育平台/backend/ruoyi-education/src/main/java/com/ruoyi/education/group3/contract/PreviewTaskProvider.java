package com.ruoyi.education.group3.contract;

import java.util.List;
import java.util.Map;

/**
 * 预习任务读取契约，供课堂、作业等下游组消费。
 * <p>
 * 提交持久化（写作业共享表）由 {@code PreviewSubmissionHandler} 契约负责，
 * 本接口仅负责读取当前调用者可见的预习任务。
 * </p>
 */
public interface PreviewTaskProvider
{
    /**
     * 列出当前调用者（教师或学生）在指定教学班下可见的预习任务。
     * <p>
     * 教师视角返回的任务包含发布统计；学生视角返回的任务额外包含
     * 作答状态与解析可见性。
     * </p>
     *
     * @param bindingId 教学班-课程绑定 ID
     * @return 预习任务列表，每个 map 包含字段：
     *         <ul>
     *           <li>{@code assignmentId} — 预习任务 ID</li>
     *           <li>{@code bindingId} — 教学班绑定 ID（学生视角）</li>
     *           <li>{@code lessonId} — 关联课次 ID（教师视角，可为空）</li>
     *           <li>{@code title} — 任务名称</li>
     *           <li>{@code instructions} — 任务说明</li>
     *           <li>{@code status} — 任务状态（PUBLISHED/CLOSED/ARCHIVED 等）</li>
     *           <li>{@code opensAt} — 开放时间（ISO-8601 UTC）</li>
     *           <li>{@code closesAt} — 截止时间（ISO-8601 UTC）</li>
     *           <li>{@code answerOpensAt} — 解析公布时间（ISO-8601 UTC，可为空）</li>
     *           <li>{@code assignmentItemId} — 题目项 ID</li>
     *           <li>{@code questionId} / {@code questionVersionId} — 题目与版本 ID</li>
     *           <li>{@code stem} — 题干</li>
     *           <li>{@code questionKind} — 题型（SINGLE/MULTIPLE/TRUE_FALSE/SHORT_TEXT）</li>
     *           <li>{@code options} — 选项列表（选择题，学生视角）</li>
     *           <li>{@code maxScore} — 满分（学生视角）</li>
     *           <li>{@code targetCount} — 应提交人数（教师视角）</li>
     *           <li>{@code submissionCount} — 已提交人数（教师视角）</li>
     *           <li>{@code submissionId} — 本人提交 ID（学生视角，未提交为 null）</li>
     *           <li>{@code submittedVersion} — 本人提交版本号（学生视角）</li>
     *           <li>{@code submittedAt} — 本人提交时间（学生视角）</li>
     *           <li>{@code state} — 任务状态（UPCOMING/OPEN/CLOSED/ARCHIVED，学生视角）</li>
     *           <li>{@code submitted} — 本人是否已提交（学生视角）</li>
     *           <li>{@code answerVisible} — 解析是否已可见（学生视角）</li>
     *         </ul>
     */
    List<Map<String, Object>> previewTasksForCurrentUser(Long bindingId);
}
