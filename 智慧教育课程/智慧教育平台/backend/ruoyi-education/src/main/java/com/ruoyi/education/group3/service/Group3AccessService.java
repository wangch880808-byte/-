package com.ruoyi.education.group3.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduClassBindingRef;
import com.ruoyi.education.course.api.EduClassMembership;
import com.ruoyi.education.course.api.EduClassRef;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.course.api.EduCourseRef;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Security;

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

    public Group3AccessService(Group3Mapper mapper, EduCourseAccessService access)
    {
        this.mapper = mapper;
        this.access = access;
    }

    public List<Map<String, Object>> contexts()
    {
        Long userId = Group3Security.currentUserId();
        List<Map<String, Object>> result = new ArrayList<>();
        if (Group3Security.isTeacher())
        {
            for (EduClassRef cls : access.classesOfTeacher(userId))
            {
                if (!cls.open())
                {
                    continue;
                }
                EduClassMembership membership = access.membership(cls.classId(), userId);
                if (!membership.teacher() || !membership.canPublish())
                {
                    continue;
                }
                for (EduClassBindingRef binding : access.activeBindings(cls.classId()))
                {
                    EduCourseRef course = access.courseRef(binding.courseSpaceId());
                    result.add(bindingMap(binding, course, cls, membership.teacherRole(), true));
                }
            }
        }
        else if (Group3Security.isStudent())
        {
            for (EduClassRef cls : access.classesOfStudent(userId))
            {
                if (!cls.open())
                {
                    continue;
                }
                for (EduClassBindingRef binding : access.activeBindings(cls.classId()))
                {
                    EduCourseRef course = access.courseRef(binding.courseSpaceId());
                    result.add(bindingMap(binding, course, cls, null, false));
                }
            }
        }
        return result;
    }

    public Map<String, Object> requireTeacherBinding(Long bindingId)
    {
        Group3Security.requireTeacher();
        EduClassBindingRef binding = access.binding(bindingId);
        if (binding == null)
        {
            throw new ServiceException("无权访问该课程或教学班", HttpStatus.FORBIDDEN);
        }
        Long userId = Group3Security.currentUserId();
        EduClassMembership membership = access.membership(binding.classId(), userId);
        if (!membership.teacher() || !membership.canPublish())
        {
            throw new ServiceException("无权访问该课程或教学班", HttpStatus.FORBIDDEN);
        }
        EduCourseRef course = access.courseRef(binding.courseSpaceId());
        EduClassRef cls = access.requireClass(binding.classId());
        return bindingMap(binding, course, cls, membership.teacherRole(), true);
    }

    public Map<String, Object> requireStudentBinding(Long bindingId)
    {
        Group3Security.requireStudent();
        EduClassBindingRef binding = access.binding(bindingId);
        if (binding == null || !access.isActiveStudent(binding.classId(), Group3Security.currentUserId()))
        {
            throw new ServiceException("您不在该教学班，或教学班授权已失效", HttpStatus.FORBIDDEN);
        }
        EduCourseRef course = access.courseRef(binding.courseSpaceId());
        EduClassRef cls = access.requireClass(binding.classId());
        return bindingMap(binding, course, cls, null, false);
    }

    public Map<String, Object> requireAnyBinding(Long bindingId)
    {
        if (Group3Security.isTeacher())
        {
            return requireTeacherBinding(bindingId);
        }
        return requireStudentBinding(bindingId);
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

    private Map<String, Object> bindingMap(EduClassBindingRef binding, EduCourseRef course, EduClassRef cls,
            String teacherRole, boolean canPublish)
    {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("bindingId", binding.bindingId());
        map.put("courseSpaceId", binding.courseSpaceId());
        map.put("courseTitle", course != null ? course.title() : null);
        map.put("courseSummary", course != null ? course.summary() : null);
        map.put("courseStatus", course != null ? course.status() : null);
        map.put("teachingClassId", cls.classId());
        map.put("className", cls.name());
        map.put("classCode", cls.code());
        map.put("classKind", cls.classKind());
        map.put("classStatus", cls.status());
        map.put("termId", cls.termId());
        map.put("termName", cls.termName());
        map.put("termStartsOn", cls.termStartsOn());
        map.put("termEndsOn", cls.termEndsOn());
        map.put("teacherRole", teacherRole);
        map.put("canPublish", canPublish);
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
