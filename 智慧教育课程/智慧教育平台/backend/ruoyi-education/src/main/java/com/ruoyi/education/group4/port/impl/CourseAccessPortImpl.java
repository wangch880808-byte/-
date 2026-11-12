package com.ruoyi.education.group4.port.impl;

import java.util.List;
import org.springframework.stereotype.Service;
import com.ruoyi.education.group4.domain.BindingContext;
import com.ruoyi.education.group4.mapper.Group4SupportMapper;
import com.ruoyi.education.group4.port.CourseAccessPort;

/**
 * 课程访问端口：成员判定与绑定上下文（规则对齐 Group3 在班/任课校验）。
 */
@Service
public class CourseAccessPortImpl implements CourseAccessPort
{
    private final Group4SupportMapper supportMapper;

    public CourseAccessPortImpl(Group4SupportMapper supportMapper)
    {
        this.supportMapper = supportMapper;
    }

    @Override
    public boolean isTeacherOfBinding(Long userId, Long bindingId)
    {
        return supportMapper.countTeacherOfBinding(userId, bindingId) > 0;
    }

    @Override
    public boolean isActiveStudentOfBinding(Long userId, Long bindingId)
    {
        return supportMapper.countActiveStudentOfBinding(userId, bindingId) > 0;
    }

    @Override
    public BindingContext getBindingContext(Long bindingId)
    {
        return supportMapper.selectBindingContext(bindingId);
    }

    @Override
    public List<BindingContext> listTeacherBindings(Long userId)
    {
        return supportMapper.selectTeacherBindings(userId);
    }

    @Override
    public List<BindingContext> listStudentBindings(Long userId)
    {
        return supportMapper.selectStudentBindings(userId);
    }
}
