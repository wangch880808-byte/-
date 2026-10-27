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

    int updatePage(@Param("sessionId") Long sessionId,
            @Param("stateSeq") Long stateSeq,
            @Param("pageNo") Integer pageNo,
            @Param("studentCanBrowse") Boolean studentCanBrowse);
}
