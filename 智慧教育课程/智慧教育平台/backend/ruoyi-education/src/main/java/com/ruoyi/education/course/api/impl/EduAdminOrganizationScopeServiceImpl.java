package com.ruoyi.education.course.api.impl;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import com.ruoyi.education.course.api.EduAdminOrganizationClassRange;
import com.ruoyi.education.course.api.EduAdminOrganizationScopeResult;
import com.ruoyi.education.course.api.EduAdminOrganizationScopeService;
import com.ruoyi.education.course.mapper.AdminOrganizationClassRangeRow;
import com.ruoyi.education.course.mapper.AdminOrganizationScopeMapper;

/**
 * 管理员组织范围课程只读契约的默认实现。
 *
 * <p>组织 ID 由第一组公共契约在请求线程捕获后传入；本实现不读组织表、不接收
 * 客户端身份参数，也不重做组织权限规则。一次批量 SQL 返回教学班、有效绑定、
 * 课程标题和当前有效学生数。</p>
 */
@Service
public class EduAdminOrganizationScopeServiceImpl implements EduAdminOrganizationScopeService
{
    private final AdminOrganizationScopeMapper mapper;

    private final Clock clock;

    /**
     * 生产构造：使用 UTC 系统时钟。
     *
     * @param mapper 课程范围查询
     */
    public EduAdminOrganizationScopeServiceImpl(AdminOrganizationScopeMapper mapper)
    {
        this(mapper, Clock.systemUTC());
    }

    /**
     * 测试构造：注入固定时钟。
     *
     * @param mapper 课程范围查询
     * @param clock  时钟，不能为空
     */
    EduAdminOrganizationScopeServiceImpl(AdminOrganizationScopeMapper mapper, Clock clock)
    {
        if (mapper == null)
        {
            throw new IllegalArgumentException("mapper 不能为空");
        }
        if (clock == null)
        {
            throw new IllegalArgumentException("clock 不能为空");
        }
        this.mapper = mapper;
        this.clock = clock;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public EduAdminOrganizationScopeResult listAuthorizedRanges(Collection<Long> organizationIds)
    {
        if (organizationIds == null)
        {
            throw new IllegalArgumentException("organizationIds 不能为空");
        }
        Set<Long> ids = new LinkedHashSet<>();
        for (Long organizationId : organizationIds)
        {
            if (organizationId == null || organizationId <= 0L)
            {
                throw new IllegalArgumentException("organizationIds 必须为正整数");
            }
            ids.add(organizationId);
        }
        Instant asOf = clock.instant();
        if (ids.isEmpty())
        {
            return EduAdminOrganizationScopeResult.noData(asOf);
        }

        List<AdminOrganizationClassRangeRow> rows;
        try
        {
            rows = mapper.selectAuthorizedClassRanges(ids,
                    LocalDateTime.ofInstant(asOf, ZoneOffset.UTC));
        }
        catch (RuntimeException exception)
        {
            return EduAdminOrganizationScopeResult.unavailable(asOf, "课程范围来源暂不可用");
        }
        if (rows == null)
        {
            return EduAdminOrganizationScopeResult.unavailable(asOf, "课程范围来源返回不完整");
        }

        try
        {
            List<EduAdminOrganizationClassRange> ranges = new ArrayList<>(rows.size());
            long studentCount = 0L;
            for (AdminOrganizationClassRangeRow row : rows)
            {
                if (row == null)
                {
                    return EduAdminOrganizationScopeResult.unavailable(asOf,
                            "课程范围来源包含空记录");
                }
                EduAdminOrganizationClassRange range = new EduAdminOrganizationClassRange(
                        row.getOrganizationId(), row.getTeachingClassId(), row.getClassName(),
                        row.getClassCode(), row.getClassStatus(), row.getBindingId(),
                        row.getCourseSpaceId(), row.getCourseName(), row.getStudentCount());
                ranges.add(range);
                studentCount += range.studentCount();
            }
            if (ranges.isEmpty())
            {
                return EduAdminOrganizationScopeResult.noData(asOf);
            }
            return EduAdminOrganizationScopeResult.ok(ranges, studentCount, asOf);
        }
        catch (RuntimeException exception)
        {
            return EduAdminOrganizationScopeResult.unavailable(asOf, "课程范围记录不完整");
        }
    }
}
