package com.ruoyi.web.controller.monitor;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.Properties;
import java.util.concurrent.Callable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.ScheduleConstants;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.exception.ForbiddenException;
import com.ruoyi.education.security.EduActor;
import com.ruoyi.framework.web.domain.Server;
import com.ruoyi.framework.web.domain.server.SysFile;
import com.ruoyi.framework.web.service.PermissionService;
import com.ruoyi.quartz.domain.SysJob;
import com.ruoyi.quartz.service.ISysJobService;

@RestController
@RequestMapping("/monitor/operations")
public class OperationsController
{
    private static final Logger log = LoggerFactory.getLogger(OperationsController.class);

    private static final String SERVER_LIST = "monitor:server:list";
    private static final String CACHE_LIST = "monitor:cache:list";
    private static final String JOB_LIST = "monitor:job:list";

    private final PermissionService permissions;
    private final RedisTemplate<Object, Object> redis;
    private final ISysJobService jobs;

    public OperationsController(PermissionService permissions, RedisTemplate<Object, Object> redis, ISysJobService jobs)
    {
        this.permissions = permissions;
        this.redis = redis;
        this.jobs = jobs;
    }

    @PreAuthorize("@ss.hasAnyPermi('" + SERVER_LIST + "," + CACHE_LIST + "," + JOB_LIST + "')")
    @GetMapping
    public AjaxResult summary()
    {
        if (!EduActor.admin())
        {
            throw new ForbiddenException("只有管理员可以查看运行监控");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        collect(data, "server", SERVER_LIST, this::readServer);
        collect(data, "cache", CACHE_LIST, this::readCache);
        collect(data, "jobs", JOB_LIST, this::readJobs);
        return AjaxResult.success(data);
    }

    private void collect(Map<String, Object> data, String section, String permission, Callable<?> reader)
    {
        if (!permissions.hasPermi(permission))
        {
            return;
        }
        try
        {
            data.put(section, reader.call());
        }
        catch (Exception exception)
        {
            log.warn("Unable to read operations section {} ({})", section, exception.getClass().getSimpleName());
            data.put(section, null);
        }
    }

    private Map<String, Object> readServer() throws Exception
    {
        Server server = new Server();
        server.copyTo();
        OptionalDouble diskUsage = server.getSysFiles().stream().mapToDouble(SysFile::getUsage).max();

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("cpuUsage", 100 - server.getCpu().getFree());
        summary.put("coreCount", server.getCpu().getCpuNum());
        summary.put("memoryUsage", server.getMem().getUsage());
        summary.put("jvmUsage", server.getJvm().getUsage());
        summary.put("uptime", server.getJvm().getRunTime());
        summary.put("diskUsage", diskUsage.isPresent() ? diskUsage.getAsDouble() : null);
        return summary;
    }

    private Map<String, Object> readCache()
    {
        return redis.execute((RedisCallback<Map<String, Object>>) connection -> {
            Properties info = connection.serverCommands().info();
            if (info == null)
            {
                return null;
            }

            Map<String, Object> summary = new LinkedHashMap<>();
            summary.put("clients", numericProperty(info, "connected_clients"));
            summary.put("memory", info.getProperty("used_memory_human"));
            summary.put("keyCount", connection.serverCommands().dbSize());
            summary.put("uptimeDays", numericProperty(info, "uptime_in_days"));
            return summary;
        });
    }

    private static Long numericProperty(Properties info, String key)
    {
        String value = info.getProperty(key);
        return value == null ? null : Long.valueOf(value);
    }

    private Map<String, Object> readJobs()
    {
        List<SysJob> scheduled = jobs.selectJobList(new SysJob());
        long active = scheduled.stream()
                .filter(job -> ScheduleConstants.Status.NORMAL.getValue().equals(job.getStatus()))
                .count();
        return Map.of("total", scheduled.size(), "active", active);
    }
}
