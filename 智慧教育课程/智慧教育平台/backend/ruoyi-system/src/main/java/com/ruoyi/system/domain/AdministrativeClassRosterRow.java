package com.ruoyi.system.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;

/** 名单导出行。导入按第一列学号匹配已有学生账号。 */
public class AdministrativeClassRosterRow
{
    @Excel(name = "学号", sort = 1, width = 24, cellType = ColumnType.TEXT)
    private final String studentNumber;
    @Excel(name = "姓名", sort = 2, width = 20)
    private final String name;
    @Excel(name = "手机号", sort = 3, width = 20, cellType = ColumnType.TEXT)
    private final String phone;
    @Excel(name = "归属组织", sort = 4, width = 32)
    private final String organization;

    public AdministrativeClassRosterRow(SysUser student)
    {
        this.studentNumber = student.getUserName();
        this.name = student.getNickName();
        this.phone = student.getPhonenumber();
        SysDept dept = student.getDept();
        this.organization = dept == null ? null : dept.getParentName();
    }
}
