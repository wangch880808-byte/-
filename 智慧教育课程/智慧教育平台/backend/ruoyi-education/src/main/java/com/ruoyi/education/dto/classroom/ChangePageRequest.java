package com.ruoyi.education.dto.classroom;

import jakarta.validation.constraints.NotNull;

/**
 * 翻页请求。
 */
public class ChangePageRequest
{
    @NotNull(message = "pageNo 不能为空")
    private Integer pageNo;

    private Boolean studentCanBrowse = false;

    public Integer getPageNo()
    {
        return pageNo;
    }

    public void setPageNo(Integer pageNo)
    {
        this.pageNo = pageNo;
    }

    public Boolean getStudentCanBrowse()
    {
        return studentCanBrowse;
    }

    public void setStudentCanBrowse(Boolean studentCanBrowse)
    {
        this.studentCanBrowse = studentCanBrowse;
    }
}
