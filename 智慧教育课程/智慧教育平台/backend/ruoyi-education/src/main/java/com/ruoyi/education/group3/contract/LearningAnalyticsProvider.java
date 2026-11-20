package com.ruoyi.education.group3.contract;

import java.util.Map;

/**
 * 学情数据契约，供统计/AI 等下游组读取第三组的学习行为数据。
 * <p>
 * 与 {@link PublishedResourceProvider}、{@link PreviewTaskProvider} 一样，本契约是进程内 bean 调用，
 * 不含 HTTP 注解；鉴权由实现方完成。由于数据跨越全班学生，实现方要求调用者为该教学班的
 * 有效任课教师（{@code canPublish}），学生视角请改用学生端看板接口。
 * </p>
 * <p>
 * 答案与解析遵循「公布前不泄露」原则：教师未到解析公布时间时，作答记录仍可用于统计提交行为，
 * 但 {@code correctAnswer / studentAnswer / explanation / correct} 一律不返回；简答题不自动判分。
 * </p>
 */
public interface LearningAnalyticsProvider
{
    /**
     * 一次性取课程学情快照，等价于同时调用下面三个方法。
     *
     * @param bindingId 教学班-课程绑定 ID
     * @return {@code {progress, submissions, discussion, serverTime}}
     */
    Map<String, Object> learningSnapshot(Long bindingId);

    /**
     * 课程任务点完成率。分母为「有效在班学生数 × 任务点数」，只统计视频任务点、
     * 已发布的预习任务和计分讨论话题；未发布的草稿任务不计入。
     *
     * @param bindingId 教学班-课程绑定 ID
     * @return {@code {summary, tasks, serverTime}}；summary 含 studentCount、taskCount、
     *         totalOpportunities、completed、percent 及三类任务点数
     */
    Map<String, Object> courseProgress(Long bindingId);

    /**
     * 预习任务作答记录（每人每题一条最新有效提交），最多返回 500 条。
     *
     * @param bindingId 教学班-课程绑定 ID
     * @return {@code {records, total, truncated, serverTime}}；记录含 studentUserId、
     *         assignmentId、questionVersionId、knowledgePoints、submittedAt、isLate、
     *         answerVisible，以及公布后才出现的 correctAnswer / studentAnswer / explanation / correct
     */
    Map<String, Object> submissionRecords(Long bindingId);

    /**
     * 讨论参与度：按学生汇总发帖/回帖/获赞，并附按话题的明细。
     * 匿名帖仍计入参与次数，但不返回帖子正文或作者姓名。
     *
     * @param bindingId 教学班-课程绑定 ID
     * @return {@code {records, topicBreakdown, total, serverTime}}
     */
    Map<String, Object> discussionParticipation(Long bindingId);
}
