package com.ruoyi.education.group3.contract;

import java.util.List;
import java.util.Map;

/**
 * Read contract for classroom, homework and AI consumers.
 * Implementations must apply the current caller's teacher/student object scope.
 */
public interface PublishedResourceProvider
{
    List<Map<String, Object>> publishedResources(Long bindingId);

    Map<String, Object> resourceVersion(Long bindingId, Long versionId);
}
