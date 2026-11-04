package com.ruoyi.education.domain.classroom;

import java.util.Date;

/**
 * 课堂投影状态 edu_session_projection
 */
public class EduSessionProjection
{
    private Long sessionId;
    private Long bindingId;
    private Long stateSeq;
    private Long releaseId;
    private Long resourceVersionId;
    private Integer pageNo;
    private Boolean studentCanBrowse;
    private Date updatedAt;

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

    public Integer getPageNo()
    {
        return pageNo;
    }

    public void setPageNo(Integer pageNo)
    {
        this.pageNo = pageNo;
    }

    public Boolean getStudentCanBrowse()
    {
        return studentCanBrowse;
    }

    public void setStudentCanBrowse(Boolean studentCanBrowse)
    {
        this.studentCanBrowse = studentCanBrowse;
    }

    public Date getUpdatedAt()
    {
        return updatedAt;
    }

    public void setUpdatedAt(Date updatedAt)
    {
        this.updatedAt = updatedAt;
    }
}
