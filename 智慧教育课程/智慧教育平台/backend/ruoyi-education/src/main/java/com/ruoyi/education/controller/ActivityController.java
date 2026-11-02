package com.ruoyi.education.controller;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.dto.activity.CheckinResponse;
import com.ruoyi.education.dto.activity.CreateActivityRequest;
import com.ruoyi.education.dto.activity.CreateActivityResponse;
import com.ruoyi.education.dto.activity.SubmitResponseRequest;
import com.ruoyi.education.dto.activity.SubmitResponseResult;
import com.ruoyi.education.service.IActivityService;
import com.ruoyi.education.service.IIdempotencyService;

/**
 * 课堂活动接口（4-02）。
 */
@RestController
@RequestMapping("/education/activity/instances")
public class ActivityController extends BaseController
{
    @Autowired
    private IActivityService activityService;

    @Autowired
    private IIdempotencyService idempotencyService;

    @PostMapping
    public AjaxResult create(@Validated @RequestBody CreateActivityRequest request)
    {
        CreateActivityResponse data = activityService.createActivity(request, SecurityUtils.getUserId());
        return success(data);
    }

    @PostMapping("/{activityId}/checkin")
    public AjaxResult checkin(@PathVariable Long activityId,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
    {
        Long userId = SecurityUtils.getUserId();
        String fingerprint = "checkin:" + activityId;
        Long replayed = idempotencyService.replayIfPresent(
                userId, "ACTIVITY.CHECKIN", activityId, idempotencyKey, fingerprint);
        if (replayed != null)
        {
            return success(activityService.getCheckinResult(activityId, userId));
        }
        CheckinResponse data = activityService.checkin(activityId, userId);
        idempotencyService.remember(
                userId, "ACTIVITY.CHECKIN", activityId, idempotencyKey, fingerprint, data.getResponseId());
        return success(data);
    }

    @PostMapping("/{activityId}/responses")
    public AjaxResult responses(@PathVariable Long activityId,
            @Validated @RequestBody SubmitResponseRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey)
    {
        Long userId = SecurityUtils.getUserId();
        String fingerprint = "responses:" + activityId + ":" + JSON.toJSONString(request.getAnswers());
        Long replayed = idempotencyService.replayIfPresent(
                userId, "ACTIVITY.RESPONSE", activityId, idempotencyKey, fingerprint);
        if (replayed != null)
        {
            return success(activityService.getSubmissionResult(activityId, userId));
        }
        SubmitResponseResult data = activityService.submitResponse(activityId, request, userId);
        idempotencyService.remember(
                userId, "ACTIVITY.RESPONSE", activityId, idempotencyKey, fingerprint, data.getResponseId());
        return success(data);
    }

    @PostMapping("/{activityId}/close")
    public AjaxResult close(@PathVariable Long activityId)
    {
        activityService.closeActivity(activityId, SecurityUtils.getUserId());
        return success();
    }

    @PostMapping("/{activityId}/publish-result")
    public AjaxResult publishResult(@PathVariable Long activityId)
    {
        activityService.publishResult(activityId, SecurityUtils.getUserId());
        return success();
    }

    @PostMapping("/{activityId}/classwork")
    public AjaxResult classwork(@PathVariable Long activityId,
            @Validated @RequestBody CreateActivityRequest request)
    {
        Long assignmentId = activityService.createClasswork(activityId, request, SecurityUtils.getUserId());
        return success(assignmentId);
    }
}
