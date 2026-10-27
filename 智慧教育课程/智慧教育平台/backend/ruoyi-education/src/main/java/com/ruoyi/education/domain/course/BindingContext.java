package com.ruoyi.education.domain.course;

/**
 * 课程-教学班绑定上下文（查询 edu_course_class_binding 及关联信息）。
 */
public class BindingContext
{
    private Long bindingId;
    private Long teachingClassId;
    private Long courseSpaceId;
    private String courseTitle;
    private String className;

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
}
