package com.ruoyi.education.dto.classroom;

/**
 * 学生本人参与状态（仅 view=student）。
 */
public class MyParticipationSummary
{
    private Boolean checkedIn;
    private Long lastResponseId;

    public Boolean getCheckedIn()
    {
        return checkedIn;
    }

    public void setCheckedIn(Boolean checkedIn)
    {
        this.checkedIn = checkedIn;
    }

    public Long getLastResponseId()
    {
        return lastResponseId;
    }

    public void setLastResponseId(Long lastResponseId)
    {
        this.lastResponseId = lastResponseId;
    }
}
