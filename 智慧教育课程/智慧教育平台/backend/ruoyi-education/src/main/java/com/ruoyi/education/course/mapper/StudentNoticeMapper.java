package com.ruoyi.education.course.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.course.domain.StudentNotice;
import com.ruoyi.education.course.service.StudentNoticeQuery;

/**
 * 学生通知查询与已读写入。授权和可见性由 Service 先解析，
 * 查询条件只使用传入的绑定范围。
 */
public interface StudentNoticeMapper
{
    long countVisible(@Param("q") StudentNoticeQuery query);

    List<StudentNotice> selectVisiblePage(@Param("q") StudentNoticeQuery query);

    StudentNotice selectVisibleById(@Param("id") Long id, @Param("q") StudentNoticeQuery query);

    StudentNotice selectVisibleByIdForUpdate(@Param("id") Long id, @Param("q") StudentNoticeQuery query);

    StudentNotice selectByIdAndRecipient(@Param("id") Long id, @Param("recipientUserId") Long recipientUserId);

    int markRead(@Param("id") Long id, @Param("readAt") LocalDateTime readAt,
            @Param("q") StudentNoticeQuery query);

    int markAllRead(@Param("operatedAt") LocalDateTime operatedAt,
            @Param("q") StudentNoticeQuery query);
}
