package com.ruoyi.education.course.service;

import java.time.LocalDateTime;
import java.util.List;
import com.ruoyi.education.course.domain.NoticeKind;

/**
 * 学生通知查询条件。列表、详情、未读数和已读更新共用同一对象，
 * 保证授权、可见性和分页口径一致。
 */
public class StudentNoticeQuery
{
    private Long recipientUserId;

    private LocalDateTime at;

    private List<NoticeBindingScope> bindings;

    private NoticeKind kind;

    private boolean unreadOnly;

    private long offset;

    private int limit;

    public Long getRecipientUserId()
    {
        return recipientUserId;
    }

    public void setRecipientUserId(Long recipientUserId)
    {
        this.recipientUserId = recipientUserId;
    }

    public LocalDateTime getAt()
    {
        return at;
    }

    public void setAt(LocalDateTime at)
    {
        this.at = at;
    }

    public List<NoticeBindingScope> getBindings()
    {
        return bindings;
    }

    public void setBindings(List<NoticeBindingScope> bindings)
    {
        this.bindings = bindings;
    }

    public NoticeKind getKind()
    {
        return kind;
    }

    public void setKind(NoticeKind kind)
    {
        this.kind = kind;
    }

    public boolean isUnreadOnly()
    {
        return unreadOnly;
    }

    public void setUnreadOnly(boolean unreadOnly)
    {
        this.unreadOnly = unreadOnly;
    }

    public long getOffset()
    {
        return offset;
    }

    public void setOffset(long offset)
    {
        this.offset = offset;
    }

    public int getLimit()
    {
        return limit;
    }

    public void setLimit(int limit)
    {
        this.limit = limit;
    }
}
