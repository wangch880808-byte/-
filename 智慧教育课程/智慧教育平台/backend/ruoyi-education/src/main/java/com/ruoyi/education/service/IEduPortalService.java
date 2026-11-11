package com.ruoyi.education.service;

import java.util.List;
import java.util.Map;

/**
 * 三端页面（管理员 / 教师 / 学生）的联调服务。
 *
 * <p>负责三件事：按当前登录人的角色裁剪数据范围、把页面字段名与库表字段对齐、在写入前做权限与业务校验。
 * 教学业务规则仍留在 course 领域（课程空间、教学班、成员、授权），这里不新增第二套模型。</p>
 */
public interface IEduPortalService {

    boolean isAdmin();

    /* ---------------- 学期 ---------------- */

    List<Map<String, Object>> termList(String keyword);

    Long createTerm(Map<String, Object> body);

    void updateTerm(Long id, Map<String, Object> body);

    void setCurrentTerm(Long id);

    /* ---------------- 教学班 ---------------- */

    List<Map<String, Object>> teachingClassList(String keyword, Long termId, String status);

    Map<String, Object> teachingClassDetail(Long id);

    List<Map<String, Object>> myTeachingClasses();

    Long createTeachingClass(Map<String, Object> body);

    void updateTeachingClass(Long id, Map<String, Object> body);

    List<Map<String, Object>> classMembers(Long classId);
    Map<String, Boolean> rosterPermissions(Long classId);
    String importRoster(Long classId, Map<Integer, String> accountRows);
    List<Map<String, Object>> exportRoster(Long classId);

    void addMember(Long classId, Map<String, Object> body);

    void removeMember(Long classId, Long studentId, String reason);

    List<Map<String, Object>> classTeachers(Long classId);

    void addTeacher(Long classId, Map<String, Object> body);

    void removeTeacher(Long classId, Long userId);

    /* ---------------- 教师 / 学生 / 组织 ---------------- */

    List<Map<String, Object>> teacherList(String keyword, Long deptId);

    List<Map<String, Object>> teacherClasses(Long userId);

    List<Map<String, Object>> studentList(String keyword);

    List<Map<String, Object>> studentClasses(Long userId);

    List<Map<String, Object>> deptTree();

    List<Map<String, Object>> officialCourses(String keyword);

    /* ---------------- 课程空间 ---------------- */

    List<Map<String, Object>> courseList(String keyword);

    Map<String, Object> courseDetail(Long id);

    Long createCourse(Map<String, Object> body);

    void updateCourse(Long id, Map<String, Object> body);

    void updateCourseStatus(Long id, String status);

    void deleteCourse(Long id);

    void bindClass(Long courseId, Long classId, String publishedAt);

    void unbindClass(Long courseId, Long bindingId);
}
