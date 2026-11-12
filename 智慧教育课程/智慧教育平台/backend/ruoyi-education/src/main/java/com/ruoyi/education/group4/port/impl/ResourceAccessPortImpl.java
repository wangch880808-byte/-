package com.ruoyi.education.group4.port.impl;

import java.util.HashMap;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group4.constant.EducationHttpStatus;
import com.ruoyi.education.group4.mapper.Group4SupportMapper;
import com.ruoyi.education.group4.port.ResourceAccessPort;

/**
 * 资源访问端口：发布校验与题目快照直查表；页预览复用 Group3 路径。
 */
@Service
public class ResourceAccessPortImpl implements ResourceAccessPort
{
    private final Group4SupportMapper supportMapper;

    public ResourceAccessPortImpl(Group4SupportMapper supportMapper)
    {
        this.supportMapper = supportMapper;
    }

    @Override
    public void validateRelease(Long bindingId, Long releaseId, Long resourceVersionId, Integer pageNo)
    {
        if (supportMapper.countValidRelease(bindingId, releaseId, resourceVersionId) == 0)
        {
            throw new ServiceException("资源未发布或版本无效", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (pageNo != null && supportMapper.countResourcePage(resourceVersionId, pageNo) == 0)
        {
            throw new ServiceException("页码超出资源范围", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Override
    public String buildPreviewUrlPath(Long bindingId, Long resourceVersionId, Integer pageNo)
    {
        if (bindingId == null || resourceVersionId == null || pageNo == null)
        {
            return null;
        }
        return "/education/group3/versions/" + resourceVersionId + "/pages/" + pageNo
                + "?bindingId=" + bindingId;
    }

    @Override
    public int countPages(Long resourceVersionId)
    {
        return supportMapper.countPagesByVersion(resourceVersionId);
    }

    @Override
    public Map<String, Object> getQuestionPublicSnapshot(Long questionVersionId)
    {
        Map<String, Object> row = supportMapper.selectQuestionVersionRow(questionVersionId);
        if (row == null || row.isEmpty())
        {
            throw new ServiceException("题目版本不存在", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> snapshot = new HashMap<>();
        snapshot.put("questionId", row.get("question_id"));
        snapshot.put("questionVersionId", row.get("id"));
        snapshot.put("questionKind", row.get("question_kind"));
        snapshot.put("stem", row.get("stem"));
        snapshot.put("options", parseJson(row.get("options_json")));
        return snapshot;
    }

    @Override
    public Map<String, Object> getQuestionTeacherSnapshot(Long questionVersionId)
    {
        Map<String, Object> snapshot = new HashMap<>(getQuestionPublicSnapshot(questionVersionId));
        Map<String, Object> row = supportMapper.selectQuestionVersionRow(questionVersionId);
        snapshot.put("answer", parseJson(row.get("answer_json")));
        snapshot.put("explanation", row.get("explanation"));
        snapshot.put("maxScore", row.get("max_score"));
        return snapshot;
    }

    private Object parseJson(Object raw)
    {
        if (raw == null)
        {
            return null;
        }
        String text = String.valueOf(raw);
        if (text.isEmpty())
        {
            return null;
        }
        return JSON.parse(text);
    }
}
