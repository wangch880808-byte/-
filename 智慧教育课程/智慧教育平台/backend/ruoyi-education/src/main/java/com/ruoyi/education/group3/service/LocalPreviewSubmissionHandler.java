package com.ruoyi.education.group3.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import com.fasterxml.jackson.databind.JsonNode;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group3.contract.PreviewSubmissionHandler;
import com.ruoyi.education.group3.mapper.Group3Mapper;

/**
 * 预习提交默认实现，直接写入共享作业表。
 * <p>
 * 第五组统一作业服务提供 {@code @Primary} 的 PreviewSubmissionHandler
 * 实现后，本类会被优先 bean 覆盖；届时移除本类即可。
 * </p>
 */
@Component
public class LocalPreviewSubmissionHandler implements PreviewSubmissionHandler
{
    private final Group3Mapper mapper;

    public LocalPreviewSubmissionHandler(Group3Mapper mapper)
    {
        this.mapper = mapper;
    }

    @Override
    public Map<String, Object> studentTarget(Long assignmentId, Long userId)
    {
        return mapper.selectStudentTargetForUpdate(assignmentId, userId);
    }

    @Override
    public Map<String, Object> existingSubmission(Long targetId)
    {
        return mapper.selectSubmissionByTarget(targetId);
    }

    @Override
    @Transactional
    public Map<String, Object> submit(PreviewSubmissionCommand command)
    {
        Map<String, Object> submissionRow = new LinkedHashMap<>();
        submissionRow.put("assignmentId", command.assignmentId());
        submissionRow.put("targetId", command.targetId());
        mapper.insertSubmission(submissionRow);

        Long submissionId = mapper.selectSubmissionIdByTarget(command.targetId());
        if (submissionId == null)
        {
            throw new ServiceException("提交记录创建失败，请重试", HttpStatus.ERROR);
        }

        int versionNo = mapper.selectNextSubmissionVersion(submissionId);
        Map<String, Object> version = new LinkedHashMap<>();
        version.put("submissionId", submissionId);
        version.put("assignmentId", command.assignmentId());
        version.put("versionNo", versionNo);
        version.put("late", false);
        mapper.insertSubmissionVersion(version);
        Long submissionVersionId = Group3AccessService.asLong(version.get("id"));

        for (Map<String, Object> item : command.items())
        {
            JsonNode answer = resolveAnswer(item, command.answers());
            Map<String, Object> answerRow = new LinkedHashMap<>();
            answerRow.put("submissionVersionId", submissionVersionId);
            answerRow.put("assignmentId", command.assignmentId());
            answerRow.put("assignmentItemId", item.get("assignmentItemId"));
            answerRow.put("answerJson", answer.toString());
            mapper.insertSubmissionAnswer(answerRow);
        }

        Map<String, Object> event = new LinkedHashMap<>();
        event.put("bindingId", command.bindingId());
        event.put("studentUserId", command.userId());
        event.put("assignmentId", command.assignmentId());
        event.put("submissionVersionId", submissionVersionId);
        event.put("clientEventKey",
                "preview-" + command.assignmentId() + "-" + command.userId() + "-v" + versionNo);
        event.put("metadataJson", "{\"questionCount\":" + command.items().size() + "}");
        mapper.insertLearningEvent(event);

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("submissionId", submissionId);
        result.put("submissionVersionId", submissionVersionId);
        result.put("versionNo", versionNo);
        return result;
    }

    /**
     * 解析单题答案：优先按 assignmentItemId 精确匹配；
     * 若只有一份答案且未精确匹配，则回退使用该唯一答案（兼容单题任务）。
     */
    private JsonNode resolveAnswer(Map<String, Object> item, Map<String, JsonNode> answers)
    {
        String itemId = String.valueOf(item.get("assignmentItemId"));
        JsonNode answer = answers.get(itemId);
        if (answer == null && answers.size() == 1)
        {
            answer = answers.values().iterator().next();
        }
        return answer;
    }
}
