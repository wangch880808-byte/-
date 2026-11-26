package com.ruoyi.education.group3.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Security;

/**
 * 环节思政融入点：在资料阅读、预习任务、讨论等教学环节上标注思政元素与融入说明。
 * 思政元素复用公共资源分类 {@code kind = 'IDEOLOGY'}，不新增元素字典。
 */
@Service
public class Group3StageIdeologyService
{
    private static final Set<String> STAGE_KINDS = Set.of("RESOURCE", "QUIZ", "DISCUSSION");
    private static final int NOTE_MAX_LENGTH = 500;

    private final Group3Mapper mapper;
    private final Group3AccessService accessService;

    public Group3StageIdeologyService(Group3Mapper mapper, Group3AccessService accessService)
    {
        this.mapper = mapper;
        this.accessService = accessService;
    }

    /**
     * 读取某个环节的思政融入点。教师与学生均可读，按各自的教学班授权校验。
     */
    public List<Map<String, Object>> points(Long bindingId, String stageKind, Long stageId)
    {
        accessService.requireAnyBinding(bindingId);
        String kind = normalizeKind(stageKind);
        requireStage(bindingId, kind, stageId);
        return mapper.selectStageIdeologyPoints(bindingId, kind, stageId);
    }

    /**
     * 读取当前教学班全部环节的思政融入点，供学生端时间轴一次性标注各环节卡片。
     */
    public List<Map<String, Object>> allPoints(Long bindingId)
    {
        accessService.requireAnyBinding(bindingId);
        return mapper.selectAllStageIdeologyPoints(bindingId);
    }

    /**
     * 教师整体覆盖某个环节的思政融入点：先清空再写入，空数组表示取消全部标注。
     */
    @Transactional
    public List<Map<String, Object>> replace(Long bindingId, String stageKind, Long stageId,
            List<Map<String, Object>> points)
    {
        Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        String kind = normalizeKind(stageKind);
        requireStage(bindingId, kind, stageId);

        List<Map<String, Object>> rows = normalizePoints(points);
        Long userId = Group3Security.currentUserId();

        mapper.deleteStageIdeologyPoints(bindingId, kind, stageId);
        for (Map<String, Object> row : rows)
        {
            Map<String, Object> insert = new LinkedHashMap<>();
            insert.put("bindingId", bindingId);
            insert.put("courseSpaceId", courseSpaceId);
            insert.put("stageKind", kind);
            insert.put("stageId", stageId);
            insert.put("categoryId", row.get("categoryId"));
            insert.put("note", row.get("note"));
            insert.put("createdByUserId", userId);
            mapper.insertStageIdeologyPoint(insert);
        }
        return mapper.selectStageIdeologyPoints(bindingId, kind, stageId);
    }

    private List<Map<String, Object>> normalizePoints(List<Map<String, Object>> points)
    {
        List<Map<String, Object>> result = new ArrayList<>();
        if (points == null || points.isEmpty())
        {
            return result;
        }
        Set<Long> seen = new LinkedHashSet<>();
        for (Map<String, Object> point : points)
        {
            if (point == null || point.get("categoryId") == null)
            {
                continue;
            }
            Long categoryId = Group3AccessService.asLong(point.get("categoryId"));
            if (categoryId == null || categoryId <= 0 || !seen.add(categoryId))
            {
                continue;
            }
            if (mapper.selectIdeologyCategoryId(categoryId) == null)
            {
                throw new ServiceException("思政元素不存在或已停用：" + categoryId, HttpStatus.BAD_REQUEST);
            }
            String note = point.get("note") == null ? "" : String.valueOf(point.get("note")).trim();
            if (note.length() > NOTE_MAX_LENGTH)
            {
                throw new ServiceException("思政融入说明不能超过 " + NOTE_MAX_LENGTH + " 字", HttpStatus.BAD_REQUEST);
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("categoryId", categoryId);
            row.put("note", note.isEmpty() ? null : note);
            result.add(row);
        }
        return result;
    }

    private String normalizeKind(String stageKind)
    {
        String kind = stageKind == null ? "" : stageKind.trim().toUpperCase();
        if (!STAGE_KINDS.contains(kind))
        {
            throw new ServiceException("不支持的环节类型：" + stageKind, HttpStatus.BAD_REQUEST);
        }
        return kind;
    }

    private void requireStage(Long bindingId, String stageKind, Long stageId)
    {
        if (stageId == null || stageId <= 0)
        {
            throw new ServiceException("缺少环节标识", HttpStatus.BAD_REQUEST);
        }
        int count = switch (stageKind)
        {
            case "RESOURCE" -> mapper.countStageResource(bindingId, stageId);
            case "QUIZ" -> mapper.countStageQuiz(bindingId, stageId);
            default -> mapper.countStageDiscussion(bindingId, stageId);
        };
        if (count <= 0)
        {
            throw new ServiceException("环节不存在或不属于当前教学班", HttpStatus.NOT_FOUND);
        }
    }
}
