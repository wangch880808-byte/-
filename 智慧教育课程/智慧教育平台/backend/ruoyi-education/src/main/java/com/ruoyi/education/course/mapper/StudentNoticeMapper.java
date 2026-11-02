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

    /**
     * 以当前读返回仍可见未读的通知 ID。用于全部已读后的剩余未读统计，
     * 避免 MySQL REPEATABLE READ 下复用事务早期快照。
     */
    List<Long> selectVisibleUnreadIdsForUpdate(@Param("q") StudentNoticeQuery query);

    StudentNotice selectByIdAndRecipient(@Param("id") Long id, @Param("recipientUserId") Long recipientUserId);

    int markRead(@Param("id") Long id, @Param("readAt") LocalDateTime readAt,
            @Param("q") StudentNoticeQuery query);

    int markAllRead(@Param("operatedAt") LocalDateTime operatedAt,
            @Param("q") StudentNoticeQuery query);
}
