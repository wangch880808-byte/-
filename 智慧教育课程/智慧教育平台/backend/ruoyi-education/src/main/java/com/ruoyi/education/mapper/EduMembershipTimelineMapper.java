package com.ruoyi.education.mapper;

import java.util.Date;
import java.util.List;
import java.util.Map;
import org.apache.ibatis.annotations.Param;

/** {@link com.ruoyi.education.course.api.EduMembershipTimelineService} 的只读查询。 */
public interface EduMembershipTimelineMapper {

    /** 绑定与教学班当前状态；绑定不存在返回 null。 */
    Map<String, Object> selectBindingWithClass(@Param("bindingId") Long bindingId);

    /** 绑定下某学生在时点 at 覆盖的全部成员段；正常 0 或 1 条，重叠脏数据会返回多条，由服务层判定。 */
    List<Map<String, Object>> selectStudentSegmentsInBinding(@Param("bindingId") Long bindingId,
                                                             @Param("userId") Long userId,
                                                             @Param("at") Date at);

    /** 绑定下在时点 at 在班的学生名单；绑定在 at 未生效或已结束返回空列表。 */
    List<Map<String, Object>> selectStudentsInBinding(@Param("bindingId") Long bindingId, @Param("at") Date at);

    /** 学生在时点 at 的全部有效绑定（成员段与绑定都覆盖 at）。 */
    List<Map<String, Object>> selectStudentBindings(@Param("userId") Long userId, @Param("at") Date at);
}
