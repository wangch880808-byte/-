package com.ruoyi.education.port.impl;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.constant.EducationHttpStatus;
import com.ruoyi.education.mapper.EduSupportMapper;
import com.ruoyi.education.port.ResourceAccessPort;

/**
 * 资源访问端口实现（直连 edu_resource_* 表）。
 */
@Service
public class ResourceAccessPortImpl implements ResourceAccessPort
{
    @Autowired
    private EduSupportMapper eduSupportMapper;

    @Override
    public void validateRelease(Long bindingId, Long releaseId, Long resourceVersionId, Integer pageNo)
    {
        if (eduSupportMapper.countValidRelease(bindingId, releaseId, resourceVersionId) == 0)
        {
            throw new ServiceException("资源未发布或版本无效", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (pageNo != null && eduSupportMapper.countResourcePage(resourceVersionId, pageNo) == 0)
        {
            throw new ServiceException("页码超出资源范围", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    @Override
    public String buildPreviewUrlPath(Long resourceVersionId, Integer pageNo)
    {
        if (resourceVersionId == null || pageNo == null)
        {
            return null;
        }
        return "/education/resource/versions/" + resourceVersionId + "/pages/" + pageNo + "/preview";
    }

    @Override
    public int countPages(Long resourceVersionId)
    {
        return eduSupportMapper.countPagesByVersion(resourceVersionId);
    }

    @Override
    public Map<String, Object> getQuestionPublicSnapshot(Long questionVersionId)
    {
        Map<String, Object> row = eduSupportMapper.selectQuestionVersionRow(questionVersionId);
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
        Map<String, Object> row = eduSupportMapper.selectQuestionVersionRow(questionVersionId);
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
