-- Group 1 menu and operation permission seed for the 6-02 announcement/notice module.
-- Run after group12-menu-permissions.sql. Repeatable; creates no tables and no business data.
-- Menu path anchors are what the frontend matches through getRouters:
--   announcements -> full path education/announcements, component education/announcements
--   notices       -> full path education/notices,       component education/notices

SET @edu_parent=(SELECT menu_id FROM sys_menu WHERE menu_type='M' AND path='education' LIMIT 1);
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '教学业务',0,6,'education',NULL,1,1,'M','0','0','','education','integration',NOW() WHERE @edu_parent IS NULL;
SET @edu_parent=(SELECT menu_id FROM sys_menu WHERE menu_type='M' AND path='education' LIMIT 1);

-- Teacher and student announcement entry. One shared menu keeps getRouters free of duplicate paths.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '班级公告',@edu_parent,6,'announcements','education/announcements',1,1,'C','0','0','education:announcement:list','education','integration',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE menu_type='C' AND component='education/announcements');
SET @announcement_menu=(SELECT menu_id FROM sys_menu WHERE menu_type='C' AND component='education/announcements' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@announcement_menu FROM sys_role r WHERE FIND_IN_SET(r.role_key,'teacher,student') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@announcement_menu);

-- Student notice entry.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,component,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '通知',@edu_parent,7,'notices','education/notices',1,1,'C','0','0','education:notice:list','education','integration',NOW()
 WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE menu_type='C' AND component='education/notices');
SET @notice_menu=(SELECT menu_id FROM sys_menu WHERE menu_type='C' AND component='education/notices' ORDER BY menu_id LIMIT 1);
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,@notice_menu FROM sys_role r WHERE FIND_IN_SET(r.role_key,'student') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=@notice_menu);

-- Teacher announcement write operations. Read uses education:announcement:list on the menu above.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '新增公告',@edu_parent,40,'#',1,1,'F','0','0','education:announcement:add','#','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:announcement:add');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:announcement:add' WHERE FIND_IN_SET(r.role_key,'teacher') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '编辑公告',@edu_parent,41,'#',1,1,'F','0','0','education:announcement:edit','#','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:announcement:edit');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:announcement:edit' WHERE FIND_IN_SET(r.role_key,'teacher') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '发布公告',@edu_parent,42,'#',1,1,'F','0','0','education:announcement:publish','#','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:announcement:publish');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:announcement:publish' WHERE FIND_IN_SET(r.role_key,'teacher') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '归档公告',@edu_parent,43,'#',1,1,'F','0','0','education:announcement:archive','#','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:announcement:archive');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:announcement:archive' WHERE FIND_IN_SET(r.role_key,'teacher') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);

-- Student notice read operation. List uses education:notice:list on the menu above.
INSERT INTO sys_menu(menu_name,parent_id,order_num,path,is_frame,is_cache,menu_type,visible,status,perms,icon,create_by,create_time)
 SELECT '阅读通知',@edu_parent,44,'#',1,1,'F','0','0','education:notice:read','#','integration',NOW() WHERE NOT EXISTS(SELECT 1 FROM sys_menu WHERE perms='education:notice:read');
INSERT INTO sys_role_menu(role_id,menu_id) SELECT r.role_id,m.menu_id FROM sys_role r JOIN sys_menu m ON m.perms='education:notice:read' WHERE FIND_IN_SET(r.role_key,'student') AND r.del_flag='0' AND NOT EXISTS(SELECT 1 FROM sys_role_menu rm WHERE rm.role_id=r.role_id AND rm.menu_id=m.menu_id);
