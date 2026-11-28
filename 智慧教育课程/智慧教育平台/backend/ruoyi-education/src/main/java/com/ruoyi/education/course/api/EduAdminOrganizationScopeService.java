package com.ruoyi.education.course.api;

import java.util.Collection;

/**
 * 第二组提供的管理员组织范围课程只读契约。
 *
 * <p>输入 organizationIds 必须来自第一组 {@code AdminOrganizationScopeProvider}
 * 在请求线程捕获的快照；本接口不接收客户端传入的组织、用户或角色参数，也不
 * 自己重做组织权限判断。调用方不得把空集合当成“全部组织”。</p>
 *
 * <p>该接口只返回教学班、当前有效课程绑定和当前有效学生数，不返回学生名单。
 * 查询在单次批量调用中完成；结果口径与第六组现有范围聚合一致：一个当前有效
 * 绑定一条记录，未绑定的教学班保留一条空绑定记录，不按教学班去重。</p>
 *
 * <p>无权限、身份或组织范围问题由第一组契约处理；本接口只区分：
 * OK、NO_DATA、UNAVAILABLE。数据库或 SQL 查询失败返回 UNAVAILABLE，不能
 * 返回空列表冒充 NO_DATA。</p>
 */
public interface EduAdminOrganizationScopeService
{
    /**
     * 批量查询授权组织内的教学班范围。
     *
     * @param organizationIds 第一组已授权的学校组织 ID；不能为 null
     * @return 范围快照；organizationIds 为空时返回 NO_DATA
     */
    EduAdminOrganizationScopeResult listAuthorizedRanges(Collection<Long> organizationIds);
}
