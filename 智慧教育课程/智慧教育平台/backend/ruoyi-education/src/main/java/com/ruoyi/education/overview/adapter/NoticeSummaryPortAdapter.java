package com.ruoyi.education.overview.adapter;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collection;
import java.util.Map;
import java.util.Objects;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.course.service.StudentNoticeService;
import com.ruoyi.education.course.service.StudentNoticeUnreadCountResult;
import com.ruoyi.education.overview.domain.NoticeSummary;
import com.ruoyi.education.overview.exception.OverviewAccessDeniedException;
import com.ruoyi.education.overview.exception.OverviewValidationException;
import com.ruoyi.education.overview.port.NoticeSummaryPort;
import com.ruoyi.education.security.EduActor;

/**
 * 本人未读通知汇总的生产适配。
 *
 * <p>6-02 当前公开的通知服务是 {@link StudentNoticeService#unreadCount(Long)}：
 * 它从认证上下文取当前学生、重新解析学生有效绑定和通知可见性，并返回同一
 * 服务端时刻的未读数与截止时间。本适配器不查通知表，也不缓存未读数。</p>
 *
 * <p>身份：源线程的认证上下文由 {@code OverviewConfiguration} 使用
 * {@code DelegatingSecurityContextExecutor} 传播真实 SecurityContext；
 * 适配器先复核请求接收人就是当前登录人，再调用来源服务，不能借此读取他人未读数。
 * 当前 6-02 只提供学生未读汇总；教师通知来源未接通时抛出不可用异常，由聚合层
 * 映射为 UNAVAILABLE，不返回假零。</p>
 *
 * <p>批量限制：6-02 现有契约只支持“当前登录学生”一人，端口批量集合中只能包含
 * 当前用户；聚合层当前每次也只请求一个接收人。待 6-02 提供批量汇总契约后再扩展。</p>
 */
@Component
public class NoticeSummaryPortAdapter implements NoticeSummaryPort
{
    private final StudentNoticeService studentNoticeService;

    /**
     * 创建通知汇总适配器。
     *
     * @param studentNoticeService 6-02 学生通知服务
     */
    @Autowired
    public NoticeSummaryPortAdapter(StudentNoticeService studentNoticeService)
    {
        this.studentNoticeService = Objects.requireNonNull(studentNoticeService,
                "studentNoticeService");
    }

    /**
     * 查询当前登录学生的未读通知汇总。
     *
     * @param recipientUserIds 接收人 ID 集合；只能包含当前登录用户
     * @return 以接收人 ID 为 key 的未读汇总；0 未读是真实值
     */
    @Override
    public Map<Long, NoticeSummary> findUnreadSummaries(Collection<Long> recipientUserIds)
    {
        Long currentUserId = requireCurrentUser(recipientUserIds);
        if (!EduActor.assigned("student"))
        {
            // 6-02 只有学生通知汇总契约；教师未读数不能由学生绑定推导，更不能用 0 冒充。
            throw new UnsupportedOperationException("6-02 未提供教师通知未读汇总契约");
        }

        StudentNoticeUnreadCountResult result = studentNoticeService.unreadCount(null);
        if (result == null || result.asOf() == null)
        {
            throw new IllegalStateException("学生通知未读汇总返回不完整结果");
        }
        // 6-02 的 asOf 与数据库列均为 UTC；这里显式按 UTC 转换，避免依赖 JVM 默认时区。
        Instant asOf = result.asOf().toInstant(ZoneOffset.UTC);
        return Map.of(currentUserId,
                new NoticeSummary(currentUserId, result.unreadCount(), asOf));
    }

    /**
     * 校验请求接收人与当前认证身份一致。
     *
     * @param recipientUserIds 端口请求的接收人 ID 集合
     * @return 当前登录用户 ID
     */
    private Long requireCurrentUser(Collection<Long> recipientUserIds)
    {
        if (recipientUserIds == null || recipientUserIds.isEmpty())
        {
            throw new OverviewValidationException("接收人 ID 集合不能为空");
        }
        Long currentUserId = SecurityUtils.getUserId();
        if (currentUserId == null)
        {
            throw new OverviewAccessDeniedException("总览缺少可信登录身份");
        }
        for (Long recipientUserId : recipientUserIds)
        {
            if (recipientUserId == null || recipientUserId <= 0L)
            {
                throw new OverviewValidationException("接收人 ID 必须是正整数");
            }
            if (!currentUserId.equals(recipientUserId))
            {
                throw new OverviewAccessDeniedException("总览只能查询当前登录人的未读通知");
            }
        }
        return currentUserId;
    }
}
