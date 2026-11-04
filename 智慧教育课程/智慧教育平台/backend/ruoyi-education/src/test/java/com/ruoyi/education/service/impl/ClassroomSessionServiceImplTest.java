package com.ruoyi.education.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.domain.activity.EduActivityInstance;
import com.ruoyi.education.domain.classroom.EduClassSession;
import com.ruoyi.education.domain.classroom.EduSessionEvent;
import com.ruoyi.education.domain.classroom.EduSessionProjection;
import com.ruoyi.education.dto.classroom.ChangePageRequest;
import com.ruoyi.education.dto.classroom.SessionSnapshotResponse;
import com.ruoyi.education.enums.ActivityKind;
import com.ruoyi.education.enums.ActivityStatus;
import com.ruoyi.education.enums.SessionStatus;
import com.ruoyi.education.enums.SnapshotView;
import com.ruoyi.education.mapper.EduActivityInstanceMapper;
import com.ruoyi.education.mapper.EduActivityResponseMapper;
import com.ruoyi.education.mapper.EduClassSessionMapper;
import com.ruoyi.education.mapper.EduSessionEventMapper;
import com.ruoyi.education.mapper.EduSessionProjectionMapper;
import com.ruoyi.education.port.CourseAccessPort;
import com.ruoyi.education.port.ResourceAccessPort;

/**
 * 4-01 / 4-03 的 G2 验收规则：视图权限、答案公布时机、翻页序号、状态事件。
 */
@ExtendWith(MockitoExtension.class)
class ClassroomSessionServiceImplTest
{
    private static final Long SESSION_ID = 5001L;

    private static final Long BINDING_ID = 1001L;

    private static final Long OTHER_USER_ID = 9L;

    @Mock
    private EduClassSessionMapper classSessionMapper;

    @Mock
    private EduSessionProjectionMapper projectionMapper;

    @Mock
    private EduSessionEventMapper sessionEventMapper;

    @Mock
    private EduActivityInstanceMapper activityInstanceMapper;

    @Mock
    private EduActivityResponseMapper activityResponseMapper;

    @Mock
    private CourseAccessPort courseAccessPort;

    @Mock
    private ResourceAccessPort resourceAccessPort;

    @InjectMocks
    private ClassroomSessionServiceImpl service;

    @Test
    void teacherViewIsRejectedForNonTeacher()
    {
        when(classSessionMapper.selectById(SESSION_ID)).thenReturn(session(SessionStatus.RUNNING));
        when(courseAccessPort.isTeacherOfBinding(OTHER_USER_ID, BINDING_ID)).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getSnapshot(SESSION_ID, SnapshotView.TEACHER, OTHER_USER_ID));

        assertEquals(403, error.getCode().intValue());
        verify(activityInstanceMapper, never()).selectOpenByBindingId(anyLong());
    }

    @Test
    void publicSnapshotHidesQuizAnswerBeforePublish()
    {
        EduActivityInstance quiz = quiz(new Date(System.currentTimeMillis() + 600_000L));
        quiz.setAnswerOpensAt(null);
        when(classSessionMapper.selectById(SESSION_ID)).thenReturn(session(SessionStatus.RUNNING));
        when(projectionMapper.selectBySessionId(SESSION_ID)).thenReturn(projection(7L, 2));
        when(activityInstanceMapper.selectOpenByBindingId(BINDING_ID)).thenReturn(List.of(quiz));
        when(resourceAccessPort.getQuestionPublicSnapshot(3001L))
                .thenReturn(Map.of("questionVersionId", 3001L));

        SessionSnapshotResponse snapshot = service.getSnapshot(SESSION_ID, SnapshotView.PUBLIC, null);

        assertNotNull(snapshot.getOpenActivity());
        assertFalse(snapshot.getOpenActivity().getQuestion().containsKey("answer"));
        verify(resourceAccessPort, never()).getQuestionTeacherSnapshot(anyLong());
    }

    @Test
    void publicSnapshotShowsQuizAnswerAfterPublish()
    {
        EduActivityInstance quiz = quiz(new Date(System.currentTimeMillis() + 600_000L));
        quiz.setAnswerOpensAt(new Date(System.currentTimeMillis() - 60_000L));
        when(classSessionMapper.selectById(SESSION_ID)).thenReturn(session(SessionStatus.RUNNING));
        when(projectionMapper.selectBySessionId(SESSION_ID)).thenReturn(projection(7L, 2));
        when(activityInstanceMapper.selectOpenByBindingId(BINDING_ID)).thenReturn(List.of(quiz));
        when(resourceAccessPort.getQuestionTeacherSnapshot(3001L))
                .thenReturn(Map.of("questionVersionId", 3001L, "answer", "B"));

        SessionSnapshotResponse snapshot = service.getSnapshot(SESSION_ID, SnapshotView.PUBLIC, null);

        assertEquals("B", snapshot.getOpenActivity().getQuestion().get("answer"));
    }

    @Test
    void changePageIncrementsStateSeqAtomically()
    {
        EduSessionProjection projection = projection(7L, 1);
        when(classSessionMapper.selectById(SESSION_ID)).thenReturn(session(SessionStatus.RUNNING));
        when(courseAccessPort.isTeacherOfBinding(OTHER_USER_ID, BINDING_ID)).thenReturn(true);
        when(projectionMapper.selectBySessionId(SESSION_ID)).thenReturn(projection);
        when(activityInstanceMapper.selectOpenByBindingId(BINDING_ID)).thenReturn(List.of());
        doAnswer(invocation -> {
            projection.setStateSeq(projection.getStateSeq() + 1);
            return 1;
        }).when(projectionMapper).updatePageAtomic(SESSION_ID, 3, false);

        service.changePage(SESSION_ID, pageRequest(3), OTHER_USER_ID);

        verify(projectionMapper).updatePageAtomic(SESSION_ID, 3, false);
        ArgumentCaptor<EduSessionEvent> captor = ArgumentCaptor.forClass(EduSessionEvent.class);
        verify(sessionEventMapper).insert(captor.capture());
        assertEquals(8L, captor.getValue().getSeqNo().longValue());
        assertEquals("PAGE_CHANGED", captor.getValue().getEventKind());
    }

    @Test
    void pauseAppendsPublicStatusEvent()
    {
        EduSessionProjection projection = projection(7L, 1);
        when(classSessionMapper.selectById(SESSION_ID)).thenReturn(session(SessionStatus.RUNNING));
        when(courseAccessPort.isTeacherOfBinding(OTHER_USER_ID, BINDING_ID)).thenReturn(true);
        when(projectionMapper.selectBySessionId(SESSION_ID)).thenReturn(projection);
        doAnswer(invocation -> {
            projection.setStateSeq(projection.getStateSeq() + 1);
            return 1;
        }).when(projectionMapper).incrementStateSeq(SESSION_ID);

        service.pauseSession(SESSION_ID, OTHER_USER_ID);

        ArgumentCaptor<EduSessionEvent> captor = ArgumentCaptor.forClass(EduSessionEvent.class);
        verify(sessionEventMapper).insert(captor.capture());
        EduSessionEvent event = captor.getValue();
        assertEquals("SESSION_STATUS_CHANGED", event.getEventKind());
        assertEquals(8L, event.getSeqNo().longValue());
        assertTrue(event.getPublicPayloadJson().contains(SessionStatus.PAUSED.name()));
    }

    private EduClassSession session(SessionStatus status)
    {
        EduClassSession session = new EduClassSession();
        session.setId(SESSION_ID);
        session.setBindingId(BINDING_ID);
        session.setStatus(status.name());
        session.setStartedAt(new Date(System.currentTimeMillis() - 60_000L));
        return session;
    }

    private EduSessionProjection projection(long stateSeq, int pageNo)
    {
        EduSessionProjection projection = new EduSessionProjection();
        projection.setSessionId(SESSION_ID);
        projection.setBindingId(BINDING_ID);
        projection.setStateSeq(stateSeq);
        projection.setReleaseId(2001L);
        projection.setResourceVersionId(3001L);
        projection.setPageNo(pageNo);
        projection.setStudentCanBrowse(false);
        return projection;
    }

    private EduActivityInstance quiz(Date closesAt)
    {
        EduActivityInstance activity = new EduActivityInstance();
        activity.setId(6001L);
        activity.setBindingId(BINDING_ID);
        activity.setActivityKind(ActivityKind.QUIZ.name());
        activity.setStatus(ActivityStatus.OPEN.name());
        activity.setTitle("随堂题");
        activity.setQuestionId(4001L);
        activity.setQuestionVersionId(3001L);
        activity.setOpensAt(new Date(System.currentTimeMillis() - 60_000L));
        activity.setClosesAt(closesAt);
        return activity;
    }

    private ChangePageRequest pageRequest(int pageNo)
    {
        ChangePageRequest request = new ChangePageRequest();
        request.setPageNo(pageNo);
        request.setStudentCanBrowse(false);
        return request;
    }
}
