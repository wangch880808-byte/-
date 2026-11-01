package com.ruoyi.education.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

public class EduClassTeacher extends BaseEntity {
    private Long id;
    private Long teachingClassId;
    private Long userId;
    private String teacherRole;
    private Boolean canPublish;
    private Boolean canGrade;
    private Boolean canExport;
    private Date validFrom;
    private Date validTo;
    private Long grantedByUserId;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getTeachingClassId() { return teachingClassId; }
    public void setTeachingClassId(Long teachingClassId) { this.teachingClassId = teachingClassId; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public String getTeacherRole() { return teacherRole; }
    public void setTeacherRole(String teacherRole) { this.teacherRole = teacherRole; }
    public Boolean getCanPublish() { return canPublish; }
    public void setCanPublish(Boolean canPublish) { this.canPublish = canPublish; }
    public Boolean getCanGrade() { return canGrade; }
    public void setCanGrade(Boolean canGrade) { this.canGrade = canGrade; }
    public Boolean getCanExport() { return canExport; }
    public void setCanExport(Boolean canExport) { this.canExport = canExport; }
    public Date getValidFrom() { return validFrom; }
    public void setValidFrom(Date validFrom) { this.validFrom = validFrom; }
    public Date getValidTo() { return validTo; }
    public void setValidTo(Date validTo) { this.validTo = validTo; }
    public Long getGrantedByUserId() { return grantedByUserId; }
    public void setGrantedByUserId(Long grantedByUserId) { this.grantedByUserId = grantedByUserId; }
}

