package com.ruoyi.education.domain.activity;

import java.util.Date;

/**
 * 活动实例 edu_activity_instance
 */
public class EduActivityInstance
{
    private Long id;
    private Long bindingId;
    private Long teachingClassId;
    private Long courseSpaceId;
    private Long sessionId;
    private Long templateId;
    private String activityKind;
    private String title;
    private Long questionId;
    private Long questionVersionId;
    private Date opensAt;
    private Date closesAt;
    private Date answerOpensAt;
    private String status;
    private Long createdByUserId;
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

    public Long getTeachingClassId()
    {
        return teachingClassId;
    }

    public void setTeachingClassId(Long teachingClassId)
    {
        this.teachingClassId = teachingClassId;
    }

    public Long getCourseSpaceId()
    {
        return courseSpaceId;
    }

    public void setCourseSpaceId(Long courseSpaceId)
    {
        this.courseSpaceId = courseSpaceId;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public Long getTemplateId()
    {
        return templateId;
    }

    public void setTemplateId(Long templateId)
    {
        this.templateId = templateId;
    }

    public String getActivityKind()
    {
        return activityKind;
    }

    public void setActivityKind(String activityKind)
    {
        this.activityKind = activityKind;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getQuestionId()
    {
        return questionId;
    }

    public void setQuestionId(Long questionId)
    {
        this.questionId = questionId;
    }

    public Long getQuestionVersionId()
    {
        return questionVersionId;
    }

    public void setQuestionVersionId(Long questionVersionId)
    {
        this.questionVersionId = questionVersionId;
    }

    public Date getOpensAt()
    {
        return opensAt;
    }

    public void setOpensAt(Date opensAt)
    {
        this.opensAt = opensAt;
    }

    public Date getClosesAt()
    {
        return closesAt;
    }

    public void setClosesAt(Date closesAt)
    {
        this.closesAt = closesAt;
    }

    public Date getAnswerOpensAt()
    {
        return answerOpensAt;
    }

    public void setAnswerOpensAt(Date answerOpensAt)
    {
        this.answerOpensAt = answerOpensAt;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getCreatedByUserId()
    {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId)
    {
        this.createdByUserId = createdByUserId;
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
