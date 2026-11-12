# 用户与角色 Excel 导入导出

官方若依 Vue3 的用户管理、角色管理均提供导入、导出和模板下载。导出使用当前筛选条件，导出全部匹配记录，不只当前一页。下载经过统一身份/权限处理，JSON 错误不会被保存成 Excel。

- 用户：沿用 /system/user/importData、importTemplate、export。导入权限 system:user:import，导出权限 system:user:export。界面只新增用户，不覆盖已存在账号；最多1000条、10MB，使用系统初始密码参数，新账号角色在编辑中分配。若依用户导入逐行处理，错误结果也可能存在已成功的行，页面会刷新列表并显示成功/失败数量。
- 角色：新增 /system/role/importData、importTemplate，导出沿用 /system/role/export。导入使用已有 system:role:add 权限，导出使用 system:role:export，无需修改权限表。仅新增基础字段，整批预校验并在事务中创建；角色 ID、菜单授权和数据范围不从文件导入，默认仅本人数据权限，菜单在编辑角色中配置。
- 支持 .xls / .xlsx，最多1000条、10MB。模板应从当前页面下载；角色导出文件的基础字段也可读取。上传成功返回逐行提示，失败文本按纯文本显示。

测试使用隔离响应和模拟服务，不向真实数据库导入测试账号或角色。
