-- Repeatable group 1/group 2 route and operation permissions.
SET @edu_parent=(SELECT menu_id FROM sys_menu WHERE menu_type='M' AND path='education' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '教学业务',0,6,'education',NULL,1,1,'M','0','0','','education','integration',NOW() WHERE @edu_parent IS NULL;
SET @edu_parent=(SELECT menu_id FROM sys_menu WHERE menu_type='M' AND path='education' LIMIT 1);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '学期管理',@edu_parent,1,'term','education/admin/TermList',1,1,'C','0','0','education:term:list','education','integration',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='education/admin/TermList' AND menu_type='C');
SET @edu_menu=(SELECT menu_id FROM sys_menu WHERE component='education/admin/TermList' AND menu_type='C' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@edu_menu FROM sys_role r WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@edu_menu);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '班级管理',@edu_parent,2,'teaching-class','education/admin/ClassList',1,1,'C','0','0','education:class:list','education','integration',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='education/admin/ClassList' AND menu_type='C');
SET @edu_menu=(SELECT menu_id FROM sys_menu WHERE component='education/admin/ClassList' AND menu_type='C' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@edu_menu FROM sys_role r WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@edu_menu);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '教师管理',@edu_parent,3,'teacher','education/admin/TeacherList',1,1,'C','0','0','education:directory:list','education','integration',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='education/admin/TeacherList' AND menu_type='C');
SET @edu_menu=(SELECT menu_id FROM sys_menu WHERE component='education/admin/TeacherList' AND menu_type='C' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@edu_menu FROM sys_role r WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@edu_menu);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '学生管理',@edu_parent,4,'student','education/admin/StudentList',1,1,'C','0','0','education:directory:list','education','integration',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='education/admin/StudentList' AND menu_type='C');
SET @edu_menu=(SELECT menu_id FROM sys_menu WHERE component='education/admin/StudentList' AND menu_type='C' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@edu_menu FROM sys_role r WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@edu_menu);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '我的课程',@edu_parent,5,'course','education/CourseListPage',1,1,'C','0','0','education:course:list','education','integration',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE component='education/CourseListPage' AND menu_type='C');
SET @edu_menu=(SELECT menu_id FROM sys_menu WHERE component='education/CourseListPage' AND menu_type='C' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@edu_menu FROM sys_role r WHERE FIND_IN_SET(r.role_key,'teacher,student') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@edu_menu);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '新增学期',@edu_parent,20,'#',1,1,'F','0','0','education:term:add','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:term:add');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:term:add' WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '编辑学期',@edu_parent,21,'#',1,1,'F','0','0','education:term:edit','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:term:edit');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:term:edit' WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '当前学期',@edu_parent,22,'#',1,1,'F','0','0','education:term:current','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:term:current');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:term:current' WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '新增班级',@edu_parent,23,'#',1,1,'F','0','0','education:class:add','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:class:add');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:class:add' WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '编辑班级',@edu_parent,24,'#',1,1,'F','0','0','education:class:edit','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:class:edit');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:class:edit' WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '班级成员',@edu_parent,25,'#',1,1,'F','0','0','education:class:member','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:class:member');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:class:member' WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '任课授权',@edu_parent,26,'#',1,1,'F','0','0','education:class:teacher','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:class:teacher');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:class:teacher' WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '创建课程',@edu_parent,27,'#',1,1,'F','0','0','education:course:add','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:course:add');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:course:add' WHERE FIND_IN_SET(r.role_key,'teacher') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '编辑课程',@edu_parent,28,'#',1,1,'F','0','0','education:course:edit','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:course:edit');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:course:edit' WHERE FIND_IN_SET(r.role_key,'teacher') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '发布课程',@edu_parent,29,'#',1,1,'F','0','0','education:course:publish','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:course:publish');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:course:publish' WHERE FIND_IN_SET(r.role_key,'teacher') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '绑定班级',@edu_parent,30,'#',1,1,'F','0','0','education:course:bind','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:course:bind');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:course:bind' WHERE FIND_IN_SET(r.role_key,'teacher') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,create_by,create_time)
 SELECT '课程上下文',@edu_parent,31,'#',1,1,'F','0','0','education:scope:read','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:scope:read');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:scope:read' WHERE FIND_IN_SET(r.role_key,'teacher,student') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@edu_parent FROM sys_role r WHERE r.role_key IN ('admin','teacher','student') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@edu_parent);
