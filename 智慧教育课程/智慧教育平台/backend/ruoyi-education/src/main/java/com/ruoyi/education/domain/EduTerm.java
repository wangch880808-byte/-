package com.ruoyi.education.domain;

import com.ruoyi.common.core.domain.BaseEntity;
import java.util.Date;

public class EduTerm extends BaseEntity {
    private Long id;
    private Long organizationId;
    private String code;
    private String name;
    private Date startsOn;
    private Date endsOn;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getOrganizationId() { return organizationId; }
    public void setOrganizationId(Long organizationId) { this.organizationId = organizationId; }
    public String getCode() { return code; }
    public void setCode(String code) { this.code = code; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public Date getStartsOn() { return startsOn; }
    public void setStartsOn(Date startsOn) { this.startsOn = startsOn; }
    public Date getEndsOn() { return endsOn; }
    public void setEndsOn(Date endsOn) { this.endsOn = endsOn; }
}
