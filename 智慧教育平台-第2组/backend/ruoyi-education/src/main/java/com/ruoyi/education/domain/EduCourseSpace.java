package com.ruoyi.education.domain;

import com.ruoyi.common.core.domain.BaseEntity;

public class EduCourseSpace extends BaseEntity {
    private Long id;
    private Long organizationId;
    private Long officialCourseId;
    private Long ownerUserId;
    private String title;
    private String summary;
    private String learningObjectives;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getOfficialCourseId() { return officialCourseId; }
    public void setOfficialCourseId(Long officialCourseId) { this.officialCourseId = officialCourseId; }
    public Long getOwnerUserId() { return ownerUserId; }
    public void setOwnerUserId(Long ownerUserId) { this.ownerUserId = ownerUserId; }
    public String getTitle() { return title; }
    public void setTitle(String title) { this.title = title; }
    public String getSummary() { return summary; }
    public void setSummary(String summary) { this.summary = summary; }
    public String getLearningObjectives() { return learningObjectives; }
    public void setLearningObjectives(String learningObjectives) { this.learningObjectives = learningObjectives; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
