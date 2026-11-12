package com.ruoyi.education.group4.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.group4.domain.idempotency.EduIdempotencyRecord;

/**
 * 幂等记录数据层。
 */
public interface EduIdempotencyMapper
{
    EduIdempotencyRecord selectByKey(@Param("actorUserId") Long actorUserId,
            @Param("operationKind") String operationKind,
            @Param("scopeId") Long scopeId,
            @Param("keyHash") byte[] keyHash);

    int insert(EduIdempotencyRecord record);

    int markSucceeded(@Param("id") Long id, @Param("resultId") Long resultId);
}
