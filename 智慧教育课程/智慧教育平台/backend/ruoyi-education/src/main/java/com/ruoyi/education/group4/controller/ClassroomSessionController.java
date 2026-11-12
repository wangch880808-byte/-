package com.ruoyi.education.group4.controller;

import java.util.List;
import java.util.Map;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Anonymous;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.R;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.group4.dto.ClassroomPrepResponse;
import com.ruoyi.education.group4.dto.classroom.ChangePageRequest;
import com.ruoyi.education.group4.dto.classroom.SessionDetailResponse;
import com.ruoyi.education.group4.dto.classroom.SessionEventItem;
import com.ruoyi.education.group4.dto.classroom.SessionSnapshotResponse;
import com.ruoyi.education.group4.dto.classroom.StartSessionRequest;
import com.ruoyi.education.group4.dto.classroom.StartSessionResponse;
import com.ruoyi.education.group4.enums.SnapshotView;
import com.ruoyi.education.group4.mapper.Group4SupportMapper;
import com.ruoyi.education.group4.port.CourseAccessPort;
import com.ruoyi.education.group4.port.ResourceAccessPort;
import com.ruoyi.education.group4.service.IClassroomSessionService;

/**
 * 课堂会话接口（4-01 / 4-03 控制台依赖）。
 */
@RestController
@RequestMapping("/education/group4/sessions")
public class ClassroomSessionController
{
    private final IClassroomSessionService classroomSessionService;
    private final CourseAccessPort courseAccessPort;
    private final ResourceAccessPort resourceAccessPort;
    private final Group4SupportMapper supportMapper;

    public ClassroomSessionController(
            IClassroomSessionService classroomSessionService,
            CourseAccessPort courseAccessPort,
            ResourceAccessPort resourceAccessPort,
            Group4SupportMapper supportMapper)
    {
        this.classroomSessionService = classroomSessionService;
        this.courseAccessPort = courseAccessPort;
        this.resourceAccessPort = resourceAccessPort;
        this.supportMapper = supportMapper;
    }

    @GetMapping("/prep")
    public R<ClassroomPrepResponse> classroomPrep(@RequestParam Long bindingId)
    {
        Long userId = SecurityUtils.getUserId();
        if (!courseAccessPort.isTeacherOfBinding(userId, bindingId))
        {
            throw new ServiceException("仅任课教师可查看开课准备信息", HttpStatus.FORBIDDEN);
        }
        ClassroomPrepResponse prep = new ClassroomPrepResponse();
        prep.setBindingId(bindingId);
        Map<String, Object> release = supportMapper.selectDefaultSlideRelease(bindingId);
        if (release != null)
        {
            Long releaseId = asLong(release.get("release_id"));
            Long versionId = asLong(release.get("resource_version_id"));
            prep.setReleaseId(releaseId);
            prep.setResourceVersionId(versionId);
            prep.setResourceTitle(release.get("resource_title") == null ? null
                    : String.valueOf(release.get("resource_title")));
            if (versionId != null)
            {
                prep.setTotalPages(resourceAccessPort.countPages(versionId));
            }
        }
        Map<String, Object> lesson = supportMapper.selectLatestLessonPlan(bindingId);
        if (lesson != null)
        {
            prep.setLessonId(asLong(lesson.get("lesson_id")));
            prep.setPlanId(asLong(lesson.get("plan_id")));
        }
        Map<String, Object> quiz = supportMapper.selectDefaultQuizTemplate(bindingId);
        if (quiz != null)
        {
            prep.setDefaultQuestionId(asLong(quiz.get("question_id")));
            prep.setDefaultQuestionVersionId(asLong(quiz.get("question_version_id")));
        }
        return R.ok(prep);
    }

    @PostMapping
    public R<StartSessionResponse> startSession(@Validated @RequestBody StartSessionRequest request)
    {
        return R.ok(classroomSessionService.startSession(request, SecurityUtils.getUserId()));
    }

    @GetMapping("/{sessionId}")
    public R<SessionDetailResponse> getSession(@PathVariable Long sessionId)
    {
        return R.ok(classroomSessionService.getSession(sessionId, SecurityUtils.getUserId()));
    }

    @PostMapping("/{sessionId}/pause")
    public R<Void> pause(@PathVariable Long sessionId)
    {
        classroomSessionService.pauseSession(sessionId, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{sessionId}/resume")
    public R<Void> resume(@PathVariable Long sessionId)
    {
        classroomSessionService.resumeSession(sessionId, SecurityUtils.getUserId());
        return R.ok();
    }

    @PostMapping("/{sessionId}/end")
    public R<SessionSnapshotResponse> end(@PathVariable Long sessionId)
    {
        return R.ok(classroomSessionService.endSession(sessionId, SecurityUtils.getUserId()));
    }

    @PostMapping("/{sessionId}/page")
    public R<SessionSnapshotResponse> changePage(@PathVariable Long sessionId,
            @Validated @RequestBody ChangePageRequest request)
    {
        return R.ok(classroomSessionService.changePage(sessionId, request, SecurityUtils.getUserId()));
    }

    @Anonymous
    @GetMapping("/{sessionId}/snapshot")
    public R<SessionSnapshotResponse> snapshot(@PathVariable Long sessionId,
            @RequestParam(required = false) String view)
    {
        SnapshotView snapshotView = SnapshotView.fromParam(view);
        Long userId = snapshotView == SnapshotView.PUBLIC ? null : SecurityUtils.getUserId();
        return R.ok(classroomSessionService.getSnapshot(sessionId, snapshotView, userId));
    }

    @GetMapping("/{sessionId}/events")
    public R<List<SessionEventItem>> events(@PathVariable Long sessionId,
            @RequestParam(required = false) Long afterSeq,
            @RequestParam(required = false) String view)
    {
        return R.ok(classroomSessionService.listEvents(
                sessionId, afterSeq, SnapshotView.fromParam(view), SecurityUtils.getUserId()));
    }

    @PostMapping("/{sessionId}/join")
    public R<SessionSnapshotResponse> join(@PathVariable Long sessionId)
    {
        return R.ok(classroomSessionService.joinSession(sessionId, SecurityUtils.getUserId()));
    }

    @GetMapping("/active")
    public R<SessionSnapshotResponse> active(@RequestParam Long bindingId)
    {
        return R.ok(classroomSessionService.getActiveSession(bindingId, SecurityUtils.getUserId()));
    }

    private static Long asLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
