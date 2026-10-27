package com.ruoyi.education.mapper;

import java.util.List;
import org.apache.ibatis.annotations.Param;
import com.ruoyi.education.domain.activity.EduActivityInstance;

/**
 * 活动实例数据层。
 */
public interface EduActivityInstanceMapper
{
    EduActivityInstance selectById(@Param("activityId") Long activityId);

    List<EduActivityInstance> selectOpenByBindingId(@Param("bindingId") Long bindingId);

    List<EduActivityInstance> selectOpenBySessionId(@Param("sessionId") Long sessionId);

    int insert(EduActivityInstance instance);

    int updateStatus(@Param("activityId") Long activityId, @Param("status") String status);

    int updateAnswerOpensAt(@Param("activityId") Long activityId);
}
