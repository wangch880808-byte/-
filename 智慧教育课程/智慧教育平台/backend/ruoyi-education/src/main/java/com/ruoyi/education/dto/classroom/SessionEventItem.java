package com.ruoyi.education.dto.classroom;

/**
 * 会话事件条目。
 */
public class SessionEventItem
{
    private Long seqNo;
    private String eventKind;
    private String visibility;
    private Object payload;
    private String occurredAt;

    public Long getSeqNo()
    {
        return seqNo;
    }

    public void setSeqNo(Long seqNo)
    {
        this.seqNo = seqNo;
    }

    public String getEventKind()
    {
        return eventKind;
    }

    public void setEventKind(String eventKind)
    {
        this.eventKind = eventKind;
    }

    public String getVisibility()
    {
        return visibility;
    }

    public void setVisibility(String visibility)
    {
        this.visibility = visibility;
    }

    public Object getPayload()
    {
        return payload;
    }

    public void setPayload(Object payload)
    {
        this.payload = payload;
    }

    public String getOccurredAt()
    {
        return occurredAt;
    }

    public void setOccurredAt(String occurredAt)
    {
        this.occurredAt = occurredAt;
    }
}
