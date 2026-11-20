package com.ruoyi.education.group3.contract;

import java.util.List;
import java.util.Map;
import com.fasterxml.jackson.databind.JsonNode;

/**
 * 预习提交持久化契约。
 * <p>
 * 第三组默认提供本地实现 {@code LocalPreviewSubmissionHandler}，
 * 直接写入共享作业表（edu_assignment_submission / edu_submission_version /
 * edu_submission_answer / edu_learning_event）作为回退。
 * </p>
 * <p>
 * 第五组统一作业服务上线后，提供标注 {@code @Primary} 的实现即可接管
 * 提交持久化、评分、改交与正式成绩写入，届时可移除本地默认实现。
 * </p>
 */
public interface PreviewSubmissionHandler
{
    /**
     * 查询学生在指定预习任务下的提交目标，用于鉴权与时间窗校验。
     *
     * @param assignmentId 预习任务 ID
     * @param userId       学生用户 ID
     * @return 目标信息，需包含字段：targetId、assignmentId、studentUserId、
     *         bindingId、teachingClassId、courseSpaceId、status、opensAt、closesAt；
     *         若学生不在任务范围内返回 {@code null}
     */
    Map<String, Object> studentTarget(Long assignmentId, Long userId);

    /**
     * 查询学生在指定目标下已有的提交记录，用于幂等判断。
     *
     * @param targetId 提交目标 ID
     * @return 提交信息（submissionId、submissionVersionId、versionNo、submittedAt、late），
     *         若无提交返回 {@code null}
     */
    Map<String, Object> existingSubmission(Long targetId);

    /**
     * 持久化一次预习提交：创建提交记录、提交版本、答案与学习事件。
     *
     * @param command 提交上下文
     * @return 包含 submissionId、submissionVersionId、versionNo 的结果
     */
    Map<String, Object> submit(PreviewSubmissionCommand command);

    /**
     * 预习提交上下文。
     *
     * @param bindingId    教学班绑定 ID（用于学习事件记录）
     * @param assignmentId 预习任务 ID
     * @param targetId     学生提交目标 ID
     * @param userId       学生用户 ID
     * @param items        题目项列表（至少包含 assignmentItemId）
     * @param answers      学生答案，key 为 assignmentItemId
     */
    record PreviewSubmissionCommand(
            Long bindingId,
            Long assignmentId,
            Long targetId,
            Long userId,
            List<Map<String, Object>> items,
            Map<String, JsonNode> answers)
    {
    }
}
