package com.ruoyi.education.group4.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.group4.domain.activity.EduActivityTarget;

/**
 * 活动目标学生数据层。
 */
public interface EduActivityTargetMapper
{
    EduActivityTarget selectByActivityAndStudent(@Param("activityId") Long activityId,
            @Param("studentUserId") Long studentUserId);

    int insert(EduActivityTarget target);

    /**
     * 活动应参与人数（目标学生快照数量）。
     */
    int countByActivityId(@Param("activityId") Long activityId);
}
