package com.ruoyi.system.domain;

import com.ruoyi.common.annotation.Excel;
import com.ruoyi.common.annotation.Excel.ColumnType;

/** 用户管理新增用户模板。导出仍使用 SysUser。 */
public class SysUserImportRow
{
    @Excel(name = "账号", sort = 1, cellType = ColumnType.TEXT, prompt = "必填，至少 2 个字符")
    private String userName;
    @Excel(name = "昵称", sort = 2, prompt = "必填")
    private String nickName;
    @Excel(name = "部门", sort = 3, cellType = ColumnType.TEXT,
            prompt = "教师填写学院，学生填写班级；填写组织名称或编号，重名时填写编号")
    private String dept;
    @Excel(name = "手机号", sort = 4, cellType = ColumnType.TEXT)
    private String phonenumber;
    @Excel(name = "邮箱", sort = 5, width = 28)
    private String email;
    @Excel(name = "性别", sort = 6, combo = { "男", "女", "未知" })
    private String sex;
    @Excel(name = "状态", sort = 7, combo = { "正常", "停用" })
    private String status;
    @Excel(name = "初始密码", sort = 8, cellType = ColumnType.TEXT, prompt = "必填，5-20 个字符")
    private String password;
    @Excel(name = "角色", sort = 9, width = 24, prompt = "必填，每行填写一个角色名称或权限字符")
    private String roles;
    @Excel(name = "备注", sort = 10, width = 30)
    private String remark;

    public String getUserName() { return userName; }
    public void setUserName(String value) { userName = value; }
    public String getNickName() { return nickName; }
    public void setNickName(String value) { nickName = value; }
    public String getDept() { return dept; }
    public void setDept(String value) { dept = value; }
    public String getPhonenumber() { return phonenumber; }
    public void setPhonenumber(String value) { phonenumber = value; }
    public String getEmail() { return email; }
    public void setEmail(String value) { email = value; }
    public String getSex() { return sex; }
    public void setSex(String value) { sex = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getPassword() { return password; }
    public void setPassword(String value) { password = value; }
    public String getRoles() { return roles; }
    public void setRoles(String value) { roles = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
}
