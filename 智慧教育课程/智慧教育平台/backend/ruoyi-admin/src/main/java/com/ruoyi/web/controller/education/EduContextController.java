package com.ruoyi.web.controller.education;
import java.util.*;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.mapper.EduContextMapper;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.security.EduActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/** First-group context contract backed by second-group active membership and binding data. */
@RestController
@RequestMapping("/education/context")
@PreAuthorize("@ss.hasPermi('education:scope:read')")
public class EduContextController {
 @Autowired private EduContextMapper contexts;
 @Autowired private EduCourseAccessService courses;
 private String role(String requested){
  String role=requested==null?EduActor.role():requested;
  if(!Set.of("teacher","student").contains(role)||!EduActor.assigned(role)||!role.equals(EduActor.role()))throw new EduForbiddenException("当前入口无权使用该课程身份");
  return role;
 }
 private static String id(Object value){return value==null?null:String.valueOf(value);}
 private static boolean flag(Object value){return Boolean.TRUE.equals(value)||(value!=null&&Set.of("1","true").contains(String.valueOf(value)));}
 private Map<String,Object> classView(Map<String,Object> row,String role){
  Map<String,Object> result=new LinkedHashMap<>();
  for(String key:List.of("classId","termId","bindingId","courseSpaceId"))result.put(key,id(row.get(key)));
  result.put("className",row.get("className"));result.put("memberRole",role);
  for(String key:List.of("canPublish","canGrade","canExport"))result.put(key,"teacher".equals(role)&&flag(row.get(key)));
  return result;
 }
 @GetMapping("/options") public AjaxResult options(@RequestParam(required=false) String role){
  String current=role(role);Long userId=SecurityUtils.getUserId();List<Map<String,Object>> data=new ArrayList<>();
  for(var row:contexts.courses(userId,current)){
   Long courseId=Long.valueOf(id(row.get("courseSpaceId")));Map<String,Object> item=new LinkedHashMap<>();
   item.put("courseSpaceId",courseId.toString());item.put("title",row.get("title"));
   item.put("classes",contexts.classes(courseId,userId,current).stream().map(c->classView(c,current)).toList());data.add(item);
  }
  Map<String,Object> result=new LinkedHashMap<>();var term=courses.currentTerm();
  result.put("term",term==null?null:Map.of("termId",term.termId().toString(),"termName",term.name()));result.put("courses",data);
  return AjaxResult.success(result);
 }
 @GetMapping("/check") public AjaxResult check(@RequestParam Long courseSpaceId,@RequestParam Long classId,@RequestParam(required=false) Long bindingId,@RequestParam(required=false) String role){
  String current=role(role);Long userId=SecurityUtils.getUserId();
  return contexts.classes(courseSpaceId,userId,current).stream()
   .filter(row->classId.toString().equals(id(row.get("classId")))&&(bindingId==null||bindingId.toString().equals(id(row.get("bindingId")))))
   .findFirst().map(row->AjaxResult.success(classView(row,current))).orElseThrow(()->new EduForbiddenException("该课程与教学班的有效授权已失效"));
 }
}
