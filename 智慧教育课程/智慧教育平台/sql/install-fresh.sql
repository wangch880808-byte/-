-- FRESH EMPTY DATABASE ONLY: upstream system-table scripts contain DROP TABLE.
-- Select a new empty smart_edu_g12 database. Never use this on an existing database.
SET NAMES utf8mb4;
SET time_zone='+08:00';

-- SOURCE upstream/ruoyi/ry_20260417.sql
-- ----------------------------
-- 1、部门表
-- ----------------------------
drop table if exists sys_dept;
create table sys_dept (
  dept_id           bigint(20)      not null auto_increment    comment '部门id',
  parent_id         bigint(20)      default 0                  comment '父部门id',
  ancestors         varchar(50)     default ''                 comment '祖级列表',
  dept_name         varchar(30)     default ''                 comment '部门名称',
  order_num         int(4)          default 0                  comment '显示顺序',
  leader            varchar(20)     default null               comment '负责人',
  phone             varchar(11)     default null               comment '联系电话',
  email             varchar(50)     default null               comment '邮箱',
  status            char(1)         default '0'                comment '部门状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (dept_id)
) engine=innodb auto_increment=200 comment = '部门表';

-- ----------------------------
-- 初始化-部门表数据
-- ----------------------------
insert into sys_dept values(100,  0,   '0',          '若依科技',   0, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(101,  100, '0,100',      '深圳总公司', 1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(102,  100, '0,100',      '长沙分公司', 2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(103,  101, '0,100,101',  '研发部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(104,  101, '0,100,101',  '市场部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(105,  101, '0,100,101',  '测试部门',   3, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(106,  101, '0,100,101',  '财务部门',   4, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(107,  101, '0,100,101',  '运维部门',   5, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(108,  102, '0,100,102',  '市场部门',   1, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);
insert into sys_dept values(109,  102, '0,100,102',  '财务部门',   2, '若依', '15888888888', 'ry@qq.com', '0', '0', 'admin', sysdate(), '', null);


-- ----------------------------
-- 2、用户信息表
-- ----------------------------
drop table if exists sys_user;
create table sys_user (
  user_id           bigint(20)      not null auto_increment    comment '用户ID',
  dept_id           bigint(20)      default null               comment '部门ID',
  user_name         varchar(30)     not null                   comment '用户账号',
  nick_name         varchar(30)     not null                   comment '用户昵称',
  user_type         varchar(2)      default '00'               comment '用户类型（00系统用户）',
  email             varchar(50)     default ''                 comment '用户邮箱',
  phonenumber       varchar(11)     default ''                 comment '手机号码',
  sex               char(1)         default '0'                comment '用户性别（0男 1女 2未知）',
  avatar            varchar(100)    default ''                 comment '头像地址',
  password          varchar(100)    default ''                 comment '密码',
  status            char(1)         default '0'                comment '账号状态（0正常 1停用）',
  del_flag          char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  login_ip          varchar(128)    default ''                 comment '最后登录IP',
  login_date        datetime                                   comment '最后登录时间',
  pwd_update_date   datetime                                   comment '密码最后更新时间',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (user_id)
) engine=innodb auto_increment=100 comment = '用户信息表';

-- ----------------------------
-- 初始化-用户信息表数据
-- ----------------------------
insert into sys_user values(1,  103, 'admin', '若依', '00', 'ry@163.com', '15888888888', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '管理员');
insert into sys_user values(2,  105, 'ry',    '若依', '00', 'ry@qq.com',  '15666666666', '1', '', '$2a$10$7JB720yubVSZvUI0rEqK/.VqGOZTH.ulu33dHOiBE8ByOhJIrdAu2', '0', '0', '127.0.0.1', sysdate(), sysdate(), 'admin', sysdate(), '', null, '测试员');


-- ----------------------------
-- 3、岗位信息表
-- ----------------------------
drop table if exists sys_post;
create table sys_post
(
  post_id       bigint(20)      not null auto_increment    comment '岗位ID',
  post_code     varchar(64)     not null                   comment '岗位编码',
  post_name     varchar(50)     not null                   comment '岗位名称',
  post_sort     int(4)          not null                   comment '显示顺序',
  status        char(1)         not null                   comment '状态（0正常 1停用）',
  create_by     varchar(64)     default ''                 comment '创建者',
  create_time   datetime                                   comment '创建时间',
  update_by     varchar(64)     default ''			       comment '更新者',
  update_time   datetime                                   comment '更新时间',
  remark        varchar(500)    default null               comment '备注',
  primary key (post_id)
) engine=innodb comment = '岗位信息表';

-- ----------------------------
-- 初始化-岗位信息表数据
-- ----------------------------
insert into sys_post values(1, 'ceo',  '董事长',    1, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(2, 'se',   '项目经理',  2, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(3, 'hr',   '人力资源',  3, '0', 'admin', sysdate(), '', null, '');
insert into sys_post values(4, 'user', '普通员工',  4, '0', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 4、角色信息表
-- ----------------------------
drop table if exists sys_role;
create table sys_role (
  role_id              bigint(20)      not null auto_increment    comment '角色ID',
  role_name            varchar(30)     not null                   comment '角色名称',
  role_key             varchar(100)    not null                   comment '角色权限字符串',
  role_sort            int(4)          not null                   comment '显示顺序',
  data_scope           char(1)         default '1'                comment '数据范围（1：全部数据权限 2：自定数据权限 3：本部门数据权限 4：本部门及以下数据权限）',
  menu_check_strictly  tinyint(1)      default 1                  comment '菜单树选择项是否关联显示',
  dept_check_strictly  tinyint(1)      default 1                  comment '部门树选择项是否关联显示',
  status               char(1)         not null                   comment '角色状态（0正常 1停用）',
  del_flag             char(1)         default '0'                comment '删除标志（0代表存在 2代表删除）',
  create_by            varchar(64)     default ''                 comment '创建者',
  create_time          datetime                                   comment '创建时间',
  update_by            varchar(64)     default ''                 comment '更新者',
  update_time          datetime                                   comment '更新时间',
  remark               varchar(500)    default null               comment '备注',
  primary key (role_id)
) engine=innodb auto_increment=100 comment = '角色信息表';

-- ----------------------------
-- 初始化-角色信息表数据
-- ----------------------------
insert into sys_role values('1', '超级管理员',  'admin',  1, 1, 1, 1, '0', '0', 'admin', sysdate(), '', null, '超级管理员');
insert into sys_role values('2', '普通角色',    'common', 2, 2, 1, 1, '0', '0', 'admin', sysdate(), '', null, '普通角色');


-- ----------------------------
-- 5、菜单权限表
-- ----------------------------
drop table if exists sys_menu;
create table sys_menu (
  menu_id           bigint(20)      not null auto_increment    comment '菜单ID',
  menu_name         varchar(50)     not null                   comment '菜单名称',
  parent_id         bigint(20)      default 0                  comment '父菜单ID',
  order_num         int(4)          default 0                  comment '显示顺序',
  path              varchar(200)    default ''                 comment '路由地址',
  component         varchar(255)    default null               comment '组件路径',
  query             varchar(255)    default null               comment '路由参数',
  route_name        varchar(50)     default ''                 comment '路由名称',
  is_frame          int(1)          default 1                  comment '是否为外链（0是 1否）',
  is_cache          int(1)          default 0                  comment '是否缓存（0缓存 1不缓存）',
  menu_type         char(1)         default ''                 comment '菜单类型（M目录 C菜单 F按钮）',
  visible           char(1)         default 0                  comment '菜单状态（0显示 1隐藏）',
  status            char(1)         default 0                  comment '菜单状态（0正常 1停用）',
  perms             varchar(100)    default null               comment '权限标识',
  icon              varchar(100)    default '#'                comment '菜单图标',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default ''                 comment '备注',
  primary key (menu_id)
) engine=innodb auto_increment=2000 comment = '菜单权限表';

-- ----------------------------
-- 初始化-菜单信息表数据
-- ----------------------------
-- 一级菜单
insert into sys_menu values('1', '系统管理', '0', '1', 'system',           null, '', '', 1, 0, 'M', '0', '0', '', 'system',   'admin', sysdate(), '', null, '系统管理目录');
insert into sys_menu values('2', '系统监控', '0', '2', 'monitor',          null, '', '', 1, 0, 'M', '0', '0', '', 'monitor',  'admin', sysdate(), '', null, '系统监控目录');
insert into sys_menu values('3', '系统工具', '0', '3', 'tool',             null, '', '', 1, 0, 'M', '0', '0', '', 'tool',     'admin', sysdate(), '', null, '系统工具目录');
insert into sys_menu values('4', '若依官网', '0', '4', 'http://ruoyi.vip', null, '', '', 0, 0, 'M', '0', '0', '', 'guide',    'admin', sysdate(), '', null, '若依官网地址');
-- 二级菜单
insert into sys_menu values('100',  '用户管理', '1',   '1', 'user',       'system/user/index',        '', '', 1, 0, 'C', '0', '0', 'system:user:list',        'user',          'admin', sysdate(), '', null, '用户管理菜单');
insert into sys_menu values('101',  '角色管理', '1',   '2', 'role',       'system/role/index',        '', '', 1, 0, 'C', '0', '0', 'system:role:list',        'peoples',       'admin', sysdate(), '', null, '角色管理菜单');
insert into sys_menu values('102',  '菜单管理', '1',   '3', 'menu',       'system/menu/index',        '', '', 1, 0, 'C', '0', '0', 'system:menu:list',        'tree-table',    'admin', sysdate(), '', null, '菜单管理菜单');
insert into sys_menu values('103',  '部门管理', '1',   '4', 'dept',       'system/dept/index',        '', '', 1, 0, 'C', '0', '0', 'system:dept:list',        'tree',          'admin', sysdate(), '', null, '部门管理菜单');
insert into sys_menu values('104',  '岗位管理', '1',   '5', 'post',       'system/post/index',        '', '', 1, 0, 'C', '0', '0', 'system:post:list',        'post',          'admin', sysdate(), '', null, '岗位管理菜单');
insert into sys_menu values('105',  '字典管理', '1',   '6', 'dict',       'system/dict/index',        '', '', 1, 0, 'C', '0', '0', 'system:dict:list',        'dict',          'admin', sysdate(), '', null, '字典管理菜单');
insert into sys_menu values('106',  '参数设置', '1',   '7', 'config',     'system/config/index',      '', '', 1, 0, 'C', '0', '0', 'system:config:list',      'edit',          'admin', sysdate(), '', null, '参数设置菜单');
insert into sys_menu values('107',  '通知公告', '1',   '8', 'notice',     'system/notice/index',      '', '', 1, 0, 'C', '0', '0', 'system:notice:list',      'message',       'admin', sysdate(), '', null, '通知公告菜单');
insert into sys_menu values('108',  '日志管理', '1',   '9', 'log',        '',                         '', '', 1, 0, 'M', '0', '0', '',                        'log',           'admin', sysdate(), '', null, '日志管理菜单');
insert into sys_menu values('109',  '在线用户', '2',   '1', 'online',     'monitor/online/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:online:list',     'online',        'admin', sysdate(), '', null, '在线用户菜单');
insert into sys_menu values('110',  '定时任务', '2',   '2', 'job',        'monitor/job/index',        '', '', 1, 0, 'C', '0', '0', 'monitor:job:list',        'job',           'admin', sysdate(), '', null, '定时任务菜单');
insert into sys_menu values('111',  '数据监控', '2',   '3', 'druid',      'monitor/druid/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:druid:list',      'druid',         'admin', sysdate(), '', null, '数据监控菜单');
insert into sys_menu values('112',  '服务监控', '2',   '4', 'server',     'monitor/server/index',     '', '', 1, 0, 'C', '0', '0', 'monitor:server:list',     'server',        'admin', sysdate(), '', null, '服务监控菜单');
insert into sys_menu values('113',  '缓存监控', '2',   '5', 'cache',      'monitor/cache/index',      '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis',         'admin', sysdate(), '', null, '缓存监控菜单');
insert into sys_menu values('114',  '缓存列表', '2',   '6', 'cacheList',  'monitor/cache/list',       '', '', 1, 0, 'C', '0', '0', 'monitor:cache:list',      'redis-list',    'admin', sysdate(), '', null, '缓存列表菜单');
insert into sys_menu values('115',  '表单构建', '3',   '1', 'build',      'tool/build/index',         '', '', 1, 0, 'C', '0', '0', 'tool:build:list',         'build',         'admin', sysdate(), '', null, '表单构建菜单');
insert into sys_menu values('116',  '代码生成', '3',   '2', 'gen',        'tool/gen/index',           '', '', 1, 0, 'C', '0', '0', 'tool:gen:list',           'code',          'admin', sysdate(), '', null, '代码生成菜单');
insert into sys_menu values('117',  '系统接口', '3',   '3', 'swagger',    'tool/swagger/index',       '', '', 1, 0, 'C', '0', '0', 'tool:swagger:list',       'swagger',       'admin', sysdate(), '', null, '系统接口菜单');
-- 三级菜单
insert into sys_menu values('500',  '操作日志', '108', '1', 'operlog',    'monitor/operlog/index',    '', '', 1, 0, 'C', '0', '0', 'monitor:operlog:list',    'form',          'admin', sysdate(), '', null, '操作日志菜单');
insert into sys_menu values('501',  '登录日志', '108', '2', 'logininfor', 'monitor/logininfor/index', '', '', 1, 0, 'C', '0', '0', 'monitor:logininfor:list', 'logininfor',    'admin', sysdate(), '', null, '登录日志菜单');
-- 用户管理按钮
insert into sys_menu values('1000', '用户查询', '100', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1001', '用户新增', '100', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1002', '用户修改', '100', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1003', '用户删除', '100', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1004', '用户导出', '100', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:export',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1005', '用户导入', '100', '6',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:import',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1006', '重置密码', '100', '7',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:user:resetPwd',       '#', 'admin', sysdate(), '', null, '');
-- 角色管理按钮
insert into sys_menu values('1007', '角色查询', '101', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1008', '角色新增', '101', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1009', '角色修改', '101', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1010', '角色删除', '101', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1011', '角色导出', '101', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:role:export',         '#', 'admin', sysdate(), '', null, '');
-- 菜单管理按钮
insert into sys_menu values('1012', '菜单查询', '102', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1013', '菜单新增', '102', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1014', '菜单修改', '102', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1015', '菜单删除', '102', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:menu:remove',         '#', 'admin', sysdate(), '', null, '');
-- 部门管理按钮
insert into sys_menu values('1016', '部门查询', '103', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1017', '部门新增', '103', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1018', '部门修改', '103', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1019', '部门删除', '103', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:dept:remove',         '#', 'admin', sysdate(), '', null, '');
-- 岗位管理按钮
insert into sys_menu values('1020', '岗位查询', '104', '1',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1021', '岗位新增', '104', '2',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1022', '岗位修改', '104', '3',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1023', '岗位删除', '104', '4',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1024', '岗位导出', '104', '5',  '', '', '', '', 1, 0, 'F', '0', '0', 'system:post:export',         '#', 'admin', sysdate(), '', null, '');
-- 字典管理按钮
insert into sys_menu values('1025', '字典查询', '105', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1026', '字典新增', '105', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1027', '字典修改', '105', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1028', '字典删除', '105', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1029', '字典导出', '105', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:dict:export',         '#', 'admin', sysdate(), '', null, '');
-- 参数设置按钮
insert into sys_menu values('1030', '参数查询', '106', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1031', '参数新增', '106', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1032', '参数修改', '106', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1033', '参数删除', '106', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:remove',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1034', '参数导出', '106', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:config:export',       '#', 'admin', sysdate(), '', null, '');
-- 通知公告按钮
insert into sys_menu values('1035', '公告查询', '107', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:query',        '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1036', '公告新增', '107', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:add',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1037', '公告修改', '107', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:edit',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1038', '公告删除', '107', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'system:notice:remove',       '#', 'admin', sysdate(), '', null, '');
-- 操作日志按钮
insert into sys_menu values('1039', '操作查询', '500', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:query',      '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1040', '操作删除', '500', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:remove',     '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1041', '日志导出', '500', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:operlog:export',     '#', 'admin', sysdate(), '', null, '');
-- 登录日志按钮
insert into sys_menu values('1042', '登录查询', '501', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:query',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1043', '登录删除', '501', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:remove',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1044', '日志导出', '501', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:export',  '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1045', '账户解锁', '501', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:logininfor:unlock',  '#', 'admin', sysdate(), '', null, '');
-- 在线用户按钮
insert into sys_menu values('1046', '在线查询', '109', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:query',       '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1047', '批量强退', '109', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:batchLogout', '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1048', '单条强退', '109', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:online:forceLogout', '#', 'admin', sysdate(), '', null, '');
-- 定时任务按钮
insert into sys_menu values('1049', '任务查询', '110', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:query',          '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1050', '任务新增', '110', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:add',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1051', '任务修改', '110', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:edit',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1052', '任务删除', '110', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:remove',         '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1053', '状态修改', '110', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:changeStatus',   '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1054', '任务导出', '110', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'monitor:job:export',         '#', 'admin', sysdate(), '', null, '');
-- 代码生成按钮
insert into sys_menu values('1055', '生成查询', '116', '1', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:query',             '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1056', '生成修改', '116', '2', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:edit',              '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1057', '生成删除', '116', '3', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:remove',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1058', '导入代码', '116', '4', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:import',            '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1059', '预览代码', '116', '5', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:preview',           '#', 'admin', sysdate(), '', null, '');
insert into sys_menu values('1060', '生成代码', '116', '6', '#', '', '', '', 1, 0, 'F', '0', '0', 'tool:gen:code',              '#', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 6、用户和角色关联表  用户N-1角色
-- ----------------------------
drop table if exists sys_user_role;
create table sys_user_role (
  user_id   bigint(20) not null comment '用户ID',
  role_id   bigint(20) not null comment '角色ID',
  primary key(user_id, role_id)
) engine=innodb comment = '用户和角色关联表';

-- ----------------------------
-- 初始化-用户和角色关联表数据
-- ----------------------------
insert into sys_user_role values ('1', '1');
insert into sys_user_role values ('2', '2');


-- ----------------------------
-- 7、角色和菜单关联表  角色1-N菜单
-- ----------------------------
drop table if exists sys_role_menu;
create table sys_role_menu (
  role_id   bigint(20) not null comment '角色ID',
  menu_id   bigint(20) not null comment '菜单ID',
  primary key(role_id, menu_id)
) engine=innodb comment = '角色和菜单关联表';

-- ----------------------------
-- 初始化-角色和菜单关联表数据
-- ----------------------------
insert into sys_role_menu values ('2', '1');
insert into sys_role_menu values ('2', '2');
insert into sys_role_menu values ('2', '3');
insert into sys_role_menu values ('2', '4');
insert into sys_role_menu values ('2', '100');
insert into sys_role_menu values ('2', '101');
insert into sys_role_menu values ('2', '102');
insert into sys_role_menu values ('2', '103');
insert into sys_role_menu values ('2', '104');
insert into sys_role_menu values ('2', '105');
insert into sys_role_menu values ('2', '106');
insert into sys_role_menu values ('2', '107');
insert into sys_role_menu values ('2', '108');
insert into sys_role_menu values ('2', '109');
insert into sys_role_menu values ('2', '110');
insert into sys_role_menu values ('2', '111');
insert into sys_role_menu values ('2', '112');
insert into sys_role_menu values ('2', '113');
insert into sys_role_menu values ('2', '114');
insert into sys_role_menu values ('2', '115');
insert into sys_role_menu values ('2', '116');
insert into sys_role_menu values ('2', '117');
insert into sys_role_menu values ('2', '500');
insert into sys_role_menu values ('2', '501');
insert into sys_role_menu values ('2', '1000');
insert into sys_role_menu values ('2', '1001');
insert into sys_role_menu values ('2', '1002');
insert into sys_role_menu values ('2', '1003');
insert into sys_role_menu values ('2', '1004');
insert into sys_role_menu values ('2', '1005');
insert into sys_role_menu values ('2', '1006');
insert into sys_role_menu values ('2', '1007');
insert into sys_role_menu values ('2', '1008');
insert into sys_role_menu values ('2', '1009');
insert into sys_role_menu values ('2', '1010');
insert into sys_role_menu values ('2', '1011');
insert into sys_role_menu values ('2', '1012');
insert into sys_role_menu values ('2', '1013');
insert into sys_role_menu values ('2', '1014');
insert into sys_role_menu values ('2', '1015');
insert into sys_role_menu values ('2', '1016');
insert into sys_role_menu values ('2', '1017');
insert into sys_role_menu values ('2', '1018');
insert into sys_role_menu values ('2', '1019');
insert into sys_role_menu values ('2', '1020');
insert into sys_role_menu values ('2', '1021');
insert into sys_role_menu values ('2', '1022');
insert into sys_role_menu values ('2', '1023');
insert into sys_role_menu values ('2', '1024');
insert into sys_role_menu values ('2', '1025');
insert into sys_role_menu values ('2', '1026');
insert into sys_role_menu values ('2', '1027');
insert into sys_role_menu values ('2', '1028');
insert into sys_role_menu values ('2', '1029');
insert into sys_role_menu values ('2', '1030');
insert into sys_role_menu values ('2', '1031');
insert into sys_role_menu values ('2', '1032');
insert into sys_role_menu values ('2', '1033');
insert into sys_role_menu values ('2', '1034');
insert into sys_role_menu values ('2', '1035');
insert into sys_role_menu values ('2', '1036');
insert into sys_role_menu values ('2', '1037');
insert into sys_role_menu values ('2', '1038');
insert into sys_role_menu values ('2', '1039');
insert into sys_role_menu values ('2', '1040');
insert into sys_role_menu values ('2', '1041');
insert into sys_role_menu values ('2', '1042');
insert into sys_role_menu values ('2', '1043');
insert into sys_role_menu values ('2', '1044');
insert into sys_role_menu values ('2', '1045');
insert into sys_role_menu values ('2', '1046');
insert into sys_role_menu values ('2', '1047');
insert into sys_role_menu values ('2', '1048');
insert into sys_role_menu values ('2', '1049');
insert into sys_role_menu values ('2', '1050');
insert into sys_role_menu values ('2', '1051');
insert into sys_role_menu values ('2', '1052');
insert into sys_role_menu values ('2', '1053');
insert into sys_role_menu values ('2', '1054');
insert into sys_role_menu values ('2', '1055');
insert into sys_role_menu values ('2', '1056');
insert into sys_role_menu values ('2', '1057');
insert into sys_role_menu values ('2', '1058');
insert into sys_role_menu values ('2', '1059');
insert into sys_role_menu values ('2', '1060');

-- ----------------------------
-- 8、角色和部门关联表  角色1-N部门
-- ----------------------------
drop table if exists sys_role_dept;
create table sys_role_dept (
  role_id   bigint(20) not null comment '角色ID',
  dept_id   bigint(20) not null comment '部门ID',
  primary key(role_id, dept_id)
) engine=innodb comment = '角色和部门关联表';

-- ----------------------------
-- 初始化-角色和部门关联表数据
-- ----------------------------
insert into sys_role_dept values ('2', '100');
insert into sys_role_dept values ('2', '101');
insert into sys_role_dept values ('2', '105');


-- ----------------------------
-- 9、用户与岗位关联表  用户1-N岗位
-- ----------------------------
drop table if exists sys_user_post;
create table sys_user_post
(
  user_id   bigint(20) not null comment '用户ID',
  post_id   bigint(20) not null comment '岗位ID',
  primary key (user_id, post_id)
) engine=innodb comment = '用户与岗位关联表';

-- ----------------------------
-- 初始化-用户与岗位关联表数据
-- ----------------------------
insert into sys_user_post values ('1', '1');
insert into sys_user_post values ('2', '2');


-- ----------------------------
-- 10、操作日志记录
-- ----------------------------
drop table if exists sys_oper_log;
create table sys_oper_log (
  oper_id           bigint(20)      not null auto_increment    comment '日志主键',
  title             varchar(50)     default ''                 comment '模块标题',
  business_type     int(2)          default 0                  comment '业务类型（0其它 1新增 2修改 3删除）',
  method            varchar(200)    default ''                 comment '方法名称',
  request_method    varchar(10)     default ''                 comment '请求方式',
  operator_type     int(1)          default 0                  comment '操作类别（0其它 1后台用户 2手机端用户）',
  oper_name         varchar(50)     default ''                 comment '操作人员',
  dept_name         varchar(50)     default ''                 comment '部门名称',
  oper_url          varchar(255)    default ''                 comment '请求URL',
  oper_ip           varchar(128)    default ''                 comment '主机地址',
  oper_location     varchar(255)    default ''                 comment '操作地点',
  oper_param        varchar(2000)   default ''                 comment '请求参数',
  json_result       varchar(2000)   default ''                 comment '返回参数',
  status            int(1)          default 0                  comment '操作状态（0正常 1异常）',
  error_msg         varchar(2000)   default ''                 comment '错误消息',
  oper_time         datetime                                   comment '操作时间',
  cost_time         bigint(20)      default 0                  comment '消耗时间',
  primary key (oper_id),
  key idx_sys_oper_log_bt (business_type),
  key idx_sys_oper_log_s  (status),
  key idx_sys_oper_log_ot (oper_time)
) engine=innodb auto_increment=100 comment = '操作日志记录';


-- ----------------------------
-- 11、字典类型表
-- ----------------------------
drop table if exists sys_dict_type;
create table sys_dict_type
(
  dict_id          bigint(20)      not null auto_increment    comment '字典主键',
  dict_name        varchar(100)    default ''                 comment '字典名称',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_id),
  unique (dict_type)
) engine=innodb auto_increment=100 comment = '字典类型表';

insert into sys_dict_type values(1,  '用户性别', 'sys_user_sex',        '0', 'admin', sysdate(), '', null, '用户性别列表');
insert into sys_dict_type values(2,  '菜单状态', 'sys_show_hide',       '0', 'admin', sysdate(), '', null, '菜单状态列表');
insert into sys_dict_type values(3,  '系统开关', 'sys_normal_disable',  '0', 'admin', sysdate(), '', null, '系统开关列表');
insert into sys_dict_type values(4,  '任务状态', 'sys_job_status',      '0', 'admin', sysdate(), '', null, '任务状态列表');
insert into sys_dict_type values(5,  '任务分组', 'sys_job_group',       '0', 'admin', sysdate(), '', null, '任务分组列表');
insert into sys_dict_type values(6,  '系统是否', 'sys_yes_no',          '0', 'admin', sysdate(), '', null, '系统是否列表');
insert into sys_dict_type values(7,  '通知类型', 'sys_notice_type',     '0', 'admin', sysdate(), '', null, '通知类型列表');
insert into sys_dict_type values(8,  '通知状态', 'sys_notice_status',   '0', 'admin', sysdate(), '', null, '通知状态列表');
insert into sys_dict_type values(9,  '操作类型', 'sys_oper_type',       '0', 'admin', sysdate(), '', null, '操作类型列表');
insert into sys_dict_type values(10, '系统状态', 'sys_common_status',   '0', 'admin', sysdate(), '', null, '登录状态列表');


-- ----------------------------
-- 12、字典数据表
-- ----------------------------
drop table if exists sys_dict_data;
create table sys_dict_data
(
  dict_code        bigint(20)      not null auto_increment    comment '字典编码',
  dict_sort        int(4)          default 0                  comment '字典排序',
  dict_label       varchar(100)    default ''                 comment '字典标签',
  dict_value       varchar(100)    default ''                 comment '字典键值',
  dict_type        varchar(100)    default ''                 comment '字典类型',
  css_class        varchar(100)    default null               comment '样式属性（其他样式扩展）',
  list_class       varchar(100)    default null               comment '表格回显样式',
  is_default       char(1)         default 'N'                comment '是否默认（Y是 N否）',
  status           char(1)         default '0'                comment '状态（0正常 1停用）',
  create_by        varchar(64)     default ''                 comment '创建者',
  create_time      datetime                                   comment '创建时间',
  update_by        varchar(64)     default ''                 comment '更新者',
  update_time      datetime                                   comment '更新时间',
  remark           varchar(500)    default null               comment '备注',
  primary key (dict_code)
) engine=innodb auto_increment=100 comment = '字典数据表';

insert into sys_dict_data values(1,  1,  '男',       '0',       'sys_user_sex',        '',   '',        'Y', '0', 'admin', sysdate(), '', null, '性别男');
insert into sys_dict_data values(2,  2,  '女',       '1',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别女');
insert into sys_dict_data values(3,  3,  '未知',     '2',       'sys_user_sex',        '',   '',        'N', '0', 'admin', sysdate(), '', null, '性别未知');
insert into sys_dict_data values(4,  1,  '显示',     '0',       'sys_show_hide',       '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '显示菜单');
insert into sys_dict_data values(5,  2,  '隐藏',     '1',       'sys_show_hide',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '隐藏菜单');
insert into sys_dict_data values(6,  1,  '正常',     '0',       'sys_normal_disable',  '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(7,  2,  '停用',     '1',       'sys_normal_disable',  '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(8,  1,  '正常',     '0',       'sys_job_status',      '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(9,  2,  '暂停',     '1',       'sys_job_status',      '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');
insert into sys_dict_data values(10, 1,  '默认',     'DEFAULT', 'sys_job_group',       '',   '',        'Y', '0', 'admin', sysdate(), '', null, '默认分组');
insert into sys_dict_data values(11, 2,  '系统',     'SYSTEM',  'sys_job_group',       '',   '',        'N', '0', 'admin', sysdate(), '', null, '系统分组');
insert into sys_dict_data values(12, 1,  '是',       'Y',       'sys_yes_no',          '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '系统默认是');
insert into sys_dict_data values(13, 2,  '否',       'N',       'sys_yes_no',          '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '系统默认否');
insert into sys_dict_data values(14, 1,  '通知',     '1',       'sys_notice_type',     '',   'warning', 'Y', '0', 'admin', sysdate(), '', null, '通知');
insert into sys_dict_data values(15, 2,  '公告',     '2',       'sys_notice_type',     '',   'success', 'N', '0', 'admin', sysdate(), '', null, '公告');
insert into sys_dict_data values(16, 1,  '正常',     '0',       'sys_notice_status',   '',   'primary', 'Y', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(17, 2,  '关闭',     '1',       'sys_notice_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '关闭状态');
insert into sys_dict_data values(18, 99, '其他',     '0',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '其他操作');
insert into sys_dict_data values(19, 1,  '新增',     '1',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '新增操作');
insert into sys_dict_data values(20, 2,  '修改',     '2',       'sys_oper_type',       '',   'info',    'N', '0', 'admin', sysdate(), '', null, '修改操作');
insert into sys_dict_data values(21, 3,  '删除',     '3',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '删除操作');
insert into sys_dict_data values(22, 4,  '授权',     '4',       'sys_oper_type',       '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '授权操作');
insert into sys_dict_data values(23, 5,  '导出',     '5',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导出操作');
insert into sys_dict_data values(24, 6,  '导入',     '6',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '导入操作');
insert into sys_dict_data values(25, 7,  '强退',     '7',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '强退操作');
insert into sys_dict_data values(26, 8,  '生成代码', '8',       'sys_oper_type',       '',   'warning', 'N', '0', 'admin', sysdate(), '', null, '生成操作');
insert into sys_dict_data values(27, 9,  '清空数据', '9',       'sys_oper_type',       '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '清空操作');
insert into sys_dict_data values(28, 1,  '成功',     '0',       'sys_common_status',   '',   'primary', 'N', '0', 'admin', sysdate(), '', null, '正常状态');
insert into sys_dict_data values(29, 2,  '失败',     '1',       'sys_common_status',   '',   'danger',  'N', '0', 'admin', sysdate(), '', null, '停用状态');


-- ----------------------------
-- 13、参数配置表
-- ----------------------------
drop table if exists sys_config;
create table sys_config (
  config_id         int(5)          not null auto_increment    comment '参数主键',
  config_name       varchar(100)    default ''                 comment '参数名称',
  config_key        varchar(100)    default ''                 comment '参数键名',
  config_value      varchar(500)    default ''                 comment '参数键值',
  config_type       char(1)         default 'N'                comment '系统内置（Y是 N否）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (config_id)
) engine=innodb auto_increment=100 comment = '参数配置表';

insert into sys_config values(1, '主框架页-默认皮肤样式名称',     'sys.index.skinName',               'skin-blue',     'Y', 'admin', sysdate(), '', null, '蓝色 skin-blue、绿色 skin-green、紫色 skin-purple、红色 skin-red、黄色 skin-yellow' );
insert into sys_config values(2, '用户管理-账号初始密码',         'sys.user.initPassword',            '123456',        'Y', 'admin', sysdate(), '', null, '初始化密码 123456' );
insert into sys_config values(3, '主框架页-侧边栏主题',           'sys.index.sideTheme',              'theme-dark',    'Y', 'admin', sysdate(), '', null, '深色主题theme-dark，浅色主题theme-light' );
insert into sys_config values(4, '账号自助-验证码开关',           'sys.account.captchaEnabled',       'true',          'Y', 'admin', sysdate(), '', null, '是否开启验证码功能（true开启，false关闭）');
insert into sys_config values(5, '账号自助-是否开启用户注册功能', 'sys.account.registerUser',         'false',         'Y', 'admin', sysdate(), '', null, '是否开启注册用户功能（true开启，false关闭）');
insert into sys_config values(6, '用户登录-黑名单列表',           'sys.login.blackIPList',            '',              'Y', 'admin', sysdate(), '', null, '设置登录IP黑名单限制，多个匹配项以;分隔，支持匹配（*通配、网段）');
insert into sys_config values(7, '用户管理-初始密码修改策略',     'sys.account.initPasswordModify',   '1',             'Y', 'admin', sysdate(), '', null, '0：初始密码修改策略关闭，没有任何提示，1：提醒用户，如果未修改初始密码，则在登录时就会提醒修改密码对话框');
insert into sys_config values(8, '用户管理-账号密码更新周期',     'sys.account.passwordValidateDays', '0',             'Y', 'admin', sysdate(), '', null, '密码更新周期（填写数字，数据初始化值为0不限制，若修改必须为大于0小于365的正整数），如果超过这个周期登录系统时，则在登录时就会提醒修改密码对话框');
insert into sys_config values(9, '用户管理-密码字符范围',         'sys.account.chrtype',              '0',             'Y', 'admin', sysdate(), '', null, '默认任意字符范围，0任意（密码可以输入任意字符），1数字（密码只能为0-9数字），2英文字母（密码只能为a-z和A-Z字母），3字母和数字（密码必须包含字母，数字）,4字母数字和特殊字符（目前支持的特殊字符包括：~!@#$%^&*()-=_+）');


-- ----------------------------
-- 14、系统访问记录
-- ----------------------------
drop table if exists sys_logininfor;
create table sys_logininfor (
  info_id        bigint(20)     not null auto_increment   comment '访问ID',
  user_name      varchar(50)    default ''                comment '用户账号',
  ipaddr         varchar(128)   default ''                comment '登录IP地址',
  login_location varchar(255)   default ''                comment '登录地点',
  browser        varchar(50)    default ''                comment '浏览器类型',
  os             varchar(50)    default ''                comment '操作系统',
  status         char(1)        default '0'               comment '登录状态（0成功 1失败）',
  msg            varchar(255)   default ''                comment '提示消息',
  login_time     datetime                                 comment '访问时间',
  primary key (info_id),
  key idx_sys_logininfor_s  (status),
  key idx_sys_logininfor_lt (login_time)
) engine=innodb auto_increment=100 comment = '系统访问记录';


-- ----------------------------
-- 15、定时任务调度表
-- ----------------------------
drop table if exists sys_job;
create table sys_job (
  job_id              bigint(20)    not null auto_increment    comment '任务ID',
  job_name            varchar(64)   default ''                 comment '任务名称',
  job_group           varchar(64)   default 'DEFAULT'          comment '任务组名',
  invoke_target       varchar(500)  not null                   comment '调用目标字符串',
  cron_expression     varchar(255)  default ''                 comment 'cron执行表达式',
  misfire_policy      varchar(20)   default '3'                comment '计划执行错误策略（1立即执行 2执行一次 3放弃执行）',
  concurrent          char(1)       default '1'                comment '是否并发执行（0允许 1禁止）',
  status              char(1)       default '0'                comment '状态（0正常 1暂停）',
  create_by           varchar(64)   default ''                 comment '创建者',
  create_time         datetime                                 comment '创建时间',
  update_by           varchar(64)   default ''                 comment '更新者',
  update_time         datetime                                 comment '更新时间',
  remark              varchar(500)  default ''                 comment '备注信息',
  primary key (job_id, job_name, job_group)
) engine=innodb auto_increment=100 comment = '定时任务调度表';

insert into sys_job values(1, '系统默认（无参）', 'DEFAULT', 'ryTask.ryNoParams',        '0/10 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(2, '系统默认（有参）', 'DEFAULT', 'ryTask.ryParams(\'ry\')',  '0/15 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');
insert into sys_job values(3, '系统默认（多参）', 'DEFAULT', 'ryTask.ryMultipleParams(\'ry\', true, 2000L, 316.50D, 100)',  '0/20 * * * * ?', '3', '1', '1', 'admin', sysdate(), '', null, '');


-- ----------------------------
-- 16、定时任务调度日志表
-- ----------------------------
drop table if exists sys_job_log;
create table sys_job_log (
  job_log_id          bigint(20)     not null auto_increment    comment '任务日志ID',
  job_name            varchar(64)    not null                   comment '任务名称',
  job_group           varchar(64)    not null                   comment '任务组名',
  invoke_target       varchar(500)   not null                   comment '调用目标字符串',
  job_message         varchar(500)                              comment '日志信息',
  status              char(1)        default '0'                comment '执行状态（0正常 1失败）',
  exception_info      varchar(2000)  default ''                 comment '异常信息',
  start_time          datetime                                  comment '执行开始时间',
  end_time            datetime                                  comment '执行结束时间',
  create_time         datetime                                  comment '创建时间',
  primary key (job_log_id)
) engine=innodb comment = '定时任务调度日志表';


-- ----------------------------
-- 17、通知公告表
-- ----------------------------
drop table if exists sys_notice;
create table sys_notice (
  notice_id         int(4)          not null auto_increment    comment '公告ID',
  notice_title      varchar(50)     not null                   comment '公告标题',
  notice_type       char(1)         not null                   comment '公告类型（1通知 2公告）',
  notice_content    longblob        default null               comment '公告内容',
  status            char(1)         default '0'                comment '公告状态（0正常 1关闭）',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time       datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(255)    default null               comment '备注',
  primary key (notice_id)
) engine=innodb auto_increment=10 comment = '通知公告表';

-- ----------------------------
-- 初始化-公告信息表数据
-- ----------------------------
insert into sys_notice values('1', '温馨提醒：2018-07-01 若依新版本发布啦', '2', '新版本内容', '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('2', '维护通知：2018-07-01 若依系统凌晨维护', '1', '维护内容',   '0', 'admin', sysdate(), '', null, '管理员');
insert into sys_notice values('3', '若依开源框架介绍', '1', '<p><span style=\"color: rgb(230, 0, 0);\">项目介绍</span></p><p><font color=\"#333333\">RuoYi开源项目是为企业用户定制的后台脚手架框架，为企业打造的一站式解决方案，降低企业开发成本，提升开发效率。主要包括用户管理、角色管理、部门管理、菜单管理、参数管理、字典管理、</font><span style=\"color: rgb(51, 51, 51);\">岗位管理</span><span style=\"color: rgb(51, 51, 51);\">、定时任务</span><span style=\"color: rgb(51, 51, 51);\">、</span><span style=\"color: rgb(51, 51, 51);\">服务监控、登录日志、操作日志、代码生成等功能。其中，还支持多数据源、数据权限、国际化、Redis缓存、Docker部署、滑动验证码、第三方认证登录、分布式事务、</span><font color=\"#333333\">分布式文件存储</font><span style=\"color: rgb(51, 51, 51);\">、分库分表处理等技术特点。</span></p><p><img src=\"https://foruda.gitee.com/images/1773931848342439032/a4d22313_1815095.png\" style=\"width: 64px;\"><br></p><p><span style=\"color: rgb(230, 0, 0);\">官网及演示</span></p><p><span style=\"color: rgb(51, 51, 51);\">若依官网地址：&nbsp;</span><a href=\"http://ruoyi.vip\" target=\"_blank\">http://ruoyi.vip</a><a href=\"http://ruoyi.vip\" target=\"_blank\"></a></p><p><span style=\"color: rgb(51, 51, 51);\">若依文档地址：&nbsp;</span><a href=\"http://doc.ruoyi.vip\" target=\"_blank\">http://doc.ruoyi.vip</a><br></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【不分离版】：&nbsp;</span><a href=\"http://demo.ruoyi.vip\" target=\"_blank\">http://demo.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【分离版本】：&nbsp;</span><a href=\"http://vue.ruoyi.vip\" target=\"_blank\">http://vue.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【微服务版】：&nbsp;</span><a href=\"http://cloud.ruoyi.vip\" target=\"_blank\">http://cloud.ruoyi.vip</a></p><p><span style=\"color: rgb(51, 51, 51);\">演示地址【移动端版】：&nbsp;</span><a href=\"http://h5.ruoyi.vip\" target=\"_blank\">http://h5.ruoyi.vip</a></p><p><br style=\"color: rgb(48, 49, 51); font-family: &quot;Helvetica Neue&quot;, Helvetica, Arial, sans-serif; font-size: 12px;\"></p>', '0', 'admin', sysdate(), '', null, '管理员');


-- ----------------------------
-- 18、公告已读记录表
-- ----------------------------
drop table if exists sys_notice_read;
create table sys_notice_read (
  read_id          bigint(20)       not null auto_increment    comment '已读主键',
  notice_id        int(4)           not null                   comment '公告id',
  user_id          bigint(20)       not null                   comment '用户id',
  read_time        datetime         not null                   comment '阅读时间',
  primary key (read_id),
  unique key uk_user_notice (user_id, notice_id)   comment '同一用户同一公告只记录一次'
) engine=innodb auto_increment=1 comment='公告已读记录表';


-- ----------------------------
-- 19、代码生成业务表
-- ----------------------------
drop table if exists gen_table;
create table gen_table (
  table_id          bigint(20)      not null auto_increment    comment '编号',
  table_name        varchar(200)    default ''                 comment '表名称',
  table_comment     varchar(500)    default ''                 comment '表描述',
  sub_table_name    varchar(64)     default null               comment '关联子表的表名',
  sub_table_fk_name varchar(64)     default null               comment '子表关联的外键名',
  class_name        varchar(100)    default ''                 comment '实体类名称',
  tpl_category      varchar(200)    default 'crud'             comment '使用的模板（crud单表操作 tree树表操作）',
  tpl_web_type      varchar(30)     default ''                 comment '前端模板类型（element-ui模版 element-plus模版）',
  package_name      varchar(100)                               comment '生成包路径',
  module_name       varchar(30)                                comment '生成模块名',
  business_name     varchar(30)                                comment '生成业务名',
  function_name     varchar(50)                                comment '生成功能名',
  function_author   varchar(50)                                comment '生成功能作者',
  form_col_num      int(1)          default 1                  comment '表单布局（单列 双列 三列）',
  gen_type          char(1)         default '0'                comment '生成代码方式（0zip压缩包 1自定义路径）',
  gen_path          varchar(200)    default '/'                comment '生成路径（不填默认项目路径）',
  options           varchar(1000)                              comment '其它生成选项',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  remark            varchar(500)    default null               comment '备注',
  primary key (table_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表';


-- ----------------------------
-- 20、代码生成业务表字段
-- ----------------------------
drop table if exists gen_table_column;
create table gen_table_column (
  column_id         bigint(20)      not null auto_increment    comment '编号',
  table_id          bigint(20)                                 comment '归属表编号',
  column_name       varchar(200)                               comment '列名称',
  column_comment    varchar(500)                               comment '列描述',
  column_type       varchar(100)                               comment '列类型',
  java_type         varchar(500)                               comment 'JAVA类型',
  java_field        varchar(200)                               comment 'JAVA字段名',
  is_pk             char(1)                                    comment '是否主键（1是）',
  is_increment      char(1)                                    comment '是否自增（1是）',
  is_required       char(1)                                    comment '是否必填（1是）',
  is_insert         char(1)                                    comment '是否为插入字段（1是）',
  is_edit           char(1)                                    comment '是否编辑字段（1是）',
  is_list           char(1)                                    comment '是否列表字段（1是）',
  is_query          char(1)                                    comment '是否查询字段（1是）',
  query_type        varchar(200)    default 'EQ'               comment '查询方式（等于、不等于、大于、小于、范围）',
  html_type         varchar(200)                               comment '显示类型（文本框、文本域、下拉框、复选框、单选框、日期控件）',
  dict_type         varchar(200)    default ''                 comment '字典类型',
  sort              int                                        comment '排序',
  create_by         varchar(64)     default ''                 comment '创建者',
  create_time 	    datetime                                   comment '创建时间',
  update_by         varchar(64)     default ''                 comment '更新者',
  update_time       datetime                                   comment '更新时间',
  primary key (column_id)
) engine=innodb auto_increment=1 comment = '代码生成业务表字段';

-- SOURCE upstream/ruoyi/quartz.sql
DROP TABLE IF EXISTS QRTZ_FIRED_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_PAUSED_TRIGGER_GRPS;
DROP TABLE IF EXISTS QRTZ_SCHEDULER_STATE;
DROP TABLE IF EXISTS QRTZ_LOCKS;
DROP TABLE IF EXISTS QRTZ_SIMPLE_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_SIMPROP_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_CRON_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_BLOB_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_TRIGGERS;
DROP TABLE IF EXISTS QRTZ_JOB_DETAILS;
DROP TABLE IF EXISTS QRTZ_CALENDARS;

-- ----------------------------
-- 1、存储每一个已配置的 jobDetail 的详细信息
-- ----------------------------
create table QRTZ_JOB_DETAILS (
    sched_name           varchar(120)    not null            comment '调度名称',
    job_name             varchar(200)    not null            comment '任务名称',
    job_group            varchar(200)    not null            comment '任务组名',
    description          varchar(250)    null                comment '相关介绍',
    job_class_name       varchar(250)    not null            comment '执行任务类名称',
    is_durable           varchar(1)      not null            comment '是否持久化',
    is_nonconcurrent     varchar(1)      not null            comment '是否并发',
    is_update_data       varchar(1)      not null            comment '是否更新数据',
    requests_recovery    varchar(1)      not null            comment '是否接受恢复执行',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, job_name, job_group)
) engine=innodb comment = '任务详细信息表';

-- ----------------------------
-- 2、 存储已配置的 Trigger 的信息
-- ----------------------------
create table QRTZ_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment '触发器的名字',
    trigger_group        varchar(200)    not null            comment '触发器所属组的名字',
    job_name             varchar(200)    not null            comment 'qrtz_job_details表job_name的外键',
    job_group            varchar(200)    not null            comment 'qrtz_job_details表job_group的外键',
    description          varchar(250)    null                comment '相关介绍',
    next_fire_time       bigint(13)      null                comment '上一次触发时间（毫秒）',
    prev_fire_time       bigint(13)      null                comment '下一次触发时间（默认为-1表示不触发）',
    priority             integer         null                comment '优先级',
    trigger_state        varchar(16)     not null            comment '触发器状态',
    trigger_type         varchar(8)      not null            comment '触发器的类型',
    start_time           bigint(13)      not null            comment '开始时间',
    end_time             bigint(13)      null                comment '结束时间',
    calendar_name        varchar(200)    null                comment '日程表名称',
    misfire_instr        smallint(2)     null                comment '补偿执行的策略',
    job_data             blob            null                comment '存放持久化job对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, job_name, job_group) references QRTZ_JOB_DETAILS(sched_name, job_name, job_group)
) engine=innodb comment = '触发器详细信息表';

-- ----------------------------
-- 3、 存储简单的 Trigger，包括重复次数，间隔，以及已触发的次数
-- ----------------------------
create table QRTZ_SIMPLE_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    repeat_count         bigint(7)       not null            comment '重复的次数统计',
    repeat_interval      bigint(12)      not null            comment '重复的间隔时间',
    times_triggered      bigint(10)      not null            comment '已经触发的次数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '简单触发器的信息表';

-- ----------------------------
-- 4、 存储 Cron Trigger，包括 Cron 表达式和时区信息
-- ---------------------------- 
create table QRTZ_CRON_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    cron_expression      varchar(200)    not null            comment 'cron表达式',
    time_zone_id         varchar(80)                         comment '时区',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Cron类型的触发器表';

-- ----------------------------
-- 5、 Trigger 作为 Blob 类型存储(用于 Quartz 用户用 JDBC 创建他们自己定制的 Trigger 类型，JobStore 并不知道如何存储实例的时候)
-- ---------------------------- 
create table QRTZ_BLOB_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    blob_data            blob            null                comment '存放持久化Trigger对象',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = 'Blob类型的触发器表';

-- ----------------------------
-- 6、 以 Blob 类型存储存放日历信息， quartz可配置一个日历来指定一个时间范围
-- ---------------------------- 
create table QRTZ_CALENDARS (
    sched_name           varchar(120)    not null            comment '调度名称',
    calendar_name        varchar(200)    not null            comment '日历名称',
    calendar             blob            not null            comment '存放持久化calendar对象',
    primary key (sched_name, calendar_name)
) engine=innodb comment = '日历信息表';

-- ----------------------------
-- 7、 存储已暂停的 Trigger 组的信息
-- ---------------------------- 
create table QRTZ_PAUSED_TRIGGER_GRPS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    primary key (sched_name, trigger_group)
) engine=innodb comment = '暂停的触发器表';

-- ----------------------------
-- 8、 存储与已触发的 Trigger 相关的状态信息，以及相联 Job 的执行信息
-- ---------------------------- 
create table QRTZ_FIRED_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    entry_id             varchar(95)     not null            comment '调度器实例id',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    instance_name        varchar(200)    not null            comment '调度器实例名',
    fired_time           bigint(13)      not null            comment '触发的时间',
    sched_time           bigint(13)      not null            comment '定时器制定的时间',
    priority             integer         not null            comment '优先级',
    state                varchar(16)     not null            comment '状态',
    job_name             varchar(200)    null                comment '任务名称',
    job_group            varchar(200)    null                comment '任务组名',
    is_nonconcurrent     varchar(1)      null                comment '是否并发',
    requests_recovery    varchar(1)      null                comment '是否接受恢复执行',
    primary key (sched_name, entry_id)
) engine=innodb comment = '已触发的触发器表';

-- ----------------------------
-- 9、 存储少量的有关 Scheduler 的状态信息，假如是用于集群中，可以看到其他的 Scheduler 实例
-- ---------------------------- 
create table QRTZ_SCHEDULER_STATE (
    sched_name           varchar(120)    not null            comment '调度名称',
    instance_name        varchar(200)    not null            comment '实例名称',
    last_checkin_time    bigint(13)      not null            comment '上次检查时间',
    checkin_interval     bigint(13)      not null            comment '检查间隔时间',
    primary key (sched_name, instance_name)
) engine=innodb comment = '调度器状态表';

-- ----------------------------
-- 10、 存储程序的悲观锁的信息(假如使用了悲观锁)
-- ---------------------------- 
create table QRTZ_LOCKS (
    sched_name           varchar(120)    not null            comment '调度名称',
    lock_name            varchar(40)     not null            comment '悲观锁名称',
    primary key (sched_name, lock_name)
) engine=innodb comment = '存储的悲观锁信息表';

-- ----------------------------
-- 11、 Quartz集群实现同步机制的行锁表
-- ---------------------------- 
create table QRTZ_SIMPROP_TRIGGERS (
    sched_name           varchar(120)    not null            comment '调度名称',
    trigger_name         varchar(200)    not null            comment 'qrtz_triggers表trigger_name的外键',
    trigger_group        varchar(200)    not null            comment 'qrtz_triggers表trigger_group的外键',
    str_prop_1           varchar(512)    null                comment 'String类型的trigger的第一个参数',
    str_prop_2           varchar(512)    null                comment 'String类型的trigger的第二个参数',
    str_prop_3           varchar(512)    null                comment 'String类型的trigger的第三个参数',
    int_prop_1           int             null                comment 'int类型的trigger的第一个参数',
    int_prop_2           int             null                comment 'int类型的trigger的第二个参数',
    long_prop_1          bigint          null                comment 'long类型的trigger的第一个参数',
    long_prop_2          bigint          null                comment 'long类型的trigger的第二个参数',
    dec_prop_1           numeric(13,4)   null                comment 'decimal类型的trigger的第一个参数',
    dec_prop_2           numeric(13,4)   null                comment 'decimal类型的trigger的第二个参数',
    bool_prop_1          varchar(1)      null                comment 'Boolean类型的trigger的第一个参数',
    bool_prop_2          varchar(1)      null                comment 'Boolean类型的trigger的第二个参数',
    primary key (sched_name, trigger_name, trigger_group),
    foreign key (sched_name, trigger_name, trigger_group) references QRTZ_TRIGGERS(sched_name, trigger_name, trigger_group)
) engine=innodb comment = '同步机制的行锁表';

commit;

-- SOURCE mysql-v3-m0-core-schema.sql
-- V3.0 M0 education baseline, part 1 of 2. Run in an empty selected database.
-- Minimum syntax target: MySQL 8.0.16, InnoDB, utf8mb4.
-- Requires an existing database and a verified RuoYi identity/organization mapping.
-- Intentionally omits CREATE DATABASE, USE, system tables, seed data and DROP statements.

CREATE TABLE edu_term (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(120) NOT NULL,
    starts_on DATE NOT NULL,
    ends_on DATE NOT NULL,
    is_current TINYINT(1) NOT NULL DEFAULT 0,
    current_flag TINYINT GENERATED ALWAYS AS (IF(is_current = 1, 1, NULL)) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_term_id_org (id, organization_id),
    UNIQUE KEY uq_edu_term_org_code (organization_id, code),
    UNIQUE KEY uq_edu_term_single_current (current_flag),
    KEY idx_edu_term_org_dates (organization_id, starts_on, ends_on),
    CONSTRAINT chk_edu_term_dates CHECK (ends_on >= starts_on)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_official_course (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_official_course_id_org (id, organization_id),
    UNIQUE KEY uq_edu_official_course_org_code (organization_id, code),
    CONSTRAINT chk_edu_official_course_status CHECK (status IN ('ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_course_space (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    official_course_id BIGINT NULL,
    owner_user_id BIGINT NOT NULL,
    title VARCHAR(160) NOT NULL,
    summary TEXT NULL,
    learning_objectives TEXT NULL,
    cover_storage_key VARCHAR(512) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_course_space_id_org (id, organization_id),
    KEY idx_edu_course_space_owner_status (owner_user_id, status),
    KEY idx_edu_course_space_official_org (official_course_id, organization_id),
    CONSTRAINT fk_edu_course_space_official_org FOREIGN KEY (official_course_id, organization_id)
        REFERENCES edu_official_course (id, organization_id),
    CONSTRAINT chk_edu_course_space_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_course_unit (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    parent_unit_id BIGINT NULL,
    unit_kind VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_course_unit_id_space (id, course_space_id),
    KEY idx_edu_course_unit_space_parent_sort (course_space_id, parent_unit_id, sort_order),
    KEY idx_edu_course_unit_parent_space (parent_unit_id, course_space_id),
    CONSTRAINT fk_edu_course_unit_space FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT fk_edu_course_unit_parent FOREIGN KEY (parent_unit_id, course_space_id)
        REFERENCES edu_course_unit (id, course_space_id),
    CONSTRAINT chk_edu_course_unit_kind CHECK (unit_kind IN ('CHAPTER', 'SECTION')),
    CONSTRAINT chk_edu_course_unit_sort CHECK (sort_order >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_knowledge_point (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    unit_id BIGINT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_knowledge_point_id_space (id, course_space_id),
    UNIQUE KEY uq_edu_knowledge_point_space_code (course_space_id, code),
    KEY idx_edu_knowledge_point_unit_space (unit_id, course_space_id),
    CONSTRAINT fk_edu_knowledge_point_space FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT fk_edu_knowledge_point_unit FOREIGN KEY (unit_id, course_space_id)
        REFERENCES edu_course_unit (id, course_space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_teaching_class (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    term_id BIGINT NOT NULL,
    official_course_id BIGINT NULL,
    code VARCHAR(60) NOT NULL,
    name VARCHAR(160) NOT NULL,
    class_kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_teaching_class_id_org (id, organization_id),
    UNIQUE KEY uq_edu_teaching_class_org_term_code (organization_id, term_id, code),
    KEY idx_edu_teaching_class_term_org (term_id, organization_id),
    KEY idx_edu_teaching_class_official_org (official_course_id, organization_id),
    CONSTRAINT fk_edu_teaching_class_term_org FOREIGN KEY (term_id, organization_id)
        REFERENCES edu_term (id, organization_id),
    CONSTRAINT fk_edu_teaching_class_official_org FOREIGN KEY (official_course_id, organization_id)
        REFERENCES edu_official_course (id, organization_id),
    CONSTRAINT chk_edu_teaching_class_kind CHECK (class_kind IN ('FORMAL', 'TRIAL')),
    CONSTRAINT chk_edu_teaching_class_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_class_teacher (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teaching_class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    teacher_role VARCHAR(20) NOT NULL,
    can_publish TINYINT(1) NOT NULL DEFAULT 0,
    can_grade TINYINT(1) NOT NULL DEFAULT 0,
    can_export TINYINT(1) NOT NULL DEFAULT 0,
    valid_from DATETIME(3) NOT NULL,
    valid_to DATETIME(3) NULL,
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN valid_to IS NULL THEN user_id ELSE NULL END) STORED,
    granted_by_user_id BIGINT NOT NULL,
    ended_by_user_id BIGINT NULL,
    end_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_class_teacher_active (teaching_class_id, active_user_id),
    KEY idx_edu_class_teacher_user_dates (user_id, valid_from, valid_to),
    CONSTRAINT fk_edu_class_teacher_class FOREIGN KEY (teaching_class_id)
        REFERENCES edu_teaching_class (id),
    CONSTRAINT chk_edu_class_teacher_role CHECK (teacher_role IN ('LEAD', 'CO_TEACHER')),
    CONSTRAINT chk_edu_class_teacher_flags CHECK
        (can_publish IN (0, 1) AND can_grade IN (0, 1) AND can_export IN (0, 1)),
    CONSTRAINT chk_edu_class_teacher_dates CHECK (valid_to IS NULL OR valid_to > valid_from)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_class_student (
    id BIGINT NOT NULL AUTO_INCREMENT,
    teaching_class_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    joined_at DATETIME(3) NOT NULL,
    left_at DATETIME(3) NULL,
    active_user_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN left_at IS NULL THEN user_id ELSE NULL END) STORED,
    recorded_by_user_id BIGINT NOT NULL,
    left_by_user_id BIGINT NULL,
    leave_reason VARCHAR(500) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_class_student_id_class_user (id, teaching_class_id, user_id),
    UNIQUE KEY uq_edu_class_student_active (teaching_class_id, active_user_id),
    KEY idx_edu_class_student_user_dates (user_id, joined_at, left_at),
    CONSTRAINT fk_edu_class_student_class FOREIGN KEY (teaching_class_id)
        REFERENCES edu_teaching_class (id),
    CONSTRAINT chk_edu_class_student_dates CHECK (left_at IS NULL OR left_at > joined_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_course_class_binding (
    id BIGINT NOT NULL AUTO_INCREMENT,
    organization_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    bound_by_user_id BIGINT NOT NULL,
    published_at DATETIME(3) NOT NULL,
    ended_at DATETIME(3) NULL,
    ended_by_user_id BIGINT NULL,
    end_reason VARCHAR(500) NULL,
    active_space_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN ended_at IS NULL THEN course_space_id ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_binding_id_class (id, teaching_class_id),
    UNIQUE KEY uq_edu_binding_id_space (id, course_space_id),
    UNIQUE KEY uq_edu_binding_active_pair (teaching_class_id, active_space_id),
    KEY idx_edu_binding_class_org (teaching_class_id, organization_id),
    KEY idx_edu_binding_space_org (course_space_id, organization_id),
    KEY idx_edu_binding_space_dates (course_space_id, published_at, ended_at),
    CONSTRAINT fk_edu_binding_class_org FOREIGN KEY (teaching_class_id, organization_id)
        REFERENCES edu_teaching_class (id, organization_id),
    CONSTRAINT fk_edu_binding_space_org FOREIGN KEY (course_space_id, organization_id)
        REFERENCES edu_course_space (id, organization_id),
    CONSTRAINT chk_edu_binding_dates CHECK (ended_at IS NULL OR ended_at > published_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_resource (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    resource_kind VARCHAR(16) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_resource_id_space (id, course_space_id),
    UNIQUE KEY uq_edu_resource_id_kind (id, resource_kind),
    KEY idx_edu_resource_space_status (course_space_id, status, id),
    CONSTRAINT fk_edu_resource_space FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT chk_edu_resource_kind CHECK (resource_kind IN ('FILE', 'LINK')),
    CONSTRAINT chk_edu_resource_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_resource_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_id BIGINT NOT NULL,
    resource_kind VARCHAR(16) NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    storage_key VARCHAR(512) NULL,
    external_uri VARCHAR(1024) NULL,
    sha256 CHAR(64) CHARACTER SET ascii COLLATE ascii_bin NULL,
    mime_type VARCHAR(120) NULL,
    byte_size BIGINT UNSIGNED NULL,
    processing_status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_resource_version_id_resource (id, resource_id),
    UNIQUE KEY uq_edu_resource_version_id_kind (id, resource_kind),
    UNIQUE KEY uq_edu_resource_version_no (resource_id, version_no),
    KEY idx_edu_resource_version_resource_kind (resource_id, resource_kind),
    CONSTRAINT fk_edu_resource_version_resource_kind FOREIGN KEY (resource_id, resource_kind)
        REFERENCES edu_resource (id, resource_kind),
    CONSTRAINT chk_edu_resource_version_no CHECK (version_no > 0),
    CONSTRAINT chk_edu_resource_version_source CHECK
        ((resource_kind = 'FILE' AND storage_key IS NOT NULL
          AND CHAR_LENGTH(TRIM(storage_key)) > 0 AND external_uri IS NULL) OR
         (resource_kind = 'LINK' AND storage_key IS NULL
          AND external_uri IS NOT NULL AND CHAR_LENGTH(TRIM(external_uri)) > 0)),
    CONSTRAINT chk_edu_resource_version_status CHECK
        (processing_status IN ('PENDING', 'READY', 'FAILED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_resource_class_release (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    resource_version_id BIGINT NOT NULL,
    released_by_user_id BIGINT NOT NULL,
    released_at DATETIME(3) NOT NULL,
    opens_at DATETIME(3) NOT NULL,
    closes_at DATETIME(3) NULL,
    ended_at DATETIME(3) NULL,
    end_kind VARCHAR(16) NULL,
    ended_by_user_id BIGINT NULL,
    end_reason VARCHAR(500) NULL,
    active_resource_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN ended_at IS NULL THEN resource_id ELSE NULL END) STORED,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_release_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_release_id_version (id, resource_version_id),
    UNIQUE KEY uq_edu_release_active_resource (binding_id, active_resource_id),
    KEY idx_edu_release_binding_space (binding_id, course_space_id),
    KEY idx_edu_release_resource_space (resource_id, course_space_id),
    KEY idx_edu_release_version_resource (resource_version_id, resource_id),
    KEY idx_edu_release_open_window (binding_id, opens_at, closes_at),
    CONSTRAINT fk_edu_release_binding_space FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_release_resource_space FOREIGN KEY (resource_id, course_space_id)
        REFERENCES edu_resource (id, course_space_id),
    CONSTRAINT fk_edu_release_version_resource FOREIGN KEY (resource_version_id, resource_id)
        REFERENCES edu_resource_version (id, resource_id),
    CONSTRAINT chk_edu_release_window CHECK (closes_at IS NULL OR closes_at > opens_at),
    CONSTRAINT chk_edu_release_end CHECK
        ((ended_at IS NULL AND end_kind IS NULL AND ended_by_user_id IS NULL AND end_reason IS NULL) OR
         (ended_at IS NOT NULL AND ended_at >= released_at
          AND end_kind IS NOT NULL AND end_kind IN ('EXPIRED', 'REPLACED', 'REVOKED'))),
    CONSTRAINT chk_edu_release_expiry CHECK
        (end_kind <> 'EXPIRED' OR (closes_at IS NOT NULL AND ended_at >= closes_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- SOURCE mysql-v3-m0-teaching-schema.sql
-- V3.0 M0 education baseline, part 2 of 2.
-- Run after mysql-v3-m0-core-schema.sql in the same empty MySQL 8.0.16+ database.
-- IDs pointing to RuoYi users and organizations are logical references until integration.
-- All DATETIME(3) values are UTC. Content versions and published history are append-only in services.

-- course: one planned lesson belongs to a single class/space binding.
CREATE TABLE edu_lesson (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    unit_id BIGINT NULL,
    title VARCHAR(160) NOT NULL,
    scheduled_at DATETIME(3) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_lesson_id_binding (id, binding_id),
    KEY idx_edu_lesson_binding_schedule (binding_id, scheduled_at),
    KEY idx_edu_lesson_binding_space (binding_id, course_space_id),
    KEY idx_edu_lesson_unit_space (unit_id, course_space_id),
    CONSTRAINT fk_edu_lesson_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_lesson_unit FOREIGN KEY (unit_id, course_space_id)
        REFERENCES edu_course_unit (id, course_space_id),
    CONSTRAINT chk_edu_lesson_status CHECK (status IN ('PLANNED', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- resource: page conversion status is independent of upload success.
CREATE TABLE edu_resource_page (
    id BIGINT NOT NULL AUTO_INCREMENT,
    resource_version_id BIGINT NOT NULL,
    resource_kind VARCHAR(16) NOT NULL DEFAULT 'FILE',
    page_no INT UNSIGNED NOT NULL,
    preview_storage_key VARCHAR(512) NOT NULL,
    width_px INT UNSIGNED NULL,
    height_px INT UNSIGNED NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_resource_page_version_no (resource_version_id, page_no),
    KEY idx_edu_resource_page_version_kind (resource_version_id, resource_kind),
    CONSTRAINT fk_edu_resource_page_version FOREIGN KEY (resource_version_id, resource_kind)
        REFERENCES edu_resource_version (id, resource_kind),
    CONSTRAINT chk_edu_resource_page_kind CHECK (resource_kind = 'FILE'),
    CONSTRAINT chk_edu_resource_page_no CHECK (page_no > 0),
    CONSTRAINT chk_edu_resource_page_key CHECK (CHAR_LENGTH(TRIM(preview_storage_key)) > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_question_id_space (id, course_space_id),
    KEY idx_edu_question_space_status (course_space_id, status),
    CONSTRAINT fk_edu_question_space FOREIGN KEY (course_space_id)
        REFERENCES edu_course_space (id),
    CONSTRAINT chk_edu_question_status CHECK (status IN ('DRAFT', 'ACTIVE', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_question_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    question_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    question_kind VARCHAR(20) NOT NULL,
    stem TEXT NOT NULL,
    options_json JSON NULL,
    answer_json JSON NULL,
    explanation TEXT NULL,
    rubric_json JSON NULL,
    max_score DECIMAL(10,2) NOT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_question_version_id_question (id, question_id),
    UNIQUE KEY uq_edu_question_version_no (question_id, version_no),
    CONSTRAINT fk_edu_question_version_question FOREIGN KEY (question_id)
        REFERENCES edu_question (id),
    CONSTRAINT chk_edu_question_version_no CHECK (version_no > 0),
    CONSTRAINT chk_edu_question_kind CHECK
        (question_kind IN ('SINGLE', 'MULTIPLE', 'TRUE_FALSE', 'SHORT_TEXT')),
    CONSTRAINT chk_edu_question_score CHECK (max_score >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_question_knowledge_point (
    question_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    knowledge_point_id BIGINT NOT NULL,
    PRIMARY KEY (question_id, knowledge_point_id),
    KEY idx_edu_qkp_point_space (knowledge_point_id, course_space_id),
    CONSTRAINT fk_edu_qkp_question FOREIGN KEY (question_id, course_space_id)
        REFERENCES edu_question (id, course_space_id),
    CONSTRAINT fk_edu_qkp_point FOREIGN KEY (knowledge_point_id, course_space_id)
        REFERENCES edu_knowledge_point (id, course_space_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- prepare: private plan and template never confer student access by themselves.
CREATE TABLE edu_lesson_plan (
    id BIGINT NOT NULL AUTO_INCREMENT,
    lesson_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    title VARCHAR(160) NOT NULL,
    private_notes TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_plan_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_plan_id_lesson_binding (id, lesson_id, binding_id),
    UNIQUE KEY uq_edu_plan_lesson_version (lesson_id, version_no),
    KEY idx_edu_plan_lesson_binding (lesson_id, binding_id),
    CONSTRAINT fk_edu_plan_lesson FOREIGN KEY (lesson_id, binding_id)
        REFERENCES edu_lesson (id, binding_id),
    CONSTRAINT chk_edu_plan_version CHECK (version_no > 0),
    CONSTRAINT chk_edu_plan_status CHECK (status IN ('DRAFT', 'READY', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_plan_material (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    resource_id BIGINT NOT NULL,
    resource_version_id BIGINT NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    private_notes TEXT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_plan_material_order (plan_id, sort_order),
    KEY idx_edu_plan_material_plan_binding (plan_id, binding_id),
    KEY idx_edu_plan_material_binding_space (binding_id, course_space_id),
    KEY idx_edu_plan_material_resource_space (resource_id, course_space_id),
    KEY idx_edu_plan_material_version_resource (resource_version_id, resource_id),
    CONSTRAINT fk_edu_plan_material_plan FOREIGN KEY (plan_id, binding_id)
        REFERENCES edu_lesson_plan (id, binding_id),
    CONSTRAINT fk_edu_plan_material_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_plan_material_resource FOREIGN KEY (resource_id, course_space_id)
        REFERENCES edu_resource (id, course_space_id),
    CONSTRAINT fk_edu_plan_material_version FOREIGN KEY (resource_version_id, resource_id)
        REFERENCES edu_resource_version (id, resource_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_activity_template (
    id BIGINT NOT NULL AUTO_INCREMENT,
    plan_id BIGINT NULL,
    binding_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    template_kind VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    question_id BIGINT NULL,
    question_version_id BIGINT NULL,
    duration_seconds INT UNSIGNED NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_edu_template_plan_binding (plan_id, binding_id),
    KEY idx_edu_template_binding_space (binding_id, course_space_id),
    KEY idx_edu_template_question_space (question_id, course_space_id),
    KEY idx_edu_template_question_version (question_version_id, question_id),
    UNIQUE KEY uq_edu_template_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_template_id_binding_kind (id, binding_id, template_kind),
    CONSTRAINT fk_edu_template_plan FOREIGN KEY (plan_id, binding_id)
        REFERENCES edu_lesson_plan (id, binding_id),
    CONSTRAINT fk_edu_template_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_template_question_space FOREIGN KEY (question_id, course_space_id)
        REFERENCES edu_question (id, course_space_id),
    CONSTRAINT fk_edu_template_question FOREIGN KEY (question_version_id, question_id)
        REFERENCES edu_question_version (id, question_id),
    CONSTRAINT chk_edu_template_kind CHECK (template_kind IN ('ATTENDANCE', 'QUIZ', 'CLASSWORK')),
    CONSTRAINT chk_edu_template_question CHECK
        ((template_kind = 'QUIZ' AND question_id IS NOT NULL AND question_version_id IS NOT NULL) OR
         (template_kind <> 'QUIZ' AND question_id IS NULL AND question_version_id IS NULL)),
    CONSTRAINT chk_edu_template_status CHECK (status IN ('DRAFT', 'READY', 'ARCHIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- classroom: snapshot sequence and ordered events support reconnection.
CREATE TABLE edu_class_session (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    lesson_id BIGINT NULL,
    plan_id BIGINT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PLANNED',
    active_binding_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN status IN ('RUNNING', 'PAUSED') THEN binding_id ELSE NULL END) STORED,
    started_by_user_id BIGINT NULL,
    started_at DATETIME(3) NULL,
    ended_by_user_id BIGINT NULL,
    ended_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_session_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_session_active_binding (active_binding_id),
    KEY idx_edu_session_binding_status (binding_id, status, id),
    KEY idx_edu_session_lesson_binding (lesson_id, binding_id),
    KEY idx_edu_session_plan_lesson_binding (plan_id, lesson_id, binding_id),
    CONSTRAINT fk_edu_session_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT fk_edu_session_lesson FOREIGN KEY (lesson_id, binding_id)
        REFERENCES edu_lesson (id, binding_id),
    CONSTRAINT fk_edu_session_plan FOREIGN KEY (plan_id, lesson_id, binding_id)
        REFERENCES edu_lesson_plan (id, lesson_id, binding_id),
    CONSTRAINT chk_edu_session_status CHECK (status IN ('PLANNED', 'RUNNING', 'PAUSED', 'ENDED')),
    CONSTRAINT chk_edu_session_times CHECK
        ((status = 'PLANNED' AND started_at IS NULL AND ended_at IS NULL) OR
         (status IN ('RUNNING', 'PAUSED') AND started_at IS NOT NULL AND ended_at IS NULL) OR
         (status = 'ENDED' AND started_at IS NOT NULL AND ended_at >= started_at)),
    CONSTRAINT chk_edu_session_plan_lesson CHECK (plan_id IS NULL OR lesson_id IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_session_projection (
    session_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    state_seq BIGINT UNSIGNED NOT NULL DEFAULT 0,
    release_id BIGINT NULL,
    resource_version_id BIGINT NULL,
    page_no INT UNSIGNED NULL,
    student_can_browse TINYINT(1) NOT NULL DEFAULT 0,
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (session_id),
    KEY idx_edu_projection_session_binding (session_id, binding_id),
    KEY idx_edu_projection_release_binding (release_id, binding_id),
    KEY idx_edu_projection_release_version (release_id, resource_version_id),
    KEY idx_edu_projection_version_page (resource_version_id, page_no),
    CONSTRAINT fk_edu_projection_session FOREIGN KEY (session_id, binding_id)
        REFERENCES edu_class_session (id, binding_id),
    CONSTRAINT fk_edu_projection_release_binding FOREIGN KEY (release_id, binding_id)
        REFERENCES edu_resource_class_release (id, binding_id),
    CONSTRAINT fk_edu_projection_release_version FOREIGN KEY (release_id, resource_version_id)
        REFERENCES edu_resource_class_release (id, resource_version_id),
    CONSTRAINT fk_edu_projection_page FOREIGN KEY (resource_version_id, page_no)
        REFERENCES edu_resource_page (resource_version_id, page_no),
    CONSTRAINT chk_edu_projection_fields CHECK
        ((release_id IS NULL AND resource_version_id IS NULL AND page_no IS NULL) OR
         (release_id IS NOT NULL AND resource_version_id IS NOT NULL AND page_no IS NOT NULL)),
    CONSTRAINT chk_edu_projection_browse CHECK (student_can_browse IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_session_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    session_id BIGINT NOT NULL,
    seq_no BIGINT UNSIGNED NOT NULL,
    event_kind VARCHAR(24) NOT NULL,
    visibility VARCHAR(16) NOT NULL,
    public_payload_json JSON NULL,
    actor_user_id BIGINT NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_session_event_seq (session_id, seq_no),
    CONSTRAINT fk_edu_session_event_session FOREIGN KEY (session_id)
        REFERENCES edu_class_session (id),
    CONSTRAINT chk_edu_session_event_visibility CHECK (visibility IN ('PUBLIC', 'TEACHER')),
    CONSTRAINT chk_edu_session_event_payload CHECK
        (visibility = 'PUBLIC' OR public_payload_json IS NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- activity: standalone or classroom attendance/quiz, with immutable target snapshot.
CREATE TABLE edu_activity_instance (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    session_id BIGINT NULL,
    template_id BIGINT NULL,
    activity_kind VARCHAR(16) NOT NULL,
    title VARCHAR(160) NOT NULL,
    question_id BIGINT NULL,
    question_version_id BIGINT NULL,
    opens_at DATETIME(3) NOT NULL,
    closes_at DATETIME(3) NOT NULL,
    answer_opens_at DATETIME(3) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    active_kind VARCHAR(16) GENERATED ALWAYS AS
        (CASE WHEN status = 'OPEN' THEN activity_kind ELSE NULL END) STORED,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_activity_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_activity_id_class (id, teaching_class_id),
    UNIQUE KEY uq_edu_activity_active_kind (binding_id, active_kind),
    KEY idx_edu_activity_session_binding (session_id, binding_id),
    KEY idx_edu_activity_binding_class (binding_id, teaching_class_id),
    KEY idx_edu_activity_binding_space (binding_id, course_space_id),
    KEY idx_edu_activity_template_binding_kind (template_id, binding_id, activity_kind),
    KEY idx_edu_activity_question_space (question_id, course_space_id),
    KEY idx_edu_activity_question_version (question_version_id, question_id),
    KEY idx_edu_activity_binding_status (binding_id, status, opens_at),
    CONSTRAINT fk_edu_activity_binding_class FOREIGN KEY (binding_id, teaching_class_id)
        REFERENCES edu_course_class_binding (id, teaching_class_id),
    CONSTRAINT fk_edu_activity_binding_space FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_activity_session FOREIGN KEY (session_id, binding_id)
        REFERENCES edu_class_session (id, binding_id),
    CONSTRAINT fk_edu_activity_template FOREIGN KEY (template_id, binding_id, activity_kind)
        REFERENCES edu_activity_template (id, binding_id, template_kind),
    CONSTRAINT fk_edu_activity_question_space FOREIGN KEY (question_id, course_space_id)
        REFERENCES edu_question (id, course_space_id),
    CONSTRAINT fk_edu_activity_question FOREIGN KEY (question_version_id, question_id)
        REFERENCES edu_question_version (id, question_id),
    CONSTRAINT chk_edu_activity_kind CHECK (activity_kind IN ('ATTENDANCE', 'QUIZ')),
    CONSTRAINT chk_edu_activity_question CHECK
        ((activity_kind = 'QUIZ' AND question_id IS NOT NULL AND question_version_id IS NOT NULL) OR
         (activity_kind = 'ATTENDANCE' AND question_id IS NULL AND question_version_id IS NULL)),
    CONSTRAINT chk_edu_activity_status CHECK (status IN ('DRAFT', 'OPEN', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT chk_edu_activity_times CHECK
        (closes_at > opens_at AND (answer_opens_at IS NULL OR answer_opens_at >= closes_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_activity_target (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    target_status VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    adjusted_by_user_id BIGINT NULL,
    adjustment_reason VARCHAR(500) NULL,
    captured_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_activity_target_student (activity_id, student_user_id),
    UNIQUE KEY uq_edu_activity_target_id_activity (id, activity_id),
    KEY idx_edu_activity_target_activity_class (activity_id, teaching_class_id),
    KEY idx_edu_activity_target_enrollment (enrollment_id, teaching_class_id, student_user_id),
    CONSTRAINT fk_edu_activity_target_activity FOREIGN KEY (activity_id, teaching_class_id)
        REFERENCES edu_activity_instance (id, teaching_class_id),
    CONSTRAINT fk_edu_activity_target_enrollment FOREIGN KEY
        (enrollment_id, teaching_class_id, student_user_id)
        REFERENCES edu_class_student (id, teaching_class_id, user_id),
    CONSTRAINT chk_edu_activity_target_status CHECK
        (target_status IN ('REQUIRED', 'EXEMPT', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_activity_response (
    id BIGINT NOT NULL AUTO_INCREMENT,
    activity_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    accepted_at DATETIME(3) NOT NULL,
    response_state VARCHAR(16) NOT NULL,
    current_revision_no INT UNSIGNED NOT NULL DEFAULT 1,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_activity_response_target (target_id),
    UNIQUE KEY uq_edu_activity_response_id_activity (id, activity_id),
    KEY idx_edu_activity_response_target_activity (target_id, activity_id),
    CONSTRAINT fk_edu_activity_response_target FOREIGN KEY (target_id, activity_id)
        REFERENCES edu_activity_target (id, activity_id),
    CONSTRAINT chk_edu_activity_response_state CHECK
        (response_state IN ('ON_TIME', 'LATE', 'CORRECTED')),
    CONSTRAINT chk_edu_activity_response_revision CHECK (current_revision_no > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_activity_response_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    response_id BIGINT NOT NULL,
    revision_no INT UNSIGNED NOT NULL,
    answer_json JSON NULL,
    is_correct TINYINT(1) NULL,
    recorded_by_user_id BIGINT NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    reason VARCHAR(500) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_activity_revision_no (response_id, revision_no),
    CONSTRAINT fk_edu_activity_revision_response FOREIGN KEY (response_id)
        REFERENCES edu_activity_response (id),
    CONSTRAINT chk_edu_activity_revision_no CHECK (revision_no > 0),
    CONSTRAINT chk_edu_activity_revision_correct CHECK (is_correct IS NULL OR is_correct IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- assignment: preview, classroom and homework share one submission/grade path.
CREATE TABLE edu_assignment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    lesson_id BIGINT NULL,
    session_id BIGINT NULL,
    assignment_kind VARCHAR(16) NOT NULL,
    title VARCHAR(200) NOT NULL,
    instructions TEXT NULL,
    opens_at DATETIME(3) NOT NULL,
    closes_at DATETIME(3) NOT NULL,
    max_submissions INT UNSIGNED NOT NULL DEFAULT 1,
    late_allowed TINYINT(1) NOT NULL DEFAULT 0,
    answer_opens_at DATETIME(3) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    published_at DATETIME(3) NULL,
    published_by_user_id BIGINT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_assignment_id_binding (id, binding_id),
    UNIQUE KEY uq_edu_assignment_id_class (id, teaching_class_id),
    UNIQUE KEY uq_edu_assignment_id_space (id, course_space_id),
    KEY idx_edu_assignment_binding_space (binding_id, course_space_id),
    KEY idx_edu_assignment_binding_class (binding_id, teaching_class_id),
    KEY idx_edu_assignment_lesson_binding (lesson_id, binding_id),
    KEY idx_edu_assignment_session_binding (session_id, binding_id),
    KEY idx_edu_assignment_binding_status (binding_id, status, closes_at),
    CONSTRAINT fk_edu_assignment_binding FOREIGN KEY (binding_id, course_space_id)
        REFERENCES edu_course_class_binding (id, course_space_id),
    CONSTRAINT fk_edu_assignment_binding_class FOREIGN KEY (binding_id, teaching_class_id)
        REFERENCES edu_course_class_binding (id, teaching_class_id),
    CONSTRAINT fk_edu_assignment_lesson FOREIGN KEY (lesson_id, binding_id)
        REFERENCES edu_lesson (id, binding_id),
    CONSTRAINT fk_edu_assignment_session FOREIGN KEY (session_id, binding_id)
        REFERENCES edu_class_session (id, binding_id),
    CONSTRAINT chk_edu_assignment_kind CHECK
        (assignment_kind IN ('PREVIEW', 'CLASSWORK', 'HOMEWORK')),
    CONSTRAINT chk_edu_assignment_status CHECK
        (status IN ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),
    CONSTRAINT chk_edu_assignment_times CHECK
        (closes_at > opens_at AND (answer_opens_at IS NULL OR answer_opens_at >= closes_at)),
    CONSTRAINT chk_edu_assignment_rules CHECK (max_submissions > 0 AND late_allowed IN (0, 1)),
    CONSTRAINT chk_edu_assignment_publish CHECK
        ((status = 'DRAFT' AND published_at IS NULL AND published_by_user_id IS NULL) OR
         (status <> 'DRAFT' AND published_at IS NOT NULL AND published_by_user_id IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_assignment_item (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    course_space_id BIGINT NOT NULL,
    sort_order INT UNSIGNED NOT NULL,
    item_kind VARCHAR(16) NOT NULL,
    prompt TEXT NOT NULL,
    question_id BIGINT NULL,
    question_version_id BIGINT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_assignment_item_id_assignment (id, assignment_id),
    UNIQUE KEY uq_edu_assignment_item_id_question (id, question_id, question_version_id),
    UNIQUE KEY uq_edu_assignment_item_order (assignment_id, sort_order),
    KEY idx_edu_assignment_item_assignment_space (assignment_id, course_space_id),
    KEY idx_edu_assignment_item_question_space (question_id, course_space_id),
    KEY idx_edu_assignment_item_question_version (question_version_id, question_id),
    CONSTRAINT fk_edu_assignment_item_assignment FOREIGN KEY (assignment_id, course_space_id)
        REFERENCES edu_assignment (id, course_space_id),
    CONSTRAINT fk_edu_assignment_item_question_space FOREIGN KEY (question_id, course_space_id)
        REFERENCES edu_question (id, course_space_id),
    CONSTRAINT fk_edu_assignment_item_question_version FOREIGN KEY (question_version_id, question_id)
        REFERENCES edu_question_version (id, question_id),
    CONSTRAINT chk_edu_assignment_item_kind CHECK (item_kind IN ('QUESTION', 'TEXT', 'FILE')),
    CONSTRAINT chk_edu_assignment_item_question CHECK
        ((item_kind = 'QUESTION' AND question_id IS NOT NULL AND question_version_id IS NOT NULL) OR
         (item_kind <> 'QUESTION' AND question_id IS NULL AND question_version_id IS NULL)),
    CONSTRAINT chk_edu_assignment_item_score CHECK (max_score >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_assignment_target (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    teaching_class_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    enrollment_id BIGINT NOT NULL,
    target_status VARCHAR(16) NOT NULL DEFAULT 'REQUIRED',
    adjusted_by_user_id BIGINT NULL,
    adjustment_reason VARCHAR(500) NULL,
    captured_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_assignment_target_student (assignment_id, student_user_id),
    UNIQUE KEY uq_edu_assignment_target_id_assignment (id, assignment_id),
    UNIQUE KEY uq_edu_assignment_target_id_student (id, student_user_id),
    KEY idx_edu_assignment_target_assignment_class (assignment_id, teaching_class_id),
    KEY idx_edu_assignment_target_enrollment (enrollment_id, teaching_class_id, student_user_id),
    CONSTRAINT fk_edu_assignment_target_assignment FOREIGN KEY (assignment_id, teaching_class_id)
        REFERENCES edu_assignment (id, teaching_class_id),
    CONSTRAINT fk_edu_assignment_target_enrollment FOREIGN KEY
        (enrollment_id, teaching_class_id, student_user_id)
        REFERENCES edu_class_student (id, teaching_class_id, user_id),
    CONSTRAINT chk_edu_assignment_target_status CHECK
        (target_status IN ('REQUIRED', 'EXEMPT', 'REMOVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_assignment_submission (
    id BIGINT NOT NULL AUTO_INCREMENT,
    assignment_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_submission_target (target_id),
    UNIQUE KEY uq_edu_submission_id_assignment (id, assignment_id),
    UNIQUE KEY uq_edu_submission_id_target (id, target_id),
    KEY idx_edu_submission_target_assignment (target_id, assignment_id),
    CONSTRAINT fk_edu_submission_target FOREIGN KEY (target_id, assignment_id)
        REFERENCES edu_assignment_target (id, assignment_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_submission_version (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    version_no INT UNSIGNED NOT NULL,
    submitted_at DATETIME(3) NOT NULL,
    is_late TINYINT(1) NOT NULL DEFAULT 0,
    superseded_at DATETIME(3) NULL,
    active_submission_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN superseded_at IS NULL THEN submission_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_submission_version_id_submission (id, submission_id),
    UNIQUE KEY uq_edu_submission_version_id_assignment (id, assignment_id),
    UNIQUE KEY uq_edu_submission_version_no (submission_id, version_no),
    UNIQUE KEY uq_edu_submission_version_active (active_submission_id),
    KEY idx_edu_submission_version_submission_assignment (submission_id, assignment_id),
    CONSTRAINT fk_edu_submission_version_submission FOREIGN KEY (submission_id, assignment_id)
        REFERENCES edu_assignment_submission (id, assignment_id),
    CONSTRAINT chk_edu_submission_version_no CHECK (version_no > 0),
    CONSTRAINT chk_edu_submission_version_late CHECK (is_late IN (0, 1)),
    CONSTRAINT chk_edu_submission_version_dates CHECK
        (superseded_at IS NULL OR superseded_at >= submitted_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_submission_answer (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_version_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    assignment_item_id BIGINT NOT NULL,
    answer_json JSON NULL,
    attachment_storage_key VARCHAR(512) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_submission_answer_item (submission_version_id, assignment_item_id),
    KEY idx_edu_submission_answer_version_assignment (submission_version_id, assignment_id),
    KEY idx_edu_submission_answer_item_assignment (assignment_item_id, assignment_id),
    CONSTRAINT fk_edu_submission_answer_version FOREIGN KEY (submission_version_id, assignment_id)
        REFERENCES edu_submission_version (id, assignment_id),
    CONSTRAINT fk_edu_submission_answer_item FOREIGN KEY (assignment_item_id, assignment_id)
        REFERENCES edu_assignment_item (id, assignment_id),
    CONSTRAINT chk_edu_submission_answer_content CHECK
        (answer_json IS NOT NULL OR attachment_storage_key IS NOT NULL)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_grade_revision (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_version_id BIGINT NOT NULL,
    revision_no INT UNSIGNED NOT NULL,
    score DECIMAL(10,2) NOT NULL,
    max_score DECIMAL(10,2) NOT NULL,
    feedback TEXT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    graded_by_user_id BIGINT NOT NULL,
    confirmed_by_user_id BIGINT NULL,
    confirmed_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_grade_id_version_status (id, submission_version_id, status),
    UNIQUE KEY uq_edu_grade_version_revision (submission_version_id, revision_no),
    CONSTRAINT fk_edu_grade_submission_version FOREIGN KEY (submission_version_id)
        REFERENCES edu_submission_version (id),
    CONSTRAINT chk_edu_grade_revision_no CHECK (revision_no > 0),
    CONSTRAINT chk_edu_grade_score CHECK (score >= 0 AND max_score >= 0 AND score <= max_score),
    CONSTRAINT chk_edu_grade_status CHECK (status IN ('DRAFT', 'CONFIRMED')),
    CONSTRAINT chk_edu_grade_confirmation CHECK
        ((status = 'DRAFT' AND confirmed_by_user_id IS NULL AND confirmed_at IS NULL) OR
         (status = 'CONFIRMED' AND confirmed_by_user_id IS NOT NULL AND confirmed_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_grade_publication (
    id BIGINT NOT NULL AUTO_INCREMENT,
    submission_id BIGINT NOT NULL,
    submission_version_id BIGINT NOT NULL,
    grade_revision_id BIGINT NOT NULL,
    required_grade_status VARCHAR(16) NOT NULL DEFAULT 'CONFIRMED',
    publication_no INT UNSIGNED NOT NULL,
    published_by_user_id BIGINT NOT NULL,
    published_at DATETIME(3) NOT NULL,
    withdrawn_at DATETIME(3) NULL,
    withdrawn_by_user_id BIGINT NULL,
    withdrawal_reason VARCHAR(500) NULL,
    active_submission_id BIGINT GENERATED ALWAYS AS
        (CASE WHEN withdrawn_at IS NULL THEN submission_id ELSE NULL END) STORED,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_publication_no (submission_id, publication_no),
    UNIQUE KEY uq_edu_publication_active (active_submission_id),
    KEY idx_edu_publication_version_submission (submission_version_id, submission_id),
    KEY idx_edu_publication_grade_status
        (grade_revision_id, submission_version_id, required_grade_status),
    CONSTRAINT fk_edu_publication_version FOREIGN KEY (submission_version_id, submission_id)
        REFERENCES edu_submission_version (id, submission_id),
    CONSTRAINT fk_edu_publication_grade FOREIGN KEY
        (grade_revision_id, submission_version_id, required_grade_status)
        REFERENCES edu_grade_revision (id, submission_version_id, status),
    CONSTRAINT chk_edu_publication_grade_status CHECK (required_grade_status = 'CONFIRMED'),
    CONSTRAINT chk_edu_publication_no CHECK (publication_no > 0),
    CONSTRAINT chk_edu_publication_withdrawal CHECK
        ((withdrawn_at IS NULL AND withdrawn_by_user_id IS NULL AND withdrawal_reason IS NULL) OR
         (withdrawn_at IS NOT NULL AND withdrawn_at >= published_at
          AND withdrawn_by_user_id IS NOT NULL AND withdrawal_reason IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    actor_user_id BIGINT NOT NULL,
    operation_kind VARCHAR(40) NOT NULL,
    scope_id BIGINT NOT NULL,
    key_hash BINARY(32) NOT NULL,
    request_hash BINARY(32) NOT NULL,
    result_id BIGINT NULL,
    status VARCHAR(16) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    expires_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_idempotency_scope (actor_user_id, operation_kind, scope_id, key_hash),
    KEY idx_edu_idempotency_expiry (expires_at),
    CONSTRAINT chk_edu_idempotency_status CHECK (status IN ('PENDING', 'SUCCEEDED', 'FAILED')),
    CONSTRAINT chk_edu_idempotency_expiry CHECK (expires_at > created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- learning: raw evidence remains distinct from materialized progress.
CREATE TABLE edu_learning_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    event_kind VARCHAR(24) NOT NULL,
    source_kind VARCHAR(24) NOT NULL,
    source_id BIGINT NOT NULL,
    source_version_id BIGINT NULL,
    client_event_key VARCHAR(100) NOT NULL,
    occurred_at DATETIME(3) NOT NULL,
    received_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    metadata_json JSON NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_learning_event_dedupe (student_user_id, client_event_key),
    KEY idx_edu_learning_event_binding_student_time (binding_id, student_user_id, occurred_at),
    CONSTRAINT fk_edu_learning_event_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_learning_event_kind CHECK
        (event_kind IN ('RESOURCE_OPEN', 'VIDEO_WATCH', 'PREVIEW_COMPLETE',
                        'ACTIVITY_COMPLETE', 'ASSIGNMENT_SUBMIT', 'REMEDIATION_COMPLETE')),
    CONSTRAINT chk_edu_learning_source_kind CHECK
        (source_kind IN ('RESOURCE_RELEASE', 'ACTIVITY', 'ASSIGNMENT', 'QUESTION'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_video_watch_interval (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    student_user_id BIGINT NOT NULL,
    release_id BIGINT NOT NULL,
    resource_version_id BIGINT NOT NULL,
    start_ms BIGINT UNSIGNED NOT NULL,
    end_ms BIGINT UNSIGNED NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_edu_video_interval_student_version (student_user_id, resource_version_id, start_ms),
    KEY idx_edu_video_interval_release_binding (release_id, binding_id),
    KEY idx_edu_video_interval_release_version (release_id, resource_version_id),
    CONSTRAINT fk_edu_video_interval_release_binding FOREIGN KEY (release_id, binding_id)
        REFERENCES edu_resource_class_release (id, binding_id),
    CONSTRAINT fk_edu_video_interval_release_version FOREIGN KEY (release_id, resource_version_id)
        REFERENCES edu_resource_class_release (id, resource_version_id),
    CONSTRAINT chk_edu_video_interval_range CHECK (end_ms > start_ms)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_wrong_question (
    id BIGINT NOT NULL AUTO_INCREMENT,
    student_user_id BIGINT NOT NULL,
    assignment_id BIGINT NOT NULL,
    submission_id BIGINT NOT NULL,
    target_id BIGINT NOT NULL,
    submission_version_id BIGINT NOT NULL,
    assignment_item_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    question_version_id BIGINT NOT NULL,
    recorded_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_wrong_question_evidence
        (student_user_id, submission_version_id, assignment_item_id),
    KEY idx_edu_wrong_question_version_assignment (submission_version_id, assignment_id),
    KEY idx_edu_wrong_question_version_submission (submission_version_id, submission_id),
    KEY idx_edu_wrong_question_submission_target (submission_id, target_id),
    KEY idx_edu_wrong_question_target_student (target_id, student_user_id),
    KEY idx_edu_wrong_question_item_assignment (assignment_item_id, assignment_id),
    KEY idx_edu_wrong_question_item_question
        (assignment_item_id, question_id, question_version_id),
    CONSTRAINT fk_edu_wrong_question_submission FOREIGN KEY (submission_version_id, assignment_id)
        REFERENCES edu_submission_version (id, assignment_id),
    CONSTRAINT fk_edu_wrong_question_version_submission FOREIGN KEY
        (submission_version_id, submission_id)
        REFERENCES edu_submission_version (id, submission_id),
    CONSTRAINT fk_edu_wrong_question_submission_target FOREIGN KEY (submission_id, target_id)
        REFERENCES edu_assignment_submission (id, target_id),
    CONSTRAINT fk_edu_wrong_question_target_student FOREIGN KEY (target_id, student_user_id)
        REFERENCES edu_assignment_target (id, student_user_id),
    CONSTRAINT fk_edu_wrong_question_item FOREIGN KEY (assignment_item_id, assignment_id)
        REFERENCES edu_assignment_item (id, assignment_id),
    CONSTRAINT fk_edu_wrong_question_item_question FOREIGN KEY
        (assignment_item_id, question_id, question_version_id)
        REFERENCES edu_assignment_item (id, question_id, question_version_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- analysis: nullable value distinguishes missing evidence from a measured zero.
CREATE TABLE edu_metric_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    metric_code VARCHAR(60) NOT NULL,
    definition_version INT UNSIGNED NOT NULL,
    subject_kind VARCHAR(20) NOT NULL,
    subject_id BIGINT NOT NULL,
    window_starts_at DATETIME(3) NOT NULL,
    window_ends_at DATETIME(3) NOT NULL,
    evidence_cutoff_at DATETIME(3) NOT NULL,
    numerator DECIMAL(18,4) NULL,
    denominator DECIMAL(18,4) NULL,
    sample_count INT UNSIGNED NOT NULL DEFAULT 0,
    metric_value DECIMAL(18,4) NULL,
    source_watermark VARCHAR(120) NULL,
    calculated_at DATETIME(3) NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_metric_snapshot_identity
        (binding_id, metric_code, definition_version, subject_kind, subject_id,
         window_starts_at, window_ends_at, evidence_cutoff_at),
    KEY idx_edu_metric_snapshot_subject (binding_id, subject_kind, subject_id, calculated_at),
    CONSTRAINT fk_edu_metric_snapshot_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_metric_snapshot_subject CHECK
        (subject_kind IN ('CLASS', 'STUDENT', 'KNOWLEDGE_POINT')),
    CONSTRAINT chk_edu_metric_snapshot_window CHECK (window_ends_at > window_starts_at),
    CONSTRAINT chk_edu_metric_snapshot_version CHECK (definition_version > 0),
    CONSTRAINT chk_edu_metric_snapshot_numbers CHECK
        ((numerator IS NULL OR numerator >= 0) AND
         (denominator IS NULL OR denominator >= 0) AND
         (metric_value IS NULL OR metric_value >= 0) AND
         (denominator IS NULL OR denominator > 0 OR metric_value IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- ai: M0 Q&A audit and citations; the service checks live authorization twice.
CREATE TABLE edu_ai_task (
    id BIGINT NOT NULL AUTO_INCREMENT,
    requester_user_id BIGINT NOT NULL,
    requester_role VARCHAR(16) NOT NULL,
    binding_id BIGINT NULL,
    conversation_key BINARY(16) NOT NULL,
    request_key BINARY(16) NOT NULL,
    question_hash BINARY(32) NOT NULL,
    question_storage_key VARCHAR(512) NULL,
    model_code VARCHAR(100) NOT NULL,
    prompt_template_version VARCHAR(60) NOT NULL,
    retrieval_index_version VARCHAR(60) NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    answer_storage_key VARCHAR(512) NULL,
    content_retention_until DATETIME(3) NULL,
    input_tokens INT UNSIGNED NULL,
    output_tokens INT UNSIGNED NULL,
    error_code VARCHAR(60) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    finished_at DATETIME(3) NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_ai_task_request (requester_user_id, request_key),
    UNIQUE KEY uq_edu_ai_task_id_binding (id, binding_id),
    KEY idx_edu_ai_task_conversation (requester_user_id, conversation_key, created_at),
    KEY idx_edu_ai_task_binding_status (binding_id, status),
    CONSTRAINT fk_edu_ai_task_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_ai_task_role CHECK (requester_role IN ('TEACHER', 'STUDENT')),
    CONSTRAINT chk_edu_ai_task_status CHECK
        (status IN ('PENDING', 'RUNNING', 'SUCCEEDED', 'FAILED', 'TIMED_OUT')),
    CONSTRAINT chk_edu_ai_task_finished CHECK
        ((status IN ('PENDING', 'RUNNING') AND finished_at IS NULL) OR
         (status IN ('SUCCEEDED', 'FAILED', 'TIMED_OUT')
          AND finished_at IS NOT NULL AND finished_at >= created_at)),
    CONSTRAINT chk_edu_ai_task_content_retention CHECK
        ((question_storage_key IS NULL AND answer_storage_key IS NULL) OR
         (content_retention_until IS NOT NULL AND content_retention_until > created_at))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_ai_citation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    ai_task_id BIGINT NOT NULL,
    binding_id BIGINT NOT NULL,
    release_id BIGINT NOT NULL,
    resource_version_id BIGINT NOT NULL,
    page_no INT UNSIGNED NULL,
    quote_hash BINARY(32) NULL,
    sort_order INT UNSIGNED NOT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_ai_citation_order (ai_task_id, sort_order),
    KEY idx_edu_ai_citation_task_binding (ai_task_id, binding_id),
    KEY idx_edu_ai_citation_release_binding (release_id, binding_id),
    KEY idx_edu_ai_citation_release_version (release_id, resource_version_id),
    KEY idx_edu_ai_citation_version_page (resource_version_id, page_no),
    CONSTRAINT fk_edu_ai_citation_task FOREIGN KEY (ai_task_id, binding_id)
        REFERENCES edu_ai_task (id, binding_id),
    CONSTRAINT fk_edu_ai_citation_release_binding FOREIGN KEY (release_id, binding_id)
        REFERENCES edu_resource_class_release (id, binding_id),
    CONSTRAINT fk_edu_ai_citation_release_version FOREIGN KEY (release_id, resource_version_id)
        REFERENCES edu_resource_class_release (id, resource_version_id),
    CONSTRAINT fk_edu_ai_citation_page FOREIGN KEY (resource_version_id, page_no)
        REFERENCES edu_resource_page (resource_version_id, page_no)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

-- course communication: announcements are scoped to one published class binding.
CREATE TABLE edu_course_announcement (
    id BIGINT NOT NULL AUTO_INCREMENT,
    binding_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    body TEXT NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'DRAFT',
    opens_at DATETIME(3) NULL,
    closes_at DATETIME(3) NULL,
    published_by_user_id BIGINT NULL,
    created_by_user_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_edu_announcement_binding_window (binding_id, status, opens_at, closes_at),
    CONSTRAINT fk_edu_announcement_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_announcement_status CHECK
        (status IN ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
    CONSTRAINT chk_edu_announcement_window CHECK
        (opens_at IS NULL OR closes_at IS NULL OR closes_at > opens_at),
    CONSTRAINT chk_edu_announcement_publish CHECK
        ((status = 'DRAFT' AND published_by_user_id IS NULL) OR
         (status <> 'DRAFT' AND published_by_user_id IS NOT NULL AND opens_at IS NOT NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE edu_user_notice (
    id BIGINT NOT NULL AUTO_INCREMENT,
    recipient_user_id BIGINT NOT NULL,
    binding_id BIGINT NULL,
    notice_kind VARCHAR(24) NOT NULL,
    source_id BIGINT NOT NULL,
    title VARCHAR(200) NOT NULL,
    visible_at DATETIME(3) NOT NULL,
    read_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uq_edu_notice_recipient_source
        (recipient_user_id, notice_kind, source_id),
    KEY idx_edu_notice_recipient_read (recipient_user_id, read_at, visible_at),
    KEY idx_edu_notice_binding (binding_id),
    CONSTRAINT fk_edu_notice_binding FOREIGN KEY (binding_id)
        REFERENCES edu_course_class_binding (id),
    CONSTRAINT chk_edu_notice_kind CHECK
        (notice_kind IN ('ANNOUNCEMENT', 'PREVIEW', 'ACTIVITY', 'ASSIGNMENT', 'GRADE')),
    CONSTRAINT chk_edu_notice_read CHECK (read_at IS NULL OR read_at >= visible_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;


-- SOURCE demo-v3-m0-seed.sql
-- Synthetic V3.0 M0 teaching demo data. Never use in a production database.
-- Run once, after both schema files, in the requested zhijiao_m0 database.
-- Back up existing data first if this database is no longer empty.
-- All user/organization IDs below are fictional logical IDs; no RuoYi accounts are created.
-- File and AI object keys are placeholders; no files are uploaded and no model is called.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS edu_demo_seed_20261022;
DELIMITER //
CREATE PROCEDURE edu_demo_seed_20261022()
BEGIN
    DECLARE v_base BIGINT DEFAULT 910000000001;
    DECLARE v_org BIGINT DEFAULT 920000000001;
    DECLARE v_teacher BIGINT DEFAULT 930000000001;
    DECLARE v_student_a BIGINT DEFAULT 930000000101;
    DECLARE v_student_b BIGINT DEFAULT 930000000102;
    DECLARE v_student_c BIGINT DEFAULT 930000000103;
    DECLARE v_student_d BIGINT DEFAULT 930000000104;
    DECLARE v_now DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3);
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF EXISTS (SELECT 1 FROM edu_term WHERE id = v_base) THEN
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = 'Demo data already exists; do not rerun seed';
    END IF;

    START TRANSACTION;
    -- course: one fictional organization, two teaching classes, four students.
    INSERT INTO edu_term
        (id, organization_id, code, name, starts_on, ends_on)
    VALUES (v_base, v_org, 'DEMO-TERM', '演示学期',
            DATE(v_now - INTERVAL 30 DAY), DATE(v_now + INTERVAL 180 DAY));
    INSERT INTO edu_official_course
        (id, organization_id, code, name)
    VALUES (v_base, v_org, 'DEMO-SMART-EDU', '智慧教育导论（演示）');
    INSERT INTO edu_course_space
        (id, organization_id, official_course_id, owner_user_id, title,
         summary, learning_objectives, status)
    VALUES (v_base, v_org, v_base, v_teacher, '智慧教育导论·教师课程空间',
            '仅用于演示数据库关系的虚构课程。',
            '认识课程资源、课堂互动与学习反馈。', 'ACTIVE');
    INSERT INTO edu_course_unit
        (id, course_space_id, parent_unit_id, unit_kind, title, sort_order)
    VALUES (v_base, v_base, NULL, 'CHAPTER', '第一章：智慧学习', 1),
           (v_base + 1, v_base, v_base, 'SECTION', '第一节：形成性评价', 1);
    INSERT INTO edu_knowledge_point
        (id, course_space_id, unit_id, code, name)
    VALUES (v_base, v_base, v_base + 1, 'KP-DEMO-1', '形成性评价的作用');
    INSERT INTO edu_teaching_class
        (id, organization_id, term_id, official_course_id, code, name,
         class_kind, status)
    VALUES (v_base, v_org, v_base, v_base, 'DEMO-A', '演示教学班 A',
            'FORMAL', 'ACTIVE'),
           (v_base + 1, v_org, v_base, v_base, 'DEMO-B', '演示教学班 B',
            'FORMAL', 'ACTIVE');
    INSERT INTO edu_class_teacher
        (id, teaching_class_id, user_id, teacher_role, can_publish,
         can_grade, can_export, valid_from, granted_by_user_id)
    VALUES (v_base, v_base, v_teacher, 'LEAD', 1, 1, 1,
            v_now - INTERVAL 30 DAY, v_teacher),
           (v_base + 1, v_base + 1, v_teacher, 'LEAD', 1, 1, 1,
            v_now - INTERVAL 30 DAY, v_teacher);
    INSERT INTO edu_class_student
        (id, teaching_class_id, user_id, joined_at, recorded_by_user_id)
    VALUES (v_base, v_base, v_student_a, v_now - INTERVAL 20 DAY, v_teacher),
           (v_base + 1, v_base, v_student_b, v_now - INTERVAL 20 DAY, v_teacher),
           (v_base + 2, v_base, v_student_c, v_now - INTERVAL 20 DAY, v_teacher),
           (v_base + 3, v_base + 1, v_student_d, v_now - INTERVAL 20 DAY, v_teacher);
    INSERT INTO edu_course_class_binding
        (id, organization_id, teaching_class_id, course_space_id,
         bound_by_user_id, published_at)
    VALUES (v_base, v_org, v_base, v_base, v_teacher, v_now - INTERVAL 14 DAY),
           (v_base + 1, v_org, v_base + 1, v_base, v_teacher,
            v_now - INTERVAL 14 DAY);

    -- resource: one slide file and one video file reused by the same course.
    INSERT INTO edu_resource
        (id, course_space_id, title, resource_kind, status, created_by_user_id)
    VALUES (v_base, v_base, '第一讲课件（演示，无实际文件）', 'FILE', 'ACTIVE', v_teacher),
           (v_base + 1, v_base, '导学视频（演示，无实际文件）', 'FILE', 'ACTIVE', v_teacher);
    INSERT INTO edu_resource_version
        (id, resource_id, resource_kind, version_no, storage_key,
         mime_type, byte_size, processing_status, created_by_user_id)
    VALUES (v_base, v_base, 'FILE', 1, 'demo-only/slides-v1.pptx',
            'application/vnd.openxmlformats-officedocument.presentationml.presentation',
            102400, 'READY', v_teacher),
           (v_base + 1, v_base + 1, 'FILE', 1, 'demo-only/intro-v1.mp4',
            'video/mp4', 204800, 'READY', v_teacher);
    INSERT INTO edu_resource_class_release
        (id, binding_id, course_space_id, resource_id, resource_version_id,
         released_by_user_id, released_at, opens_at, closes_at)
    VALUES (v_base, v_base, v_base, v_base, v_base, v_teacher,
            v_now - INTERVAL 7 DAY, v_now - INTERVAL 7 DAY, v_now + INTERVAL 30 DAY),
           (v_base + 1, v_base + 1, v_base, v_base, v_base, v_teacher,
            v_now - INTERVAL 7 DAY, v_now - INTERVAL 7 DAY, v_now + INTERVAL 30 DAY),
           (v_base + 2, v_base, v_base, v_base + 1, v_base + 1, v_teacher,
            v_now - INTERVAL 7 DAY, v_now - INTERVAL 7 DAY, v_now + INTERVAL 30 DAY);
    INSERT INTO edu_resource_page
        (id, resource_version_id, page_no, preview_storage_key, width_px, height_px)
    VALUES (v_base, v_base, 1, 'demo-only/slides-v1/page-1.png', 1280, 720),
           (v_base + 1, v_base, 2, 'demo-only/slides-v1/page-2.png', 1280, 720);
    INSERT INTO edu_question
        (id, course_space_id, title, status, created_by_user_id)
    VALUES (v_base, v_base, '形成性评价基础题', 'ACTIVE', v_teacher);
    INSERT INTO edu_question_version
        (id, question_id, version_no, question_kind, stem,
         options_json, answer_json, explanation, max_score, created_by_user_id)
    VALUES (v_base, v_base, 1, 'SINGLE',
            '以下哪一项最能体现形成性评价？',
            JSON_ARRAY('只在期末给分', '在学习过程中反馈并调整教学', '只统计登录次数'),
            JSON_OBJECT('correct_index', 1),
            '形成性评价强调学习过程中的证据与反馈。', 10, v_teacher);
    INSERT INTO edu_question_knowledge_point
        (question_id, course_space_id, knowledge_point_id)
    VALUES (v_base, v_base, v_base);

    -- prepare and classroom: one active class with a public slide snapshot.
    INSERT INTO edu_lesson
        (id, binding_id, course_space_id, unit_id, title, scheduled_at,
         status, created_by_user_id)
    VALUES (v_base, v_base, v_base, v_base + 1, '第 1 次课：形成性评价',
            v_now, 'ACTIVE', v_teacher);
    INSERT INTO edu_lesson_plan
        (id, lesson_id, binding_id, version_no, title, private_notes,
         status, created_by_user_id)
    VALUES (v_base, v_base, v_base, 1, '第一讲备课方案',
            '演示用教师私有备注，不应投放到学生端。', 'READY', v_teacher);
    INSERT INTO edu_plan_material
        (id, plan_id, binding_id, course_space_id, resource_id,
         resource_version_id, sort_order, private_notes)
    VALUES (v_base, v_base, v_base, v_base, v_base, v_base, 1,
            '先讲概念，再打开随堂题。');
    INSERT INTO edu_activity_template
        (id, plan_id, binding_id, course_space_id, template_kind, title,
         question_id, question_version_id, duration_seconds, status,
         created_by_user_id)
    VALUES (v_base, v_base, v_base, v_base, 'QUIZ', '课堂单选题模板',
            v_base, v_base, 900, 'READY', v_teacher);
    INSERT INTO edu_class_session
        (id, binding_id, lesson_id, plan_id, status,
         started_by_user_id, started_at)
    VALUES (v_base, v_base, v_base, v_base, 'RUNNING',
            v_teacher, v_now - INTERVAL 30 MINUTE);
    INSERT INTO edu_session_projection
        (session_id, binding_id, state_seq, release_id, resource_version_id,
         page_no, student_can_browse, updated_at)
    VALUES (v_base, v_base, 2, v_base, v_base, 2, 0, v_now);
    INSERT INTO edu_session_event
        (id, session_id, seq_no, event_kind, visibility,
         public_payload_json, actor_user_id, occurred_at)
    VALUES (v_base, v_base, 1, 'PAGE_CHANGED', 'PUBLIC',
            JSON_OBJECT('page', 1), v_teacher, v_now - INTERVAL 25 MINUTE),
           (v_base + 1, v_base, 2, 'PAGE_CHANGED', 'PUBLIC',
            JSON_OBJECT('page', 2), v_teacher, v_now - INTERVAL 20 MINUTE);

    -- activity: a quiz and attendance can be open together in the same class.
    INSERT INTO edu_activity_instance
        (id, binding_id, teaching_class_id, course_space_id, session_id,
         template_id, activity_kind, title, question_id, question_version_id,
         opens_at, closes_at, answer_opens_at, status, created_by_user_id)
    VALUES (v_base, v_base, v_base, v_base, v_base, v_base,
            'QUIZ', '课堂随堂题', v_base, v_base,
            v_now - INTERVAL 15 MINUTE, v_now + INTERVAL 15 MINUTE,
            v_now + INTERVAL 15 MINUTE, 'OPEN', v_teacher),
           (v_base + 1, v_base, v_base, v_base, v_base, NULL,
            'ATTENDANCE', '本次课堂签到', NULL, NULL,
            v_now - INTERVAL 20 MINUTE, v_now + INTERVAL 10 MINUTE,
            NULL, 'OPEN', v_teacher);
    INSERT INTO edu_activity_target
        (id, activity_id, teaching_class_id, student_user_id,
         enrollment_id, captured_at)
    VALUES (v_base, v_base, v_base, v_student_a, v_base, v_now - INTERVAL 15 MINUTE),
           (v_base + 1, v_base, v_base, v_student_b, v_base + 1, v_now - INTERVAL 15 MINUTE),
           (v_base + 2, v_base, v_base, v_student_c, v_base + 2, v_now - INTERVAL 15 MINUTE),
           (v_base + 3, v_base + 1, v_base, v_student_a, v_base, v_now - INTERVAL 20 MINUTE),
           (v_base + 4, v_base + 1, v_base, v_student_b, v_base + 1, v_now - INTERVAL 20 MINUTE),
           (v_base + 5, v_base + 1, v_base, v_student_c, v_base + 2, v_now - INTERVAL 20 MINUTE);
    INSERT INTO edu_activity_response
        (id, activity_id, target_id, accepted_at, response_state)
    VALUES (v_base, v_base, v_base, v_now - INTERVAL 8 MINUTE, 'ON_TIME'),
           (v_base + 1, v_base, v_base + 1, v_now - INTERVAL 7 MINUTE, 'ON_TIME'),
           (v_base + 2, v_base + 1, v_base + 3, v_now - INTERVAL 6 MINUTE, 'ON_TIME');
    INSERT INTO edu_activity_response_revision
        (id, response_id, revision_no, answer_json, is_correct,
         recorded_by_user_id, recorded_at)
    VALUES (v_base, v_base, 1, JSON_OBJECT('choice_index', 1), 1,
            v_student_a, v_now - INTERVAL 8 MINUTE),
           (v_base + 1, v_base + 1, 1, JSON_OBJECT('choice_index', 0), 0,
            v_student_b, v_now - INTERVAL 7 MINUTE),
           (v_base + 2, v_base + 2, 1, NULL, NULL,
            v_student_a, v_now - INTERVAL 6 MINUTE);

    -- assignment: preview, classroom work and homework use the same engine.
    INSERT INTO edu_assignment
        (id, binding_id, teaching_class_id, course_space_id, lesson_id,
         session_id, assignment_kind, title, instructions, opens_at,
         closes_at, max_submissions, answer_opens_at, status,
         published_at, published_by_user_id, created_by_user_id)
    VALUES (v_base, v_base, v_base, v_base, v_base, NULL,
            'HOMEWORK', '课后作业：形成性评价', '完成单选题。',
            v_now - INTERVAL 7 DAY, v_now - INTERVAL 1 DAY, 2,
            v_now - INTERVAL 1 DAY, 'CLOSED',
            v_now - INTERVAL 7 DAY, v_teacher, v_teacher),
           (v_base + 1, v_base, v_base, v_base, v_base, NULL,
            'PREVIEW', '课前预习：阅读第一讲', '阅读课件并写一句收获。',
            v_now - INTERVAL 1 DAY, v_now + INTERVAL 3 DAY, 1,
            v_now + INTERVAL 3 DAY, 'PUBLISHED',
            v_now - INTERVAL 1 DAY, v_teacher, v_teacher),
           (v_base + 2, v_base, v_base, v_base, v_base, v_base,
            'CLASSWORK', '课堂小作业：写出反馈例子', '简述一个形成性反馈例子。',
            v_now - INTERVAL 10 MINUTE, v_now + INTERVAL 50 MINUTE, 1,
            v_now + INTERVAL 50 MINUTE, 'PUBLISHED',
            v_now - INTERVAL 10 MINUTE, v_teacher, v_teacher);
    INSERT INTO edu_assignment_item
        (id, assignment_id, course_space_id, sort_order, item_kind,
         prompt, question_id, question_version_id, max_score)
    VALUES (v_base, v_base, v_base, 1, 'QUESTION',
            '以下哪一项最能体现形成性评价？', v_base, v_base, 10),
           (v_base + 1, v_base + 1, v_base, 1, 'TEXT',
            '写一句你对第一讲的收获。', NULL, NULL, 0),
           (v_base + 2, v_base + 2, v_base, 1, 'TEXT',
            '简述一个教师反馈学生学习过程的例子。', NULL, NULL, 5);
    INSERT INTO edu_assignment_target
        (id, assignment_id, teaching_class_id, student_user_id,
         enrollment_id, captured_at)
    VALUES (v_base, v_base, v_base, v_student_a, v_base, v_now - INTERVAL 7 DAY),
           (v_base + 1, v_base, v_base, v_student_b, v_base + 1, v_now - INTERVAL 7 DAY),
           (v_base + 2, v_base, v_base, v_student_c, v_base + 2, v_now - INTERVAL 7 DAY),
           (v_base + 3, v_base + 1, v_base, v_student_a, v_base, v_now - INTERVAL 1 DAY),
           (v_base + 4, v_base + 1, v_base, v_student_b, v_base + 1, v_now - INTERVAL 1 DAY),
           (v_base + 5, v_base + 1, v_base, v_student_c, v_base + 2, v_now - INTERVAL 1 DAY),
           (v_base + 6, v_base + 2, v_base, v_student_a, v_base, v_now - INTERVAL 10 MINUTE),
           (v_base + 7, v_base + 2, v_base, v_student_b, v_base + 1, v_now - INTERVAL 10 MINUTE),
           (v_base + 8, v_base + 2, v_base, v_student_c, v_base + 2, v_now - INTERVAL 10 MINUTE);
    INSERT INTO edu_assignment_submission
        (id, assignment_id, target_id)
    VALUES (v_base, v_base, v_base), (v_base + 1, v_base, v_base + 1);
    INSERT INTO edu_submission_version
        (id, submission_id, assignment_id, version_no, submitted_at, is_late)
    VALUES (v_base, v_base, v_base, 1, v_now - INTERVAL 2 DAY, 0),
           (v_base + 1, v_base + 1, v_base, 1, v_now - INTERVAL 2 DAY, 0);
    INSERT INTO edu_submission_answer
        (id, submission_version_id, assignment_id, assignment_item_id, answer_json)
    VALUES (v_base, v_base, v_base, v_base, JSON_OBJECT('choice_index', 1)),
           (v_base + 1, v_base + 1, v_base, v_base, JSON_OBJECT('choice_index', 0));
    INSERT INTO edu_grade_revision
        (id, submission_version_id, revision_no, score, max_score,
         feedback, status, graded_by_user_id, confirmed_by_user_id, confirmed_at)
    VALUES (v_base, v_base, 1, 10, 10, '理解正确。', 'CONFIRMED',
            v_teacher, v_teacher, v_now - INTERVAL 12 HOUR),
           (v_base + 1, v_base + 1, 1, 0, 10, '请复习过程性反馈。',
            'CONFIRMED', v_teacher, v_teacher, v_now - INTERVAL 12 HOUR);
    INSERT INTO edu_grade_publication
        (id, submission_id, submission_version_id, grade_revision_id,
         publication_no, published_by_user_id, published_at)
    VALUES (v_base, v_base, v_base, v_base, 1,
            v_teacher, v_now - INTERVAL 11 HOUR),
           (v_base + 1, v_base + 1, v_base + 1, v_base + 1, 1,
            v_teacher, v_now - INTERVAL 11 HOUR);
    INSERT INTO edu_idempotency_record
        (id, actor_user_id, operation_kind, scope_id, key_hash, request_hash,
         result_id, status, created_at, expires_at)
    VALUES (v_base, v_student_a, 'ASSIGNMENT_SUBMIT', v_base,
            UNHEX(SHA2('demo-submit-a', 256)),
            UNHEX(SHA2('demo-answer-a', 256)), v_base, 'SUCCEEDED',
            v_now - INTERVAL 2 DAY, v_now + INTERVAL 30 DAY);

    -- learning and analysis: two submissions out of three targets, one wrong answer.
    INSERT INTO edu_learning_event
        (id, binding_id, student_user_id, event_kind, source_kind, source_id,
         source_version_id, client_event_key, occurred_at)
    VALUES (v_base, v_base, v_student_a, 'RESOURCE_OPEN', 'RESOURCE_RELEASE',
            v_base, v_base, 'demo-resource-open-a', v_now - INTERVAL 3 DAY),
           (v_base + 1, v_base, v_student_a, 'ASSIGNMENT_SUBMIT', 'ASSIGNMENT',
            v_base, v_base, 'demo-homework-submit-a', v_now - INTERVAL 2 DAY),
           (v_base + 2, v_base, v_student_b, 'ASSIGNMENT_SUBMIT', 'ASSIGNMENT',
            v_base, v_base + 1, 'demo-homework-submit-b', v_now - INTERVAL 2 DAY);
    INSERT INTO edu_video_watch_interval
        (id, binding_id, student_user_id, release_id, resource_version_id,
         start_ms, end_ms, recorded_at)
    VALUES (v_base, v_base, v_student_a, v_base + 2, v_base + 1,
            0, 60000, v_now - INTERVAL 3 DAY);
    INSERT INTO edu_wrong_question
        (id, student_user_id, assignment_id, submission_id, target_id,
         submission_version_id, assignment_item_id, question_id,
         question_version_id, recorded_at)
    VALUES (v_base, v_student_b, v_base, v_base + 1, v_base + 1,
            v_base + 1, v_base, v_base, v_base, v_now - INTERVAL 11 HOUR);
    INSERT INTO edu_metric_snapshot
        (id, binding_id, metric_code, definition_version, subject_kind,
         subject_id, window_starts_at, window_ends_at, evidence_cutoff_at,
         numerator, denominator, sample_count, metric_value,
         source_watermark, calculated_at)
    VALUES (v_base, v_base, 'HOMEWORK_SUBMISSION_RATE', 1, 'CLASS', v_base,
            v_now - INTERVAL 7 DAY, v_now, v_now,
            2, 3, 3, 0.6667, 'demo-assignment-1', v_now);

    -- ai: metadata and citation are synthetic; no provider call or object exists.
    INSERT INTO edu_ai_task
        (id, requester_user_id, requester_role, binding_id, conversation_key,
         request_key, question_hash, question_storage_key, model_code,
         prompt_template_version, retrieval_index_version, status,
         answer_storage_key, content_retention_until, input_tokens,
         output_tokens, created_at, finished_at)
    VALUES (v_base, v_teacher, 'TEACHER', v_base,
            UNHEX(REPEAT('11', 16)), UNHEX(REPEAT('22', 16)),
            UNHEX(SHA2('demo teaching question', 256)), 'demo-only/ai/question-1',
            'DEMO_NO_PROVIDER', 'demo-v1', 'demo-index-v1', 'SUCCEEDED',
            'demo-only/ai/answer-1', v_now + INTERVAL 7 DAY,
            100, 50, v_now - INTERVAL 1 MINUTE, v_now);
    INSERT INTO edu_ai_citation
        (id, ai_task_id, binding_id, release_id, resource_version_id,
         page_no, sort_order)
    VALUES (v_base, v_base, v_base, v_base, v_base, 1, 1);

    -- announcement and inbox notices complete the workbench demo.
    INSERT INTO edu_course_announcement
        (id, binding_id, title, body, status, opens_at,
         published_by_user_id, created_by_user_id)
    VALUES (v_base, v_base, '课堂提醒（演示）',
            '请在课堂中完成签到与随堂题。此公告仅为虚构测试数据。',
            'PUBLISHED', v_now - INTERVAL 1 DAY, v_teacher, v_teacher);
    INSERT INTO edu_user_notice
        (id, recipient_user_id, binding_id, notice_kind, source_id,
         title, visible_at)
    VALUES (v_base, v_student_a, v_base, 'ANNOUNCEMENT', v_base,
            '课堂提醒（演示）', v_now - INTERVAL 1 DAY),
           (v_base + 1, v_student_a, v_base, 'GRADE', v_base,
            '课后作业成绩已发布（演示）', v_now - INTERVAL 11 HOUR);

    COMMIT;
END//
DELIMITER ;
CALL edu_demo_seed_20261022();
DROP PROCEDURE edu_demo_seed_20261022;

-- Expected: term=1, classes=2, students=4, assignments=3,
-- submissions=2, grades=2, AI tasks=1.
SELECT DATABASE() AS schema_name,
       (SELECT COUNT(*) FROM edu_term) AS terms,
       (SELECT COUNT(*) FROM edu_teaching_class) AS classes,
       (SELECT COUNT(*) FROM edu_class_student) AS students,
       (SELECT COUNT(*) FROM edu_assignment) AS assignments,
       (SELECT COUNT(*) FROM edu_assignment_submission) AS submissions,
       (SELECT COUNT(*) FROM edu_grade_publication) AS grades,
       (SELECT COUNT(*) FROM edu_ai_task) AS ai_tasks;


-- SOURCE demo-v3-m0-ruoyi-login-seed.sql
-- Local demo only. Run after the RuoYi system tables and M0 teaching demo rows exist.
-- Demo usernames and passwords are listed in docs/demo-login-accounts.txt.
-- These fixed BCrypt hashes are for disposable test databases only.
-- High demo IDs advance MySQL AUTO_INCREMENT counters; use only in a disposable test DB.
-- No menu grants are added: role identity works, while getRouters may be empty.

SET NAMES utf8mb4;

DROP PROCEDURE IF EXISTS edu_demo_login_seed_20261022;
DELIMITER //
CREATE PROCEDURE edu_demo_login_seed_20261022()
BEGIN
    DECLARE v_admin_hash VARCHAR(100)
        DEFAULT '$2b$10$w.4XLiGxiarh2L5n4ro0oufPMTHjUNyWKJDZAoAAVEPLy5ORjbZh2';
    DECLARE v_teacher_hash VARCHAR(100)
        DEFAULT '$2b$10$w.4XLiGxiarh2L5n4ro0oufPMTHjUNyWKJDZAoAAVEPLy5ORjbZh2';
    DECLARE v_student_hash VARCHAR(100)
        DEFAULT '$2b$10$w.4XLiGxiarh2L5n4ro0oufPMTHjUNyWKJDZAoAAVEPLy5ORjbZh2';
    DECLARE EXIT HANDLER FOR SQLEXCEPTION
    BEGIN
        ROLLBACK;
        RESIGNAL;
    END;

    IF (SELECT COUNT(*) FROM sys_user WHERE user_id = 1 AND user_name = 'admin') <> 1 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'RuoYi admin account is missing';
    END IF;
    IF (SELECT COUNT(*) FROM edu_class_teacher
        WHERE user_id = 930000000001) <> 2
       OR (SELECT COUNT(*) FROM edu_class_student
           WHERE user_id IN (930000000101, 930000000102,
                             930000000103, 930000000104)) <> 4 THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'M0 teaching demo IDs are missing or changed';
    END IF;
    IF EXISTS (SELECT 1 FROM sys_dept WHERE dept_id = 920000000001)
       OR EXISTS (SELECT 1 FROM sys_role
                  WHERE role_id IN (940000000001, 940000000002)
                     OR role_key IN ('teacher', 'student'))
       OR EXISTS (SELECT 1 FROM sys_user
                  WHERE user_id IN (930000000001, 930000000101,
                                    930000000102, 930000000103,
                                    930000000104)
                     OR user_name IN ('teacher', 'student',
                                      'student2', 'student3', 'student4')) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = 'Demo department, roles or accounts already exist';
    END IF;

    START TRANSACTION;
    UPDATE sys_user
    SET password = v_admin_hash, pwd_update_date = NOW()
    WHERE user_id = 1;
    INSERT INTO sys_dept
        (dept_id, parent_id, ancestors, dept_name, order_num,
         status, del_flag, create_by, create_time)
    VALUES (920000000001, 0, '0', '演示学校', 1,
            '0', '0', 'demo-seed', NOW());

    UPDATE sys_user SET dept_id=920000000001 WHERE user_id=1;

    -- Scope 5 is "self" in RuoYi. Teaching-class access remains a separate check.
    INSERT INTO sys_role
        (role_id, role_name, role_key, role_sort, data_scope,
         menu_check_strictly, dept_check_strictly, status, del_flag,
         create_by, create_time, remark)
    VALUES (940000000001, '演示教师', 'teacher', 10, '5',
            1, 1, '0', '0', 'demo-seed', NOW(), '本机登录联调'),
           (940000000002, '演示学生', 'student', 11, '5',
            1, 1, '0', '0', 'demo-seed', NOW(), '本机登录联调');

    INSERT INTO sys_user
        (user_id, dept_id, user_name, nick_name, user_type,
         email, phonenumber, sex, avatar, password,
         status, del_flag, create_by, create_time, pwd_update_date, remark)
    VALUES (930000000001, 920000000001, 'teacher', '演示教师', '00',
            '', '', '2', '', v_teacher_hash, '0', '0', 'demo-seed', NOW(), NOW(), '虚构测试账号'),
           (930000000101, 920000000001, 'student', '演示学生甲', '00',
            '', '', '2', '', v_student_hash, '0', '0', 'demo-seed', NOW(), NOW(), '虚构测试账号'),
           (930000000102, 920000000001, 'student2', '演示学生乙', '00',
            '', '', '2', '', v_student_hash, '0', '0', 'demo-seed', NOW(), NOW(), '虚构测试账号'),
           (930000000103, 920000000001, 'student3', '演示学生丙', '00',
            '', '', '2', '', v_student_hash, '0', '0', 'demo-seed', NOW(), NOW(), '虚构测试账号'),
           (930000000104, 920000000001, 'student4', '演示学生丁', '00',
            '', '', '2', '', v_student_hash, '0', '0', 'demo-seed', NOW(), NOW(), '虚构测试账号');

    INSERT INTO sys_user_role (user_id, role_id)
    VALUES (930000000001, 940000000001),
           (930000000101, 940000000002),
           (930000000102, 940000000002),
           (930000000103, 940000000002),
           (930000000104, 940000000002);
    COMMIT;
END//
DELIMITER ;
CALL edu_demo_login_seed_20261022();
DROP PROCEDURE edu_demo_login_seed_20261022;

-- Expected: 5 demo accounts, 2 roles, all five IDs present in education tables.
SELECT u.user_id, u.user_name, r.role_key, u.dept_id
FROM sys_user u
JOIN sys_user_role ur ON ur.user_id = u.user_id
JOIN sys_role r ON r.role_id = ur.role_id
WHERE u.user_id IN (930000000001, 930000000101, 930000000102,
                    930000000103, 930000000104)
ORDER BY u.user_id;


-- SOURCE group12-system-directory.sql
-- Group 1 public read-only identity contract for the group 2 directory queries.
-- No password, token, email, address or authentication secret is exposed.
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_user AS
 SELECT user_id,user_name,nick_name,dept_id,phonenumber,status,del_flag FROM sys_user;
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_user_role AS SELECT user_id,role_id FROM sys_user_role;
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_role AS SELECT role_id,role_key,status,del_flag FROM sys_role;
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_dept AS SELECT dept_id,parent_id,ancestors,dept_name,order_num,status,del_flag FROM sys_dept;


-- SOURCE group12-menu-permissions.sql
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


UPDATE edu_term SET is_current=1 WHERE code='DEMO-TERM';

-- 在当前项目使用的既有数据库中执行。可重复执行。
SET @edu_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='edu_course_space' AND column_name='deleted_at'), 'SELECT 1', 'ALTER TABLE edu_course_space ADD COLUMN deleted_at DATETIME(3) NULL');
PREPARE edu_stmt FROM @edu_ddl;
EXECUTE edu_stmt;
DEALLOCATE PREPARE edu_stmt;

SET @edu_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='edu_course_unit' AND column_name='content_text'), 'SELECT 1', 'ALTER TABLE edu_course_unit ADD COLUMN content_text TEXT NULL');
PREPARE edu_stmt FROM @edu_ddl;
EXECUTE edu_stmt;
DEALLOCATE PREPARE edu_stmt;

SET @edu_ddl = IF(EXISTS(SELECT 1 FROM information_schema.columns WHERE table_schema=DATABASE() AND table_name='edu_knowledge_point' AND column_name='content_text'), 'SELECT 1', 'ALTER TABLE edu_knowledge_point ADD COLUMN content_text TEXT NULL');
PREPARE edu_stmt FROM @edu_ddl;
EXECUTE edu_stmt;
DEALLOCATE PREPARE edu_stmt;

CREATE TABLE IF NOT EXISTS edu_outline_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    course_space_id BIGINT NOT NULL,
    unit_id BIGINT NULL,
    point_id BIGINT NULL,
    file_name VARCHAR(200) NOT NULL,
    file_size INT UNSIGNED NOT NULL,
    file_data MEDIUMBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    KEY idx_outline_attachment_unit (unit_id, course_space_id),
    KEY idx_outline_attachment_point (point_id, course_space_id),
    CONSTRAINT fk_outline_attachment_unit FOREIGN KEY (unit_id, course_space_id)
        REFERENCES edu_course_unit(id, course_space_id) ON DELETE CASCADE,
    CONSTRAINT fk_outline_attachment_point FOREIGN KEY (point_id, course_space_id)
        REFERENCES edu_knowledge_point(id, course_space_id) ON DELETE CASCADE,
    CONSTRAINT chk_outline_attachment_target CHECK ((unit_id IS NULL) <> (point_id IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
