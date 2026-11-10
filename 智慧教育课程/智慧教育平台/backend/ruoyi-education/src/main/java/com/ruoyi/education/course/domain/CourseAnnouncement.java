package com.ruoyi.education.course.domain;

import java.time.LocalDateTime;

/**
 * 教学班公告，对应 edu_course_announcement。
 *
 * <p>时间字段按数据库约定为 UTC。该对象只映射当前实际表结构，
 * 不包含 visibilityState、发布人姓名等派生或尚未确认的响应字段。</p>
 */
public class CourseAnnouncement
{
    private Long id;

    private Long bindingId;

    private String title;

    private String body;

    private AnnouncementStatus status;

    private LocalDateTime opensAt;

    private LocalDateTime closesAt;

    private Long publishedByUserId;

    private Long createdByUserId;

    private LocalDateTime createdAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public String getBody()
    {
        return body;
    }

    public void setBody(String body)
    {
        this.body = body;
    }

    public AnnouncementStatus getStatus()
    {
        return status;
    }

    public void setStatus(AnnouncementStatus status)
    {
        this.status = status;
    }

    public LocalDateTime getOpensAt()
    {
        return opensAt;
    }

    public void setOpensAt(LocalDateTime opensAt)
    {
        this.opensAt = opensAt;
    }

    public LocalDateTime getClosesAt()
    {
        return closesAt;
    }

    public void setClosesAt(LocalDateTime closesAt)
    {
        this.closesAt = closesAt;
    }

    public Long getPublishedByUserId()
    {
        return publishedByUserId;
    }

    public void setPublishedByUserId(Long publishedByUserId)
    {
        this.publishedByUserId = publishedByUserId;
    }

    public Long getCreatedByUserId()
    {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId)
    {
        this.createdByUserId = createdByUserId;
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
