package com.ruoyi.education.service.impl;

import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.domain.EduClassStudent;
import com.ruoyi.education.domain.EduClassTeacher;
import com.ruoyi.education.domain.EduTeachingClass;
import com.ruoyi.education.mapper.EduClassStudentMapper;
import com.ruoyi.education.mapper.EduClassTeacherMapper;
import com.ruoyi.education.mapper.EduTeachingClassMapper;
import com.ruoyi.education.service.IEduTeachingClassService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
public class EduTeachingClassServiceImpl implements IEduTeachingClassService {

    @Autowired
    private EduTeachingClassMapper teachingClassMapper;

    @Autowired
    private EduClassTeacherMapper classTeacherMapper;

    @Autowired
    private EduClassStudentMapper classStudentMapper;

    /**
     * 校验当前用户有没有操作这个班的权限
     * 规则：管理员 或 这个班的任课老师
     */
    private void checkClassPermission(Long classId) {
        Long userId = SecurityUtils.getUserId();
        String role = SecurityUtils.getLoginUser().getUser().getRoles().get(0).getRoleKey();

        // 管理员放行
        if ("admin".equals(role)) {
            return;
        }

        // 查这个老师是不是这个班的任课老师
        EduClassTeacher teacher = classTeacherMapper.selectActive(classId, userId);
        if (teacher == null) {
            throw new ServiceException("无权操作该教学班");
        }
    }

    @Override
    public List<EduTeachingClass> list(EduTeachingClass query) {
        Long userId = SecurityUtils.getUserId();
        String role = SecurityUtils.getLoginUser().getUser().getRoles().get(0).getRoleKey();

        if ("admin".equals(role)) {
            return teachingClassMapper.selectList(query);
        } else if ("teacher".equals(role)) {
            return teachingClassMapper.selectListByTeacherUserId(userId);
        } else if ("student".equals(role)) {
            return teachingClassMapper.selectListByStudentUserId(userId);
        }
        return List.of();
    }

    @Override
    public EduTeachingClass getById(Long id) {
        return teachingClassMapper.selectById(id);
    }

    @Override
    public Long create(EduTeachingClass teachingClass) {
        teachingClassMapper.insert(teachingClass);
        return teachingClass.getId();
    }

    @Override
    public void update(EduTeachingClass teachingClass) {
        teachingClassMapper.update(teachingClass);
    }

    @Override
    public List<EduClassTeacher> listTeachers(Long classId) {
        checkClassPermission(classId);
        return classTeacherMapper.selectByClassId(classId);
    }

    @Override
    public void addTeacher(Long classId, Long userId, String teacherRole) {
        checkClassPermission(classId);
        EduClassTeacher existing = classTeacherMapper.selectActive(classId, userId);
        if (existing != null) {
            throw new ServiceException("该老师已经是这个班的任课教师了");
        }
        EduClassTeacher record = new EduClassTeacher();
        record.setTeachingClassId(classId);
        record.setUserId(userId);
        record.setTeacherRole(teacherRole);
        record.setCanPublish(true);
        record.setCanGrade(true);
        record.setCanExport(true);
        record.setGrantedByUserId(SecurityUtils.getUserId());
        classTeacherMapper.insert(record);
    }

    @Override
    public void removeTeacher(Long classId, Long userId) {
        checkClassPermission(classId);
        classTeacherMapper.revoke(classId, userId, SecurityUtils.getUserId());
    }

    @Override
    public List<EduClassStudent> listStudents(Long classId) {
        checkClassPermission(classId);
        return classStudentMapper.selectByClassId(classId);
    }

    @Override
    public void addStudent(Long classId, Long userId) {
        checkClassPermission(classId);
        EduClassStudent existing = classStudentMapper.selectActive(classId, userId);
        if (existing != null) {
            throw new ServiceException("该学生已经在这个班了");
        }
        EduClassStudent record = new EduClassStudent();
        record.setTeachingClassId(classId);
        record.setUserId(userId);
        record.setRecordedByUserId(SecurityUtils.getUserId());
        classStudentMapper.insert(record);
    }

    @Override
    public void removeStudent(Long classId, Long userId, String reason) {
        checkClassPermission(classId);
        classStudentMapper.leave(classId, userId, SecurityUtils.getUserId(), reason);
    }
}
