# 智慧教育平台：第一、二组对接版

基于当前第一组工程接入第二组业务，保留原界面，使用若依 Vue3 + Spring Boot 分离目录。唯一前端在 frontend/，唯一后端启动类为 backend/ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java。

- **运行项目：先阅读 [运行说明.md](运行说明.md)**，包含新空库安装／已有库升级、配置、构建、启动与演示账号。
- **本次对接范围与验证状态：** [docs/group12-integration.md](docs/group12-integration.md)。
- **需求来源：** 见仓库中的架构方案与需求文档。

| 目录 | 用途 |
|---|---|
| frontend | 官方若依 Vue3 工程；第一组外壳、系统页面与第二组教务／课程页面 |
| backend | 若依 Maven 多模块；ruoyi-admin 启动，ruoyi-education 承载业务 |
| sql | 新安装总脚本、已有库升级脚本、单项表结构与权限脚本 |
| scripts | 前后端构建脚本 build-group12.ps1 |
| docs | 对接说明、需求来源、既有设计与历史交接记录 |
