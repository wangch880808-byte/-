package com.ruoyi.education.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

public class EduClassStudent extends BaseEntity {
    private Long id;
    private Long teachingClassId;
    private Long userId;
    private Date joinedAt;
    private Date leftAt;
    private Long recordedByUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeachingClassId() { return teachingClassId; }
    public void setTeachingClassId(Long teachingClassId) { this.teachingClassId = teachingClassId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Date getJoinedAt() { return joinedAt; }
    public void setJoinedAt(Date joinedAt) { this.joinedAt = joinedAt; }
    public Date getLeftAt() { return leftAt; }
    public void setLeftAt(Date leftAt) { this.leftAt = leftAt; }
    public Long getRecordedByUserId() { return recordedByUserId; }
    public void setRecordedByUserId(Long recordedByUserId) { this.recordedByUserId = recordedByUserId; }
}
