package com.ruoyi.system.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;

/** 组织导入、导出使用相同列，以可见组织路径代替环境相关的数据库编号。 */
public class SysDeptTransferRow
{
    @Excel(name = "组织路径", sort = 1, width = 55, cellType = ColumnType.TEXT,
            prompt = "必填，例如：演示学校/人工智能学院/2026级1班。上级须已存在或在本文件中。")
    private String path;
    @Excel(name = "负责人", sort = 2, prompt = "最多20个字符")
    private String leader;
    @Excel(name = "显示顺序", sort = 3, prompt = "非负整数，留空为0")
    private String orderNum;
    @Excel(name = "组织状态", sort = 4, combo = { "开启", "停用" }, prompt = "留空为开启")
    private String status;
    @Excel(name = "联系电话", sort = 5, cellType = ColumnType.TEXT, width = 20,
            prompt = "最多11个字符，请按文本填写以保留开头的0")
    private String phone;
    @Excel(name = "邮箱", sort = 6, width = 30)
    private String email;
    @Excel(name = "组织类型", sort = 7, width = 16, combo = { "学校", "学院", "专业", "班级", "其他" },
            prompt = "新增班级请选班级；留空保留旧分类或按旧模板导入")
    private String deptType;

    @Excel(name = "所属年级", sort = 8, prompt = "仅班级填写，例如 2026 或 2026级")
    private String gradeYear;
    @Excel(name = "教育层次", sort = 9, combo = { "中专", "专科", "本科", "硕士", "博士", "其他" })
    private String educationLevel;
    @Excel(name = "学制", sort = 10, prompt = "仅班级填写，例如 4 或 4年")
    private String studyYears;
    @Excel(name = "备注", sort = 11, width = 30)
    private String classRemark;

    public String getPath() { return path; }
    public void setPath(String value) { path = value; }
    public String getLeader() { return leader; }
    public void setLeader(String value) { leader = value; }
    public String getOrderNum() { return orderNum; }
    public void setOrderNum(String value) { orderNum = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getPhone() { return phone; }
    public void setPhone(String value) { phone = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public String getDeptType() { return deptType; }
    public void setDeptType(String value) { deptType = value; }
    public String getGradeYear() { return gradeYear; }
    public void setGradeYear(String value) { gradeYear = value; }
    public String getEducationLevel() { return educationLevel; }
    public void setEducationLevel(String value) { educationLevel = value; }
    public String getStudyYears() { return studyYears; }
    public void setStudyYears(String value) { studyYears = value; }
    public String getClassRemark() { return classRemark; }
    public void setClassRemark(String value) { classRemark = value; }
}
