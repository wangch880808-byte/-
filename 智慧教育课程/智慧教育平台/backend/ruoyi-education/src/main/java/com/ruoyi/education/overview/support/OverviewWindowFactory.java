package com.ruoyi.education.overview.support;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeParseException;
import com.ruoyi.education.overview.dto.OverviewWindow;
import com.ruoyi.education.overview.exception.OverviewValidationException;

/**
 * 将请求参数转换为总览时间窗。
 *
 * <p>参数使用 RFC 3339 UTC 时间（例如 {@code 2026-10-30T00:00:00Z}）。
 * 两个参数都缺省时使用服务端当前 UTC 自然日；只传一个或格式错误返回 400。</p>
 *
 * <p>TODO：默认窗口当前按 UTC 自然日实现，待评审后改为当前学期/业务时区；
 * 解除条件为 6-01 合同中的默认窗口口径冻结。</p>
 */
public class OverviewWindowFactory
{
    private final Clock clock;

    /**
     * 创建时间窗工厂。
     *
     * @param clock 服务端时钟，不能为空
     */
    public OverviewWindowFactory(Clock clock)
    {
        if (clock == null)
        {
            throw new OverviewValidationException("clock 不能为空");
        }
        this.clock = clock;
    }

    /**
     * 解析请求时间窗。
     *
     * @param from 开始时间，可为空
     * @param to   结束时间，可为空
     * @return 左闭右开时间窗
     * @throws OverviewValidationException 只传一个参数、格式错误或时间窗非法
     */
    public OverviewWindow fromQuery(String from, String to)
    {
        boolean hasFrom = from != null && !from.isBlank();
        boolean hasTo = to != null && !to.isBlank();
        if (!hasFrom && !hasTo)
        {
            return defaultWindow();
        }
        if (hasFrom != hasTo)
        {
            throw new OverviewValidationException("from 和 to 必须同时提供");
        }
        return OverviewWindow.of(parse(from, "from"), parse(to, "to"));
    }

    /**
     * 返回默认 UTC 自然日窗口。
     *
     * @return 当前 UTC 日 00:00 到次日 00:00
     */
    public OverviewWindow defaultWindow()
    {
        Instant startsAt = LocalDate.now(clock).atStartOfDay(ZoneOffset.UTC).toInstant();
        return OverviewWindow.of(startsAt, startsAt.plusSeconds(24 * 60 * 60));
    }

    private Instant parse(String value, String field)
    {
        try
        {
            return Instant.parse(value);
        }
        catch (DateTimeParseException e)
        {
            throw new OverviewValidationException(field + " 必须是 RFC 3339 UTC 时间");
        }
    }
}
