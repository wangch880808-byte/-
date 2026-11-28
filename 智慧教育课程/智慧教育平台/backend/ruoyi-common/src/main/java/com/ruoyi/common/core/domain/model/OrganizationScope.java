package com.ruoyi.common.core.domain.model;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

/**
 * 当前登录账号在指定权限下可访问的组织范围。
 *
 * <p>{@code organizationIds} 是数据过滤的最终依据，对应 {@code sys_dept.dept_id}；
 * 业务表的 {@code organization_id} 直接引用该值。{@code schoolIds} 只用于学校选择或展示，
 * 不能只用它过滤，否则会放大 {@code DEPT}、{@code DEPT_AND_CHILD}、{@code SELF} 的范围。</p>
 */
public class OrganizationScope implements Serializable {
    private static final long serialVersionUID = 1L;

    /** 超管或全部数据权限。 */
    public static final String TYPE_ALL = "ALL";
    /** 自定义、部门、部门及以下、仅本人等多角色并集。 */
    public static final String TYPE_CUSTOM = "CUSTOM";
    /** 本部门数据权限。 */
    public static final String TYPE_DEPT = "DEPT";
    /** 本部门及以下数据权限。 */
    public static final String TYPE_DEPT_AND_CHILD = "DEPT_AND_CHILD";
    /** 仅本人数据权限。 */
    public static final String TYPE_SELF = "SELF";
    /** 没有任何可访问组织。 */
    public static final String TYPE_NONE = "NONE";

    /** 当前登录账号所属部门 ID。 */
    private Long deptId;

    /** 当前登录账号所属部门对应的学校根部门 ID，无法归属学校时为空。 */
    private Long schoolId;

    /** 可访问的学校根部门 ID，按学校排序；空列表表示没有可访问学校。 */
    private List<Long> schoolIds = new ArrayList<>();

    /** 可访问的组织部门 ID，业务过滤使用该集合。 */
    private List<Long> organizationIds = new ArrayList<>();

    /** 范围类型，取值见本类常量。 */
    private String scopeType;

    /** 是否全部学校；为 true 时调用方可以不加学校过滤。 */
    private boolean allSchools;

    /** 计算该范围时使用的权限串。 */
    private String permission;

    public Long getDeptId() {
        return deptId;
    }

    public void setDeptId(Long deptId) {
        this.deptId = deptId;
    }

    public Long getSchoolId() {
        return schoolId;
    }

    public void setSchoolId(Long schoolId) {
        this.schoolId = schoolId;
    }

    public List<Long> getSchoolIds() {
        return schoolIds;
    }

    public void setSchoolIds(List<Long> schoolIds) {
        this.schoolIds = schoolIds == null ? new ArrayList<>() : schoolIds;
    }

    public List<Long> getOrganizationIds() {
        return organizationIds;
    }

    public void setOrganizationIds(List<Long> organizationIds) {
        this.organizationIds = organizationIds == null ? new ArrayList<>() : organizationIds;
    }

    public String getScopeType() {
        return scopeType;
    }

    public void setScopeType(String scopeType) {
        this.scopeType = scopeType;
    }

    public boolean isAllSchools() {
        return allSchools;
    }

    public void setAllSchools(boolean allSchools) {
        this.allSchools = allSchools;
    }

    public String getPermission() {
        return permission;
    }

    public void setPermission(String permission) {
        this.permission = permission;
    }
}
