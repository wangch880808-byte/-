package com.ruoyi.education.group3.support;

import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.security.EduActor;

/**
 * Keeps role checks explicit for the group 3 teacher and student surfaces.
 */
public final class Group3Security
{
    private Group3Security()
    {
    }

    public static Long currentUserId()
    {
        return SecurityUtils.getUserId();
    }

    public static void requireTeacher()
    {
        requireExactRole("teacher");
    }

    public static void requireStudent()
    {
        requireExactRole("student");
    }

    public static boolean isTeacher()
    {
        return "teacher".equals(EduActor.role());
    }

    public static boolean isStudent()
    {
        return "student".equals(EduActor.role());
    }

    private static void requireExactRole(String roleKey)
    {
        if (!roleKey.equals(EduActor.role()))
        {
            throw new EduForbiddenException("当前账号无权访问该教学业务");
        }
    }
}
