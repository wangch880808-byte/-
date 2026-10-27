package com.ruoyi.education.domain.classroom;

import java.util.Date;

/**
 * 课堂会话事件 edu_session_event
 */
public class EduSessionEvent
{
    private Long id;
    private Long sessionId;
    private Long seqNo;
    private String eventKind;
    private String visibility;
    private String publicPayloadJson;
    private Long actorUserId;
    private Date occurredAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

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

    public String getPublicPayloadJson()
    {
        return publicPayloadJson;
    }

    public void setPublicPayloadJson(String publicPayloadJson)
    {
        this.publicPayloadJson = publicPayloadJson;
    }

    public Long getActorUserId()
    {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId)
    {
        this.actorUserId = actorUserId;
    }

    public Date getOccurredAt()
    {
        return occurredAt;
    }

    public void setOccurredAt(Date occurredAt)
    {
        this.occurredAt = occurredAt;
    }
}
