package com.ruoyi.education.service;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.mapper.EduClassStudentMapper;
import com.ruoyi.education.mapper.EduCourseManagementMapper;
import com.ruoyi.education.mapper.EduCourseSpaceMapper;
import com.ruoyi.education.mapper.EduPortalMapper;
import com.ruoyi.education.security.EduActor;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Isolation;

@Service
public class EduCourseManagementService {
    private final EduCourseManagementMapper management;
    private final EduCourseSpaceMapper courses;
    private final EduPortalMapper portal;
    private final EduClassStudentMapper students;
    private final IEduPortalService portalService;

    public EduCourseManagementService(EduCourseManagementMapper management, EduCourseSpaceMapper courses,
            EduPortalMapper portal, EduClassStudentMapper students, IEduPortalService portalService) {
        this.management = management;
        this.courses = courses;
        this.portal = portal;
        this.students = students;
        this.portalService = portalService;
    }

    public List<Map<String, Object>> team(Long courseId) {
        requireCourse(courseId, false, false);
        return management.team(courseId);
    }

    public List<Map<String, Object>> teacherCandidates(Long courseId, String keyword, int page, int size) {
        EduCourseSpace course = requireCourse(courseId, true, false);
        if (keyword != null && keyword.length() > 100) throw new ServiceException("搜索关键词过长");
        PageHelper.startPage(Math.max(1, page), Math.max(1, Math.min(size, 100)));
        return management.teacherCandidates(courseId, course.getOrganizationId(), keyword, null);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void saveTeacher(Long courseId, Long userId, boolean manageStudents, boolean export) {
        EduCourseSpace course = requireCourse(courseId, true, true);
        if (course.getOwnerUserId().equals(userId)) throw new ServiceException("课程负责人已经在团队中");
        if (management.teacherCandidates(courseId, course.getOrganizationId(), null, userId).isEmpty()) {
            throw new ServiceException("请选择本校已启用的教师账号");
        }
        management.saveTeacher(courseId, userId, manageStudents, export, SecurityUtils.getUserId());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void removeTeacher(Long courseId, Long userId) {
        EduCourseSpace course = requireCourse(courseId, true, true);
        if (course.getOwnerUserId().equals(userId)) throw new ServiceException("不能移除课程负责人");
        management.removeTeacher(courseId, userId, SecurityUtils.getUserId());
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void renameClass(Long courseId, Long classId, String name) {
        requireCourse(courseId, true, true);
        requireClass(courseId, classId, false);
        String value = name == null ? "" : name.strip();
        if (value.isEmpty() || value.length() > 160) throw new ServiceException("班级名称须为 1 至 160 个字符");
        if (management.activeBindingCount(classId) > 1) {
            throw new ServiceException("此教学班被多门课程共用，请先确认所有课程后再修改名称");
        }
        Map<String, Object> current = portal.selectTeachingClassById(classId);
        if (value.equals(current.get("name"))) return;
        if (portal.countCourseClassByName(courseId, value) > 0) throw new ServiceException("这门课程已有同名教学班");
        management.renameClass(classId, value);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void deleteClass(Long courseId, Long classId) {
        requireCourse(courseId, true, true);
        if (management.courseClassHistory(courseId, classId) == 0) {
            throw new EduForbiddenException("该教学班不属于这门课程");
        }
        management.lockClass(classId);
        if (portal.countActiveBinding(courseId, classId) == 0) return;
        management.detachClass(courseId, classId, SecurityUtils.getUserId());
        if (management.activeBindingCount(classId) == 0) management.archiveClass(classId);
    }

    @Transactional(rollbackFor = Exception.class, isolation = Isolation.READ_COMMITTED)
    public void changeStudents(Long courseId, Long classId, List<Long> ids, Long targetClassId) {
        requireCourse(courseId, false, true);
        List<Long> selected = distinctIds(ids);
        if (targetClassId != null && targetClassId.equals(classId)) throw new ServiceException("请选择其他教学班");
        requireClass(courseId, classId, true);
        if (targetClassId != null) requireClass(courseId, targetClassId, true);
        List<Long> active = management.activeStudentIds(classId, selected);
        if (active.size() != selected.size()) throw new ServiceException("学生名单已变化，请刷新后重新选择");
        Long operatorId = SecurityUtils.getUserId();
        if (targetClassId != null) {
            for (Long studentId : active) {
                if (students.selectActive(targetClassId, studentId) == null) {
                    portal.insertClassStudent(targetClassId, studentId, operatorId);
                }
            }
        }
        String reason = targetClassId == null ? "教师移出教学班" : "教师调整至教学班 " + targetClassId;
        if (management.endStudents(classId, active, operatorId, reason) != active.size()) {
            throw new ServiceException("学生名单已变化，请刷新后重试");
        }
    }

    private EduCourseSpace requireCourse(Long courseId, boolean ownerOnly, boolean lock) {
        if (!"teacher".equals(EduActor.role()) || portal.countActiveAccountRole(SecurityUtils.getUserId(), "teacher") == 0) {
            throw new EduForbiddenException("只有有效教师可以管理课程");
        }
        EduCourseSpace course = lock ? courses.selectByIdForUpdate(courseId) : courses.selectById(courseId);
        if (course == null) throw new ServiceException("课程不存在");
        boolean owner = SecurityUtils.getUserId().equals(course.getOwnerUserId());
        if (!owner && (ownerOnly || management.teamAccess(courseId, SecurityUtils.getUserId()) == null)) {
            throw new EduForbiddenException(ownerOnly ? "只有课程负责人可以操作" : "你不在本课程的教师团队中");
        }
        if (lock && "ARCHIVED".equals(course.getStatus())) throw new ServiceException("已归档课程只允许查看");
        return course;
    }

    private void requireClass(Long courseId, Long classId, boolean editStudents) {
        if (management.lockClass(classId) == null || portal.countActiveBinding(courseId, classId) == 0) {
            throw new EduForbiddenException("该教学班不属于这门课程");
        }
        if (editStudents && !Boolean.TRUE.equals(portalService.rosterPermissions(classId).get("canImport"))) {
            throw new EduForbiddenException("你没有这个教学班的学生管理权限");
        }
    }

    private List<Long> distinctIds(List<Long> ids) {
        if (ids == null || ids.isEmpty() || ids.size() > 1000 || ids.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ServiceException("每次请选择 1 至 1000 名学生");
        }
        return new ArrayList<>(new LinkedHashSet<>(ids)).stream().sorted().toList();
    }
}
