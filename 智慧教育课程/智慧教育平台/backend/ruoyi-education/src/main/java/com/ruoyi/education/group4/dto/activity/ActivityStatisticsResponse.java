package com.ruoyi.education.group4.dto.activity;

/**
 * 活动参与情况统计（4-03：控制台查看即时人数与答题统计）。
 *
 * <p>只返回聚合数字，不含任何学生明细；学生侧仅在公布结果后才可读取。</p>
 */
public class ActivityStatisticsResponse
{
    private Long activityId;
    private String activityKind;
    private String status;
    private Integer targetCount;
    private Integer respondedCount;
    private Integer correctCount;
    private Double responseRate;
    private Boolean answerPublished;

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

    public Integer getTargetCount()
    {
        return targetCount;
    }

    public void setTargetCount(Integer targetCount)
    {
        this.targetCount = targetCount;
    }

    public Integer getRespondedCount()
    {
        return respondedCount;
    }

    public void setRespondedCount(Integer respondedCount)
    {
        this.respondedCount = respondedCount;
    }

    public Integer getCorrectCount()
    {
        return correctCount;
    }

    public void setCorrectCount(Integer correctCount)
    {
        this.correctCount = correctCount;
    }

    public Double getResponseRate()
    {
        return responseRate;
    }

    public void setResponseRate(Double responseRate)
    {
        this.responseRate = responseRate;
    }

    public Boolean getAnswerPublished()
    {
        return answerPublished;
    }

    public void setAnswerPublished(Boolean answerPublished)
    {
        this.answerPublished = answerPublished;
    }
}
