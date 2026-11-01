package com.ruoyi.education.service.impl;

import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.domain.EduTeachingClass;
import com.ruoyi.education.domain.EduTerm;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.mapper.EduClassStudentMapper;
import com.ruoyi.education.mapper.EduClassTeacherMapper;
import com.ruoyi.education.mapper.EduCourseClassBindingMapper;
import com.ruoyi.education.mapper.EduCourseSpaceMapper;
import com.ruoyi.education.mapper.EduPortalMapper;
import com.ruoyi.education.mapper.EduTeachingClassMapper;
import com.ruoyi.education.mapper.EduTermMapper;
import com.ruoyi.education.domain.EduCourseClassBinding;
import com.ruoyi.education.service.IEduPortalService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class EduPortalServiceImpl implements IEduPortalService {

    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_TEACHER = "teacher";
    private static final String ROLE_STUDENT = "student";

    @Autowired
    private EduPortalMapper portalMapper;
    @Autowired
    private EduTermMapper termMapper;
    @Autowired
    private EduTeachingClassMapper teachingClassMapper;
    @Autowired
    private EduCourseSpaceMapper courseSpaceMapper;
    @Autowired
    private EduCourseClassBindingMapper bindingMapper;
    @Autowired
    private EduClassStudentMapper classStudentMapper;
    @Autowired
    private EduClassTeacherMapper classTeacherMapper;

    /* ================= 身份与范围 ================= */

    private LoginUser loginUser() {
        LoginUser loginUser = SecurityUtils.getLoginUser();
        if (loginUser == null || loginUser.getUser() == null) {
            throw new EduForbiddenException("登录状态无效，请重新登录");
        }
        return loginUser;
    }

    private Long currentUserId() {
        return loginUser().getUserId();
    }

    private boolean hasRole(String roleKey) {
        SysUser user = loginUser().getUser();
        if (user.isAdmin()) {
            return ROLE_ADMIN.equals(roleKey);
        }
        List<SysRole> roles = user.getRoles();
        if (roles == null) {
            return false;
        }
        for (SysRole role : roles) {
            if (roleKey.equals(role.getRoleKey())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean isAdmin() {
        return loginUser().getUser().isAdmin() || hasRole(ROLE_ADMIN);
    }

    private void requireAdmin(String action) {
        if (!isAdmin()) {
            throw new EduForbiddenException("只有管理员可以" + action);
        }
    }

    private Long organizationId() {
        Long orgId = portalMapper.selectOrganizationIdByUserId(currentUserId());
        if (orgId == null || orgId == 0L) {
            throw new ServiceException("当前账号没有归属部门，无法确定所属组织");
        }
        return orgId;
    }

    /* ================= 学期 ================= */

    @Override
    public List<Map<String, Object>> termList(String keyword) {
        requireAdmin("查看学期");
        return portalMapper.selectTermList(keyword);
    }

    @Override
    public Long createTerm(Map<String, Object> body) {
        requireAdmin("新建学期");
        EduTerm term = new EduTerm();
        term.setOrganizationId(organizationId());
        term.setName(required(body, "name", "学期名称不能为空"));
        term.setCode(required(body, "code", "学期编码不能为空"));
        term.setStartsOn(date(body, "startsOn", "开始日期不能为空"));
        term.setEndsOn(date(body, "endsOn", "结束日期不能为空"));
        if (term.getEndsOn().before(term.getStartsOn())) {
            throw new ServiceException("结束日期不能早于开始日期");
        }
        try {
            termMapper.insert(term);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("同一学校内学期编码不能重复");
        }
        return term.getId();
    }

    @Override
    public void updateTerm(Long id, Map<String, Object> body) {
        requireAdmin("修改学期");
        EduTerm term = new EduTerm();
        term.setId(id);
        term.setName(optional(body, "name"));
        term.setCode(optional(body, "code"));
        term.setStartsOn(dateOrNull(body, "startsOn"));
        term.setEndsOn(dateOrNull(body, "endsOn"));
        if (term.getStartsOn() != null && term.getEndsOn() != null
                && term.getEndsOn().before(term.getStartsOn())) {
            throw new ServiceException("结束日期不能早于开始日期");
        }
        try {
            if (termMapper.update(term) == 0) {
                throw new ServiceException("学期不存在");
            }
        } catch (DuplicateKeyException e) {
            throw new ServiceException("同一学校内学期编码不能重复");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void setCurrentTerm(Long id) {
        requireAdmin("设置当前学期");
        if (portalMapper.selectTermById(id) == null) {
            throw new ServiceException("学期不存在");
        }
        // 唯一约束只允许一行 is_current=1，先清零再置位
        portalMapper.clearCurrentTerm();
        portalMapper.markCurrentTerm(id);
    }

    /* ================= 教学班 ================= */

    @Override
    public List<Map<String, Object>> teachingClassList(String keyword, Long termId, String status) {
        requireAdmin("查看教学班");
        return portalMapper.selectTeachingClassList(keyword, termId, status);
    }

    @Override
    public Map<String, Object> teachingClassDetail(Long id) {
        Map<String, Object> row = portalMapper.selectTeachingClassById(id);
        if (row == null) {
            throw new ServiceException("教学班不存在");
        }
        if (!isAdmin() && portalMapper.countActiveTeacher(id, currentUserId()) == 0) {
            throw new EduForbiddenException("你不是该教学班的任课教师");
        }
        return row;
    }

    @Override
    public List<Map<String, Object>> myTeachingClasses() {
        if (isAdmin()) {
            return portalMapper.selectTeachingClassList(null, null, "ACTIVE");
        }
        if (!hasRole(ROLE_TEACHER)) {
            throw new EduForbiddenException("当前账号没有教师身份");
        }
        return portalMapper.selectTeachingClassByTeacher(currentUserId());
    }

    @Override
    public Long createTeachingClass(Map<String, Object> body) {
        requireAdmin("新建教学班");
        Long termId = longValue(body.get("termId"));
        if (termId == null) {
            throw new ServiceException("所属学期不能为空");
        }
        Long orgId = portalMapper.selectOrganizationIdByTermId(termId);
        if (orgId == null) {
            throw new ServiceException("所属学期不存在");
        }
        EduTeachingClass teachingClass = new EduTeachingClass();
        teachingClass.setOrganizationId(orgId);
        teachingClass.setTermId(termId);
        teachingClass.setOfficialCourseId(longValue(body.get("officialCourseId")));
        teachingClass.setName(required(body, "name", "班级名称不能为空"));
        teachingClass.setCode(required(body, "code", "班级编码不能为空"));
        teachingClass.setClassKind(defaultString(body.get("classKind"), "FORMAL"));
        teachingClass.setStatus(defaultString(body.get("status"), "DRAFT"));
        try {
            teachingClassMapper.insert(teachingClass);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("同一学期内班级编码不能重复");
        }
        return teachingClass.getId();
    }

    @Override
    public void updateTeachingClass(Long id, Map<String, Object> body) {
        requireAdmin("修改教学班");
        Long termId = longValue(body.get("termId"));
        EduTeachingClass teachingClass = new EduTeachingClass();
        teachingClass.setId(id);
        teachingClass.setTermId(termId);
        teachingClass.setOfficialCourseId(longValue(body.get("officialCourseId")));
        teachingClass.setName(optional(body, "name"));
        teachingClass.setCode(optional(body, "code"));
        teachingClass.setClassKind(optional(body, "classKind"));
        teachingClass.setStatus(optional(body, "status"));
        if (termId != null) {
            Long orgId = portalMapper.selectOrganizationIdByTermId(termId);
            if (orgId == null) {
                throw new ServiceException("所属学期不存在");
            }
            teachingClass.setOrganizationId(orgId);
        }
        try {
            if (teachingClassMapper.update(teachingClass) == 0) {
                throw new ServiceException("教学班不存在");
            }
        } catch (DuplicateKeyException e) {
            throw new ServiceException("同一学期内班级编码不能重复");
        }
    }

    @Override
    public List<Map<String, Object>> classMembers(Long classId) {
        requireClassAccess(classId);
        return portalMapper.selectClassMembers(classId);
    }

    @Override
    public void addMember(Long classId, Map<String, Object> body) {
        requireAdmin("维护班级成员");
        Long studentId = longValue(body.get("studentId"));
        if (studentId == null) {
            throw new ServiceException("学生不能为空");
        }
        Long joiner = currentUserId();
        try {
            portalMapper.insertClassStudent(classId, studentId, joiner);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("该学生已在本班，不能重复加入");
        }
    }

    @Override
    public void removeMember(Long classId, Long studentId, String reason) {
        requireAdmin("维护班级成员");
        if (classStudentMapper.leave(classId, studentId, currentUserId(), reason) == 0) {
            throw new ServiceException("该学生不在本班");
        }
    }

    @Override
    public List<Map<String, Object>> classTeachers(Long classId) {
        requireAdmin("查看任课教师授权");
        return portalMapper.selectClassTeachers(classId);
    }

    @Override
    public void addTeacher(Long classId, Map<String, Object> body) {
        requireAdmin("指定任课教师");
        Long userId = longValue(body.get("userId"));
        if (userId == null) {
            throw new ServiceException("教师不能为空");
        }
        String teacherRole = defaultString(body.get("teacherRole"), "LEAD");
        try {
            portalMapper.insertClassTeacher(classId, userId, teacherRole,
                    flag(body.get("canPublish")), flag(body.get("canGrade")), flag(body.get("canExport")),
                    optional(body, "validTo"), currentUserId());
        } catch (DuplicateKeyException e) {
            throw new ServiceException("该教师已在本班任课，不能重复授权");
        }
    }

    @Override
    public void removeTeacher(Long classId, Long userId) {
        requireAdmin("撤销任课教师");
        if (classTeacherMapper.revoke(classId, userId, currentUserId()) == 0) {
            throw new ServiceException("该教师在本班没有有效授权");
        }
    }

    private void requireClassAccess(Long classId) {
        if (isAdmin()) {
            return;
        }
        if (portalMapper.countActiveTeacher(classId, currentUserId()) == 0) {
            throw new EduForbiddenException("你不是该教学班的任课教师");
        }
    }

    /* ================= 教师 / 学生 / 组织 ================= */

    @Override
    public List<Map<String, Object>> teacherList(String keyword, Long deptId) {
        requireAdmin("查看教师");
        return portalMapper.selectTeacherList(keyword, deptId);
    }

    @Override
    public List<Map<String, Object>> teacherClasses(Long userId) {
        requireAdmin("查看教师任教关系");
        return portalMapper.selectTeacherClasses(userId);
    }

    @Override
    public List<Map<String, Object>> studentList(String keyword) {
        requireAdmin("查看学生");
        return portalMapper.selectStudentList(keyword);
    }

    @Override
    public List<Map<String, Object>> studentClasses(Long userId) {
        requireAdmin("查看学生在班记录");
        return portalMapper.selectStudentClasses(userId);
    }

    @Override
    public List<Map<String, Object>> deptTree() {
        requireAdmin("查看组织架构");
        return portalMapper.selectDeptTree();
    }

    @Override
    public List<Map<String, Object>> officialCourses(String keyword) {
        return portalMapper.selectOfficialCourses(keyword);
    }

    /* ================= 课程空间 ================= */

    @Override
    public List<Map<String, Object>> courseList(String keyword) {
        Long userId = currentUserId();
        if (isAdmin()) {
            return portalMapper.selectCourseList(null, null, keyword);
        }
        if (hasRole(ROLE_TEACHER)) {
            return portalMapper.selectCourseList(userId, null, keyword);
        }
        if (hasRole(ROLE_STUDENT)) {
            return portalMapper.selectCourseList(null, userId, keyword);
        }
        throw new EduForbiddenException("当前账号没有课程访问角色");
    }

    @Override
    public Map<String, Object> courseDetail(Long id) {
        Map<String, Object> course = portalMapper.selectCourseById(id);
        if (course == null) {
            throw new ServiceException("课程不存在");
        }
        Long userId = currentUserId();
        boolean admin = isAdmin();
        boolean owner = userId.equals(longValue(course.get("ownerUserId")));
        if (!admin && !owner) {
            boolean studentCanRead = hasRole(ROLE_STUDENT)
                    && "ACTIVE".equals(String.valueOf(course.get("status")))
                    && portalMapper.countStudentInCourse(id, userId) > 0;
            if (!studentCanRead) {
                throw new EduForbiddenException("你没有这门课程的访问权限");
            }
        }
        course.put("units", unitTree(portalMapper.selectCourseUnits(id)));
        course.put("knowledgePoints", portalMapper.selectCourseKnowledgePoints(id));
        course.put("classes", portalMapper.selectCourseBindings(id));
        return course;
    }

    @Override
    public Long createCourse(Map<String, Object> body) {
        Long userId = currentUserId();
        EduCourseSpace space = new EduCourseSpace();
        space.setOwnerUserId(userId);
        space.setTitle(required(body, "name", "课程名称不能为空"));
        space.setSummary(optional(body, "summary"));
        space.setLearningObjectives(optional(body, "learningObjectives"));
        space.setStatus("DRAFT");
        Long officialCourseId = longValue(body.get("officialCourseId"));
        space.setOfficialCourseId(officialCourseId);
        Long orgId = officialCourseId == null ? null
                : portalMapper.selectOrganizationIdByOfficialCourseId(officialCourseId);
        if (orgId == null) {
            orgId = organizationId();
        }
        space.setOrganizationId(orgId);
        courseSpaceMapper.insert(space);
        return space.getId();
    }

    @Override
    public void updateCourse(Long id, Map<String, Object> body) {
        EduCourseSpace space = requireCourseOwnership(id, "修改课程");
        space.setTitle(optional(body, "name"));
        space.setSummary(optional(body, "summary"));
        space.setLearningObjectives(optional(body, "learningObjectives"));
        Long officialCourseId = longValue(body.get("officialCourseId"));
        if (officialCourseId != null) {
            space.setOfficialCourseId(officialCourseId);
        }
        courseSpaceMapper.update(space);
    }

    @Override
    public void updateCourseStatus(Long id, String status) {
        if (!"DRAFT".equals(status) && !"ACTIVE".equals(status) && !"ARCHIVED".equals(status)) {
            throw new ServiceException("不支持的课程状态：" + status);
        }
        requireCourseOwnership(id, "发布或撤回课程");
        portalMapper.updateCourseStatus(id, status);
    }

    @Override
    public void bindClass(Long courseId, Long classId, String publishedAt) {
        if (classId == null) {
            throw new ServiceException("教学班不能为空");
        }
        EduCourseSpace space = requireCourseOwnership(courseId, "绑定教学班");
        Map<String, Object> teachingClass = portalMapper.selectTeachingClassById(classId);
        if (teachingClass == null) {
            throw new ServiceException("教学班不存在");
        }
        if (!isAdmin() && portalMapper.countActiveTeacher(classId, currentUserId()) == 0) {
            throw new EduForbiddenException("只能把课程绑定到自己任教的教学班");
        }
        if (portalMapper.countActiveBinding(courseId, classId) > 0) {
            throw new ServiceException("该课程已绑定这个教学班");
        }
        EduCourseClassBinding binding = new EduCourseClassBinding();
        binding.setCourseSpaceId(courseId);
        binding.setTeachingClassId(classId);
        binding.setOrganizationId(space.getOrganizationId());
        binding.setBoundByUserId(currentUserId());
        try {
            bindingMapper.insert(binding);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("该教学班已绑定其他课程，请先解绑");
        }
    }

    @Override
    public void unbindClass(Long courseId, Long bindingId) {
        requireCourseOwnership(courseId, "解绑教学班");
        if (portalMapper.endBinding(bindingId, courseId, currentUserId()) == 0) {
            throw new ServiceException("绑定不存在或已解绑");
        }
    }

    private EduCourseSpace requireCourseOwnership(Long courseId, String action) {
        EduCourseSpace space = courseSpaceMapper.selectById(courseId);
        if (space == null) {
            throw new ServiceException("课程不存在");
        }
        if (!isAdmin() && !currentUserId().equals(space.getOwnerUserId())) {
            throw new EduForbiddenException("只有课程负责人可以" + action);
        }
        return space;
    }

    /* ================= 小工具 ================= */

    private List<Map<String, Object>> unitTree(List<Map<String, Object>> rows) {
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows) {
            row.put("children", new ArrayList<Map<String, Object>>());
            byId.put(longValue(row.get("id")), row);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> row : rows) {
            Long parentId = longValue(row.get("parentUnitId"));
            Map<String, Object> parent = parentId == null ? null : byId.get(parentId);
            if (parent == null) {
                roots.add(row);
            } else {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> children = (List<Map<String, Object>>) parent.get("children");
                children.add(row);
            }
        }
        return roots;
    }

    private static String required(Map<String, Object> body, String key, String message) {
        String value = optional(body, key);
        if (value == null || value.isEmpty()) {
            throw new ServiceException(message);
        }
        return value;
    }

    private static String optional(Map<String, Object> body, String key) {
        Object value = body == null ? null : body.get(key);
        if (value == null) {
            return null;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? null : text;
    }

    private static String defaultString(Object value, String fallback) {
        if (value == null) {
            return fallback;
        }
        String text = String.valueOf(value).trim();
        return text.isEmpty() ? fallback : text;
    }

    private static Long longValue(Object value) {
        if (value == null || "".equals(value)) {
            return null;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            throw new ServiceException("编号格式不正确：" + value);
        }
    }

    private static Integer flag(Object value) {
        if (value instanceof Boolean) {
            return ((Boolean) value) ? 1 : 0;
        }
        if (value == null || "".equals(value)) {
            return 0;
        }
        try {
            return Integer.valueOf(String.valueOf(value));
        } catch (NumberFormatException e) {
            return 0;
        }
    }

    private static Date dateOrNull(Map<String, Object> body, String key) {
        String text = optional(body, key);
        if (text == null) {
            return null;
        }
        if (text.length() > 10) {
            text = text.substring(0, 10);
        }
        try {
            return new java.sql.Date(java.sql.Date.valueOf(text).getTime());
        } catch (IllegalArgumentException e) {
            throw new ServiceException("日期格式应为 yyyy-MM-dd：" + text);
        }
    }

    private static Date date(Map<String, Object> body, String key, String message) {
        Date value = dateOrNull(body, key);
        if (value == null) {
            throw new ServiceException(message);
        }
        return value;
    }
}
