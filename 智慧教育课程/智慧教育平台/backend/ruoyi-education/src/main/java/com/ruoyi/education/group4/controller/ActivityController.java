package com.ruoyi.education.group4.controller;

import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.group4.dto.activity.ActivityStatisticsResponse;
import com.ruoyi.education.group4.dto.activity.CheckinResponse;
import com.ruoyi.education.group4.dto.activity.CreateActivityRequest;
import com.ruoyi.education.group4.dto.activity.CreateActivityResponse;
import com.ruoyi.education.group4.dto.activity.SubmitResponseRequest;
import com.ruoyi.education.group4.dto.activity.SubmitResponseResult;
import com.ruoyi.education.group4.service.IActivityService;
import com.ruoyi.education.group4.service.IIdempotencyService;

/**
 * 课堂活动接口（4-02）。
 */
@RestController
@RequestMapping("/education/group4/activities")
public class ActivityController
{
    private final IActivityService activityService;
    private final IIdempotencyService idempotencyService;

    public ActivityController(IActivityService activityService, IIdempotencyService idempotencyService)
    {
        this.activityService = activityService;
        this.idempotencyService = idempotencyService;
    }

    @PostMapping
    public R<CreateActivityResponse> create(@Validated @RequestBody CreateActivityRequest request)
    {
        return R.ok(activityService.createActivity(request, SecurityUtils.getUserId()));
    }

    @PostMapping("/{activityId}/checkin")
    public R<CheckinResponse> checkin(@PathVariable Long activityId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
    {
        Long userId = SecurityUtils.getUserId();
        String fingerprint = "checkin:" + activityId;
        Long replayed = idempotencyService.replayIfPresent(
                userId, "ACTIVITY.CHECKIN", activityId, idempotencyKey, fingerprint);
        if (replayed != null)
        {
            return R.ok(activityService.getCheckinResult(activityId, userId));
        }
        CheckinResponse data = activityService.checkin(activityId, userId);
        idempotencyService.remember(
                userId, "ACTIVITY.CHECKIN", activityId, idempotencyKey, fingerprint, data.getResponseId());
        return R.ok(data);
    }

    @PostMapping("/{activityId}/responses")
    public R<SubmitResponseResult> responses(@PathVariable Long activityId,
            @Validated @RequestBody SubmitResponseRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
    {
        Long userId = SecurityUtils.getUserId();
        String fingerprint = "responses:" + activityId + ":" + JSON.toJSONString(request.getAnswers());
        Long replayed = idempotencyService.replayIfPresent(
                userId, "ACTIVITY.RESPONSE", activityId, idempotencyKey, fingerprint);
        if (replayed != null)
        {
            return R.ok(activityService.getSubmissionResult(activityId, userId));
        }
        SubmitResponseResult data = activityService.submitResponse(activityId, request, userId);
        idempotencyService.remember(
                userId, "ACTIVITY.RESPONSE", activityId, idempotencyKey, fingerprint, data.getResponseId());
        return R.ok(data);
    }

    @PostMapping("/{activityId}/close")
    public R<Void> close(@PathVariable Long activityId)
    {
        activityService.closeActivity(activityId, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{activityId}/publish-result")
    public R<Void> publishResult(@PathVariable Long activityId)
    {
        activityService.publishResult(activityId, SecurityUtils.getUserId());
        return R.ok();
    }

    @GetMapping("/{activityId}/statistics")
    public R<ActivityStatisticsResponse> statistics(@PathVariable Long activityId)
    {
        return R.ok(activityService.getStatistics(activityId, SecurityUtils.getUserId()));
    }

    @PostMapping("/{activityId}/classwork")
    public R<Long> classwork(@PathVariable Long activityId,
            @Validated @RequestBody CreateActivityRequest request)
    {
        return R.ok(activityService.createClasswork(activityId, request, SecurityUtils.getUserId()));
    }
}
