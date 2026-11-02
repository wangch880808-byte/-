package com.ruoyi.education.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.domain.activity.EduActivityInstance;
import com.ruoyi.education.domain.activity.EduActivityResponse;
import com.ruoyi.education.domain.activity.EduActivityTarget;
import com.ruoyi.education.dto.activity.ActivityStatisticsResponse;
import com.ruoyi.education.dto.activity.CheckinResponse;
import com.ruoyi.education.dto.activity.SubmitResponseRequest;
import com.ruoyi.education.enums.ActivityKind;
import com.ruoyi.education.enums.ActivityStatus;
import com.ruoyi.education.enums.ResponseState;
import com.ruoyi.education.mapper.EduActivityInstanceMapper;
import com.ruoyi.education.mapper.EduActivityResponseMapper;
import com.ruoyi.education.mapper.EduActivityResponseRevisionMapper;
import com.ruoyi.education.mapper.EduActivityTargetMapper;
import com.ruoyi.education.mapper.EduClassSessionMapper;
import com.ruoyi.education.mapper.EduSupportMapper;
import com.ruoyi.education.port.AssignmentBridgePort;
import com.ruoyi.education.port.CourseAccessPort;
import com.ruoyi.education.port.ResourceAccessPort;

/**
 * 4-02 的 G2 验收规则：重复签到不新增记录、外班拒绝、截止后拒收。
 */
@ExtendWith(MockitoExtension.class)
class ActivityServiceImplTest
{
    private static final Long ACTIVITY_ID = 6001L;

    private static final Long BINDING_ID = 1001L;

    private static final Long STUDENT_ID = 9L;

    private static final Long TEACHER_ID = 8L;

    @Mock
    private EduActivityInstanceMapper activityInstanceMapper;

    @Mock
    private EduActivityTargetMapper activityTargetMapper;

    @Mock
    private EduActivityResponseMapper activityResponseMapper;

    @Mock
    private EduActivityResponseRevisionMapper responseRevisionMapper;

    @Mock
    private EduClassSessionMapper classSessionMapper;

    @Mock
    private EduSupportMapper eduSupportMapper;

    @Mock
    private CourseAccessPort courseAccessPort;

    @Mock
    private ResourceAccessPort resourceAccessPort;

    @Mock
    private AssignmentBridgePort assignmentBridgePort;

    @InjectMocks
    private ActivityServiceImpl service;

    @Test
    void repeatedCheckinDoesNotCreateSecondRecord()
    {
        when(activityInstanceMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ActivityKind.ATTENDANCE,
                new Date(System.currentTimeMillis() - 60_000L), new Date(System.currentTimeMillis() + 600_000L)));
        when(courseAccessPort.isActiveStudentOfBinding(STUDENT_ID, BINDING_ID)).thenReturn(true);
        when(activityTargetMapper.selectByActivityAndStudent(ACTIVITY_ID, STUDENT_ID)).thenReturn(target());
        EduActivityResponse existing = new EduActivityResponse();
        existing.setId(7001L);
        existing.setResponseState(ResponseState.ON_TIME.name());
        existing.setAcceptedAt(new Date());
        existing.setCurrentRevisionNo(1);
        when(activityResponseMapper.selectByTargetId(701L)).thenReturn(existing);

        CheckinResponse response = service.checkin(ACTIVITY_ID, STUDENT_ID);

        assertTrue(response.getAlreadyCheckedIn());
        assertEquals(7001L, response.getResponseId().longValue());
        verify(activityResponseMapper, never()).insert(any());
        verify(responseRevisionMapper, never()).insert(any());
    }

    @Test
    void checkinIsRejectedForOutsider()
    {
        when(activityInstanceMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ActivityKind.ATTENDANCE,
                new Date(System.currentTimeMillis() - 60_000L), new Date(System.currentTimeMillis() + 600_000L)));
        when(courseAccessPort.isActiveStudentOfBinding(STUDENT_ID, BINDING_ID)).thenReturn(false);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.checkin(ACTIVITY_ID, STUDENT_ID));

        assertEquals(403, error.getCode().intValue());
        verify(activityResponseMapper, never()).insert(any());
    }

    @Test
    void responseIsRejectedAfterDeadline()
    {
        when(activityInstanceMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ActivityKind.QUIZ,
                new Date(System.currentTimeMillis() - 600_000L), new Date(System.currentTimeMillis() - 60_000L)));
        when(courseAccessPort.isActiveStudentOfBinding(STUDENT_ID, BINDING_ID)).thenReturn(true);
        when(activityTargetMapper.selectByActivityAndStudent(ACTIVITY_ID, STUDENT_ID)).thenReturn(target());

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.submitResponse(ACTIVITY_ID, new SubmitResponseRequest(), STUDENT_ID));

        assertEquals(422, error.getCode().intValue());
        verify(activityResponseMapper, never()).insert(any());
    }

    @Test
    void statisticsAreVisibleToTeacher()
    {
        when(activityInstanceMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ActivityKind.QUIZ,
                new Date(System.currentTimeMillis() - 600_000L), new Date(System.currentTimeMillis() + 600_000L)));
        when(courseAccessPort.isTeacherOfBinding(TEACHER_ID, BINDING_ID)).thenReturn(true);
        when(activityTargetMapper.countByActivityId(ACTIVITY_ID)).thenReturn(30);
        when(activityResponseMapper.countByActivityId(ACTIVITY_ID)).thenReturn(18);
        when(activityResponseMapper.countCorrectByActivityId(ACTIVITY_ID)).thenReturn(12);

        ActivityStatisticsResponse stats = service.getStatistics(ACTIVITY_ID, TEACHER_ID);

        assertEquals(30, stats.getTargetCount().intValue());
        assertEquals(18, stats.getRespondedCount().intValue());
        assertEquals(12, stats.getCorrectCount().intValue());
        assertEquals(0.6D, stats.getResponseRate().doubleValue(), 0.001D);
    }

    @Test
    void statisticsAreHiddenFromStudentBeforePublish()
    {
        when(activityInstanceMapper.selectById(ACTIVITY_ID)).thenReturn(activity(ActivityKind.QUIZ,
                new Date(System.currentTimeMillis() - 600_000L), new Date(System.currentTimeMillis() + 600_000L)));
        when(courseAccessPort.isTeacherOfBinding(STUDENT_ID, BINDING_ID)).thenReturn(false);
        when(courseAccessPort.isActiveStudentOfBinding(STUDENT_ID, BINDING_ID)).thenReturn(true);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.getStatistics(ACTIVITY_ID, STUDENT_ID));

        assertEquals(403, error.getCode().intValue());
        verify(activityTargetMapper, never()).countByActivityId(any());
        verify(activityResponseMapper, never()).countByActivityId(any());
    }

    @Test
    void statisticsAreVisibleToStudentAfterPublish()
    {
        EduActivityInstance quiz = activity(ActivityKind.QUIZ,
                new Date(System.currentTimeMillis() - 600_000L),
                new Date(System.currentTimeMillis() + 600_000L));
        quiz.setAnswerOpensAt(new Date(System.currentTimeMillis() - 60_000L));
        when(activityInstanceMapper.selectById(ACTIVITY_ID)).thenReturn(quiz);
        when(courseAccessPort.isTeacherOfBinding(STUDENT_ID, BINDING_ID)).thenReturn(false);
        when(courseAccessPort.isActiveStudentOfBinding(STUDENT_ID, BINDING_ID)).thenReturn(true);
        when(activityTargetMapper.countByActivityId(ACTIVITY_ID)).thenReturn(30);
        when(activityResponseMapper.countByActivityId(ACTIVITY_ID)).thenReturn(30);
        when(activityResponseMapper.countCorrectByActivityId(ACTIVITY_ID)).thenReturn(20);

        ActivityStatisticsResponse stats = service.getStatistics(ACTIVITY_ID, STUDENT_ID);

        assertTrue(stats.getAnswerPublished());
        assertEquals(1.0D, stats.getResponseRate().doubleValue(), 0.001D);
        assertEquals(20, stats.getCorrectCount().intValue());
    }

    private EduActivityInstance activity(ActivityKind kind, Date opensAt, Date closesAt)
    {
        EduActivityInstance activity = new EduActivityInstance();
        activity.setId(ACTIVITY_ID);
        activity.setBindingId(BINDING_ID);
        activity.setActivityKind(kind.name());
        activity.setStatus(ActivityStatus.OPEN.name());
        activity.setTitle("课堂活动");
        activity.setOpensAt(opensAt);
        activity.setClosesAt(closesAt);
        return activity;
    }

    private EduActivityTarget target()
    {
        EduActivityTarget target = new EduActivityTarget();
        target.setId(701L);
        target.setActivityId(ACTIVITY_ID);
        target.setStudentUserId(STUDENT_ID);
        return target;
    }
}
