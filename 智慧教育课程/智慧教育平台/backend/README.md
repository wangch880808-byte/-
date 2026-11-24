# 后端工程

当前交付为第一、二、三组对接版，运行步骤以项目根目录的 [运行说明.md](../运行说明.md) 为准。

启动类：ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java，JDK 17，Spring Boot 3.5.16，若依 3.9.2。Maven reactor 共八个项目：父 POM、common、system、framework、education、quartz、generator、admin。

新增教务接口在 ruoyi-admin 的 com.ruoyi.web.controller.education，业务服务、数据映射和跨组只读契约在 ruoyi-education。

- /education/term：学期、当前学期。
- /education/teaching-class：教学班、学生入退班、任课教师授权。
- /education/teacher、student、organization、official-course：教务目录与关联查询。
- /education/course：教师建课、编辑、发布与班级绑定；学生查询本人课程。
- /education/course/{id}/outline：章节、知识点维护，必须具有操作权限且是课程负责人。
- /education/context：第一组公共课程／教学班上下文。
- /education/scope：为后续业务提供服务端有效班级、成员、绑定只读契约。
- /education/group3：第三组课程建设与课前学习入口，覆盖资料与文件夹、题库与题目资源库、章节与知识点详情、预习任务（含整卷发布）、讨论、学生进度看板与笔记、公共教学资源库；实现位于 `ruoyi-education` 的 `com.ruoyi.education.group3`。

复制 .env.example 为 .env 并填写私有配置，前端构建完成后从项目根执行 mvn -f backend/pom.xml -pl ruoyi-admin -am -DskipTests clean package，再执行 backend/run-local.ps1。脚本读取的是本目录 .env；IDEA 不会自动读取它。

已完成跳过测试的打包检查，并在本机 MySQL 8.0 + Redis 环境完成三组联调；第三组功能通过真实接口验证。原有公告／通知等其他组代码予以保留，其后续授权适配和业务页面不在本次三组对接验收范围内。
