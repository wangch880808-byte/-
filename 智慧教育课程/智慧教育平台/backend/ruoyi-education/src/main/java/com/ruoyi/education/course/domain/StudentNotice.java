package com.ruoyi.education.course.domain;

import java.time.LocalDateTime;

/**
 * 学生站内通知，对应 edu_user_notice。
 *
 * <p>通知只是提醒，不是来源业务的访问凭证；时间字段按数据库约定为 UTC。</p>
 */
public class StudentNotice
{
    private Long id;

    private Long recipientUserId;

    private Long bindingId;

    private NoticeKind noticeKind;

    private Long sourceId;

    private String title;

    private LocalDateTime visibleAt;

    private LocalDateTime readAt;

    private LocalDateTime createdAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getRecipientUserId()
    {
        return recipientUserId;
    }

    public void setRecipientUserId(Long recipientUserId)
    {
        this.recipientUserId = recipientUserId;
    }

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
    }

    public NoticeKind getNoticeKind()
    {
        return noticeKind;
    }

    public void setNoticeKind(NoticeKind noticeKind)
    {
        this.noticeKind = noticeKind;
    }

    public Long getSourceId()
    {
        return sourceId;
    }

    public void setSourceId(Long sourceId)
    {
        this.sourceId = sourceId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public LocalDateTime getVisibleAt()
    {
        return visibleAt;
    }

    public void setVisibleAt(LocalDateTime visibleAt)
    {
        this.visibleAt = visibleAt;
    }

    public LocalDateTime getReadAt()
    {
        return readAt;
    }

    public void setReadAt(LocalDateTime readAt)
    {
        this.readAt = readAt;
    }

    public LocalDateTime getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt)
    {
        this.createdAt = createdAt;
    }
}
