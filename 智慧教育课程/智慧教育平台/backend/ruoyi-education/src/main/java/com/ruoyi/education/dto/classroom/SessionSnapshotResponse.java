package com.ruoyi.education.dto.classroom;

import java.util.List;

/**
 * 课堂快照（重连入口）。
 */
public class SessionSnapshotResponse
{
    private Long sessionId;
    private Long bindingId;
    private Long stateSeq;
    private String status;
    private Integer pageNo;
    private Long resourceVersionId;
    private String previewUrl;
    private Boolean studentCanBrowse;
    private String serverTime;
    private OpenActivitySummary openActivity;
    private List<OpenActivitySummary> openActivities;
    private MyParticipationSummary myParticipation;

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

    public Long getStateSeq()
    {
        return stateSeq;
    }

    public void setStateSeq(Long stateSeq)
    {
        this.stateSeq = stateSeq;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Integer getPageNo()
    {
        return pageNo;
    }

    public void setPageNo(Integer pageNo)
    {
        this.pageNo = pageNo;
    }

    public Long getResourceVersionId()
    {
        return resourceVersionId;
    }

    public void setResourceVersionId(Long resourceVersionId)
    {
        this.resourceVersionId = resourceVersionId;
    }

    public String getPreviewUrl()
    {
        return previewUrl;
    }

    public void setPreviewUrl(String previewUrl)
    {
        this.previewUrl = previewUrl;
    }

    public Boolean getStudentCanBrowse()
    {
        return studentCanBrowse;
    }

    public void setStudentCanBrowse(Boolean studentCanBrowse)
    {
        this.studentCanBrowse = studentCanBrowse;
    }

    public String getServerTime()
    {
        return serverTime;
    }

    public void setServerTime(String serverTime)
    {
        this.serverTime = serverTime;
    }

    public OpenActivitySummary getOpenActivity()
    {
        return openActivity;
    }

    public void setOpenActivity(OpenActivitySummary openActivity)
    {
        this.openActivity = openActivity;
    }

    public List<OpenActivitySummary> getOpenActivities()
    {
        return openActivities;
    }

    public void setOpenActivities(List<OpenActivitySummary> openActivities)
    {
        this.openActivities = openActivities;
    }

    public MyParticipationSummary getMyParticipation()
    {
        return myParticipation;
    }

    public void setMyParticipation(MyParticipationSummary myParticipation)
    {
        this.myParticipation = myParticipation;
    }
}
