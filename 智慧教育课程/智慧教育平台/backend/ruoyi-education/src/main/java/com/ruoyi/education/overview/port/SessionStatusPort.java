package com.ruoyi.education.overview.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.ruoyi.education.overview.domain.SessionStatus;
import com.ruoyi.education.overview.dto.OverviewWindow;

/**
 * 课堂会话聚合端口。
 *
 * <p>本阶段只定义契约，由第 4 组课堂服务提供生产适配器；6-01 不直读课堂表。
 * 查询必须按绑定 ID 批量执行，适配器一次查询完成。</p>
 *
 * <p>返回 Map 必须包含所有请求的绑定 ID；空 List 表示窗口内没有课堂；
 * 缺 key 视为契约违约，调用方映射为 UNAVAILABLE。</p>
 */
public interface SessionStatusPort
{
    /**
     * 批量查询时间窗内的课堂会话。
     *
     * @param bindingIds 课程空间与教学班绑定 ID 集合，不能为空
     * @param window     时间窗，不能为空
     * @return 以绑定 ID 为 key 的课堂列表；每个请求 key 必须有条目
     * @throws com.ruoyi.education.overview.exception.OverviewValidationException 参数为空
     */
    Map<Long, List<SessionStatus>> findSessions(Collection<Long> bindingIds,
            OverviewWindow window);
}
