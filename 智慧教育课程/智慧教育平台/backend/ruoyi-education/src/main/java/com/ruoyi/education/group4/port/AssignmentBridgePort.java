package com.ruoyi.education.group4.port;

import com.ruoyi.education.group4.dto.activity.CreateActivityRequest;

/**
 * 课堂小作业桥接端口（待第 5 组统一作业引擎接入）。
 */
public interface AssignmentBridgePort
{
    Long createClasswork(CreateActivityRequest request, Long sessionId, Long userId);
}
