package com.ruoyi.education.port;

import java.util.List;
import com.ruoyi.education.domain.course.BindingContext;

/**
 * 课程成员与绑定访问端口（临时直连 edu_* 表，待第 2 组 API 替换）。
 */
public interface CourseAccessPort
{
    boolean isTeacherOfBinding(Long userId, Long bindingId);

    boolean isActiveStudentOfBinding(Long userId, Long bindingId);

    BindingContext getBindingContext(Long bindingId);

    List<BindingContext> listTeacherBindings(Long userId);

    List<BindingContext> listStudentBindings(Long userId);
}
