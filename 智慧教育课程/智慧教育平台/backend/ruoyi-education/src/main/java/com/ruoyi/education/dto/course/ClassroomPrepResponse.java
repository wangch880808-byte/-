package com.ruoyi.education.dto.course;

/**
 * 开课准备信息（临时接口，待第 2/3 组正式 API 替换）。
 */
public class ClassroomPrepResponse
{
    private Long bindingId;
    private Long releaseId;
    private Long resourceVersionId;
    private Integer totalPages;
    private Long lessonId;
    private Long planId;
    private Long defaultQuestionId;
    private Long defaultQuestionVersionId;
    private String resourceTitle;

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
    }

    public Long getReleaseId()
    {
        return releaseId;
    }

    public void setReleaseId(Long releaseId)
    {
        this.releaseId = releaseId;
    }

    public Long getResourceVersionId()
    {
        return resourceVersionId;
    }

    public void setResourceVersionId(Long resourceVersionId)
    {
        this.resourceVersionId = resourceVersionId;
    }

    public Integer getTotalPages()
    {
        return totalPages;
    }

    public void setTotalPages(Integer totalPages)
    {
        this.totalPages = totalPages;
    }

    public Long getLessonId()
    {
        return lessonId;
    }

    public void setLessonId(Long lessonId)
    {
        this.lessonId = lessonId;
    }

    public Long getPlanId()
    {
        return planId;
    }

    public void setPlanId(Long planId)
    {
        this.planId = planId;
    }

    public Long getDefaultQuestionId()
    {
        return defaultQuestionId;
    }

    public void setDefaultQuestionId(Long defaultQuestionId)
    {
        this.defaultQuestionId = defaultQuestionId;
    }

    public Long getDefaultQuestionVersionId()
    {
        return defaultQuestionVersionId;
    }

    public void setDefaultQuestionVersionId(Long defaultQuestionVersionId)
    {
        this.defaultQuestionVersionId = defaultQuestionVersionId;
    }

    public String getResourceTitle()
    {
        return resourceTitle;
    }

    public void setResourceTitle(String resourceTitle)
    {
        this.resourceTitle = resourceTitle;
    }
}
