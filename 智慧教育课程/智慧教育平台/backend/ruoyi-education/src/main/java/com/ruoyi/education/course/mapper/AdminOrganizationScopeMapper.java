package com.ruoyi.education.course.mapper;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import org.apache.ibatis.annotations.Param;

/**
 * 管理员组织范围课程查询。
 *
 * <p>只读课程领域自己的表；组织授权由第一组公共契约完成，本 Mapper 不读取
 * {@code sys_dept} 或客户端传入的组织参数。</p>
 */
public interface AdminOrganizationScopeMapper
{
    /**
     * 批量查询授权组织内 ACTIVE 教学班，展开当前有效课程绑定，并统计当前有效学生数。
     *
     * @param organizationIds 第一组授权学校 ID，非空
     * @param at              统计时点，UTC
     * @return 每个绑定一行；未绑定的教学班保留一行空绑定
     */
    List<AdminOrganizationClassRangeRow> selectAuthorizedClassRanges(
            @Param("organizationIds") Collection<Long> organizationIds,
            @Param("at") LocalDateTime at);
}
