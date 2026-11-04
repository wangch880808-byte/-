package com.ruoyi.education.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.domain.classroom.EduSessionEvent;

/**
 * 课堂事件数据层。
 */
public interface EduSessionEventMapper
{
    List<EduSessionEvent> selectAfterSeq(@Param("sessionId") Long sessionId,
            @Param("afterSeq") Long afterSeq,
            @Param("publicOnly") boolean publicOnly);

    Long selectMaxSeqNo(@Param("sessionId") Long sessionId);

    int insert(EduSessionEvent event);
}
