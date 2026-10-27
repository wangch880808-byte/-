package com.ruoyi.education.dto.activity;

import java.util.List;
import jakarta.validation.constraints.NotEmpty;

/**
 * 学生作答请求。
 */
public class SubmitResponseRequest
{
    @NotEmpty(message = "answers 不能为空")
    private List<AnswerItem> answers;

    public List<AnswerItem> getAnswers()
    {
        return answers;
    }

    public void setAnswers(List<AnswerItem> answers)
    {
        this.answers = answers;
    }
}
