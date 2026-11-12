package com.ruoyi.education.course.api.impl;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.course.api.EduClassBindingRef;
import com.ruoyi.education.course.api.EduClassMembership;
import com.ruoyi.education.course.api.EduClassRef;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.course.api.EduCourseRef;
import com.ruoyi.education.course.api.EduMemberRef;
import com.ruoyi.education.course.api.EduTermRef;
import com.ruoyi.education.domain.EduClassStudent;
import com.ruoyi.education.domain.EduClassTeacher;
import com.ruoyi.education.domain.EduCourseClassBinding;
import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.mapper.EduClassStudentMapper;
import com.ruoyi.education.mapper.EduClassTeacherMapper;
import com.ruoyi.education.mapper.EduCourseClassBindingMapper;
import com.ruoyi.education.mapper.EduCourseSpaceMapper;
import com.ruoyi.education.mapper.EduPortalMapper;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.security.EduActor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * {@link EduCourseAccessService} 的默认实现。
 *
 * <p>实现刻意保持「一次调用一到两条轻量查询」：跨领域接口会在课堂事件、作业提交等热路径上被调用，
 * 不做全量 join、不加缓存。缓存会让撤权延时生效，与 2-01 的验收条件冲突。</p>
 */
@Service
public class EduCourseAccessServiceImpl implements EduCourseAccessService {

    private static final String ROLE_ADMIN = "admin";
    private static final String STATUS_ACTIVE = "ACTIVE";

    @Autowired
    private EduPortalMapper portalMapper;
    @Autowired
    private EduClassTeacherMapper classTeacherMapper;
    @Autowired
    private EduClassStudentMapper classStudentMapper;
    @Autowired
    private EduCourseClassBindingMapper bindingMapper;
    @Autowired
    private EduCourseSpaceMapper courseSpaceMapper;

    /* ================= 教学班 ================= */

    @Override
    public EduClassRef requireClass(Long classId) {
        if (classId == null) {
            throw new ServiceException("教学班编号不能为空");
        }
        Map<String, Object> row = portalMapper.selectTeachingClassById(classId);
        if (row == null) {
            throw new ServiceException("教学班不存在");
        }
        return new EduClassRef(
                classId,
                text(row.get("name")),
                text(row.get("code")),
                number(row.get("termId")),
                text(row.get("termName")),
                portalMapper.selectOrganizationIdByClassId(classId),
                text(row.get("status")),
                text(row.get("classKind")));
    }

    @Override
    public EduClassRef requireActiveClass(Long classId) {
        EduClassRef ref = requireClass(classId);
        if (!ref.open()) {
            throw new ServiceException("教学班当前状态为 " + ref.status() + "，未开放");
        }
        return ref;
    }

    @Override
    public boolean isActiveTeacher(Long classId, Long userId) {
        if (classId == null || userId == null) {
            return false;
        }
        return portalMapper.countActiveTeacher(classId, userId) > 0;
    }

    @Override
    public boolean isActiveStudent(Long classId, Long userId) {
        if (classId == null || userId == null) {
            return false;
        }
        return classStudentMapper.selectActive(classId, userId) != null;
    }

    @Override
    public EduClassMembership membership(Long classId, Long userId) {
        requireClass(classId);
        EduClassTeacher teacher = classTeacherMapper.selectActive(classId, userId);
        EduClassStudent student = classStudentMapper.selectActive(classId, userId);
        if (teacher == null && student == null) {
            throw new EduForbiddenException("你不是该教学班的成员");
        }
        return new EduClassMembership(
                classId,
                userId,
                teacher != null,
                student != null,
                teacher == null ? null : teacher.getTeacherRole(),
                teacher != null && Boolean.TRUE.equals(teacher.getCanPublish()),
                teacher != null && Boolean.TRUE.equals(teacher.getCanGrade()),
                teacher != null && Boolean.TRUE.equals(teacher.getCanExport()),
                teacher == null ? null : date(teacher.getValidTo()));
    }

    @Override
    public List<EduMemberRef> activeMembers(Long classId) {
        List<Map<String, Object>> rows = portalMapper.selectClassMembers(classId);
        List<EduMemberRef> members = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (!"ACTIVE".equals(row.get("status"))) continue;
            members.add(new EduMemberRef(
                    classId,
                    number(row.get("memberId")),
                    number(row.get("studentId")),
                    text(row.get("studentNo")),
                    text(row.get("studentName")),
                    text(row.get("joinedAt"))));
        }
        return members;
    }

    @Override
    public List<EduClassRef> classesOfTeacher(Long userId) {
        List<Map<String, Object>> rows = portalMapper.selectTeachingClassByTeacher(userId);
        List<EduClassRef> classes = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            classes.add(new EduClassRef(
                    number(row.get("id")),
                    text(row.get("name")),
                    text(row.get("code")),
                    number(row.get("termId")),
                    text(row.get("termName")),
                    number(row.get("organizationId")),
                    text(row.get("status")),
                    text(row.get("classKind"))));
        }
        return classes;
    }

    @Override
    public List<EduClassRef> classesOfStudent(Long userId) {
        List<Map<String, Object>> rows = portalMapper.selectStudentClasses(userId);
        List<EduClassRef> classes = new ArrayList<>(rows.size());
        for (Map<String, Object> row : rows) {
            if (!STATUS_ACTIVE.equals(text(row.get("status"))) || !STATUS_ACTIVE.equals(text(row.get("classStatus")))) {
                continue;
            }
            classes.add(new EduClassRef(
                    number(row.get("classId")),
                    text(row.get("className")),
                    text(row.get("code")),
                    number(row.get("termId")),
                    text(row.get("termName")),
                    number(row.get("organizationId")),
                    text(row.get("classStatus")),
                    text(row.get("classKind"))));
        }
        return classes;
    }

    @Override
    public EduClassBindingRef activeBinding(Long classId) {
        List<EduClassBindingRef> bindings = activeBindings(classId);
        if (bindings.size() > 1) throw new ServiceException("该班有多个课程绑定，请指定课程编号或使用绑定列表接口");
        return bindings.isEmpty() ? null : bindings.get(0);
    }

    @Override
    public List<EduClassBindingRef> activeBindings(Long classId) {
        return bindingMapper.selectActiveListByClassId(classId).stream()
                .map(binding -> new EduClassBindingRef(binding.getId(), binding.getTeachingClassId(),
                        binding.getCourseSpaceId(), date(binding.getPublishedAt()))).toList();
    }

    /* ================= 课程 ================= */

    @Override
    public EduCourseRef requireCourseReadable(Long courseSpaceId, Long userId) {
        EduCourseSpace space = loadCourse(courseSpaceId);
        if (isAdmin() || ("teacher".equals(EduActor.role()) && userId.equals(space.getOwnerUserId()))) {
            return toRef(space);
        }
        boolean studentReadable = "student".equals(EduActor.role())
                && STATUS_ACTIVE.equals(space.getStatus())
                && portalMapper.countStudentInCourse(courseSpaceId, userId) > 0;
        if (!studentReadable) {
            throw new EduForbiddenException("你没有这门课程的访问权限");
        }
        return toRef(space);
    }

    @Override
    public EduCourseRef requireCourseOwned(Long courseSpaceId, Long userId) {
        EduCourseSpace space = loadCourse(courseSpaceId);
        if (!isAdmin() && !userId.equals(space.getOwnerUserId())) {
            throw new EduForbiddenException("只有课程负责人可以操作这门课程");
        }
        return toRef(space);
    }

    private EduCourseSpace loadCourse(Long courseSpaceId) {
        if (courseSpaceId == null) {
            throw new ServiceException("课程编号不能为空");
        }
        EduCourseSpace space = courseSpaceMapper.selectById(courseSpaceId);
        if (space == null) {
            throw new ServiceException("课程不存在");
        }
        return space;
    }

    private EduCourseRef toRef(EduCourseSpace space) {
        return new EduCourseRef(space.getId(), space.getTitle(), space.getOwnerUserId(),
                space.getOrganizationId(), space.getStatus());
    }

    /* ================= 学期 ================= */

    @Override
    public EduTermRef currentTerm() {
        List<Map<String, Object>> rows = portalMapper.selectTermList(null);
        for (Map<String, Object> row : rows) {
            if (truthy(row.get("current"))) {
                return new EduTermRef(
                        number(row.get("id")),
                        text(row.get("name")),
                        text(row.get("code")),
                        text(row.get("startsOn")),
                        text(row.get("endsOn")),
                        true);
            }
        }
        return null;
    }

    /* ================= 内部工具 ================= */

    private boolean isAdmin() { return com.ruoyi.education.security.EduActor.admin(); }

    private static String text(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Date date) {
            return date(date);
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static Long number(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        String text = String.valueOf(value).trim();
        if (text.isEmpty()) {
            return null;
        }
        try {
            return Long.valueOf(text);
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean truthy(Object value) {
        if (value == null) {
            return false;
        }
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        String text = String.valueOf(value).trim();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private static String date(Date value) {
        return value == null ? null : new SimpleDateFormat("yyyy-MM-dd").format(value);
    }
}
