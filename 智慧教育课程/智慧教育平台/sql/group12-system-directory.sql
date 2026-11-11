-- Group 1 public read-only identity contract for the group 2 directory queries.
-- No password, token, email, address or authentication secret is exposed.
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_user AS
 SELECT user_id,user_name,nick_name,dept_id,phonenumber,status,del_flag FROM sys_user;
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_user_role AS SELECT user_id,role_id FROM sys_user_role;
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_role AS SELECT role_id,role_key,status,del_flag FROM sys_role;
CREATE OR REPLACE SQL SECURITY INVOKER VIEW v_identity_dept AS SELECT dept_id,parent_id,ancestors,dept_name,order_num,status,del_flag FROM sys_dept;
