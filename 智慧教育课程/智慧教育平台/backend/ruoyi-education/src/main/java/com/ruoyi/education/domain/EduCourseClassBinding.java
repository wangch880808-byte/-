package com.ruoyi.education.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

public class EduCourseClassBinding extends BaseEntity {
    private Long id;
    private Long organizationId;
    private Long teachingClassId;
    private Long courseSpaceId;
    private Long boundByUserId;
    private Date publishedAt;
    private Date endedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getTeachingClassId() { return teachingClassId; }
    public void setTeachingClassId(Long teachingClassId) { this.teachingClassId = teachingClassId; }
    public Long getCourseSpaceId() { return courseSpaceId; }
    public void setCourseSpaceId(Long courseSpaceId) { this.courseSpaceId = courseSpaceId; }
    public Long getBoundByUserId() { return boundByUserId; }
    public void setBoundByUserId(Long boundByUserId) { this.boundByUserId = boundByUserId; }
    public Date getPublishedAt() { return publishedAt; }
    public void setPublishedAt(Date publishedAt) { this.publishedAt = publishedAt; }
    public Date getEndedAt() { return endedAt; }
    public void setEndedAt(Date endedAt) { this.endedAt = endedAt; }
}
