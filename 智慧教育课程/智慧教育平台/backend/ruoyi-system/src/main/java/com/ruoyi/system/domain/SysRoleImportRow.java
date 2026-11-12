package com.ruoyi.system.domain;

import com.ruoyi.common.annotation.Excel;

/** 角色导入仅接收基础字段，不接收 ID、菜单授权或数据范围。 */
public class SysRoleImportRow
{
    @Excel(name = "角色名称") private String roleName;
    @Excel(name = "角色权限") private String roleKey;
    @Excel(name = "角色排序") private String roleSort;
    @Excel(name = "角色状态", prompt = "0或正常；1或停用") private String status;
    @Excel(name = "备注") private String remark;
    public String getRoleName() { return roleName; }
    public void setRoleName(String value) { roleName = value; }
    public String getRoleKey() { return roleKey; }
    public void setRoleKey(String value) { roleKey = value; }
    public String getRoleSort() { return roleSort; }
    public void setRoleSort(String value) { roleSort = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { status = value; }
    public String getRemark() { return remark; }
    public void setRemark(String value) { remark = value; }
}
