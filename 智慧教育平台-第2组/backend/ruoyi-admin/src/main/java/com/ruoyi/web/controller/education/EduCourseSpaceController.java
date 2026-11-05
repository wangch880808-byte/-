package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.service.IEduCourseSpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;

/**
 * 早期课程空间 CRUD。
 *
 * @deprecated 三端页面统一走 {@link EduCourseController}（`/education/course`），
 * 该控制器只保留给内部脚本或既有调用方，新代码不要再用 `/education/courseSpace`。
 */
@Deprecated
@RestController
@RequestMapping("/education/courseSpace")
public class EduCourseSpaceController extends BaseController {

    @Autowired
    private IEduCourseSpaceService courseSpaceService;

    @GetMapping("/list")
    public TableDataInfo list(EduCourseSpace query) {
        startPage();
        List<EduCourseSpace> list = courseSpaceService.list(query);
        return getDataTable(list);
    }

    @GetMapping("/{id}")
    public AjaxResult getInfo(@PathVariable Long id) {
        return success(courseSpaceService.getById(id));
    }

    @PostMapping
    public AjaxResult add(@RequestBody EduCourseSpace courseSpace) {
        return success(courseSpaceService.create(courseSpace));
    }

    @PutMapping
    public AjaxResult edit(@RequestBody EduCourseSpace courseSpace) {
        courseSpaceService.update(courseSpace);
        return success();
    }

    // 查某门课绑了哪些班
    @GetMapping("/{courseId}/bindings")
    public AjaxResult listBindings(@PathVariable Long courseId) {
        return success(courseSpaceService.listBindings(courseId));
    }

    // 把课程绑到某个班
    @PostMapping("/{courseId}/bindings")
    public AjaxResult bindClass(@PathVariable Long courseId, @RequestBody Map<String, Object> body) {
        Long classId = Long.valueOf(body.get("classId").toString());
        courseSpaceService.bindClass(courseId, classId);
        return success();
    }

    // 解绑
    @DeleteMapping("/bindings/{bindingId}")
    public AjaxResult unbindClass(@PathVariable Long bindingId) {
        courseSpaceService.unbindClass(bindingId);
        return success();
    }
}
