package com.ruoyi.education.service;

/**
 * 课堂写操作的幂等保护（契约 §1：Idempotency-Key，24h 内同键同载荷返回相同结果）。
 */
public interface IIdempotencyService
{
    /**
     * 若同一幂等键已经成功执行过，返回当时的 resultId。
     *
     * @param idempotencyKey     请求头 Idempotency-Key，为空表示不启用幂等
     * @param requestFingerprint 请求内容指纹，用于识别「同键不同载荷」
     * @return 已存在的 resultId；没有可回放记录时返回 null
     */
    Long replayIfPresent(Long userId, String operationKind, Long scopeId,
            String idempotencyKey, String requestFingerprint);

    /**
     * 记录一次成功执行的写操作，供后续同键请求回放。记录失败不影响业务结果。
     */
    void remember(Long userId, String operationKind, Long scopeId,
            String idempotencyKey, String requestFingerprint, Long resultId);
}
