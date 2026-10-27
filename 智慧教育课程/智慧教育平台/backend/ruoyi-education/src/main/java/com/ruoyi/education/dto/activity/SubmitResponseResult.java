package com.ruoyi.education.dto.activity;

/**
 * 作答提交响应。
 */
public class SubmitResponseResult
{
    private Long responseId;
    private Integer revisionNo;
    private String responseState;
    private String acceptedAt;
    private Boolean alreadySubmitted;

    public Long getResponseId()
    {
        return responseId;
    }

    public void setResponseId(Long responseId)
    {
        this.responseId = responseId;
    }

    public Integer getRevisionNo()
    {
        return revisionNo;
    }

    public void setRevisionNo(Integer revisionNo)
    {
        this.revisionNo = revisionNo;
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

    public Boolean getAlreadySubmitted()
    {
        return alreadySubmitted;
    }

    public void setAlreadySubmitted(Boolean alreadySubmitted)
    {
        this.alreadySubmitted = alreadySubmitted;
    }
}
