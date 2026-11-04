package com.ruoyi.education.domain.activity;

import java.util.Date;

/**
 * 活动目标学生 edu_activity_target
 */
public class EduActivityTarget
{
    private Long id;
    private Long activityId;
    private Long teachingClassId;
    private Long studentUserId;
    private Long enrollmentId;
    private String targetStatus;
    private Date capturedAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getActivityId()
    {
        return activityId;
    }

    public void setActivityId(Long activityId)
    {
        this.activityId = activityId;
    }

    public Long getTeachingClassId()
    {
        return teachingClassId;
    }

    public void setTeachingClassId(Long teachingClassId)
    {
        this.teachingClassId = teachingClassId;
    }

    public Long getStudentUserId()
    {
        return studentUserId;
    }

    public void setStudentUserId(Long studentUserId)
    {
        this.studentUserId = studentUserId;
    }

    public Long getEnrollmentId()
    {
        return enrollmentId;
    }

    public void setEnrollmentId(Long enrollmentId)
    {
        this.enrollmentId = enrollmentId;
    }

    public String getTargetStatus()
    {
        return targetStatus;
    }

    public void setTargetStatus(String targetStatus)
    {
        this.targetStatus = targetStatus;
    }

    public Date getCapturedAt()
    {
        return capturedAt;
    }

    public void setCapturedAt(Date capturedAt)
    {
        this.capturedAt = capturedAt;
    }
}
