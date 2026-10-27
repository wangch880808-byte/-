package com.ruoyi.education.service.impl;

import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.alibaba.fastjson2.JSON;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.constant.EducationHttpStatus;
import com.ruoyi.education.domain.activity.EduActivityInstance;
import com.ruoyi.education.domain.activity.EduActivityResponse;
import com.ruoyi.education.domain.classroom.EduClassSession;
import com.ruoyi.education.domain.classroom.EduSessionEvent;
import com.ruoyi.education.domain.classroom.EduSessionProjection;
import com.ruoyi.education.dto.classroom.ChangePageRequest;
import com.ruoyi.education.dto.classroom.MyParticipationSummary;
import com.ruoyi.education.dto.classroom.OpenActivitySummary;
import com.ruoyi.education.dto.classroom.SessionDetailResponse;
import com.ruoyi.education.dto.classroom.SessionEventItem;
import com.ruoyi.education.dto.classroom.SessionSnapshotResponse;
import com.ruoyi.education.dto.classroom.StartSessionRequest;
import com.ruoyi.education.dto.classroom.StartSessionResponse;
import com.ruoyi.education.enums.ActivityKind;
import com.ruoyi.education.enums.ActivityStatus;
import com.ruoyi.education.enums.EventVisibility;
import com.ruoyi.education.enums.SessionStatus;
import com.ruoyi.education.enums.SnapshotView;
import com.ruoyi.education.mapper.EduActivityInstanceMapper;
import com.ruoyi.education.mapper.EduActivityResponseMapper;
import com.ruoyi.education.mapper.EduClassSessionMapper;
import com.ruoyi.education.mapper.EduSessionEventMapper;
import com.ruoyi.education.mapper.EduSessionProjectionMapper;
import com.ruoyi.education.port.CourseAccessPort;
import com.ruoyi.education.port.ResourceAccessPort;
import com.ruoyi.education.service.IClassroomSessionService;
import com.ruoyi.education.util.EducationDateUtils;

/**
 * 课堂会话服务实现。
 */
@Service
public class ClassroomSessionServiceImpl implements IClassroomSessionService
{
    @Autowired
    private EduClassSessionMapper classSessionMapper;

    @Autowired
    private EduSessionProjectionMapper projectionMapper;

    @Autowired
    private EduSessionEventMapper sessionEventMapper;

    @Autowired
    private EduActivityInstanceMapper activityInstanceMapper;

    @Autowired
    private EduActivityResponseMapper activityResponseMapper;

    @Autowired
    private CourseAccessPort courseAccessPort;

    @Autowired
    private ResourceAccessPort resourceAccessPort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public StartSessionResponse startSession(StartSessionRequest request, Long userId)
    {
        Long bindingId = request.getBindingId();
        requireTeacher(userId, bindingId);
        if (classSessionMapper.selectActiveByBindingId(bindingId) != null)
        {
            throw new ServiceException("该班已有进行中会话", HttpStatus.CONFLICT);
        }
        Integer pageNo = request.getInitialPageNo() == null ? 1 : request.getInitialPageNo();
        resourceAccessPort.validateRelease(bindingId, request.getReleaseId(),
                request.getResourceVersionId(), pageNo);

        Date now = new Date();
        EduClassSession session = new EduClassSession();
        session.setBindingId(bindingId);
        session.setLessonId(request.getLessonId());
        session.setPlanId(request.getPlanId());
        session.setStatus(SessionStatus.RUNNING.name());
        session.setStartedByUserId(userId);
        session.setStartedAt(now);
        try
        {
            classSessionMapper.insert(session);
        }
        catch (DuplicateKeyException ex)
        {
            throw new ServiceException("该班已有进行中会话", HttpStatus.CONFLICT);
        }

        EduSessionProjection projection = new EduSessionProjection();
        projection.setSessionId(session.getId());
        projection.setBindingId(bindingId);
        projection.setStateSeq(1L);
        projection.setReleaseId(request.getReleaseId());
        projection.setResourceVersionId(request.getResourceVersionId());
        projection.setPageNo(pageNo);
        projection.setStudentCanBrowse(false);
        projectionMapper.insert(projection);

        appendPageEvent(session.getId(), userId, 1L, pageNo, now);

        StartSessionResponse response = new StartSessionResponse();
        response.setSessionId(session.getId());
        response.setBindingId(bindingId);
        response.setStatus(session.getStatus());
        response.setStateSeq(projection.getStateSeq());
        response.setStartedAt(EducationDateUtils.toIsoString(now));
        return response;
    }

    @Override
    public SessionDetailResponse getSession(Long sessionId, Long userId)
    {
        EduClassSession session = requireSession(sessionId);
        requireTeacher(userId, session.getBindingId());
        EduSessionProjection projection = requireProjection(sessionId);
        SessionDetailResponse response = new SessionDetailResponse();
        response.setSessionId(session.getId());
        response.setBindingId(session.getBindingId());
        response.setLessonId(session.getLessonId());
        response.setPlanId(session.getPlanId());
        response.setStatus(session.getStatus());
        response.setStartedAt(EducationDateUtils.toIsoString(session.getStartedAt()));
        response.setEndedAt(EducationDateUtils.toIsoString(session.getEndedAt()));
        response.setStateSeq(projection.getStateSeq());
        response.setPageNo(projection.getPageNo());
        response.setReleaseId(projection.getReleaseId());
        response.setResourceVersionId(projection.getResourceVersionId());
        response.setStudentCanBrowse(projection.getStudentCanBrowse());
        response.setOpenActivities(buildActivitySummaries(
                activityInstanceMapper.selectOpenBySessionId(sessionId), SnapshotView.TEACHER));
        return response;
    }

    @Override
    public SessionSnapshotResponse getSnapshot(Long sessionId, SnapshotView view, Long userId)
    {
        EduClassSession session = requireSession(sessionId);
        SnapshotView effectiveView = resolveView(view, userId, session);
        return buildSnapshot(session, effectiveView, userId);
    }

    @Override
    public List<SessionEventItem> listEvents(Long sessionId, Long afterSeq, SnapshotView view, Long userId)
    {
        EduClassSession session = requireSession(sessionId);
        SnapshotView effectiveView = resolveView(view, userId, session);
        boolean publicOnly = effectiveView != SnapshotView.TEACHER;
        long cursor = afterSeq == null ? 0L : afterSeq;
        List<EduSessionEvent> events = sessionEventMapper.selectAfterSeq(sessionId, cursor, publicOnly);
        List<SessionEventItem> items = new ArrayList<>();
        for (EduSessionEvent event : events)
        {
            SessionEventItem item = new SessionEventItem();
            item.setSeqNo(event.getSeqNo());
            item.setEventKind(event.getEventKind());
            item.setVisibility(event.getVisibility());
            item.setOccurredAt(EducationDateUtils.toIsoString(event.getOccurredAt()));
            if (event.getPublicPayloadJson() != null)
            {
                item.setPayload(JSON.parse(event.getPublicPayloadJson()));
            }
            items.add(item);
        }
        return items;
    }

    @Override
    public SessionSnapshotResponse joinSession(Long sessionId, Long userId)
    {
        EduClassSession session = requireSession(sessionId);
        if (!courseAccessPort.isActiveStudentOfBinding(userId, session.getBindingId()))
        {
            throw new ServiceException("非本班或已退班学生无法加入课堂", HttpStatus.FORBIDDEN);
        }
        if (SessionStatus.ENDED.name().equals(session.getStatus()))
        {
            return buildSnapshot(session, SnapshotView.STUDENT, userId);
        }
        return buildSnapshot(session, SnapshotView.STUDENT, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void pauseSession(Long sessionId, Long userId)
    {
        EduClassSession session = requireSession(sessionId);
        requireTeacher(userId, session.getBindingId());
        if (!SessionStatus.RUNNING.name().equals(session.getStatus()))
        {
            throw new ServiceException("仅进行中的会话可暂停", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
        classSessionMapper.updateStatus(sessionId, SessionStatus.PAUSED.name(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resumeSession(Long sessionId, Long userId)
    {
        EduClassSession session = requireSession(sessionId);
        requireTeacher(userId, session.getBindingId());
        if (!SessionStatus.PAUSED.name().equals(session.getStatus()))
        {
            throw new ServiceException("仅已暂停的会话可恢复", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
        classSessionMapper.updateStatus(sessionId, SessionStatus.RUNNING.name(), null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SessionSnapshotResponse endSession(Long sessionId, Long userId)
    {
        EduClassSession session = requireSession(sessionId);
        requireTeacher(userId, session.getBindingId());
        if (!SessionStatus.ENDED.name().equals(session.getStatus()))
        {
            classSessionMapper.updateStatus(sessionId, SessionStatus.ENDED.name(), userId);
            session = requireSession(sessionId);
        }
        return buildSnapshot(session, SnapshotView.TEACHER, userId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SessionSnapshotResponse changePage(Long sessionId, ChangePageRequest request, Long userId)
    {
        EduClassSession session = requireSession(sessionId);
        requireTeacher(userId, session.getBindingId());
        requireActiveSession(session);
        EduSessionProjection projection = requireProjection(sessionId);
        resourceAccessPort.validateRelease(session.getBindingId(), projection.getReleaseId(),
                projection.getResourceVersionId(), request.getPageNo());

        long nextSeq = projection.getStateSeq() + 1;
        boolean studentCanBrowse = Boolean.TRUE.equals(request.getStudentCanBrowse());
        projectionMapper.updatePage(sessionId, nextSeq, request.getPageNo(), studentCanBrowse);
        appendPageEvent(sessionId, userId, nextSeq, request.getPageNo(), new Date());
        return buildSnapshot(requireSession(sessionId), SnapshotView.TEACHER, userId);
    }

    @Override
    public SessionSnapshotResponse getActiveSession(Long bindingId, Long userId)
    {
        if (bindingId == null)
        {
            throw new ServiceException("bindingId 不能为空", HttpStatus.BAD_REQUEST);
        }
        if (!courseAccessPort.isTeacherOfBinding(userId, bindingId)
                && !courseAccessPort.isActiveStudentOfBinding(userId, bindingId))
        {
            throw new ServiceException("无权访问该课程绑定", HttpStatus.FORBIDDEN);
        }
        EduClassSession session = classSessionMapper.selectActiveByBindingId(bindingId);
        if (session == null)
        {
            return null;
        }
        SnapshotView view = courseAccessPort.isTeacherOfBinding(userId, bindingId)
                ? SnapshotView.TEACHER : SnapshotView.STUDENT;
        return buildSnapshot(session, view, userId);
    }

    private SessionSnapshotResponse buildSnapshot(EduClassSession session, SnapshotView view, Long userId)
    {
        EduSessionProjection projection = projectionMapper.selectBySessionId(session.getId());
        SessionSnapshotResponse snapshot = new SessionSnapshotResponse();
        snapshot.setSessionId(session.getId());
        snapshot.setBindingId(session.getBindingId());
        snapshot.setStatus(session.getStatus());
        snapshot.setServerTime(EducationDateUtils.nowIsoString());
        if (projection != null)
        {
            snapshot.setStateSeq(projection.getStateSeq());
            snapshot.setPageNo(projection.getPageNo());
            snapshot.setResourceVersionId(projection.getResourceVersionId());
            snapshot.setStudentCanBrowse(projection.getStudentCanBrowse());
            if (projection.getResourceVersionId() != null && projection.getPageNo() != null)
            {
                snapshot.setPreviewUrl(resourceAccessPort.buildPreviewUrlPath(
                        projection.getResourceVersionId(), projection.getPageNo()));
            }
        }
        List<EduActivityInstance> openActivities = activityInstanceMapper.selectOpenByBindingId(session.getBindingId());
        List<OpenActivitySummary> summaries = buildActivitySummaries(openActivities, view);
        snapshot.setOpenActivities(summaries);
        if (!summaries.isEmpty())
        {
            snapshot.setOpenActivity(summaries.get(0));
        }
        if (view == SnapshotView.STUDENT && userId != null)
        {
            snapshot.setMyParticipation(buildParticipation(openActivities, userId));
        }
        return snapshot;
    }

    private MyParticipationSummary buildParticipation(List<EduActivityInstance> openActivities, Long userId)
    {
        MyParticipationSummary participation = new MyParticipationSummary();
        participation.setCheckedIn(false);
        participation.setLastResponseId(null);
        for (EduActivityInstance activity : openActivities)
        {
            EduActivityResponse response = activityResponseMapper.selectByActivityAndStudent(activity.getId(), userId);
            if (response == null)
            {
                continue;
            }
            if (ActivityKind.ATTENDANCE.name().equals(activity.getActivityKind()))
            {
                participation.setCheckedIn(true);
            }
            participation.setLastResponseId(response.getId());
        }
        return participation;
    }

    private List<OpenActivitySummary> buildActivitySummaries(List<EduActivityInstance> activities, SnapshotView view)
    {
        List<OpenActivitySummary> summaries = new ArrayList<>();
        for (EduActivityInstance activity : activities)
        {
            if (!ActivityStatus.OPEN.name().equals(activity.getStatus()))
            {
                continue;
            }
            OpenActivitySummary summary = new OpenActivitySummary();
            summary.setActivityId(activity.getId());
            summary.setActivityKind(activity.getActivityKind());
            summary.setStatus(activity.getStatus());
            summary.setTitle(activity.getTitle());
            summary.setClosesAt(EducationDateUtils.toIsoString(activity.getClosesAt()));
            if (ActivityKind.QUIZ.name().equals(activity.getActivityKind())
                    && activity.getQuestionVersionId() != null)
            {
                boolean includeAnswer = view == SnapshotView.TEACHER
                        || activity.getAnswerOpensAt() != null;
                if (includeAnswer && view == SnapshotView.TEACHER)
                {
                    summary.setQuestion(resourceAccessPort.getQuestionTeacherSnapshot(
                            activity.getQuestionVersionId()));
                }
                else
                {
                    summary.setQuestion(resourceAccessPort.getQuestionPublicSnapshot(
                            activity.getQuestionVersionId()));
                }
            }
            summaries.add(summary);
        }
        return summaries;
    }

    private SnapshotView resolveView(SnapshotView view, Long userId, EduClassSession session)
    {
        if (view == SnapshotView.PUBLIC)
        {
            return SnapshotView.PUBLIC;
        }
        if (userId == null)
        {
            throw new ServiceException("未登录无法访问非公开快照", HttpStatus.UNAUTHORIZED);
        }
        if (view == SnapshotView.STUDENT)
        {
            if (!courseAccessPort.isActiveStudentOfBinding(userId, session.getBindingId()))
            {
                throw new ServiceException("无权以学生视图访问", HttpStatus.FORBIDDEN);
            }
            return SnapshotView.STUDENT;
        }
        if (view == SnapshotView.TEACHER || courseAccessPort.isTeacherOfBinding(userId, session.getBindingId()))
        {
            return SnapshotView.TEACHER;
        }
        if (courseAccessPort.isActiveStudentOfBinding(userId, session.getBindingId()))
        {
            return SnapshotView.STUDENT;
        }
        throw new ServiceException("无权访问课堂快照", HttpStatus.FORBIDDEN);
    }

    private void appendPageEvent(Long sessionId, Long userId, long seqNo, int pageNo, Date occurredAt)
    {
        EduSessionEvent event = new EduSessionEvent();
        event.setSessionId(sessionId);
        event.setSeqNo(seqNo);
        event.setEventKind("PAGE_CHANGED");
        event.setVisibility(EventVisibility.PUBLIC.name());
        event.setPublicPayloadJson(JSON.toJSONString(java.util.Map.of("page", pageNo)));
        event.setActorUserId(userId);
        event.setOccurredAt(occurredAt);
        sessionEventMapper.insert(event);
    }

    private EduClassSession requireSession(Long sessionId)
    {
        EduClassSession session = classSessionMapper.selectById(sessionId);
        if (session == null)
        {
            throw new ServiceException("会话不存在", HttpStatus.NOT_FOUND);
        }
        return session;
    }

    private EduSessionProjection requireProjection(Long sessionId)
    {
        EduSessionProjection projection = projectionMapper.selectBySessionId(sessionId);
        if (projection == null)
        {
            throw new ServiceException("会话投影不存在", HttpStatus.NOT_FOUND);
        }
        return projection;
    }

    private void requireTeacher(Long userId, Long bindingId)
    {
        if (!courseAccessPort.isTeacherOfBinding(userId, bindingId))
        {
            throw new ServiceException("无任课权限", HttpStatus.FORBIDDEN);
        }
    }

    private void requireActiveSession(EduClassSession session)
    {
        if (SessionStatus.ENDED.name().equals(session.getStatus()))
        {
            throw new ServiceException("会话已结束", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
    }
}
