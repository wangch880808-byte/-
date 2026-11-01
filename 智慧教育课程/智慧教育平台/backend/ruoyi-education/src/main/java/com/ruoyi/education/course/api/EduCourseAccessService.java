package com.ruoyi.education.course.api;

import java.util.List;

/**
 * 第 2 组（course 领域）对外唯一只读契约。
 *
 * <h2>为什么要有这个接口</h2>
 * 资源、备课、课堂、活动、作业、学情、总览这些领域都需要回答同一个问题：
 * 「这个账号现在能不能对这个教学班 / 这门课做这件事」。如果各自去 join
 * {@code edu_teaching_class}、{@code edu_class_student}、{@code edu_class_teacher}，
 * 就会出现七套撤权规则、七个越权口子。因此：
 *
 * <ul>
 *   <li>跨领域一律调用本接口，<b>不直接读 course 的表</b>；</li>
 *   <li>本接口每次调用都查当前库，不做缓存，撤权/退班后下一次请求即失效；</li>
 *   <li>返回的是快照记录（record），调用方可以落库保存，但落库只用于追溯，
 *       权限判定必须重新调用。</li>
 * </ul>
 *
 * <h2>异常语义</h2>
 * <ul>
 *   <li>对象不存在 → {@link com.ruoyi.common.exception.ServiceException}，最终 HTTP 200 + code=500；</li>
 *   <li>对象存在但当前账号无权 → {@link com.ruoyi.education.exception.EduForbiddenException}，
 *       由 {@code EduApiExceptionAdvice} 转成 code=403；</li>
 *   <li>调用方不要 catch 后自己造 403/500，否则错误口径会分叉。</li>
 * </ul>
 *
 * <h2>并发与状态</h2>
 * 状态判断使用「当前库状态」，不做乐观锁。需要把成员名单、班级状态固定到某个时点的
 * （例如发布作业），由调用方在自己的事务里读取本接口结果并写入快照表。
 *
 * @see EduClassRef
 * @see EduMemberRef
 * @see EduClassMembership
 */
public interface EduCourseAccessService {

    /**
     * 取教学班快照，不校验状态。用于展示历史班级、归档班级详情。
     *
     * @throws com.ruoyi.common.exception.ServiceException 教学班不存在
     */
    EduClassRef requireClass(Long classId);

    /**
     * 取教学班快照，并要求 {@code status = ACTIVE}。
     * 课堂开始、资源发布、作业布置等「对外产生教学内容」的动作应使用本方法。
     *
     * @throws com.ruoyi.common.exception.ServiceException 教学班不存在或未开放
     */
    EduClassRef requireActiveClass(Long classId);

    /** 是否为该班有效任课教师（valid_to IS NULL）。 */
    boolean isActiveTeacher(Long classId, Long userId);

    /** 是否为该班有效在班学生（left_at IS NULL）。 */
    boolean isActiveStudent(Long classId, Long userId);

    /**
     * 取当前账号在该班的身份与权限位。
     * 既不是教师也不是学生时抛 {@link com.ruoyi.education.exception.EduForbiddenException}。
     * 课堂加入校验、学生跟课、教师控制台入口都应先调用本方法。
     */
    EduClassMembership membership(Long classId, Long userId);

    /**
     * 该班当前有效成员名单，按入班时间排序。
     * 只返回有效记录；历史退班记录用于审计，不在本契约内。
     * 调用方需自行确认调用者是否有权查看名单（管理员或本班教师）。
     */
    List<EduMemberRef> activeMembers(Long classId);

    /** 某教师当前被授权的全部班级（valid_to IS NULL）。 */
    List<EduClassRef> classesOfTeacher(Long userId);

    /** 某学生当前在班的全部班级（left_at IS NULL）。 */
    List<EduClassRef> classesOfStudent(Long userId);

    /**
     * 该班当前有效的课程空间绑定；没有绑定时返回 {@code null}。
     * 一个教学班同一时刻最多一条有效绑定（库内唯一索引保证）。
     */
    EduClassBindingRef activeBinding(Long classId);

    /**
     * 校验「当前账号能否读这门课」，返回课程快照。
     * 允许：管理员、课程负责人、课程 ACTIVE 且本人在该课任一绑定班级中有效在班。
     *
     * @throws com.ruoyi.education.exception.EduForbiddenException 无权限
     */
    EduCourseRef requireCourseReadable(Long courseSpaceId, Long userId);

    /**
     * 校验「当前账号能否改这门课」，返回课程快照。
     * 允许：管理员、课程负责人。
     *
     * @throws com.ruoyi.education.exception.EduForbiddenException 无权限
     */
    EduCourseRef requireCourseOwned(Long courseSpaceId, Long userId);

    /**
     * 当前学期；没有设置时返回 {@code null}。
     * 教师端、学生端、第 6 组总览的默认课程上下文都取这里，避免各页各猜一个学期。
     */
    EduTermRef currentTerm();
}
