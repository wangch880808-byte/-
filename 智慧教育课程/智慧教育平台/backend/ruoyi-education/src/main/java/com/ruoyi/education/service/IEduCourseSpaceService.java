package com.ruoyi.education.service;

import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.domain.EduCourseClassBinding;
import java.util.List;

public interface IEduCourseSpaceService {
    List<EduCourseSpace> list(EduCourseSpace query);
    EduCourseSpace getById(Long id);
    Long create(EduCourseSpace courseSpace);
    void update(EduCourseSpace courseSpace);
    // 课程绑定教学班
    List<EduCourseClassBinding> listBindings(Long courseId);
    void bindClass(Long courseId, Long classId);
    void unbindClass(Long bindingId);
}
