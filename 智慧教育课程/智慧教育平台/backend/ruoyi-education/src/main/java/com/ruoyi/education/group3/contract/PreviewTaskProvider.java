package com.ruoyi.education.group3.contract;

import java.util.List;
import java.util.Map;

/**
 * Preview task contract. Submission remains on the shared assignment engine.
 */
public interface PreviewTaskProvider
{
    List<Map<String, Object>> previewTasksForCurrentUser(Long bindingId);
}
