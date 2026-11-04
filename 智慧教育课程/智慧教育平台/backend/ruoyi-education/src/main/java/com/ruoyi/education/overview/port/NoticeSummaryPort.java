package com.ruoyi.education.overview.port;

import java.util.Collection;
import java.util.Map;
import com.ruoyi.education.overview.domain.NoticeSummary;

/**
 * 本人未读通知汇总端口。
 *
 * <p>6-02 已合并到 main，但 6-02 现有服务没有面向本端口的批量未读汇总契约：
 * 本接口仍只定义签名，不 import 6-02 通知类，也不直读通知表。</p>
 *
 * <p>TODO：待 6-02 提供批量用户未读汇总契约、明确调用角色/授权方式和
 * SecurityContext 依赖后新增生产适配器；解除条件为上述契约评审通过且
 * 适配器集成测试完成。在此之前，调用方必须把本端口缺失映射为 UNAVAILABLE，
 * 不能返回 0 未读。</p>
 *
 * <p>返回 Map 必须包含所有请求的接收人 ID；0 未读是真实值，
 * 调用方映射为 OK；缺 key 视为契约违约，映射为 UNAVAILABLE。</p>
 */
public interface NoticeSummaryPort
{
    /**
     * 批量查询接收人的未读通知汇总。
     *
     * @param recipientUserIds 接收人用户 ID 集合，不能为空
     * @return 以接收人 ID 为 key 的未读汇总；每个请求 key 必须有条目
     * @throws com.ruoyi.education.overview.exception.OverviewValidationException 参数为空
     */
    Map<Long, NoticeSummary> findUnreadSummaries(Collection<Long> recipientUserIds);
}
