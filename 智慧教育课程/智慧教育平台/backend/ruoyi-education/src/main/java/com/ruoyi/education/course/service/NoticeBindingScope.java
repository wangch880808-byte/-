package com.ruoyi.education.course.service;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * 通知查询使用的入班区间快照。由授权适配层提供，不直接查询成员表。
 */
public class NoticeBindingScope
{
    private Long bindingId;

    private LocalDateTime joinedAt;

    private LocalDateTime leftAt;

    public NoticeBindingScope()
    {
    }

    public NoticeBindingScope(Long bindingId, LocalDateTime joinedAt, LocalDateTime leftAt)
    {
        this.bindingId = bindingId;
        this.joinedAt = joinedAt;
        this.leftAt = leftAt;
    }

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
    }

    public LocalDateTime getJoinedAt()
    {
        return joinedAt;
    }

    public void setJoinedAt(LocalDateTime joinedAt)
    {
        this.joinedAt = joinedAt;
    }

    public LocalDateTime getLeftAt()
    {
        return leftAt;
    }

    public void setLeftAt(LocalDateTime leftAt)
    {
        this.leftAt = leftAt;
    }

    @Override
    public boolean equals(Object other)
    {
        if (this == other)
        {
            return true;
        }
        if (!(other instanceof NoticeBindingScope scope))
        {
            return false;
        }
        return Objects.equals(bindingId, scope.bindingId)
                && Objects.equals(joinedAt, scope.joinedAt)
                && Objects.equals(leftAt, scope.leftAt);
    }

    @Override
    public int hashCode()
    {
        return Objects.hash(bindingId, joinedAt, leftAt);
    }
}
