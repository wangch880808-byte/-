package com.ruoyi.education.service;

import com.ruoyi.education.dto.activity.CheckinResponse;
import com.ruoyi.education.dto.activity.CreateActivityRequest;
import com.ruoyi.education.dto.activity.CreateActivityResponse;
import com.ruoyi.education.dto.activity.SubmitResponseRequest;
import com.ruoyi.education.dto.activity.SubmitResponseResult;

/**
 * 课堂活动服务。
 */
public interface IActivityService
{
    CreateActivityResponse createActivity(CreateActivityRequest request, Long userId);

    CheckinResponse checkin(Long activityId, Long userId);

    SubmitResponseResult submitResponse(Long activityId, SubmitResponseRequest request, Long userId);

    void closeActivity(Long activityId, Long userId);

    void publishResult(Long activityId, Long userId);

    Long createClasswork(Long activityId, CreateActivityRequest request, Long userId);
}
