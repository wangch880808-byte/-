package com.ruoyi.education.dto.activity;

/**
 * 创建活动实例响应。
 */
public class CreateActivityResponse
{
    private Long activityId;
    private String activityKind;
    private String status;
    private String opensAt;
    private String closesAt;

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

    public String getOpensAt()
    {
        return opensAt;
    }

    public void setOpensAt(String opensAt)
    {
        this.opensAt = opensAt;
    }

    public String getClosesAt()
    {
        return closesAt;
    }

    public void setClosesAt(String closesAt)
    {
        this.closesAt = closesAt;
    }
}
