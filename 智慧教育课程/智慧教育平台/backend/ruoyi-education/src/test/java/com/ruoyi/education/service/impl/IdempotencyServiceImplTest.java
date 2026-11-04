package com.ruoyi.education.service.impl;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Date;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.domain.idempotency.EduIdempotencyRecord;
import com.ruoyi.education.mapper.EduIdempotencyMapper;

/**
 * 契约 §1 的 Idempotency-Key 行为：同键同载荷回放、同键异载荷冲突。
 */
@ExtendWith(MockitoExtension.class)
class IdempotencyServiceImplTest
{
    private static final Long USER_ID = 9L;

    private static final Long SCOPE_ID = 6001L;

    private static final String OPERATION = "ACTIVITY.CHECKIN";

    private static final String FINGERPRINT = "checkin:6001";

    @Mock
    private EduIdempotencyMapper idempotencyMapper;

    @InjectMocks
    private IdempotencyServiceImpl service;

    @Test
    void noHeaderMeansNoIdempotency()
    {
        assertNull(service.replayIfPresent(USER_ID, OPERATION, SCOPE_ID, null, FINGERPRINT));
        verify(idempotencyMapper, never()).selectByKey(any(), any(), any(), any());
    }

    @Test
    void sameKeySamePayloadReplaysStoredResult()
    {
        EduIdempotencyRecord record = record(sha256(FINGERPRINT), "SUCCEEDED", 7001L);
        when(idempotencyMapper.selectByKey(any(), any(), any(), any())).thenReturn(record);

        Long result = service.replayIfPresent(USER_ID, OPERATION, SCOPE_ID, "key-1", FINGERPRINT);

        assertEquals(7001L, result.longValue());
    }

    @Test
    void sameKeyDifferentPayloadIsRejected()
    {
        EduIdempotencyRecord record = record(sha256("checkin:9999"), "SUCCEEDED", 7001L);
        when(idempotencyMapper.selectByKey(any(), any(), any(), any())).thenReturn(record);

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.replayIfPresent(USER_ID, OPERATION, SCOPE_ID, "key-1", FINGERPRINT));

        assertEquals(409, error.getCode().intValue());
    }

    @Test
    void expiredRecordIsIgnored()
    {
        EduIdempotencyRecord record = record(sha256(FINGERPRINT), "SUCCEEDED", 7001L);
        record.setExpiresAt(new Date(System.currentTimeMillis() - 1_000L));
        when(idempotencyMapper.selectByKey(any(), any(), any(), any())).thenReturn(record);

        assertNull(service.replayIfPresent(USER_ID, OPERATION, SCOPE_ID, "key-1", FINGERPRINT));
    }

    private EduIdempotencyRecord record(byte[] requestHash, String status, Long resultId)
    {
        EduIdempotencyRecord record = new EduIdempotencyRecord();
        record.setId(1L);
        record.setActorUserId(USER_ID);
        record.setOperationKind(OPERATION);
        record.setScopeId(SCOPE_ID);
        record.setKeyHash(sha256("key-1"));
        record.setRequestHash(requestHash);
        record.setStatus(status);
        record.setResultId(resultId);
        record.setExpiresAt(new Date(System.currentTimeMillis() + 3_600_000L));
        return record;
    }

    private byte[] sha256(String value)
    {
        try
        {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        }
        catch (Exception ex)
        {
            throw new IllegalStateException(ex);
        }
    }
}
