package com.ruoyi.education.group3.support;

import java.util.Objects;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;

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
        return hasExactRole("teacher");
    }

    public static boolean isStudent()
    {
        return hasExactRole("student");
    }

    private static boolean hasExactRole(String roleKey)
    {
        return SecurityUtils.getLoginUser().getUser().getRoles().stream()
                .map(SysRole::getRoleKey)
                .filter(Objects::nonNull)
                .anyMatch(roleKey::equals);
    }

    private static void requireExactRole(String roleKey)
    {
        if (!hasExactRole(roleKey))
        {
            throw new ServiceException("当前账号无权访问该教学业务", HttpStatus.FORBIDDEN);
        }
    }
}
