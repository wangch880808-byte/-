package com.ruoyi.education.course.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.course.domain.NoticeKind;
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

    /**
     * 公告发布 fan-out 批量插入 ANNOUNCEMENT 通知。
     *
     * <p>依赖唯一键 {@code (recipient_user_id, notice_kind, source_id)} 去重；
     * 重复公告/重复 fan-out 通过 {@code ON DUPLICATE KEY UPDATE id = id} 保持幂等，
     * 不覆盖已有通知的 {@code read_at}、标题或可见时间。</p>
     *
     * @param announcementId 公告 ID，作为通知 source_id
     * @param bindingId 公告所属课程—教学班绑定 ID
     * @param title 公告标题快照
     * @param visibleAt 通知对学生可见时间，取公告 opensAt
     * @param createdAt fan-out 写入时间
     * @param recipientUserIds 本批接收人，调用方须保证单批不超过 500 条
     * @return 数据库影响行数；MySQL 默认驱动可能把重复键计为匹配行
     * @throws org.springframework.dao.DataAccessException 数据库写入失败时由调用方转换
     */
    int insertAnnouncementNotices(@Param("announcementId") Long announcementId,
            @Param("bindingId") Long bindingId,
            @Param("title") String title,
            @Param("visibleAt") LocalDateTime visibleAt,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("recipientUserIds") List<Long> recipientUserIds);

    /**
     * 上游个人通知事件批量写入。
     *
     * <p>通知类型由事件给出；唯一键
     * {@code (recipient_user_id, notice_kind, source_id)} 负责去重。
     * {@code ON DUPLICATE KEY UPDATE id = id} 不覆盖已有 readAt、标题和可见时间。</p>
     *
     * @param noticeKind 通知类型，只能是 PREVIEW/ACTIVITY/ASSIGNMENT/GRADE
     * @param bindingId 课程—教学班绑定 ID
     * @param sourceId 来源业务对象 ID，按事件契约映射
     * @param title 接收时标题快照
     * @param visibleAt 学生可见时间
     * @param createdAt 事件发生时间
     * @param recipientUserIds 本批接收人，单批不超过 500
     * @return 数据库影响行数；MySQL 默认驱动可能把重复键计为匹配行
     * @throws org.springframework.dao.DataAccessException 数据库写入失败时由调用方转换
     */
    int insertUpstreamNotices(@Param("noticeKind") NoticeKind noticeKind,
            @Param("bindingId") Long bindingId,
            @Param("sourceId") Long sourceId,
            @Param("title") String title,
            @Param("visibleAt") LocalDateTime visibleAt,
            @Param("createdAt") LocalDateTime createdAt,
            @Param("recipientUserIds") List<Long> recipientUserIds);
}
