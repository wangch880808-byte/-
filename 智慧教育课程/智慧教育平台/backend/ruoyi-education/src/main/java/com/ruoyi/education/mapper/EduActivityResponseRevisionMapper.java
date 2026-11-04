package com.ruoyi.education.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.domain.activity.EduActivityResponseRevision;

/**
 * 活动作答版本数据层。
 */
public interface EduActivityResponseRevisionMapper
{
    EduActivityResponseRevision selectLatestByResponseId(@Param("responseId") Long responseId);

    int insert(EduActivityResponseRevision revision);
}
