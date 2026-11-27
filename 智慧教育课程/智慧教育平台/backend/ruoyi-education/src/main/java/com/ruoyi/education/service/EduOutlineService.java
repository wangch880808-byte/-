package com.ruoyi.education.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.mapper.EduOutlineMapper;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 课程目录（章、节、知识点）。
 *
 * <p>教师的所有编辑先写入草稿表（{@code edu_course_unit_draft} / {@code edu_knowledge_point_draft}），
 * 发布后才复制到正式表；学生始终读正式表。课程创建者直接拥有全部目录权限，
 * 协作教师需持有 outline / knowledgeGraph 权限键。</p>
 */
@Service
public class EduOutlineService {
    private final EduOutlineMapper mapper;
    private final EduCourseManagementService management;
    public EduOutlineService(EduOutlineMapper mapper, EduCourseManagementService management) {
        this.mapper = mapper; this.management = management;
    }

    private EduCourseSpace requireManage(Long courseId, String permissionKey, String action) {
        return management.requireManagePermission(courseId, permissionKey, action);
    }

    private EduCourseSpace requireManageAny(Long courseId, String action, String... permissionKeys) {
        return management.requireManagePermissionAny(courseId, action, permissionKeys);
    }

    private void ensureDraft(Long courseId) {
        if (mapper.draftCount(courseId) > 0) return;
        mapper.copyPublishedUnitsToDraft(courseId);
        mapper.copyPublishedPointsToDraft(courseId);
    }

    private String text(Map<String,Object> body,String key,int max) {
        String value=body.get(key)==null?"":String.valueOf(body.get(key)).trim();
        if(value.isEmpty()||value.length()>max) throw new ServiceException("名称或编码不能为空，且不能超过规定长度");
        return value;
    }
    private String optionalContent(Map<String,Object> body,String key) {
        if(body.get(key)==null) return "";
        String value=String.valueOf(body.get(key)).trim();
        if(value.length()>4000) throw new ServiceException("大纲内容不能超过 4000 字");
        return value;
    }
    private Long id(Object value) {
        if(value==null||"".equals(value)) return null;
        try {long result=Long.parseLong(String.valueOf(value));if(result>0)return result;}
        catch(NumberFormatException ignored) { }
        throw new ServiceException("对象编号必须为正整数");
    }
    private Map<String,Object> requireDraftUnit(Long courseId,Long id) {
        for (Map<String,Object> row : mapper.draftUnits(courseId)) {
            if (id.equals(((Number) row.get("id")).longValue())) return row;
        }
        throw new ServiceException("章节不存在或不属于本课程");
    }
    private int childCountDraft(List<Map<String,Object>> units, Long id) {
        int count = 0;
        for (Map<String,Object> row : units) {
            Object parent = row.get("parentUnitId");
            if (parent != null && id.equals(((Number) parent).longValue())) count++;
        }
        return count;
    }

    @Transactional
    public Long saveUnit(Long courseId,Long unitId,Map<String,Object> input) {
        requireManage(courseId, "outline", "你没有「编辑课程章节」的权限");
        ensureDraft(courseId);
        List<Map<String,Object>> units = mapper.draftUnits(courseId);
        Map<String,Object> existing = unitId == null ? null : requireDraftUnit(courseId, unitId);
        String kind=String.valueOf(input.get("unitKind"));
        if(!"CHAPTER".equals(kind)&&!"SECTION".equals(kind)) throw new ServiceException("章节类型不正确");
        Long parent=id(input.get("parentUnitId"));
        if("CHAPTER".equals(kind)&&parent!=null)throw new ServiceException("章必须位于目录根层");
        if("SECTION".equals(kind)) {
            if(parent==null||parent.equals(unitId))throw new ServiceException("节必须属于本课程中的章");
            Map<String,Object> parentRow = requireDraftUnit(courseId, parent);
            if(!"CHAPTER".equals(parentRow.get("unitKind")))throw new ServiceException("节必须属于本课程中的章");
            if(existing!=null&&childCountDraft(units, unitId)>0)throw new ServiceException("请先处理下级章节");
        }
        int order;
        try{order=Integer.parseInt(String.valueOf(input.getOrDefault("sortOrder",0)));}
        catch(NumberFormatException e){throw new ServiceException("排序必须为非负整数");}
        if(order<0)throw new ServiceException("排序必须为非负整数");
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("courseId",courseId);body.put("parentUnitId",parent);
        body.put("unitKind",kind);body.put("title",text(input,"title",160));body.put("sortOrder",order);
        body.put("contentText",optionalContent(input,"contentText"));
        if(unitId==null){body.put("id",mapper.nextUnitId());mapper.insertUnitDraft(body);}
        else{body.put("id",unitId);mapper.updateUnitDraft(body);}
        mapper.setOutlineDirty(courseId, 1);
        return ((Number) body.get("id")).longValue();
    }

    @Transactional
    public void reorder(Long courseId, List<Map<String,Object>> items) {
        requireManage(courseId, "outline", "你没有「编辑课程章节」的权限");
        ensureDraft(courseId);
        if (items == null || items.isEmpty() || items.size() > 500) throw new ServiceException("请提交 1 至 500 条章节顺序");
        for (Map<String,Object> item : items) {
            Long unitId = id(item.get("id"));
            Map<String,Object> row = requireDraftUnit(courseId, unitId);
            Long parent = id(item.get("parentUnitId"));
            if ("CHAPTER".equals(row.get("unitKind"))) {
                if (parent != null) throw new ServiceException("章必须位于目录根层");
            } else {
                if (parent == null || parent.equals(unitId) || !"CHAPTER".equals(requireDraftUnit(courseId, parent).get("unitKind"))) {
                    throw new ServiceException("节必须属于本课程中的章");
                }
            }
            int order;
            try { order = Integer.parseInt(String.valueOf(item.getOrDefault("sortOrder", 0))); }
            catch (NumberFormatException e) { throw new ServiceException("排序必须为非负整数"); }
            if (order < 0) throw new ServiceException("排序必须为非负整数");
            if (mapper.updateUnitOrderDraft(courseId, unitId, parent, order) == 0) {
                throw new ServiceException("章节不存在或不属于本课程");
            }
        }
        mapper.setOutlineDirty(courseId, 1);
    }

    @Transactional
    public Long savePoint(Long courseId,Long pointId,Map<String,Object> input) {
        requireManage(courseId, "knowledgeGraph", "你没有「编辑知识图谱」的权限");
        ensureDraft(courseId);
        if(pointId!=null&&mapper.draftPoints(courseId).stream().noneMatch(row->pointId.equals(((Number)row.get("id")).longValue())))
            throw new ServiceException("知识点不存在或不属于本课程");
        Long unitId=id(input.get("unitId"));
        if(unitId!=null)requireDraftUnit(courseId,unitId);
        Map<String,Object> body=new LinkedHashMap<>();
        body.put("courseId",courseId);body.put("unitId",unitId);
        body.put("code",text(input,"code",60));body.put("name",text(input,"name",160));
        body.put("contentText",optionalContent(input,"contentText"));
        try{
            if(pointId==null){body.put("id",mapper.nextPointId());mapper.insertPointDraft(body);}
            else{body.put("id",pointId);mapper.updatePointDraft(body);}
        }
        catch(DataIntegrityViolationException e){throw new ServiceException("知识点编码重复或关联章节已变化，请刷新后重试");}
        mapper.setOutlineDirty(courseId, 1);
        return ((Number) body.get("id")).longValue();
    }

    @Transactional
    public void delete(Long courseId,Long id,boolean point) {
        requireManage(courseId, point ? "knowledgeGraph" : "outline",
                point ? "你没有「编辑知识图谱」的权限" : "你没有「编辑课程章节」的权限");
        ensureDraft(courseId);
        if (!point) {
            Map<String,Object> row = requireDraftUnit(courseId, id);
            if ("CHAPTER".equals(row.get("unitKind")) && childCountDraft(mapper.draftUnits(courseId), id) > 0) {
                throw new ServiceException("请先删除该章下的所有小节");
            }
            mapper.deletePointsOfUnitDraft(courseId, id);
        }
        int rows;
        try {
            rows = point ? mapper.deletePointDraft(courseId,id) : mapper.deleteUnitDraft(courseId,id);
            if(rows==0)throw new ServiceException("对象不存在或不属于本课程");
        } catch(DataIntegrityViolationException e) {
            throw new ServiceException("该对象仍被教学业务引用，请先处理引用");
        }
        mapper.setOutlineDirty(courseId, 1);
    }

    @Transactional
    public void publish(Long courseId) {
        requireManageAny(courseId, "你没有发布目录的权限", "outline", "knowledgeGraph");
        ensureDraft(courseId);
        try {
            mapper.deleteAttachmentsOfMissingPoints(courseId);
            mapper.publishDeleteMissingPoints(courseId);
            mapper.publishUpsertUnits(courseId);
            mapper.publishUpsertSections(courseId);
            mapper.publishUpsertPoints(courseId);
            mapper.deleteAttachmentsOfMissingUnits(courseId);
            mapper.publishDeleteMissingSections(courseId);
            mapper.publishDeleteMissingUnits(courseId);
        } catch (DataIntegrityViolationException e) {
            throw new ServiceException("目录中仍有内容被题库或其他业务引用，无法发布，请先处理相关引用");
        }
        mapper.setOutlineDirty(courseId, 0);
    }

    public String unitTitle(Long courseId, Long unitId) {
        if (mapper.draftCount(courseId) > 0) {
            for (Map<String, Object> row : mapper.draftUnits(courseId)) {
                if (unitId.equals(((Number) row.get("id")).longValue())) return String.valueOf(row.get("title"));
            }
            return null;
        }
        Map<String, Object> row = mapper.unit(courseId, unitId);
        return row == null ? null : String.valueOf(row.get("title"));
    }

    public String pointTitle(Long courseId, Long pointId) {
        if (mapper.draftCount(courseId) > 0) {
            for (Map<String, Object> row : mapper.draftPoints(courseId)) {
                if (pointId.equals(((Number) row.get("id")).longValue())) return String.valueOf(row.get("name"));
            }
            return null;
        }
        Map<String, Object> row = mapper.point(courseId, pointId);
        return row == null ? null : String.valueOf(row.get("name"));
    }
}
