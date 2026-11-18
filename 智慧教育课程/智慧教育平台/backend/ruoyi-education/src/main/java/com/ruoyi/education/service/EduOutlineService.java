package com.ruoyi.education.service;

import java.util.LinkedHashMap;
import java.util.Map;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.mapper.EduOutlineMapper;
import com.ruoyi.education.security.EduActor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class EduOutlineService {
    private final EduOutlineMapper mapper;
    private final EduCourseAccessService courses;
    public EduOutlineService(EduOutlineMapper mapper, EduCourseAccessService courses) {
        this.mapper=mapper; this.courses=courses;
    }
    private void requireOwner(Long courseId) {
        EduActor.requireTeacher();
        var course=courses.requireCourseOwned(courseId,SecurityUtils.getUserId());
        if ("ARCHIVED".equals(course.status())) throw new ServiceException("已归档课程只允许查看");
    }
    private String text(Map<String,Object> body,String key,int max) {
        String value=body.get(key)==null?"":String.valueOf(body.get(key)).trim();
        if(value.isEmpty()||value.length()>max) throw new ServiceException("名称或编码不能为空，且不能超过规定长度");
        return value;
    }
    private Long id(Object value) {
        if(value==null||"".equals(value)) return null;
        try {long result=Long.parseLong(String.valueOf(value));if(result>0)return result;}
        catch(NumberFormatException ignored) { }
        throw new ServiceException("对象编号必须为正整数");
    }
    private Map<String,Object> requireUnit(Long courseId,Long id) {
        var row=mapper.unit(courseId,id);
        if(row==null)throw new ServiceException("章节不存在或不属于本课程");
        return row;
    }
    @Transactional
    public void saveUnit(Long courseId,Long unitId,Map<String,Object> input) {
        requireOwner(courseId);
        var existing=unitId==null?null:requireUnit(courseId,unitId);
        String kind=String.valueOf(input.get("unitKind"));
        if(!"CHAPTER".equals(kind)&&!"SECTION".equals(kind)) throw new ServiceException("章节类型不正确");
        Long parent=id(input.get("parentUnitId"));
        if("CHAPTER".equals(kind)&&parent!=null)throw new ServiceException("章必须位于目录根层");
        if("SECTION".equals(kind)) {
            if(parent==null||parent.equals(unitId)||!"CHAPTER".equals(requireUnit(courseId,parent).get("unitKind")))
                throw new ServiceException("节必须属于本课程中的章");
            if(existing!=null&&((Number)existing.get("childCount")).longValue()>0)throw new ServiceException("请先处理下级章节");
        }
        int order;
        try{order=Integer.parseInt(String.valueOf(input.getOrDefault("sortOrder",0)));}
        catch(NumberFormatException e){throw new ServiceException("排序必须为非负整数");}
        if(order<0)throw new ServiceException("排序必须为非负整数");
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("courseId",courseId);body.put("id",unitId);body.put("parentUnitId",parent);
        body.put("unitKind",kind);body.put("title",text(input,"title",160));body.put("sortOrder",order);
        if(unitId==null)mapper.insertUnit(body);else mapper.updateUnit(body);
    }
    @Transactional
    public void savePoint(Long courseId,Long pointId,Map<String,Object> input) {
        requireOwner(courseId);
        if(pointId!=null&&mapper.point(courseId,pointId)==null)throw new ServiceException("知识点不存在或不属于本课程");
        Long unitId=id(input.get("unitId"));
        if(unitId!=null)requireUnit(courseId,unitId);
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("courseId",courseId);body.put("id",pointId);body.put("unitId",unitId);
        body.put("code",text(input,"code",60));body.put("name",text(input,"name",160));
        try{if(pointId==null)mapper.insertPoint(body);else mapper.updatePoint(body);}
        catch(DataIntegrityViolationException e){throw new ServiceException("知识点编码重复或关联章节已变化，请刷新后重试");}
    }
    @Transactional
    public void delete(Long courseId,Long id,boolean point) {
        requireOwner(courseId);
        try {
            int rows=point?mapper.deletePoint(courseId,id):mapper.deleteUnit(courseId,id);
            if(rows==0)throw new ServiceException("对象不存在或不属于本课程");
        } catch(DataIntegrityViolationException e) {
            throw new ServiceException("该对象仍被子章节、知识点或教学业务引用，请先处理引用");
        }
    }
}
