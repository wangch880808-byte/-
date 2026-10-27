package com.ruoyi.education.port.impl;

import java.util.List;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import com.ruoyi.education.domain.course.BindingContext;
import com.ruoyi.education.mapper.EduSupportMapper;
import com.ruoyi.education.port.CourseAccessPort;

/**
 * 课程访问端口实现（直连 edu_* 表）。
 */
@Service
public class CourseAccessPortImpl implements CourseAccessPort
{
    @Autowired
    private EduSupportMapper eduSupportMapper;

    @Override
    public boolean isTeacherOfBinding(Long userId, Long bindingId)
    {
        return eduSupportMapper.countTeacherOfBinding(userId, bindingId) > 0;
    }

    @Override
    public boolean isActiveStudentOfBinding(Long userId, Long bindingId)
    {
        return eduSupportMapper.countActiveStudentOfBinding(userId, bindingId) > 0;
    }

    @Override
    public BindingContext getBindingContext(Long bindingId)
    {
        return eduSupportMapper.selectBindingContext(bindingId);
    }

    @Override
    public List<BindingContext> listTeacherBindings(Long userId)
    {
        return eduSupportMapper.selectTeacherBindings(userId);
    }

    @Override
    public List<BindingContext> listStudentBindings(Long userId)
    {
        return eduSupportMapper.selectStudentBindings(userId);
    }
}
