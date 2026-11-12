package com.ruoyi.education.group4.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.group4.domain.activity.EduActivityResponse;

/**
 * 活动作答数据层。
 */
public interface EduActivityResponseMapper
{
    EduActivityResponse selectByTargetId(@Param("targetId") Long targetId);

    EduActivityResponse selectByActivityAndStudent(@Param("activityId") Long activityId,
            @Param("studentUserId") Long studentUserId);

    int insert(EduActivityResponse response);

    /**
     * 活动已提交人数。
     */
    int countByActivityId(@Param("activityId") Long activityId);

    /**
     * 活动答对人数（按当前有效版本判定）。
     */
    int countCorrectByActivityId(@Param("activityId") Long activityId);
}
