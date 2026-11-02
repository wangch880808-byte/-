package com.ruoyi.education.course.service;

/**
 * 上游个人通知事件接收端口。
 *
 * <p>内部服务契约，不对外暴露学生数据；当前不提供 HTTP 接收端点。
 * 生产接入方式、鉴权和上游投递可靠性待与第 3、4、5 组联调确认。</p>
 *
 * <p>TODO 真实上游投递、鉴权、重试和对账尚未联调；解除条件：第 3、4、5 组确认
 * 事件字段、投递通道和鉴权方式，并完成失败隔离与重试验证。</p>
 */
public interface UpstreamNoticeEventPort
{
    /**
     * 接收一条上游事件并生成个人通知。
     *
     * <p>单事件写入在同一事务内完成；按 500 条分批；重复事件依赖唯一键幂等，
     * 不覆盖已有 readAt。按教学班投递时使用服务端事件发生时间过滤
     * {@code [joinedAt, leftAt)}；指定学生列表投递时不查询成员表。</p>
     *
     * @param event 上游事件，字段必须完整
     * @return 本次实际插入的通知行数；重复键不计入，无接收人时为 0
     * @throws com.ruoyi.education.course.exception.NoticeValidationException
     *         事件字段不完整或类型非法；映射为 400
     * @throws com.ruoyi.education.course.exception.NoticeAuthorizationUnavailableException
     *         按教学班投递时学生授权适配器缺失、异常、返回 null、跨绑定或字段不完整；
     *         映射为 503
     * @throws com.ruoyi.education.course.exception.NoticePersistenceException
     *         通知写入失败；映射为 500
     */
    int receive(UpstreamNoticeEvent event);
}
