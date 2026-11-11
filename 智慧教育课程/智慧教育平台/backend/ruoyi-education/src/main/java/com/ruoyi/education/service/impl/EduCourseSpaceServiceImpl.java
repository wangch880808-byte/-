package com.ruoyi.education.service.impl;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.domain.EduCourseClassBinding;
import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.mapper.EduCourseClassBindingMapper;
import com.ruoyi.education.mapper.EduCourseSpaceMapper;
import com.ruoyi.education.service.IEduCourseSpaceService;
import com.ruoyi.education.service.IEduPortalService;
import org.springframework.stereotype.Service;

/** 旧 Java 调用兼容层；身份、归属与撤权规则统一交给当前业务服务。 */
@Deprecated
@Service
public class EduCourseSpaceServiceImpl implements IEduCourseSpaceService {
    private final IEduPortalService portal;
    private final EduCourseSpaceMapper spaces;
    private final EduCourseClassBindingMapper bindings;
    public EduCourseSpaceServiceImpl(IEduPortalService portal,EduCourseSpaceMapper spaces,EduCourseClassBindingMapper bindings){this.portal=portal;this.spaces=spaces;this.bindings=bindings;}
    public List<EduCourseSpace> list(EduCourseSpace query){return portal.courseList(query==null?null:query.getTitle()).stream().map(row->spaces.selectById(((Number)row.get("id")).longValue())).toList();}
    public EduCourseSpace getById(Long id){portal.courseDetail(id);return spaces.selectById(id);}
    private Map<String,Object> body(EduCourseSpace space){Map<String,Object> body=new LinkedHashMap<>();body.put("name",space.getTitle());body.put("summary",space.getSummary());body.put("learningObjectives",space.getLearningObjectives());body.put("officialCourseId",space.getOfficialCourseId());return body;}
    public Long create(EduCourseSpace space){return portal.createCourse(body(space));}
    public void update(EduCourseSpace space){portal.updateCourse(space.getId(),body(space));}
    public List<EduCourseClassBinding> listBindings(Long courseId){portal.courseDetail(courseId);return bindings.selectByCourseId(courseId);}
    public void bindClass(Long courseId,Long classId){portal.bindClass(courseId,classId,null);}
    public void unbindClass(Long bindingId){var binding=bindings.selectById(bindingId);if(binding==null)throw new ServiceException("绑定不存在");portal.unbindClass(binding.getCourseSpaceId(),bindingId);}
}
