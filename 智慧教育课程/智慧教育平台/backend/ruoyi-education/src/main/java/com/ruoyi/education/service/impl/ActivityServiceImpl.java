package com.ruoyi.education.service.impl;

import java.time.Instant;
import java.util.Date;
import java.util.List;
import java.util.Map;
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
import com.ruoyi.education.domain.activity.EduActivityResponseRevision;
import com.ruoyi.education.domain.activity.EduActivityTarget;
import com.ruoyi.education.domain.classroom.EduClassSession;
import com.ruoyi.education.domain.course.BindingContext;
import com.ruoyi.education.dto.activity.CheckinResponse;
import com.ruoyi.education.dto.activity.CreateActivityRequest;
import com.ruoyi.education.dto.activity.CreateActivityResponse;
import com.ruoyi.education.dto.activity.SubmitResponseRequest;
import com.ruoyi.education.dto.activity.SubmitResponseResult;
import com.ruoyi.education.enums.ActivityKind;
import com.ruoyi.education.enums.ActivityStatus;
import com.ruoyi.education.enums.ResponseState;
import com.ruoyi.education.enums.SessionStatus;
import com.ruoyi.education.mapper.EduActivityInstanceMapper;
import com.ruoyi.education.mapper.EduActivityResponseMapper;
import com.ruoyi.education.mapper.EduActivityResponseRevisionMapper;
import com.ruoyi.education.mapper.EduActivityTargetMapper;
import com.ruoyi.education.mapper.EduClassSessionMapper;
import com.ruoyi.education.mapper.EduSupportMapper;
import com.ruoyi.education.port.AssignmentBridgePort;
import com.ruoyi.education.port.CourseAccessPort;
import com.ruoyi.education.port.ResourceAccessPort;
import com.ruoyi.education.service.IActivityService;
import com.ruoyi.education.util.EducationDateUtils;

/**
 * 课堂活动服务实现。
 */
@Service
public class ActivityServiceImpl implements IActivityService
{
    @Autowired
    private EduActivityInstanceMapper activityInstanceMapper;

    @Autowired
    private EduActivityTargetMapper activityTargetMapper;

    @Autowired
    private EduActivityResponseMapper activityResponseMapper;

    @Autowired
    private EduActivityResponseRevisionMapper responseRevisionMapper;

    @Autowired
    private EduClassSessionMapper classSessionMapper;

    @Autowired
    private EduSupportMapper eduSupportMapper;

    @Autowired
    private CourseAccessPort courseAccessPort;

    @Autowired
    private ResourceAccessPort resourceAccessPort;

    @Autowired
    private AssignmentBridgePort assignmentBridgePort;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CreateActivityResponse createActivity(CreateActivityRequest request, Long userId)
    {
        Long bindingId = request.getBindingId();
        requireTeacher(userId, bindingId);
        BindingContext binding = requireBinding(bindingId);
        validateActivityRequest(request);

        if (request.getSessionId() != null)
        {
            EduClassSession session = classSessionMapper.selectById(request.getSessionId());
            if (session == null || !bindingId.equals(session.getBindingId()))
            {
                throw new ServiceException("会话与绑定不匹配", EducationHttpStatus.UNPROCESSABLE_ENTITY);
            }
            if (SessionStatus.ENDED.name().equals(session.getStatus()))
            {
                throw new ServiceException("会话已结束，无法发起活动", EducationHttpStatus.UNPROCESSABLE_ENTITY);
            }
        }

        Date opensAt = parseInstant(request.getOpensAt());
        Date closesAt = parseInstant(request.getClosesAt());
        if (!closesAt.after(opensAt))
        {
            throw new ServiceException("closesAt 必须晚于 opensAt", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }

        EduActivityInstance instance = new EduActivityInstance();
        instance.setBindingId(bindingId);
        instance.setTeachingClassId(binding.getTeachingClassId());
        instance.setCourseSpaceId(binding.getCourseSpaceId());
        instance.setSessionId(request.getSessionId());
        instance.setActivityKind(request.getActivityKind());
        instance.setTitle(request.getTitle());
        instance.setQuestionId(request.getQuestionId());
        instance.setQuestionVersionId(request.getQuestionVersionId());
        instance.setOpensAt(opensAt);
        instance.setClosesAt(closesAt);
        instance.setStatus(ActivityStatus.OPEN.name());
        instance.setCreatedByUserId(userId);
        try
        {
            activityInstanceMapper.insert(instance);
        }
        catch (DuplicateKeyException ex)
        {
            throw new ServiceException("该班已有同类型进行中的活动", HttpStatus.CONFLICT);
        }

        captureTargets(instance);
        CreateActivityResponse response = new CreateActivityResponse();
        response.setActivityId(instance.getId());
        response.setActivityKind(instance.getActivityKind());
        response.setStatus(instance.getStatus());
        response.setOpensAt(EducationDateUtils.toIsoString(instance.getOpensAt()));
        response.setClosesAt(EducationDateUtils.toIsoString(instance.getClosesAt()));
        return response;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public CheckinResponse checkin(Long activityId, Long userId)
    {
        EduActivityInstance activity = requireOpenActivity(activityId, ActivityKind.ATTENDANCE);
        requireActiveStudent(userId, activity.getBindingId());
        EduActivityTarget target = requireTarget(activityId, userId);
        Date now = new Date();
        requireWithinWindow(activity, now);

        EduActivityResponse existing = activityResponseMapper.selectByTargetId(target.getId());
        if (existing != null)
        {
            return toCheckinResponse(existing, true);
        }

        EduActivityResponse response = new EduActivityResponse();
        response.setActivityId(activityId);
        response.setTargetId(target.getId());
        response.setAcceptedAt(now);
        response.setResponseState(resolveResponseState(activity, now));
        response.setCurrentRevisionNo(1);
        try
        {
            activityResponseMapper.insert(response);
        }
        catch (DuplicateKeyException ex)
        {
            EduActivityResponse persisted = activityResponseMapper.selectByTargetId(target.getId());
            return toCheckinResponse(persisted, true);
        }

        EduActivityResponseRevision revision = new EduActivityResponseRevision();
        revision.setResponseId(response.getId());
        revision.setRevisionNo(1);
        revision.setAnswerJson(null);
        revision.setIsCorrect(null);
        revision.setRecordedByUserId(userId);
        revision.setRecordedAt(now);
        responseRevisionMapper.insert(revision);
        return toCheckinResponse(response, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SubmitResponseResult submitResponse(Long activityId, SubmitResponseRequest request, Long userId)
    {
        EduActivityInstance activity = requireOpenActivity(activityId, ActivityKind.QUIZ);
        requireActiveStudent(userId, activity.getBindingId());
        EduActivityTarget target = requireTarget(activityId, userId);
        Date now = new Date();
        if (now.after(activity.getClosesAt()))
        {
            throw new ServiceException("活动已截止，无法提交作答", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }

        EduActivityResponse existing = activityResponseMapper.selectByTargetId(target.getId());
        if (existing != null)
        {
            return toSubmitResult(existing, true);
        }

        EduActivityResponse response = new EduActivityResponse();
        response.setActivityId(activityId);
        response.setTargetId(target.getId());
        response.setAcceptedAt(now);
        response.setResponseState(resolveResponseState(activity, now));
        response.setCurrentRevisionNo(1);
        try
        {
            activityResponseMapper.insert(response);
        }
        catch (DuplicateKeyException ex)
        {
            EduActivityResponse persisted = activityResponseMapper.selectByTargetId(target.getId());
            return toSubmitResult(persisted, true);
        }

        EduActivityResponseRevision revision = new EduActivityResponseRevision();
        revision.setResponseId(response.getId());
        revision.setRevisionNo(1);
        revision.setAnswerJson(JSON.toJSONString(request.getAnswers()));
        revision.setIsCorrect(null);
        revision.setRecordedByUserId(userId);
        revision.setRecordedAt(now);
        responseRevisionMapper.insert(revision);
        return toSubmitResult(response, false);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void closeActivity(Long activityId, Long userId)
    {
        EduActivityInstance activity = requireActivity(activityId);
        requireTeacher(userId, activity.getBindingId());
        if (!ActivityStatus.OPEN.name().equals(activity.getStatus()))
        {
            return;
        }
        activityInstanceMapper.updateStatus(activityId, ActivityStatus.CLOSED.name());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void publishResult(Long activityId, Long userId)
    {
        EduActivityInstance activity = requireActivity(activityId);
        requireTeacher(userId, activity.getBindingId());
        activityInstanceMapper.updateAnswerOpensAt(activityId);
    }

    @Override
    public Long createClasswork(Long activityId, CreateActivityRequest request, Long userId)
    {
        EduActivityInstance activity = requireActivity(activityId);
        requireTeacher(userId, activity.getBindingId());
        return assignmentBridgePort.createClasswork(request, activity.getSessionId(), userId);
    }

    private void captureTargets(EduActivityInstance instance)
    {
        Date capturedAt = new Date();
        List<Map<String, Object>> students = eduSupportMapper.selectActiveStudentsForBinding(instance.getBindingId());
        for (Map<String, Object> student : students)
        {
            EduActivityTarget target = new EduActivityTarget();
            target.setActivityId(instance.getId());
            target.setTeachingClassId(toLong(student.get("teaching_class_id")));
            target.setStudentUserId(toLong(student.get("student_user_id")));
            target.setEnrollmentId(toLong(student.get("enrollment_id")));
            target.setTargetStatus("REQUIRED");
            target.setCapturedAt(capturedAt);
            activityTargetMapper.insert(target);
        }
    }

    private void validateActivityRequest(CreateActivityRequest request)
    {
        String kind = request.getActivityKind();
        if (ActivityKind.QUIZ.name().equals(kind))
        {
            if (request.getQuestionId() == null || request.getQuestionVersionId() == null)
            {
                throw new ServiceException("随堂题须指定 questionId 与 questionVersionId",
                        EducationHttpStatus.UNPROCESSABLE_ENTITY);
            }
            resourceAccessPort.getQuestionPublicSnapshot(request.getQuestionVersionId());
        }
        else if (ActivityKind.ATTENDANCE.name().equals(kind))
        {
            request.setQuestionId(null);
            request.setQuestionVersionId(null);
        }
        else if (!ActivityKind.CLASSWORK.name().equals(kind))
        {
            throw new ServiceException("不支持的活动类型: " + kind, EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private EduActivityInstance requireOpenActivity(Long activityId, ActivityKind expectedKind)
    {
        EduActivityInstance activity = requireActivity(activityId);
        if (!expectedKind.name().equals(activity.getActivityKind()))
        {
            throw new ServiceException("活动类型不匹配", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (!ActivityStatus.OPEN.name().equals(activity.getStatus()))
        {
            throw new ServiceException("活动未开放", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
        return activity;
    }

    private EduActivityInstance requireActivity(Long activityId)
    {
        EduActivityInstance activity = activityInstanceMapper.selectById(activityId);
        if (activity == null)
        {
            throw new ServiceException("活动不存在", HttpStatus.NOT_FOUND);
        }
        return activity;
    }

    private EduActivityTarget requireTarget(Long activityId, Long studentUserId)
    {
        EduActivityTarget target = activityTargetMapper.selectByActivityAndStudent(activityId, studentUserId);
        if (target == null)
        {
            throw new ServiceException("非本次活动目标学生", HttpStatus.FORBIDDEN);
        }
        return target;
    }

    private BindingContext requireBinding(Long bindingId)
    {
        BindingContext binding = courseAccessPort.getBindingContext(bindingId);
        if (binding == null)
        {
            throw new ServiceException("课程绑定不存在", HttpStatus.NOT_FOUND);
        }
        return binding;
    }

    private void requireTeacher(Long userId, Long bindingId)
    {
        if (!courseAccessPort.isTeacherOfBinding(userId, bindingId))
        {
            throw new ServiceException("无任课权限", HttpStatus.FORBIDDEN);
        }
    }

    private void requireActiveStudent(Long userId, Long bindingId)
    {
        if (!courseAccessPort.isActiveStudentOfBinding(userId, bindingId))
        {
            throw new ServiceException("非本班或已退班学生", HttpStatus.FORBIDDEN);
        }
    }

    private void requireWithinWindow(EduActivityInstance activity, Date now)
    {
        if (now.before(activity.getOpensAt()))
        {
            throw new ServiceException("活动尚未开始", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
        if (now.after(activity.getClosesAt()))
        {
            throw new ServiceException("活动已截止", EducationHttpStatus.UNPROCESSABLE_ENTITY);
        }
    }

    private String resolveResponseState(EduActivityInstance activity, Date now)
    {
        return now.after(activity.getClosesAt()) ? ResponseState.LATE.name() : ResponseState.ON_TIME.name();
    }

    private Date parseInstant(String value)
    {
        return Date.from(Instant.parse(value));
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number)
        {
            return ((Number) value).longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private CheckinResponse toCheckinResponse(EduActivityResponse response, boolean alreadyCheckedIn)
    {
        CheckinResponse result = new CheckinResponse();
        result.setResponseId(response.getId());
        result.setResponseState(response.getResponseState());
        result.setAcceptedAt(EducationDateUtils.toIsoString(response.getAcceptedAt()));
        result.setAlreadyCheckedIn(alreadyCheckedIn);
        return result;
    }

    private SubmitResponseResult toSubmitResult(EduActivityResponse response, boolean alreadySubmitted)
    {
        SubmitResponseResult result = new SubmitResponseResult();
        result.setResponseId(response.getId());
        result.setRevisionNo(response.getCurrentRevisionNo());
        result.setResponseState(response.getResponseState());
        result.setAcceptedAt(EducationDateUtils.toIsoString(response.getAcceptedAt()));
        result.setAlreadySubmitted(alreadySubmitted);
        return result;
    }
}
