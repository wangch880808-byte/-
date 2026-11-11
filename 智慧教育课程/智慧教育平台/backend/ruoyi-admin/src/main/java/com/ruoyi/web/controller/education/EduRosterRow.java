package com.ruoyi.web.controller.education;

import com.ruoyi.common.annotation.Excel;

public class EduRosterRow {
    @Excel(name = "学号/账号", cellType = Excel.ColumnType.TEXT, prompt = "填写现有学生的学号或账号，保留前导零")
    private String studentNo;

    @Excel(name = "姓名", type = Excel.Type.EXPORT)
    private String studentName;

    @Excel(name = "入班时间", type = Excel.Type.EXPORT)
    private String joinedAt;

    public EduRosterRow() {
    }

    public EduRosterRow(String studentNo, String studentName, String joinedAt) {
        this.studentNo = studentNo;
        this.studentName = studentName;
        this.joinedAt = joinedAt;
    }
}
