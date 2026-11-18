package com.ruoyi.education.security;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.exception.EduForbiddenException;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
public final class EduActor {
 private EduActor(){}
 public static boolean assigned(String role){
  var user=SecurityUtils.getLoginUser().getUser();
  return (user.isAdmin()&&"admin".equals(role))||(user.getRoles()!=null&&user.getRoles().stream().anyMatch(r->"0".equals(r.getStatus())&&role.equals(r.getRoleKey())));
 }
 public static String role(){
  var attributes=RequestContextHolder.getRequestAttributes();
  String selected=attributes instanceof ServletRequestAttributes request?request.getRequest().getHeader("X-Edu-Role"):null;
  if(selected!=null&&!selected.isBlank()){if(!assigned(selected))throw new EduForbiddenException("当前账号没有该角色");return selected;}
  for(String role:new String[]{"admin","teacher","student"})if(assigned(role))return role;
  throw new EduForbiddenException("当前账号没有教学业务角色");
 }
 public static boolean admin(){return "admin".equals(role());}
 public static void requireTeacher(){if(!admin()&&!"teacher".equals(role()))throw new EduForbiddenException("只有教师或管理员可以创建课程");}
}
