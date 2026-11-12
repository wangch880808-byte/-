package com.ruoyi.education.group4.port;

import java.util.Map;

/**
 * 资源发布与预览访问端口。校验走教学库发布表；预览 URL 指向 Group3 已实现的页图接口。
 */
public interface ResourceAccessPort
{
    void validateRelease(Long bindingId, Long releaseId, Long resourceVersionId, Integer pageNo);

    String buildPreviewUrlPath(Long bindingId, Long resourceVersionId, Integer pageNo);

    int countPages(Long resourceVersionId);

    Map<String, Object> getQuestionPublicSnapshot(Long questionVersionId);

    Map<String, Object> getQuestionTeacherSnapshot(Long questionVersionId);
}
