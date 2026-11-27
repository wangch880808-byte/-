package com.ruoyi.web.controller.education;

import java.util.Map;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.education.service.EduOutlineService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/education/course/{courseId}/outline")
@PreAuthorize("@ss.hasPermi('education:course:edit')")
public class EduOutlineController {
    private final EduOutlineService service;
    public EduOutlineController(EduOutlineService service){this.service=service;}
    @PostMapping("/units")
    public AjaxResult addUnit(@PathVariable Long courseId,@RequestBody Map<String,Object> body){
        service.saveUnit(courseId,null,body);
        EduOperLog.record("新增课程章节："+body.get("title"),BusinessType.INSERT);
        return AjaxResult.success();
    }
    @PutMapping("/units/{id}")
    public AjaxResult editUnit(@PathVariable Long courseId,@PathVariable Long id,@RequestBody Map<String,Object> body){
        service.saveUnit(courseId,id,body);
        EduOperLog.record("修改课程章节："+body.get("title"),BusinessType.UPDATE);
        return AjaxResult.success();
    }
    @PutMapping("/units/reorder") @Log(title="调整章节顺序",businessType=BusinessType.UPDATE)
    public AjaxResult reorderUnits(@PathVariable Long courseId,@RequestBody Map<String,Object> body){
        Object raw=body.get("items");
        if(!(raw instanceof java.util.List)) throw new com.ruoyi.common.exception.ServiceException("请提交章节顺序列表");
        @SuppressWarnings("unchecked") java.util.List<Map<String,Object>> items=(java.util.List<Map<String,Object>>)raw;
        service.reorder(courseId,items);return AjaxResult.success();
    }
    @PostMapping("/publish") @Log(title="发布课程目录",businessType=BusinessType.UPDATE)
    public AjaxResult publish(@PathVariable Long courseId){service.publish(courseId);return AjaxResult.success();}
    @DeleteMapping("/units/{id}")
    public AjaxResult deleteUnit(@PathVariable Long courseId,@PathVariable Long id){
        String title=service.unitTitle(courseId,id);
        service.delete(courseId,id,false);
        EduOperLog.record("删除课程章节"+(title==null?"":"："+title),BusinessType.DELETE);
        return AjaxResult.success();
    }
    @PostMapping("/points")
    public AjaxResult addPoint(@PathVariable Long courseId,@RequestBody Map<String,Object> body){
        service.savePoint(courseId,null,body);
        EduOperLog.record("新增课程知识点："+body.get("name"),BusinessType.INSERT);
        return AjaxResult.success();
    }
    @PutMapping("/points/{id}")
    public AjaxResult editPoint(@PathVariable Long courseId,@PathVariable Long id,@RequestBody Map<String,Object> body){
        service.savePoint(courseId,id,body);
        EduOperLog.record("修改课程知识点："+body.get("name"),BusinessType.UPDATE);
        return AjaxResult.success();
    }
    @DeleteMapping("/points/{id}")
    public AjaxResult deletePoint(@PathVariable Long courseId,@PathVariable Long id){
        String name=service.pointTitle(courseId,id);
        service.delete(courseId,id,true);
        EduOperLog.record("删除课程知识点"+(name==null?"":"："+name),BusinessType.DELETE);
        return AjaxResult.success();
    }
}
