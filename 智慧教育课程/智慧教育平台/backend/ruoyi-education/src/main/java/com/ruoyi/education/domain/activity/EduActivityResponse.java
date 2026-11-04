package com.ruoyi.education.domain.activity;

import java.util.Date;

/**
 * 活动作答 edu_activity_response
 */
public class EduActivityResponse
{
    private Long id;
    private Long activityId;
    private Long targetId;
    private Date acceptedAt;
    private String responseState;
    private Integer currentRevisionNo;

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

    public Long getTargetId()
    {
        return targetId;
    }

    public void setTargetId(Long targetId)
    {
        this.targetId = targetId;
    }

    public Date getAcceptedAt()
    {
        return acceptedAt;
    }

    public void setAcceptedAt(Date acceptedAt)
    {
        this.acceptedAt = acceptedAt;
    }

    public String getResponseState()
    {
        return responseState;
    }

    public void setResponseState(String responseState)
    {
        this.responseState = responseState;
    }

    public Integer getCurrentRevisionNo()
    {
        return currentRevisionNo;
    }

    public void setCurrentRevisionNo(Integer currentRevisionNo)
    {
        this.currentRevisionNo = currentRevisionNo;
    }
}
