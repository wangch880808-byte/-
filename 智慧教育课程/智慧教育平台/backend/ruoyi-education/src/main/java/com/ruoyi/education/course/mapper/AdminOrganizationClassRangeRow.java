package com.ruoyi.education.course.mapper;

/**
 * 管理员组织范围课程查询的数据库行。
 *
 * <p>仅用于 MyBatis 映射；对外返回前由服务转换为
 * {@link com.ruoyi.education.course.api.EduAdminOrganizationClassRange}。</p>
 */
public class AdminOrganizationClassRangeRow
{
    private Long organizationId;

    private Long teachingClassId;

    private String className;

    private String classCode;

    private String classStatus;

    private Long bindingId;

    private Long courseSpaceId;

    private String courseName;

    private Long studentCount;

    public Long getOrganizationId()
    {
        return organizationId;
    }

    public void setOrganizationId(Long organizationId)
    {
        this.organizationId = organizationId;
    }

    public Long getTeachingClassId()
    {
        return teachingClassId;
    }

    public void setTeachingClassId(Long teachingClassId)
    {
        this.teachingClassId = teachingClassId;
    }

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    public String getClassCode()
    {
        return classCode;
    }

    public void setClassCode(String classCode)
    {
        this.classCode = classCode;
    }

    public String getClassStatus()
    {
        return classStatus;
    }

    public void setClassStatus(String classStatus)
    {
        this.classStatus = classStatus;
    }

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
    }

    public Long getCourseSpaceId()
    {
        return courseSpaceId;
    }

    public void setCourseSpaceId(Long courseSpaceId)
    {
        this.courseSpaceId = courseSpaceId;
    }

    public String getCourseName()
    {
        return courseName;
    }

    public void setCourseName(String courseName)
    {
        this.courseName = courseName;
    }

    public Long getStudentCount()
    {
        return studentCount;
    }

    public void setStudentCount(Long studentCount)
    {
        this.studentCount = studentCount;
    }
}
