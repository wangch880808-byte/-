package com.ruoyi.education.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.domain.EduCourseClassBinding;
import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.mapper.EduCourseClassBindingMapper;
import com.ruoyi.education.mapper.EduCourseSpaceMapper;
import com.ruoyi.education.mapper.EduTeachingClassMapper;
import com.ruoyi.education.service.IEduCourseSpaceService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EduCourseSpaceServiceImpl implements IEduCourseSpaceService {

    @Autowired
    private EduCourseSpaceMapper courseSpaceMapper;

    @Autowired
    private EduCourseClassBindingMapper bindingMapper;

    @Autowired
    private EduTeachingClassMapper teachingClassMapper;

    @Override
    public List<EduCourseSpace> list(EduCourseSpace query) {
        Long userId = SecurityUtils.getUserId();
        String role = SecurityUtils.getLoginUser().getUser().getRoles().get(0).getRoleKey();

        if ("admin".equals(role)) {
            return courseSpaceMapper.selectAll(query);
        } else {
            return courseSpaceMapper.selectByOwnerUserId(userId);
        }
    }

    @Override
    public EduCourseSpace getById(Long id) {
        EduCourseSpace course = courseSpaceMapper.selectById(id);
        if (course == null) {
            throw new ServiceException("课程不存在");
        }
        // 校验：老师只能看自己的课
        Long userId = SecurityUtils.getUserId();
        String role = SecurityUtils.getLoginUser().getUser().getRoles().get(0).getRoleKey();
        if (!"admin".equals(role) && !course.getOwnerUserId().equals(userId)) {
            throw new ServiceException("无权查看该课程");
        }
        return course;
    }

    @Override
    public Long create(EduCourseSpace courseSpace) {
        courseSpace.setOwnerUserId(SecurityUtils.getUserId());
        courseSpace.setOrganizationId(SecurityUtils.getLoginUser().getDeptId());
        courseSpaceMapper.insert(courseSpace);
        return courseSpace.getId();
    }

    @Override
    public void update(EduCourseSpace courseSpace) {
        // 先查出来，校验是不是自己的课
        EduCourseSpace existing = courseSpaceMapper.selectById(courseSpace.getId());
        if (existing == null) {
            throw new ServiceException("课程不存在");
        }
        Long userId = SecurityUtils.getUserId();
        String role = SecurityUtils.getLoginUser().getUser().getRoles().get(0).getRoleKey();
        if (!"admin".equals(role) && !existing.getOwnerUserId().equals(userId)) {
            throw new ServiceException("无权修改该课程");
        }
        courseSpaceMapper.update(courseSpace);
    }

    @Override
    public List<EduCourseClassBinding> listBindings(Long courseId) {
        // 查绑定前先校验课程权限
        this.getById(courseId);
        return bindingMapper.selectByCourseId(courseId);
    }

    @Override
    public void bindClass(Long courseId, Long classId) {
        // 校验课程是不是自己的
        EduCourseSpace course = courseSpaceMapper.selectById(courseId);
        if (course == null) {
            throw new ServiceException("课程不存在");
        }
        Long userId = SecurityUtils.getUserId();
        String role = SecurityUtils.getLoginUser().getUser().getRoles().get(0).getRoleKey();
        if (!"admin".equals(role) && !course.getOwnerUserId().equals(userId)) {
            throw new ServiceException("无权操作该课程");
        }

        EduCourseClassBinding existing = bindingMapper.selectActiveByClassId(classId);
        if (existing != null) {
            throw new ServiceException("这个班已经绑了其他课程，不能重复绑定");
        }

        com.ruoyi.education.domain.EduTeachingClass teachingClass = teachingClassMapper.selectById(classId);
        if (teachingClass == null) {
            throw new ServiceException("教学班不存在");
        }

        EduCourseClassBinding binding = new EduCourseClassBinding();
        binding.setOrganizationId(teachingClass.getOrganizationId());
        binding.setTeachingClassId(classId);
        binding.setCourseSpaceId(courseId);
        binding.setBoundByUserId(SecurityUtils.getUserId());
        bindingMapper.insert(binding);
    }

    @Override
    public void unbindClass(Long bindingId) {
        bindingMapper.endBinding(bindingId, SecurityUtils.getUserId());
    }
}
