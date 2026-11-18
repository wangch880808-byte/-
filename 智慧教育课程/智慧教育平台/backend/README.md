# 后端工程

当前交付为第一、二组对接版，运行步骤以项目根目录的 [运行说明.md](../运行说明.md) 为准。

启动类：ruoyi-admin/src/main/java/com/ruoyi/RuoYiApplication.java，JDK 17，Spring Boot 3.5.16，若依 3.9.2。Maven reactor 共八个项目：父 POM、common、system、framework、education、quartz、generator、admin。

新增教务接口在 ruoyi-admin 的 com.ruoyi.web.controller.education，业务服务、数据映射和跨组只读契约在 ruoyi-education。

- /education/term：学期、当前学期。
- /education/teaching-class：教学班、学生入退班、任课教师授权。
- /education/teacher、student、organization、official-course：教务目录与关联查询。
- /education/course：教师建课、编辑、发布与班级绑定；学生查询本人课程。
- /education/course/{id}/outline：章节、知识点维护，必须具有操作权限且是课程负责人。
- /education/context：第一组公共课程／教学班上下文。
- /education/scope：为后续业务提供服务端有效班级、成员、绑定只读契约。

复制 .env.example 为 .env 并填写私有配置，前端构建完成后从项目根执行 mvn -f backend/pom.xml -pl ruoyi-admin -am -DskipTests clean package，再执行 backend/run-local.ps1。脚本读取的是本目录 .env；IDEA 不会自动读取它。

本轮已完成跳过测试的打包检查，没有启动后端或连接真实数据库。原有公告／通知等其他组代码予以保留，其后续授权适配和业务页面不在本次两组对接验收范围内。
