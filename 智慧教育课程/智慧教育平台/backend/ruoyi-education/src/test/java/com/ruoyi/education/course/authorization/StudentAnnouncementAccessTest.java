package com.ruoyi.education.course.authorization;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import com.ruoyi.education.course.domain.StudentAnnouncementAuthority;

class StudentAnnouncementAccessTest
{
    private static final StudentAnnouncementAuthority ACTIVE =
            new StudentAnnouncementAuthority(true, true, true);

    @Test
    void threeArgumentConstructorLeavesMembershipWindowNull()
    {
        StudentAnnouncementAccess access = new StudentAnnouncementAccess(10001L, 20001L, ACTIVE);

        assertNull(access.joinedAt());
        assertNull(access.leftAt());
        assertEquals(10001L, access.bindingId());
        assertEquals(20001L, access.teachingClassId());
    }

    @Test
    void fiveArgumentConstructorKeepsMembershipWindow()
    {
        LocalDateTime joinedAt = LocalDateTime.of(2026, 10, 1, 0, 0);
        LocalDateTime leftAt = LocalDateTime.of(2026, 12, 1, 0, 0);

        StudentAnnouncementAccess access =
                new StudentAnnouncementAccess(10001L, 20001L, joinedAt, leftAt, ACTIVE);

        assertEquals(joinedAt, access.joinedAt());
        assertEquals(leftAt, access.leftAt());
    }
}
