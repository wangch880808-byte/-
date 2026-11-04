package com.ruoyi.education.dto.activity;

/**
 * 签到响应。
 */
public class CheckinResponse
{
    private Long responseId;
    private String responseState;
    private String acceptedAt;
    private Boolean alreadyCheckedIn;

    public Long getResponseId()
    {
        return responseId;
    }

    public void setResponseId(Long responseId)
    {
        this.responseId = responseId;
    }

    public String getResponseState()
    {
        return responseState;
    }

    public void setResponseState(String responseState)
    {
        this.responseState = responseState;
    }

    public String getAcceptedAt()
    {
        return acceptedAt;
    }

    public void setAcceptedAt(String acceptedAt)
    {
        this.acceptedAt = acceptedAt;
    }

    public Boolean getAlreadyCheckedIn()
    {
        return alreadyCheckedIn;
    }

    public void setAlreadyCheckedIn(Boolean alreadyCheckedIn)
    {
        this.alreadyCheckedIn = alreadyCheckedIn;
    }
}
