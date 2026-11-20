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
    @PostMapping("/units") @Log(title="课程章节",businessType=BusinessType.INSERT)
    public AjaxResult addUnit(@PathVariable Long courseId,@RequestBody Map<String,Object> body){service.saveUnit(courseId,null,body);return AjaxResult.success();}
    @PutMapping("/units/{id}") @Log(title="课程章节",businessType=BusinessType.UPDATE)
    public AjaxResult editUnit(@PathVariable Long courseId,@PathVariable Long id,@RequestBody Map<String,Object> body){service.saveUnit(courseId,id,body);return AjaxResult.success();}
    @DeleteMapping("/units/{id}") @Log(title="课程章节",businessType=BusinessType.DELETE)
    public AjaxResult deleteUnit(@PathVariable Long courseId,@PathVariable Long id){service.delete(courseId,id,false);return AjaxResult.success();}
    @PostMapping("/points") @Log(title="课程知识点",businessType=BusinessType.INSERT)
    public AjaxResult addPoint(@PathVariable Long courseId,@RequestBody Map<String,Object> body){service.savePoint(courseId,null,body);return AjaxResult.success();}
    @PutMapping("/points/{id}") @Log(title="课程知识点",businessType=BusinessType.UPDATE)
    public AjaxResult editPoint(@PathVariable Long courseId,@PathVariable Long id,@RequestBody Map<String,Object> body){service.savePoint(courseId,id,body);return AjaxResult.success();}
    @DeleteMapping("/points/{id}") @Log(title="课程知识点",businessType=BusinessType.DELETE)
    public AjaxResult deletePoint(@PathVariable Long courseId,@PathVariable Long id){service.delete(courseId,id,true);return AjaxResult.success();}
}
