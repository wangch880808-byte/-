package com.ruoyi.education.course.api;

/**
 * 管理员组织范围内课程范围查询的状态。
 *
 * <p>OK 表示有明确范围；NO_DATA 表示授权组织内确实没有符合条件的教学班；
 * UNAVAILABLE 表示数据库或来源查询失败。NO_DATA 不能写成 0，UNAVAILABLE
 * 也不能当成空范围继续聚合。</p>
 */
public enum EduAdminOrganizationScopeStatus
{
    OK,
    NO_DATA,
    UNAVAILABLE
}
