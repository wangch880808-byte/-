package com.ruoyi.education.dto.classroom;

import java.util.Map;

/**
 * 进行中活动摘要。
 */
public class OpenActivitySummary
{
    private Long activityId;
    private String activityKind;
    private String status;
    private String title;
    private String closesAt;
    private Map<String, Object> question;

    public Long getActivityId()
    {
        return activityId;
    }

    public void setActivityId(Long activityId)
    {
        this.activityId = activityId;
    }

    public String getActivityKind()
    {
        return activityKind;
    }

    public void setActivityKind(String activityKind)
    {
        this.activityKind = activityKind;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getClosesAt()
    {
        return closesAt;
    }

    public void setClosesAt(String closesAt)
    {
        this.closesAt = closesAt;
    }

    public Map<String, Object> getQuestion()
    {
        return question;
    }

    public void setQuestion(Map<String, Object> question)
    {
        this.question = question;
    }
}
