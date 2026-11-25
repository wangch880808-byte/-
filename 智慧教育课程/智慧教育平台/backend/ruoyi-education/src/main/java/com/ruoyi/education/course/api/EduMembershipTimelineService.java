package com.ruoyi.education.course.api;

import java.time.Instant;
import java.util.List;

/**
 * 按任意历史时点 at 查询成员段的新增只读契约（course 领域对外提供，供第 6 组通知授权适配器等消费方使用）。
 *
 * <p>背景：通知投递经常晚于业务事件（例如事件 9:00 发生、学生 10:00 退班、通知 11:00 才投递），
 * 接收人必须按事件时点的成员名单确定，而不是按投递时点。本契约把「成员段 + 绑定有效性」做成
 * 显式带 {@code at} 参数的查询；学生现在能否看到通知，仍由消费方按读取时点（at=服务端 now）
 * 另行判断，两件事分开处理。</p>
 *
 * <h2>统一时间口径</h2>
 * <ul>
 *   <li>所有时间列为 {@code DATETIME(3)}，毫秒精度；连接串 {@code serverTimezone=UTC}，
 *       全库按 UTC 写入与比较。本契约的 {@link Instant} 均为 UTC、毫秒。</li>
 *   <li>区间一律半开 {@code [start, end)}：
 *       学生成员段 = {@code joined_at <= at AND (left_at IS NULL OR left_at > at)}；
 *       教师授权段 = {@code valid_from <= at AND (valid_to IS NULL OR valid_to > at)}；
 *       课程绑定 = {@code published_at <= at AND (ended_at IS NULL OR ended_at > at)}。</li>
 *   <li>教学班 status、课程空间 status 没有生效/失效时间列（只有 created_at/updated_at），
 *       是「当前值」，不能按历史时点回溯；本契约只返回其当前库值（classOpen），
 *       消费方自行决定如何参与可见性判定。</li>
 *   <li>生成列 {@code active_user_id}/{@code active_space_id} 只表达「left_at/valid_to/ended_at 为 NULL」，
 *       不检查入班/生效时间是否已到，也不考虑未来退班/结束时间，仅作唯一索引辅助，
 *       <b>不能当作权限判定依据</b>。权限判定一律使用上面的半开区间条件。</li>
 * </ul>
 *
 * <h2>使用约定</h2>
 * <ul>
 *   <li>{@code at} 只接受服务端当前时间或上游事件的 {@code occurredAt}，不接受客户端时间；
 *       投递场景 at=occurredAt，读取/已读授权场景 at=服务端 now。</li>
 *   <li>本契约是纯只读查询，不校验调用者身份，只按传入参数返回数据；
 *       调用方必须传入服务端可信身份（{@code SecurityUtils.getUserId()}）并自行执行角色/对象权限判定。</li>
 *   <li>每次调用现查库、不缓存；调用方在自身事务内调用可把结果固定到同一时点。</li>
 *   <li>多次入退班时返回覆盖 at 的那一段；正常至多一段。
 *       同一学生在同一教学班有多条覆盖 at 的入班记录属于跨行重叠脏数据，
 *       三个查询一律抛 {@link com.ruoyi.common.exception.ServiceException} 失败关闭，
 *       不猜测取其中一条作为正常授权。</li>
 * </ul>
 *
 * <h2>异常语义</h2>
 * <ul>
 *   <li>绑定不存在 → {@link com.ruoyi.common.exception.ServiceException}（沿用现有契约语义）；</li>
 *   <li>绑定存在但不满足条件、无成员段 → 不抛异常：
 *       {@link #studentInBinding} 返回 active=false 快照，其余方法返回空列表。</li>
 *   <li>跨行重叠成员段（同一业务主键在多行覆盖 at）→ ServiceException，消费方按授权不可用处理；</li>
 *   <li>时点列返回无法识别的非空值（区别于真正的数据库 NULL）→ ServiceException，消费方按授权不可用处理；
 *       NULL 仍按「无上界」处理。</li>
 * </ul>
 *
 * <p>本端口为追加式增补，{@link EduCourseAccessService} 的现有方法与语义不变。</p>
 */
public interface EduMembershipTimelineService {

    /** (a) 学生在指定绑定下、时点 at 的成员段与可见性。绑定不存在抛异常；无成员段返回 active=false 快照。 */
    EduStudentBindingAccess studentInBinding(Long bindingId, Long userId, Instant at);

    /** (b) 学生在时点 at 的全部有效绑定（每个绑定返回该生命中的成员段）。空列表正常。 */
    List<EduStudentBindingAccess> studentBindings(Long userId, Instant at);

    /** (c) 绑定下在时点 at 在班的学生名单（含每人的成员段 joinedAt/leftAt）。空列表正常。 */
    List<EduStudentMemberSegment> studentsInBinding(Long bindingId, Instant at);
}
