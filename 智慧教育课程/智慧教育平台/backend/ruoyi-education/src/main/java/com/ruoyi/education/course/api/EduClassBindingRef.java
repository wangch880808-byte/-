package com.ruoyi.education.course.api;

/**
 * 教学班当前有效的课程空间绑定（published_at 已到、ended_at 为空或未到，半开区间 [published_at, ended_at)）。
 *
 * <p>资源发布、预习与课堂都必须同时校验「绑定未结束」，只有 classId 不足以证明课程可用。</p>
 *
 * @param bindingId    绑定记录 ID，解绑时使用
 * @param classId      教学班 ID
 * @param courseSpaceId 课程空间 ID
 * @param publishedAt  绑定/发布到该班的日期 yyyy-MM-dd（仅展示精度；毫秒级时点查询用 {@link EduMembershipTimelineService}）
 */
public record EduClassBindingRef(
        Long bindingId,
        Long classId,
        Long courseSpaceId,
        String publishedAt) {
}
