package com.ruoyi.education.mapper;

import com.ruoyi.education.domain.EduCourseSpace;
import org.apache.ibatis.annotations.Param;
import java.util.List;

public interface EduCourseSpaceMapper {
    // 老师查自己创建的课程
    List<EduCourseSpace> selectByOwnerUserId(@Param("userId") Long userId);

    // 管理员查所有课程
    List<EduCourseSpace> selectAll(EduCourseSpace query);

    EduCourseSpace selectById(@Param("id") Long id);

    int insert(EduCourseSpace courseSpace);

    int update(EduCourseSpace courseSpace);
}
