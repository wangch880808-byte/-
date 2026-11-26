-- Group 1 overview menu and permission seed for the 6-01/6-03 overview pages.
-- Run after group12-menu-permissions.sql. Repeatable; creates no tables and no business data.
-- Route anchors:
--   teacher/overview      -> full path education/teacher/overview,
--                            component education/teacher/overview
--   student/today-todos   -> full path education/student/today-todos,
--                            component education/student/today-todos

SET @edu_parent=(SELECT menu_id FROM sys_menu WHERE menu_type='M' AND path='education' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '教学业务',0,6,'education',NULL,1,1,'M','0','0','','education','integration',NOW() WHERE @edu_parent IS NULL;
SET @edu_parent=(SELECT menu_id FROM sys_menu WHERE menu_type='M' AND path='education' LIMIT 1);

-- Teacher overview entry.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '教学总览',@edu_parent,8,'teacher/overview','education/teacher/overview',1,1,'C','0','0','education:overview:teacher','education','integration',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE menu_type='C' AND component='education/teacher/overview');
SET @teacher_overview=(SELECT menu_id FROM sys_menu WHERE menu_type='C' AND component='education/teacher/overview' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@teacher_overview FROM sys_role r WHERE FIND_IN_SET(r.role_key,'teacher') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@teacher_overview);

-- Student overview / today-todos entry.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '学习总览',@edu_parent,9,'student/today-todos','education/student/today-todos',1,1,'C','0','0','education:overview:student','education','integration',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE menu_type='C' AND component='education/student/today-todos');
SET @student_overview=(SELECT menu_id FROM sys_menu WHERE menu_type='C' AND component='education/student/today-todos' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@student_overview FROM sys_role r WHERE FIND_IN_SET(r.role_key,'student') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@student_overview);

-- Admin overview permission used by 6-01 /education/admin/overview and /education/admin/todos.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '教育总览',@edu_parent,45,'#',1,1,'F','0','0','education:overview:admin','#','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:overview:admin');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:overview:admin' WHERE FIND_IN_SET(r.role_key,'admin') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
