package com.ruoyi.education.overview.port;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import com.ruoyi.education.overview.domain.AssignmentTodo;
import com.ruoyi.education.overview.dto.OverviewWindow;

/**
 * 预习/作业待办聚合端口。
 *
 * <p>本阶段只定义契约，由第 5 组统一任务/提交服务提供生产适配器；
 * 第 3 组预习发布数据经第 5 组契约进入本端口，6-01 不直读任务表。</p>
 *
 * <p>所有方法必须按用户或绑定批量查询；返回 Map 必须包含所有请求 key；
 * 空 List 表示没有待办；缺 key 视为契约违约，调用方映射为 UNAVAILABLE。</p>
 */
public interface AssignmentTodoPort
{
    /**
     * 批量查询学生待办。
     *
     * @param studentUserIds 学生用户 ID 集合，不能为空
     * @param window         时间窗，不能为空
     * @return 以学生用户 ID 为 key 的待办列表；每个请求 key 必须有条目
     * @throws com.ruoyi.education.overview.exception.OverviewValidationException 参数为空
     */
    Map<Long, List<AssignmentTodo>> findStudentTodos(Collection<Long> studentUserIds,
            OverviewWindow window);

    /**
     * 批量查询教师班级待办汇总。
     *
     * @param teacherUserIds 教师用户 ID 集合，不能为空
     * @param window         时间窗，不能为空
     * @return 以教师用户 ID 为 key 的待办列表；每个请求 key 必须有条目
     * @throws com.ruoyi.education.overview.exception.OverviewValidationException 参数为空
     */
    Map<Long, List<AssignmentTodo>> findTeacherTodos(Collection<Long> teacherUserIds,
            OverviewWindow window);

    /**
     * 按绑定批量查询班级待办汇总。
     *
     * @param bindingIds 绑定 ID 集合，不能为空
     * @param window     时间窗，不能为空
     * @return 以绑定 ID 为 key 的待办列表；每个请求 key 必须有条目
     * @throws com.ruoyi.education.overview.exception.OverviewValidationException 参数为空
     */
    Map<Long, List<AssignmentTodo>> findBindingTodos(Collection<Long> bindingIds,
            OverviewWindow window);
}
