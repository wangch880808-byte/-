package com.ruoyi.education.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

public class EduTeachingClass extends BaseEntity {
    private Long id;
    private Long organizationId;
    private Long termId;
    private Long officialCourseId;
    private String code;
    private String name;
    private String classKind;
    private String status;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public Long getTermId() { return termId; }
    public void setTermId(Long termId) { this.termId = termId; }
    public Long getOfficialCourseId() { return officialCourseId; }
    public void setOfficialCourseId(Long officialCourseId) { this.officialCourseId = officialCourseId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getClassKind() { return classKind; }
    public void setClassKind(String classKind) { this.classKind = classKind; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
}
