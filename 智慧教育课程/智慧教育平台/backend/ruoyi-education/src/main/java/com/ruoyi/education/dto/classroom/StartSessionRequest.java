package com.ruoyi.education.dto.classroom;

import jakarta.validation.constraints.NotNull;

/**
 * 创建并开始课堂请求。
 */
public class StartSessionRequest
{
    @NotNull(message = "bindingId 不能为空")
    private Long bindingId;

    private Long lessonId;

    private Long planId;

    @NotNull(message = "releaseId 不能为空")
    private Long releaseId;

    @NotNull(message = "resourceVersionId 不能为空")
    private Long resourceVersionId;

    private Integer initialPageNo = 1;

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
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

    public Integer getInitialPageNo()
    {
        return initialPageNo;
    }

    public void setInitialPageNo(Integer initialPageNo)
    {
        this.initialPageNo = initialPageNo;
    }
}
