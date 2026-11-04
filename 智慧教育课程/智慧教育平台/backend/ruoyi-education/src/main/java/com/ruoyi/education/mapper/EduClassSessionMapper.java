package com.ruoyi.education.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.domain.classroom.EduClassSession;

/**
 * 课堂会话数据层。
 */
public interface EduClassSessionMapper
{
    EduClassSession selectById(@Param("sessionId") Long sessionId);

    EduClassSession selectActiveByBindingId(@Param("bindingId") Long bindingId);

    int insert(EduClassSession session);

    int updateStatus(@Param("sessionId") Long sessionId,
            @Param("status") String status,
            @Param("endedByUserId") Long endedByUserId);
}
