package com.ruoyi.education.course.mapper;

import java.time.LocalDateTime;
import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;

/**
 * 教学班公告查询。授权、绑定和成员关系由调用方在服务端校验。
 */
public interface CourseAnnouncementMapper
{
    /**
     * 按 ID 查询公告，教师视图使用，不限制状态。
     */
    CourseAnnouncement selectById(@Param("id") Long id);

    /**
     * 按 ID 当前读公告并加行锁，用于条件更新影响 0 行后的原因分类。
     * 避免 MySQL REPEATABLE READ 的快照读返回旧状态。
     */
    CourseAnnouncement selectByIdForUpdate(@Param("id") Long id);

    /**
     * 查询某个课程—教学班绑定下的公告，可按状态过滤。
     */
    List<CourseAnnouncement> selectByBindingId(@Param("bindingId") Long bindingId,
            @Param("status") AnnouncementStatus status);

    /**
     * 查询某个绑定下当前对学生可见的公告。
     *
     * <p>只处理公告状态和时间窗口；调用方仍需校验学生当前入班关系。</p>
     */
    List<CourseAnnouncement> selectVisibleByBindingId(@Param("bindingId") Long bindingId,
            @Param("now") LocalDateTime now);

    /**
     * 查询单条当前对学生可见的公告。
     */
    CourseAnnouncement selectVisibleById(@Param("id") Long id, @Param("now") LocalDateTime now);

    /**
     * 统计多个绑定下当前对学生可见的公告。conditions 与分页查询共用。
     */
    long countVisibleByBindingIds(@Param("bindingIds") List<Long> bindingIds,
            @Param("at") LocalDateTime at);

    /**
     * 分页查询多个绑定下当前对学生可见的公告，只返回摘要字段。
     */
    List<CourseAnnouncement> selectVisibleByBindingIdsPage(@Param("bindingIds") List<Long> bindingIds,
            @Param("at") LocalDateTime at, @Param("offset") long offset, @Param("limit") int limit);

    /**
     * 查询教师可见公告。无发布权限时只返回本人创建的草稿，
     * 已发布和已归档公告仍按当前任课关系返回。
     */
    List<CourseAnnouncement> selectTeacherVisibleByBindingId(@Param("bindingId") Long bindingId,
            @Param("status") AnnouncementStatus status, @Param("userId") Long userId,
            @Param("canReadAllDrafts") boolean canReadAllDrafts);

    /**
     * 统计教师可见公告总数，条件与列表查询一致。
     */
    long countTeacherVisibleByBindingId(@Param("bindingId") Long bindingId,
            @Param("status") AnnouncementStatus status, @Param("userId") Long userId,
            @Param("canReadAllDrafts") boolean canReadAllDrafts);

    /**
     * 分页查询教师可见公告，按创建时间和 ID 倒序固定排序。
     */
    List<CourseAnnouncement> selectTeacherVisibleByBindingIdPage(@Param("bindingId") Long bindingId,
            @Param("status") AnnouncementStatus status, @Param("userId") Long userId,
            @Param("canReadAllDrafts") boolean canReadAllDrafts,
            @Param("offset") long offset, @Param("limit") int limit);

    /**
     * 创建公告草稿，状态由 SQL 固定为 DRAFT。
     */
    int insertDraft(CourseAnnouncement announcement);

    /**
     * 更新草稿；仅匹配 DRAFT 状态和同一绑定。
     */
    int updateDraft(CourseAnnouncement announcement);

    /**
     * 将草稿发布；仅匹配 DRAFT 且尚未记录发布人。
     */
    int publishDraft(@Param("id") Long id, @Param("bindingId") Long bindingId,
            @Param("publishedByUserId") Long publishedByUserId, @Param("now") LocalDateTime now);

    /**
     * 归档已发布公告；仅匹配 PUBLISHED 状态和同一绑定。
     */
    int archivePublished(@Param("id") Long id, @Param("bindingId") Long bindingId);
}
