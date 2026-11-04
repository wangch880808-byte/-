package com.ruoyi.education.domain.classroom;

import java.util.Date;

/**
 * 课堂会话 edu_class_session
 */
public class EduClassSession
{
    private Long id;
    private Long bindingId;
    private Long lessonId;
    private Long planId;
    private String status;
    private Long startedByUserId;
    private Date startedAt;
    private Long endedByUserId;
    private Date endedAt;
    private Date createdAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
    }

    public Long getLessonId()
    {
        return lessonId;
    }

    public void setLessonId(Long lessonId)
    {
        this.lessonId = lessonId;
    }

    public Long getPlanId()
    {
        return planId;
    }

    public void setPlanId(Long planId)
    {
        this.planId = planId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getStartedByUserId()
    {
        return startedByUserId;
    }

    public void setStartedByUserId(Long startedByUserId)
    {
        this.startedByUserId = startedByUserId;
    }

    public Date getStartedAt()
    {
        return startedAt;
    }

    public void setStartedAt(Date startedAt)
    {
        this.startedAt = startedAt;
    }

    public Long getEndedByUserId()
    {
        return endedByUserId;
    }

    public void setEndedByUserId(Long endedByUserId)
    {
        this.endedByUserId = endedByUserId;
    }

    public Date getEndedAt()
    {
        return endedAt;
    }

    public void setEndedAt(Date endedAt)
    {
        this.endedAt = endedAt;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }
}
