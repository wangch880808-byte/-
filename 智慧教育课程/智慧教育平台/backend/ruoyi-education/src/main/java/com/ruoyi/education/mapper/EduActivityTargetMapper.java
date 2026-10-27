package com.ruoyi.education.mapper;

import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.domain.activity.EduActivityTarget;

/**
 * 活动目标学生数据层。
 */
public interface EduActivityTargetMapper
{
    EduActivityTarget selectByActivityAndStudent(@Param("activityId") Long activityId,
            @Param("studentUserId") Long studentUserId);

    int insert(EduActivityTarget target);
}
