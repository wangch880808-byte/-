package com.ruoyi.education.controller;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.dto.classroom.ChangePageRequest;
import com.ruoyi.education.dto.classroom.SessionDetailResponse;
import com.ruoyi.education.dto.classroom.SessionEventItem;
import com.ruoyi.education.dto.classroom.SessionSnapshotResponse;
import com.ruoyi.education.dto.classroom.StartSessionRequest;
import com.ruoyi.education.dto.classroom.StartSessionResponse;
import com.ruoyi.education.enums.SnapshotView;
import com.ruoyi.education.service.IClassroomSessionService;

/**
 * 课堂会话接口（4-01）。
 */
@RestController
@RequestMapping("/education/classroom/sessions")
public class ClassroomSessionController extends BaseController
{
    @Autowired
    private IClassroomSessionService classroomSessionService;

    @PostMapping
    public AjaxResult startSession(@Validated @RequestBody StartSessionRequest request)
    {
        StartSessionResponse data = classroomSessionService.startSession(request, SecurityUtils.getUserId());
        return success(data);
    }

    @GetMapping("/{sessionId}")
    public AjaxResult getSession(@PathVariable Long sessionId)
    {
        SessionDetailResponse data = classroomSessionService.getSession(sessionId, SecurityUtils.getUserId());
        return success(data);
    }

    @PostMapping("/{sessionId}/pause")
    public AjaxResult pause(@PathVariable Long sessionId)
    {
        classroomSessionService.pauseSession(sessionId, SecurityUtils.getUserId());
        return success();
    }

    @PostMapping("/{sessionId}/resume")
    public AjaxResult resume(@PathVariable Long sessionId)
    {
        classroomSessionService.resumeSession(sessionId, SecurityUtils.getUserId());
        return success();
    }

    @PostMapping("/{sessionId}/end")
    public AjaxResult end(@PathVariable Long sessionId)
    {
        SessionSnapshotResponse data = classroomSessionService.endSession(sessionId, SecurityUtils.getUserId());
        return success(data);
    }

    @PostMapping("/{sessionId}/page")
    public AjaxResult changePage(@PathVariable Long sessionId,
            @Validated @RequestBody ChangePageRequest request)
    {
        SessionSnapshotResponse data = classroomSessionService.changePage(sessionId, request, SecurityUtils.getUserId());
        return success(data);
    }

    @Anonymous
    @GetMapping("/{sessionId}/snapshot")
    public AjaxResult snapshot(@PathVariable Long sessionId,
            @RequestParam(required = false) String view)
    {
        SnapshotView snapshotView = SnapshotView.fromParam(view);
        Long userId = snapshotView == SnapshotView.PUBLIC ? null : SecurityUtils.getUserId();
        SessionSnapshotResponse data = classroomSessionService.getSnapshot(sessionId, snapshotView, userId);
        return success(data);
    }

    @GetMapping("/{sessionId}/events")
    public AjaxResult events(@PathVariable Long sessionId,
            @RequestParam(required = false) Long afterSeq,
            @RequestParam(required = false) String view)
    {
        List<SessionEventItem> data = classroomSessionService.listEvents(
                sessionId, afterSeq, SnapshotView.fromParam(view), SecurityUtils.getUserId());
        return success(data);
    }

    @PostMapping("/{sessionId}/join")
    public AjaxResult join(@PathVariable Long sessionId)
    {
        SessionSnapshotResponse data = classroomSessionService.joinSession(sessionId, SecurityUtils.getUserId());
        return success(data);
    }

    @GetMapping("/active")
    public AjaxResult active(@RequestParam Long bindingId)
    {
        SessionSnapshotResponse data = classroomSessionService.getActiveSession(bindingId, SecurityUtils.getUserId());
        return success(data);
    }
}
