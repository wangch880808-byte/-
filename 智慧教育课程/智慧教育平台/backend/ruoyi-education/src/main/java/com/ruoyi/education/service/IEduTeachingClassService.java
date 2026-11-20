package com.ruoyi.education.service;

import com.ruoyi.education.domain.EduTeachingClass;
import java.util.List;
import com.ruoyi.education.domain.EduClassTeacher;
import com.ruoyi.education.domain.EduClassStudent;

public interface IEduTeachingClassService {
    List<EduTeachingClass> list(EduTeachingClass query);
    EduTeachingClass getById(Long id);
    Long create(EduTeachingClass teachingClass);
    void update(EduTeachingClass teachingClass);
    // 新增：教师授权相关
    List<EduClassTeacher> listTeachers(Long classId);
    void addTeacher(Long classId, Long userId, String teacherRole);
    void removeTeacher(Long classId, Long userId);
//    新增：学生入退班相关
    List<EduClassStudent> listStudents(Long classId);
    void addStudent(Long classId, Long userId);
    void removeStudent(Long classId, Long userId, String reason);
}
