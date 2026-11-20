package com.ruoyi.education.group3.service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import com.ruoyi.education.group3.mapper.Group3Mapper;

/**
 * 第三组资源保留策略。
 *
 * <p>每天凌晨 3 点清理超过保留期限仍处于 FAILED 的资源版本及其原始文件、预览文件。
 * 只清理失败版本，不触碰 READY / PENDING 版本，避免误删已发布资料。</p>
 *
 * <p>对象存储（S3/MinIO）与病毒扫描（如 ClamAV）属于基础设施能力，本服务不内置实现；
 * 接入时替换 {@link Group3FileStorageService#delete(String)} 的本地删除即可，保留策略调度逻辑不变。</p>
 */
@Service
public class Group3RetentionService
{
    private static final Logger log = LoggerFactory.getLogger(Group3RetentionService.class);

    private final Group3Mapper mapper;
    private final Group3FileStorageService storage;

    @Value("${group3.retention.enabled:true}")
    private boolean enabled;

    @Value("${group3.retention.failed-days:30}")
    private int failedDays;

    public Group3RetentionService(Group3Mapper mapper, Group3FileStorageService storage)
    {
        this.mapper = mapper;
        this.storage = storage;
    }

    @Scheduled(cron = "0 0 3 * * ?")
    public void purgeExpiredFailedVersions()
    {
        if (!enabled)
        {
            return;
        }
        int days = failedDays > 0 ? failedDays : 30;
        LocalDateTime before = LocalDateTime.now().minusDays(days);
        List<Map<String, Object>> expired = mapper.selectExpiredFailedVersions(before);
        if (expired.isEmpty())
        {
            return;
        }
        int purged = 0;
        for (Map<String, Object> row : expired)
        {
            Long versionId = Group3AccessService.asLong(row.get("id"));
            String storageKey = row.get("storageKey") == null ? null : row.get("storageKey").toString();
            try
            {
                for (String previewKey : mapper.selectResourcePageKeys(versionId))
                {
                    storage.delete(previewKey);
                }
                mapper.clearResourcePages(versionId);
                storage.delete(storageKey);
                mapper.deleteResourceVersion(versionId);
                purged++;
            }
            catch (RuntimeException error)
            {
                log.warn("清理过期失败版本时出错，versionId={}：{}", versionId, error.getMessage());
            }
        }
        if (purged > 0)
        {
            log.info("第三组保留策略已清理 {} 个过期失败资源版本（保留期限 {} 天）", purged, days);
        }
    }
}
