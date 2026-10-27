package com.ruoyi.education.port;

import java.util.Map;

/**
 * 资源发布与预览访问端口（临时直连 edu_resource_* 表，待第 3 组 API 替换）。
 */
public interface ResourceAccessPort
{
    void validateRelease(Long bindingId, Long releaseId, Long resourceVersionId, Integer pageNo);

    String buildPreviewUrlPath(Long resourceVersionId, Integer pageNo);

    int countPages(Long resourceVersionId);

    Map<String, Object> getQuestionPublicSnapshot(Long questionVersionId);

    Map<String, Object> getQuestionTeacherSnapshot(Long questionVersionId);
}
