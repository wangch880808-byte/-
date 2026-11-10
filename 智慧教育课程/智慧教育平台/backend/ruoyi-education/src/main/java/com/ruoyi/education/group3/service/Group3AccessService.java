package com.ruoyi.education.group3.service;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Security;

@Service
public class Group3AccessService
{
    private final Group3Mapper mapper;

    public Group3AccessService(Group3Mapper mapper)
    {
        this.mapper = mapper;
    }

    public List<Map<String, Object>> contexts()
    {
        Long userId = Group3Security.currentUserId();
        if (Group3Security.isTeacher())
        {
            return mapper.selectTeacherContexts(userId);
        }
        if (Group3Security.isStudent())
        {
            return mapper.selectStudentContexts(userId);
        }
        return List.of();
    }

    public Map<String, Object> requireTeacherBinding(Long bindingId)
    {
        Group3Security.requireTeacher();
        Map<String, Object> binding = mapper.selectTeacherBinding(Group3Security.currentUserId(), bindingId);
        if (binding == null)
        {
            throw new ServiceException("无权访问该课程或教学班", HttpStatus.FORBIDDEN);
        }
        return binding;
    }

    public Map<String, Object> requireStudentBinding(Long bindingId)
    {
        Group3Security.requireStudent();
        Map<String, Object> binding = mapper.selectStudentBinding(Group3Security.currentUserId(), bindingId);
        if (binding == null)
        {
            throw new ServiceException("您不在该教学班，或教学班授权已失效", HttpStatus.FORBIDDEN);
        }
        return binding;
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
        Map<String, Object> version = mapper.selectTeacherVersion(
                bindingId, versionId, Group3Security.currentUserId());
        if (version == null)
        {
            throw new ServiceException("资源版本不存在或不属于当前课程", HttpStatus.NOT_FOUND);
        }
        return decorate(version, binding);
    }

    public Map<String, Object> studentVersion(Long bindingId, Long versionId)
    {
        Map<String, Object> binding = requireStudentBinding(bindingId);
        Map<String, Object> version = mapper.selectStudentVisibleVersion(
                bindingId, versionId, Group3Security.currentUserId());
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
