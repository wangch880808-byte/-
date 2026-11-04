package com.ruoyi.education.dto.course;

/**
 * 课程绑定摘要（临时 /mine 接口）。
 */
public class BindingSummaryResponse
{
    private Long bindingId;
    private Long teachingClassId;
    private Long courseSpaceId;
    private String courseTitle;
    private String className;
    private String role;

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
    }

    public Long getTeachingClassId()
    {
        return teachingClassId;
    }

    public void setTeachingClassId(Long teachingClassId)
    {
        this.teachingClassId = teachingClassId;
    }

    public Long getCourseSpaceId()
    {
        return courseSpaceId;
    }

    public void setCourseSpaceId(Long courseSpaceId)
    {
        this.courseSpaceId = courseSpaceId;
    }

    public String getCourseTitle()
    {
        return courseTitle;
    }

    public void setCourseTitle(String courseTitle)
    {
        this.courseTitle = courseTitle;
    }

    public String getClassName()
    {
        return className;
    }

    public void setClassName(String className)
    {
        this.className = className;
    }

    public String getRole()
    {
        return role;
    }

    public void setRole(String role)
    {
        this.role = role;
    }
}
