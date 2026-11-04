package com.ruoyi.education.dto.classroom;

import java.util.List;

/**
 * 会话详情（教师查询）。
 */
public class SessionDetailResponse
{
    private Long sessionId;
    private Long bindingId;
    private Long lessonId;
    private Long planId;
    private String status;
    private String startedAt;
    private String endedAt;
    private Long stateSeq;
    private Integer pageNo;
    private Long releaseId;
    private Long resourceVersionId;
    private Boolean studentCanBrowse;
    private List<OpenActivitySummary> openActivities;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
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

    public String getStartedAt()
    {
        return startedAt;
    }

    public void setStartedAt(String startedAt)
    {
        this.startedAt = startedAt;
    }

    public String getEndedAt()
    {
        return endedAt;
    }

    public void setEndedAt(String endedAt)
    {
        this.endedAt = endedAt;
    }

    public Long getStateSeq()
    {
        return stateSeq;
    }

    public void setStateSeq(Long stateSeq)
    {
        this.stateSeq = stateSeq;
    }

    public Integer getPageNo()
    {
        return pageNo;
    }

    public void setPageNo(Integer pageNo)
    {
        this.pageNo = pageNo;
    }

    public Long getReleaseId()
    {
        return releaseId;
    }

    public void setReleaseId(Long releaseId)
    {
        this.releaseId = releaseId;
    }

    public Long getResourceVersionId()
    {
        return resourceVersionId;
    }

    public void setResourceVersionId(Long resourceVersionId)
    {
        this.resourceVersionId = resourceVersionId;
    }

    public Boolean getStudentCanBrowse()
    {
        return studentCanBrowse;
    }

    public void setStudentCanBrowse(Boolean studentCanBrowse)
    {
        this.studentCanBrowse = studentCanBrowse;
    }

    public List<OpenActivitySummary> getOpenActivities()
    {
        return openActivities;
    }

    public void setOpenActivities(List<OpenActivitySummary> openActivities)
    {
        this.openActivities = openActivities;
    }
}
