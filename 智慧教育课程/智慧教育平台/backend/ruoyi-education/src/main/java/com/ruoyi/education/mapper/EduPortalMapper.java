package com.ruoyi.education.mapper;

import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 三端页面（管理员 / 教师 / 学生）的查询与命令映射。
 *
 * <p>查询结果直接按页面的字段名起别名返回 Map：这些是跨表拼接的展示模型（课程 + 学期 + 教师 + 人数），
 * 不属于单个领域实体，避免为每个页面造一个只用一次的空壳 VO。写入仍走各领域的 Mapper / Service。</p>
 */
public interface EduPortalMapper {

    /* ---------------- 学期 ---------------- */

    List<Map<String, Object>> selectTermList(@Param("keyword") String keyword);

    Map<String, Object> selectTermById(@Param("id") Long id);

    int clearCurrentTerm();

    int markCurrentTerm(@Param("id") Long id);

    /* ---------------- 教学班 ---------------- */

    List<Map<String, Object>> selectTeachingClassList(@Param("keyword") String keyword,
                                                      @Param("termId") Long termId,
                                                      @Param("status") String status);

    Map<String, Object> selectTeachingClassById(@Param("id") Long id);

    /** 当前教师被授权的教学班（valid_to IS NULL） */
    List<Map<String, Object>> selectTeachingClassByTeacher(@Param("userId") Long userId);

    List<Map<String, Object>> selectClassMembers(@Param("classId") Long classId);

    List<Map<String, Object>> selectClassTeachers(@Param("classId") Long classId);

    int countActiveTeacher(@Param("classId") Long classId, @Param("userId") Long userId);

    int insertClassStudent(@Param("classId") Long classId, @Param("userId") Long userId,
                           @Param("recordedBy") Long recordedBy);

    int insertClassTeacher(@Param("classId") Long classId, @Param("userId") Long userId,
                           @Param("teacherRole") String teacherRole,
                           @Param("canPublish") Integer canPublish, @Param("canGrade") Integer canGrade,
                           @Param("canExport") Integer canExport, @Param("validTo") String validTo,
                           @Param("grantedBy") Long grantedBy);

    /* ---------------- 教师 / 学生 / 组织 ---------------- */

    List<Map<String, Object>> selectTeacherList(@Param("keyword") String keyword, @Param("deptId") Long deptId);

    List<Map<String, Object>> selectTeacherClasses(@Param("userId") Long userId);

    List<Map<String, Object>> selectStudentList(@Param("keyword") String keyword);

    List<Map<String, Object>> selectStudentClasses(@Param("userId") Long userId);

    List<Map<String, Object>> selectDeptTree();

    List<Map<String, Object>> selectOfficialCourses(@Param("keyword") String keyword);

    /* ---------------- 课程空间 ---------------- */

    /**
     * @param ownerUserId   非空时只查该教师创建的课程
     * @param studentUserId 非空时只查该学生所在教学班已绑定、且课程已发布的课程
     */
    List<Map<String, Object>> selectCourseList(@Param("ownerUserId") Long ownerUserId,
                                               @Param("studentUserId") Long studentUserId,
                                               @Param("keyword") String keyword);

    Map<String, Object> selectCourseById(@Param("id") Long id);

    List<Map<String, Object>> selectCourseUnits(@Param("courseId") Long courseId);

    List<Map<String, Object>> selectCourseKnowledgePoints(@Param("courseId") Long courseId);

    List<Map<String, Object>> selectCourseBindings(@Param("courseId") Long courseId);

    int countStudentInCourse(@Param("courseId") Long courseId, @Param("userId") Long userId);

    int countActiveBinding(@Param("courseId") Long courseId, @Param("classId") Long classId);

    int updateCourseStatus(@Param("id") Long id, @Param("status") String status);

    int updateCourseStatusForOwner(@Param("id") Long id, @Param("status") String status,
                                   @Param("ownerUserId") Long ownerUserId);

    int endBinding(@Param("bindingId") Long bindingId, @Param("courseId") Long courseId,
                   @Param("userId") Long userId);

    /* ---------------- 组织推导 ---------------- */

    /** 用户所属部门的顶层部门 = 演示数据里的「学校」组织 id */
    Long selectOrganizationIdByUserId(@Param("userId") Long userId);

    Long selectOrganizationIdByTermId(@Param("termId") Long termId);

    Long selectOrganizationIdByClassId(@Param("classId") Long classId);

    Long selectOrganizationIdByOfficialCourseId(@Param("officialCourseId") Long officialCourseId);
}
