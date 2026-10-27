package com.ruoyi.education.dto.classroom;

/**
 * 创建并开始课堂响应。
 */
public class StartSessionResponse
{
    private Long sessionId;
    private Long bindingId;
    private String status;
    private Long stateSeq;
    private String startedAt;

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

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Long getStateSeq()
    {
        return stateSeq;
    }

    public void setStateSeq(Long stateSeq)
    {
        this.stateSeq = stateSeq;
    }

    public String getStartedAt()
    {
        return startedAt;
    }

    public void setStartedAt(String startedAt)
    {
        this.startedAt = startedAt;
    }
}
