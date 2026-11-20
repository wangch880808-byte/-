package com.ruoyi.education.group3.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.education.course.api.EduClassBindingRef;
import com.ruoyi.education.course.api.EduClassMembership;
import com.ruoyi.education.course.api.EduClassRef;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.domain.EduTerm;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Security;
import com.ruoyi.education.service.IEduPortalService;
import com.ruoyi.education.service.IEduTermService;

/**
 * 第三组访问控制。
 *
 * <p>所有对教学班、任课、在班、课程绑定的判断一律通过第二组暴露的
 * {@link EduCourseAccessService} 契约完成，不再直读 {@code edu_class_teacher}、
 * {@code edu_class_student}、{@code edu_teaching_class}、
 * {@code edu_course_class_binding} 等课程领域私有表。</p>
 */
@Service
public class Group3AccessService
{
    private final Group3Mapper mapper;
    private final EduCourseAccessService access;
    private final IEduPortalService portal;
    private final IEduTermService terms;

    public Group3AccessService(Group3Mapper mapper, EduCourseAccessService access,
            IEduPortalService portal, IEduTermService terms)
    {
        this.mapper = mapper;
        this.access = access;
        this.portal = portal;
        this.terms = terms;
    }

    public List<Map<String, Object>> contexts()
    {
        Long userId = Group3Security.currentUserId();
        boolean teacher = Group3Security.isTeacher();
        if (!teacher && !Group3Security.isStudent())
        {
            return List.of();
        }
        Map<Long, Map<String, Object>> visibleCourses = new HashMap<>();
        for (Map<String, Object> course : portal.courseList(null))
        {
            if (usableCourse(course, teacher))
            {
                visibleCourses.put(asLong(course.get("id")), course);
            }
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (EduClassRef cls : classes(userId, teacher))
        {
            if (!cls.open())
            {
                continue;
            }
            EduClassMembership membership = teacher ? publishingMembership(cls.classId(), userId) : null;
            if (teacher && membership == null)
            {
                continue;
            }
            for (EduClassBindingRef binding : access.activeBindings(cls.classId()))
            {
                Map<String, Object> course = visibleCourses.get(binding.courseSpaceId());
                if (course != null)
                {
                    result.add(bindingMap(binding, course, cls, membership));
                }
            }
        }
        return result;
    }

    public Map<String, Object> requireTeacherBinding(Long bindingId)
    {
        Group3Security.requireTeacher();
        return requireBinding(bindingId, true);
    }

    public Map<String, Object> requireStudentBinding(Long bindingId)
    {
        Group3Security.requireStudent();
        return requireBinding(bindingId, false);
    }

    public Map<String, Object> requireTeacherExportBinding(Long bindingId)
    {
        Map<String, Object> binding = requireTeacherBinding(bindingId);
        if (!Boolean.TRUE.equals(binding.get("canExport")))
        {
            throw new EduForbiddenException("当前账号没有该教学班的导出权限");
        }
        return binding;
    }

    private Map<String, Object> requireBinding(Long bindingId, boolean teacher)
    {
        Long userId = Group3Security.currentUserId();
        for (EduClassRef cls : classes(userId, teacher))
        {
            if (!cls.open())
            {
                continue;
            }
            for (EduClassBindingRef binding : access.activeBindings(cls.classId()))
            {
                if (!binding.bindingId().equals(bindingId))
                {
                    continue;
                }
                EduClassMembership membership = teacher ? publishingMembership(cls.classId(), userId) : null;
                if (teacher ? membership == null : !access.isActiveStudent(cls.classId(), userId))
                {
                    throw new EduForbiddenException("当前账号没有该教学班的有效授权");
                }
                Map<String, Object> course = portal.courseDetail(binding.courseSpaceId());
                if (!usableCourse(course, teacher))
                {
                    throw new EduForbiddenException("当前课程未开放");
                }
                return bindingMap(binding, course, cls, membership);
            }
        }
        throw new EduForbiddenException("无权访问该课程或教学班，或绑定已失效");
    }

    private List<EduClassRef> classes(Long userId, boolean teacher)
    {
        return teacher ? access.classesOfTeacher(userId) : access.classesOfStudent(userId);
    }

    private EduClassMembership publishingMembership(Long classId, Long userId)
    {
        EduClassMembership membership = access.membership(classId, userId);
        return membership.teacher() && membership.canPublish() ? membership : null;
    }

    private boolean usableCourse(Map<String, Object> course, boolean teacher)
    {
        Object status = course.get("status");
        return "ACTIVE".equals(status) || (teacher && "DRAFT".equals(status));
    }

    public Map<String, Object> requireAnyBinding(Long bindingId)
    {
        if (Group3Security.isTeacher())
        {
            return requireTeacherBinding(bindingId);
        }
        return requireStudentBinding(bindingId);
    }

    /**
     * 取教学班当前有效在班学生 ID，供学情统计的分母使用。
     * 只有本班具备发布权限的任课教师可调用；学生名单来自第二组契约，不直读课程表。
     */
    public List<Long> activeStudentIds(Long bindingId)
    {
        Map<String, Object> binding = requireTeacherBinding(bindingId);
        Long classId = asLong(binding.get("teachingClassId"));
        return access.activeMembers(classId).stream().map(member -> member.userId()).toList();
    }

    public Map<String, Object> teacherVersion(Long bindingId, Long versionId)
    {
        Map<String, Object> binding = requireTeacherBinding(bindingId);
        Long courseSpaceId = asLong(binding.get("courseSpaceId"));
        Map<String, Object> version = mapper.selectTeacherVersion(versionId, courseSpaceId);
        if (version == null)
        {
            throw new ServiceException("资源版本不存在或不属于当前课程", HttpStatus.NOT_FOUND);
        }
        return decorate(version, binding);
    }

    public Map<String, Object> studentVersion(Long bindingId, Long versionId)
    {
        Map<String, Object> binding = requireStudentBinding(bindingId);
        Map<String, Object> version = mapper.selectStudentVisibleVersion(bindingId, versionId);
        if (version == null)
        {
            throw new ServiceException("资料尚未开放、已截止或您无权访问", HttpStatus.FORBIDDEN);
        }
        return decorate(version, binding);
    }

    public Map<String, Object> visibleVersion(Long bindingId, Long versionId)
    {
        return Group3Security.isTeacher()
                ? teacherVersion(bindingId, versionId)
                : studentVersion(bindingId, versionId);
    }

    private Map<String, Object> bindingMap(EduClassBindingRef binding, Map<String, Object> course, EduClassRef cls,
            EduClassMembership membership)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bindingId", binding.bindingId());
        map.put("courseSpaceId", binding.courseSpaceId());
        map.put("courseTitle", course.get("title"));
        map.put("courseSummary", course.get("summary"));
        map.put("courseStatus", course.get("status"));
        map.put("teachingClassId", cls.classId());
        map.put("className", cls.name());
        map.put("classCode", cls.code());
        map.put("classKind", cls.classKind());
        map.put("classStatus", cls.status());
        map.put("termId", cls.termId());
        map.put("termName", cls.termName());
        EduTerm term = cls.termId() == null ? null : terms.getById(cls.termId());
        map.put("termStartsOn", term == null || term.getStartsOn() == null ? null
                : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, term.getStartsOn()));
        map.put("termEndsOn", term == null || term.getEndsOn() == null ? null
                : DateUtils.parseDateToStr(DateUtils.YYYY_MM_DD, term.getEndsOn()));
        map.put("teacherRole", membership == null ? null : membership.teacherRole());
        map.put("canPublish", membership != null);
        map.put("canGrade", membership != null && membership.canGrade());
        map.put("canExport", membership != null && membership.canExport());
        return map;
    }

    private Map<String, Object> decorate(Map<String, Object> version, Map<String, Object> binding)
    {
        version.put("bindingId", binding.get("bindingId"));
        version.put("courseSpaceId", binding.get("courseSpaceId"));
        version.put("courseTitle", binding.get("courseTitle"));
        version.put("className", binding.get("className"));
        return version;
    }

    public static Long asLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        return Long.valueOf(value.toString());
    }
}
