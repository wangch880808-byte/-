package com.ruoyi.education.group4.service;

import com.ruoyi.education.group4.dto.activity.ActivityStatisticsResponse;
import com.ruoyi.education.group4.dto.activity.CheckinResponse;
import com.ruoyi.education.group4.dto.activity.CreateActivityRequest;
import com.ruoyi.education.group4.dto.activity.CreateActivityResponse;
import com.ruoyi.education.group4.dto.activity.SubmitResponseRequest;
import com.ruoyi.education.group4.dto.activity.SubmitResponseResult;

/**
 * 课堂活动服务。
 */
public interface IActivityService
{
    CreateActivityResponse createActivity(CreateActivityRequest request, Long userId);

    CheckinResponse checkin(Long activityId, Long userId);

    /**
     * 读取学生已有签到结果，用于 Idempotency-Key 回放。
     */
    CheckinResponse getCheckinResult(Long activityId, Long userId);

    SubmitResponseResult submitResponse(Long activityId, SubmitResponseRequest request, Long userId);

    /**
     * 读取学生已有作答结果，用于 Idempotency-Key 回放。
     */
    SubmitResponseResult getSubmissionResult(Long activityId, Long userId);

    void closeActivity(Long activityId, Long userId);

    void publishResult(Long activityId, Long userId);

    Long createClasswork(Long activityId, CreateActivityRequest request, Long userId);

    /**
     * 活动参与统计。教师随时可读；学生仅在公布结果后可读，且只返回聚合数字。
     */
    ActivityStatisticsResponse getStatistics(Long activityId, Long userId);
}
