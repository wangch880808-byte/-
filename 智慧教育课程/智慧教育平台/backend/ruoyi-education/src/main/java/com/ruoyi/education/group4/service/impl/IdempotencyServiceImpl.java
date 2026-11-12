package com.ruoyi.education.group4.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Date;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group4.domain.idempotency.EduIdempotencyRecord;
import com.ruoyi.education.group4.mapper.EduIdempotencyMapper;
import com.ruoyi.education.group4.service.IIdempotencyService;

/**
 * 幂等记录服务实现。
 *
 * <p>只依赖 edu_idempotency_record 主键与唯一约束，不引入额外存储。
 * 记录体量小、按 expires_at 过期（24h），由调用方按需清理。</p>
 */
@Service
public class IdempotencyServiceImpl implements IIdempotencyService
{
    private static final String STATUS_PENDING = "PENDING";

    private static final String STATUS_SUCCEEDED = "SUCCEEDED";

    /** 幂等窗口：24 小时。 */
    private static final long WINDOW_MILLIS = 24L * 60L * 60L * 1000L;

    @Autowired
    private EduIdempotencyMapper idempotencyMapper;

    @Override
    public Long replayIfPresent(Long userId, String operationKind, Long scopeId,
            String idempotencyKey, String requestFingerprint)
    {
        if (isBlank(idempotencyKey))
        {
            return null;
        }
        byte[] keyHash = sha256(idempotencyKey.trim());
        EduIdempotencyRecord record = idempotencyMapper.selectByKey(userId, operationKind, scopeId, keyHash);
        if (record == null || isExpired(record))
        {
            return null;
        }
        byte[] requestHash = sha256(requestFingerprint);
        if (!MessageDigest.isEqual(record.getRequestHash(), requestHash))
        {
            throw new ServiceException("同一 Idempotency-Key 不能重复用于不同的请求内容", HttpStatus.CONFLICT);
        }
        if (STATUS_PENDING.equals(record.getStatus()))
        {
            throw new ServiceException("相同请求正在处理中，请稍后重试", HttpStatus.CONFLICT);
        }
        if (!STATUS_SUCCEEDED.equals(record.getStatus()))
        {
            return null;
        }
        return record.getResultId();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void remember(Long userId, String operationKind, Long scopeId,
            String idempotencyKey, String requestFingerprint, Long resultId)
    {
        if (isBlank(idempotencyKey) || resultId == null)
        {
            return;
        }
        EduIdempotencyRecord record = new EduIdempotencyRecord();
        record.setActorUserId(userId);
        record.setOperationKind(operationKind);
        record.setScopeId(scopeId);
        record.setKeyHash(sha256(idempotencyKey.trim()));
        record.setRequestHash(sha256(requestFingerprint));
        record.setResultId(resultId);
        record.setStatus(STATUS_PENDING);
        record.setExpiresAt(new Date(System.currentTimeMillis() + WINDOW_MILLIS));
        try
        {
            idempotencyMapper.insert(record);
        }
        catch (DuplicateKeyException ex)
        {
            // 并发下另一个请求先落库，保留对方的结果即可。
            return;
        }
        idempotencyMapper.markSucceeded(record.getId(), resultId);
    }

    private boolean isExpired(EduIdempotencyRecord record)
    {
        return record.getExpiresAt() != null && record.getExpiresAt().before(new Date());
    }

    private boolean isBlank(String value)
    {
        return value == null || value.trim().isEmpty();
    }

    private byte[] sha256(String value)
    {
        try
        {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return digest.digest(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (NoSuchAlgorithmException ex)
        {
            throw new IllegalStateException("当前 JVM 不支持 SHA-256", ex);
        }
    }
}
