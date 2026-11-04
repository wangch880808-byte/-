package com.ruoyi.education.overview.port;

import java.util.Collection;
import java.util.Map;
import com.ruoyi.education.overview.domain.GradeTodo;

/**
 * 待批/待发布成绩汇总端口。
 *
 * <p>本阶段只定义契约，由第 5 组统一评价服务提供生产适配器；
 * 6-01 不重算成绩、不直读成绩表。查询必须按绑定 ID 批量执行。</p>
 *
 * <p>返回 Map 必须包含所有请求的绑定 ID；缺 key 视为契约违约，
 * 调用方映射为 UNAVAILABLE。</p>
 */
public interface GradeTodoPort
{
    /**
     * 批量查询待批/待发布成绩汇总。
     *
     * @param bindingIds 绑定 ID 集合，不能为空
     * @return 以绑定 ID 为 key 的成绩待办；每个请求 key 必须有条目
     * @throws com.ruoyi.education.overview.exception.OverviewValidationException 参数为空
     */
    Map<Long, GradeTodo> findPendingGrades(Collection<Long> bindingIds);
}
