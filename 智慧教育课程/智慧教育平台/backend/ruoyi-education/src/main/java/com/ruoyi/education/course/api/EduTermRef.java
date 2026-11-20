package com.ruoyi.education.course.api;

/**
 * 学期快照。课程上下文（当前学期）由本契约给出，页面不要再自己猜。
 *
 * @param termId   学期 ID
 * @param name     学期名称
 * @param code     学期编码
 * @param startsOn 开始日期 yyyy-MM-dd
 * @param endsOn   结束日期 yyyy-MM-dd
 * @param current  是否当前学期
 */
public record EduTermRef(
        Long termId,
        String name,
        String code,
        String startsOn,
        String endsOn,
        boolean current) {
}
