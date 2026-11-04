package com.ruoyi.education.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.domain.classroom.EduSessionProjection;

/**
 * 课堂投影数据层。
 */
public interface EduSessionProjectionMapper
{
    EduSessionProjection selectBySessionId(@Param("sessionId") Long sessionId);

    int insert(EduSessionProjection projection);

    /**
     * 原子自增 state_seq（不改页码），用于暂停/恢复这类只改状态的操作。
     */
    int incrementStateSeq(@Param("sessionId") Long sessionId);

    /**
     * 原子自增 state_seq 并写入新页码，避免并发翻页读到同一个旧序号。
     */
    int updatePageAtomic(@Param("sessionId") Long sessionId,
            @Param("pageNo") Integer pageNo,
            @Param("studentCanBrowse") Boolean studentCanBrowse);
}
