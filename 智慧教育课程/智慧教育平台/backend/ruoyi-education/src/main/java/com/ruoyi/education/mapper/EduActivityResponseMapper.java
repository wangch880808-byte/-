package com.ruoyi.education.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.domain.activity.EduActivityResponse;

/**
 * 活动作答数据层。
 */
public interface EduActivityResponseMapper
{
    EduActivityResponse selectByTargetId(@Param("targetId") Long targetId);

    EduActivityResponse selectByActivityAndStudent(@Param("activityId") Long activityId,
            @Param("studentUserId") Long studentUserId);

    int insert(EduActivityResponse response);
}
