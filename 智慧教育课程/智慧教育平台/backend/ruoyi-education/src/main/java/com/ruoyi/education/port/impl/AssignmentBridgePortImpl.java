package com.ruoyi.education.port.impl;

import org.springframework.stereotype.Service;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.dto.activity.CreateActivityRequest;
import com.ruoyi.education.port.AssignmentBridgePort;

/**
 * 课堂小作业桥接占位实现（待第 5 组接入）。
 *
 * @see docs/api-contracts-classroom.md §3.6
 */
@Service
public class AssignmentBridgePortImpl implements AssignmentBridgePort
{
    @Override
    public Long createClasswork(CreateActivityRequest request, Long sessionId, Long userId)
    {
        throw new ServiceException("待第5组接入统一作业引擎");
    }
}
