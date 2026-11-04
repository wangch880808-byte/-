package com.ruoyi.education.dto.activity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 创建活动实例请求。
 */
public class CreateActivityRequest
{
    private Long sessionId;

    @NotNull(message = "bindingId 不能为空")
    private Long bindingId;

    @NotBlank(message = "activityKind 不能为空")
    private String activityKind;

    @NotBlank(message = "title 不能为空")
    private String title;

    private Long questionId;

    private Long questionVersionId;

    @NotNull(message = "opensAt 不能为空")
    private String opensAt;

    @NotNull(message = "closesAt 不能为空")
    private String closesAt;

    public Long getSessionId()
    {
        return sessionId;
    }

    public void setSessionId(Long sessionId)
    {
        this.sessionId = sessionId;
    }

    public Long getBindingId()
    {
        return bindingId;
    }

    public void setBindingId(Long bindingId)
    {
        this.bindingId = bindingId;
    }

    public String getActivityKind()
    {
        return activityKind;
    }

    public void setActivityKind(String activityKind)
    {
        this.activityKind = activityKind;
    }

    public String getTitle()
    {
        return title;
    }

    public void setTitle(String title)
    {
        this.title = title;
    }

    public Long getQuestionId()
    {
        return questionId;
    }

    public void setQuestionId(Long questionId)
    {
        this.questionId = questionId;
    }

    public Long getQuestionVersionId()
    {
        return questionVersionId;
    }

    public void setQuestionVersionId(Long questionVersionId)
    {
        this.questionVersionId = questionVersionId;
    }

    public String getOpensAt()
    {
        return opensAt;
    }

    public void setOpensAt(String opensAt)
    {
        this.opensAt = opensAt;
    }

    public String getClosesAt()
    {
        return closesAt;
    }

    public void setClosesAt(String closesAt)
    {
        this.closesAt = closesAt;
    }
}
