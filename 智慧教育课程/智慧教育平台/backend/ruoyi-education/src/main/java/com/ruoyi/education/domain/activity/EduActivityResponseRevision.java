package com.ruoyi.education.domain.activity;

import java.util.Date;

/**
 * 活动作答版本 edu_activity_response_revision
 */
public class EduActivityResponseRevision
{
    private Long id;
    private Long responseId;
    private Integer revisionNo;
    private String answerJson;
    private Boolean isCorrect;
    private Long recordedByUserId;
    private Date recordedAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

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

    public String getAnswerJson()
    {
        return answerJson;
    }

    public void setAnswerJson(String answerJson)
    {
        this.answerJson = answerJson;
    }

    public Boolean getIsCorrect()
    {
        return isCorrect;
    }

    public void setIsCorrect(Boolean isCorrect)
    {
        this.isCorrect = isCorrect;
    }

    public Long getRecordedByUserId()
    {
        return recordedByUserId;
    }

    public void setRecordedByUserId(Long recordedByUserId)
    {
        this.recordedByUserId = recordedByUserId;
    }

    public Date getRecordedAt()
    {
        return recordedAt;
    }

    public void setRecordedAt(Date recordedAt)
    {
        this.recordedAt = recordedAt;
    }
}
