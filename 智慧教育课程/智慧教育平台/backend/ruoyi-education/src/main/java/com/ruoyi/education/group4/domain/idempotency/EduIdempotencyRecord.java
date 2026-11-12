package com.ruoyi.education.group4.domain.idempotency;

import java.util.Date;

/**
 * 幂等记录（对应 edu_idempotency_record）。
 *
 * <p>同一 (actor_user_id, operation_kind, scope_id, key_hash) 只落一条，
 * 用 result_id 记住首次成功请求产生的业务实体，重复请求直接回放。</p>
 */
public class EduIdempotencyRecord
{
    private Long id;
    private Long actorUserId;
    private String operationKind;
    private Long scopeId;
    private byte[] keyHash;
    private byte[] requestHash;
    private Long resultId;
    private String status;
    private Date createdAt;
    private Date expiresAt;

    public Long getId()
    {
        return id;
    }

    public void setId(Long id)
    {
        this.id = id;
    }

    public Long getActorUserId()
    {
        return actorUserId;
    }

    public void setActorUserId(Long actorUserId)
    {
        this.actorUserId = actorUserId;
    }

    public String getOperationKind()
    {
        return operationKind;
    }

    public void setOperationKind(String operationKind)
    {
        this.operationKind = operationKind;
    }

    public Long getScopeId()
    {
        return scopeId;
    }

    public void setScopeId(Long scopeId)
    {
        this.scopeId = scopeId;
    }

    public byte[] getKeyHash()
    {
        return keyHash;
    }

    public void setKeyHash(byte[] keyHash)
    {
        this.keyHash = keyHash;
    }

    public byte[] getRequestHash()
    {
        return requestHash;
    }

    public void setRequestHash(byte[] requestHash)
    {
        this.requestHash = requestHash;
    }

    public Long getResultId()
    {
        return resultId;
    }

    public void setResultId(Long resultId)
    {
        this.resultId = resultId;
    }

    public String getStatus()
    {
        return status;
    }

    public void setStatus(String status)
    {
        this.status = status;
    }

    public Date getCreatedAt()
    {
        return createdAt;
    }

    public void setCreatedAt(Date createdAt)
    {
        this.createdAt = createdAt;
    }

    public Date getExpiresAt()
    {
        return expiresAt;
    }

    public void setExpiresAt(Date expiresAt)
    {
        this.expiresAt = expiresAt;
    }
}
