package com.ruoyi.education.service.impl;

import com.github.pagehelper.PageHelper;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.education.domain.EduClassTeacher;
import com.ruoyi.education.domain.EduCourseSpace;
import com.ruoyi.education.domain.EduTeachingClass;
import com.ruoyi.education.domain.EduTerm;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.mapper.EduClassStudentMapper;
import com.ruoyi.education.mapper.EduClassTeacherMapper;
import com.ruoyi.education.mapper.EduCourseClassBindingMapper;
import com.ruoyi.education.mapper.EduCourseSpaceMapper;
import com.ruoyi.education.mapper.EduPortalMapper;
import com.ruoyi.education.mapper.EduCourseManagementMapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class EduPortalServiceImpl implements IEduPortalService {

    private static final String ROLE_ADMIN = "admin";
    private static final String ROLE_TEACHER = "teacher";
    private static final String ROLE_STUDENT = "student";
    private static final int MAX_ROSTER_BATCH = 1000;

    @Autowired
    private EduPortalMapper portalMapper;
    @Autowired
    private EduCourseManagementMapper management;
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
        return roleKey.equals(com.ruoyi.education.security.EduActor.role());
    }

    @Override
    public boolean isAdmin() {
        return com.ruoyi.education.security.EduActor.admin();
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
    public List<Map<String, Object>> termSchools() {
        requireAdmin("新建学期");
        Long schoolScope = SecurityUtils.isAdmin() ? null : organizationId();
        return portalMapper.selectTermSchools(schoolScope);
    }

    @Override
    public Long createTerm(Map<String, Object> body) {
        requireAdmin("新建学期");
        Long schoolScope = SecurityUtils.isAdmin() ? null : organizationId();
        Long schoolId = longValue(optional(body, "organizationId"));
        if (schoolId == null) {
            schoolId = schoolScope != null ? schoolScope : portalMapper.selectOrganizationIdByUserId(currentUserId());
        }
        if (schoolId == null || schoolId <= 0) {
            throw new ServiceException("请选择所属学校");
        }
        if (schoolScope != null && !schoolScope.equals(schoolId)) {
            throw new EduForbiddenException("只能为所属学校新建学期");
        }
        if (portalMapper.selectTermSchools(schoolId).isEmpty()) {
            throw new ServiceException("所选学校不存在或已停用，请重新选择");
        }
        EduTerm term = new EduTerm();
        term.setOrganizationId(schoolId);
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
    public List<Map<String, Object>> teachingClassList(String keyword, Long termId, String status, Long deptId) {
        requireAdmin("查看教学班");
        return portalMapper.selectTeachingClassList(keyword, termId, status, deptId);
    }

    @Override
    public List<Map<String, Object>> majorClasses(Long majorId, String keyword) {
        requireAdmin("查看专业班级");
        return portalMapper.selectMajorClasses(majorId, keyword);
    }

    @Override
    public boolean hasOrganizationReferences(List<Long> organizationIds) {
        return !organizationIds.isEmpty() && portalMapper.hasOrganizationReferences(organizationIds);
    }

    @Override
    public Map<String, Object> teachingClassDetail(Long id) {
        Map<String, Object> row = portalMapper.selectTeachingClassById(id);
        if (row == null) {
            throw new ServiceException("教学班不存在");
        }
        if (!isAdmin() && (!hasRole(ROLE_TEACHER) || portalMapper.countActiveTeacher(id, currentUserId()) == 0)) {
            throw new EduForbiddenException("你不是该教学班的任课教师");
        }
        return row;
    }

    @Override
    public List<Map<String, Object>> myTeachingClasses() {
        if (isAdmin()) {
            return portalMapper.selectTeachingClassList(null, null, "ACTIVE", null);
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
        List<Map<String,Object>> rows=portalMapper.selectClassMembers(classId);
        return isAdmin()?rows:rows.stream().filter(r->"ACTIVE".equals(r.get("status"))).toList();
    }

    @Override
    public Map<String, Boolean> rosterPermissions(Long classId) {
        if (!hasRole(ROLE_TEACHER)) {
            throw new EduForbiddenException("仅任课教师可以操作教学班名单");
        }
        EduClassTeacher teacher = classTeacherMapper.selectActive(classId, currentUserId());
        Map<String, Object> team = management.teamClassAccess(classId, currentUserId());
        if (teacher == null && team == null) {
            throw new EduForbiddenException("你没有这个教学班的任课授权");
        }
        EduTeachingClass teachingClass = teachingClassMapper.selectById(classId);
        boolean canImport = teachingClass != null && "ACTIVE".equals(teachingClass.getStatus())
                && ((teacher != null && "LEAD".equals(teacher.getTeacherRole()))
                    || (team != null && flag(team.get("canManageStudents")) == 1));
        boolean canExport = (teacher != null && Boolean.TRUE.equals(teacher.getCanExport()))
                || (team != null && flag(team.get("canExport")) == 1);
        return Map.of("canImport", canImport, "canExport", canExport);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String importRoster(Long classId, Map<Integer, String> accountRows) {
        management.lockClass(classId);
        if (!rosterPermissions(classId).get("canImport")) {
            throw new EduForbiddenException("仅开放教学班的主讲教师可以导入名单");
        }
        if (accountRows.isEmpty() || accountRows.size() > MAX_ROSTER_BATCH) {
            throw new ServiceException("每批需要填写 1 至 1000 条学生账号");
        }
        Long organizationId = portalMapper.selectOrganizationIdByClassId(classId);
        List<String> uniqueAccounts = new ArrayList<>(new LinkedHashSet<>(accountRows.values()));
        Map<String, Long> studentIds = new LinkedHashMap<>();
        for (Map<String, Object> student : portalMapper.selectRosterStudents(organizationId, uniqueAccounts)) {
            studentIds.put((String) student.get("userName"), longValue(student.get("userId")));
        }
        for (Map.Entry<Integer, String> row : accountRows.entrySet()) {
            if (!studentIds.containsKey(row.getValue())) {
                throw new ServiceException("第 " + row.getKey() + " 行未匹配到本组织的有效学生账号，请核对后重试");
            }
        }

        int added = insertRosterStudents(classId, new ArrayList<>(studentIds.values()));
        return rosterResult(added, accountRows.size());
    }

    private int insertRosterStudents(Long classId, List<Long> studentIds) {
        int added = 0;
        Long operatorId = currentUserId();
        for (Long studentId : studentIds.stream().sorted().toList()) {
            if (classStudentMapper.selectActive(classId, studentId) != null) continue;
            try {
                added += portalMapper.insertClassStudent(classId, studentId, operatorId);
            } catch (DuplicateKeyException ignored) {
                // 同时导入时，由数据库的在班唯一约束保护。
            }
        }
        return added;
    }

    private String rosterResult(int added, int requested) {
        return "新增 " + added + " 名学生，跳过 " + (requested - added) + " 条重复记录";
    }

    @Override
    public List<Map<String, Object>> exportRoster(Long classId) {
        if (!rosterPermissions(classId).get("canExport")) {
            throw new EduForbiddenException("你没有这个教学班的名单导出权限");
        }
        return classMembers(classId);
    }

    @Override
    public void addMember(Long classId, Map<String, Object> body) {
        requireAdmin("维护班级成员");
        Long studentId = longValue(body.get("studentId"));
        if (studentId == null) {
            throw new ServiceException("学生不能为空");
        }
        requireExistingClass(classId);
        if (portalMapper.countActiveAccountRole(studentId, "student") == 0) throw new ServiceException("所选账号不是有效学生");
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
        requireExistingClass(classId);
        if (portalMapper.countActiveAccountRole(userId, "teacher") == 0) throw new ServiceException("所选账号不是有效教师");
        if (portalMapper.countActiveTeacher(classId,userId)>0) throw new ServiceException("该教师在本班已有有效授权，请先撤销原授权");
        String teacherRole = defaultString(body.get("teacherRole"), "LEAD");
        if (!java.util.Set.of("LEAD", "CO_TEACHER").contains(teacherRole)) throw new ServiceException("任课类型不正确");
        Date validTo = dateOrNull(body, "validTo");
        if (validTo != null && !validTo.after(new Date())) throw new ServiceException("授权截止日期必须晚于当前时间");
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

    private void requireExistingClass(Long classId) {
        if (classId == null || portalMapper.selectTeachingClassById(classId) == null) throw new ServiceException("教学班不存在");
    }

    private void requireClassAccess(Long classId) {
        if (isAdmin()) {
            return;
        }
        if (!hasRole(ROLE_TEACHER) || portalMapper.countActiveTeacher(classId, currentUserId()) == 0) {
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
    public List<Map<String, Object>> studentList(String keyword, Long deptId) {
        requireAdmin("查看学生");
        return portalMapper.selectStudentList(keyword, deptId);
    }

    @Override
    public List<Map<String, Object>> studentClasses(Long userId) {
        requireAdmin("查看学生在班记录");
        return portalMapper.selectStudentClasses(userId);
    }

    @Override
    public List<Map<String, Object>> deptTree(boolean teacherOnly) {
        requireAdmin("查看组织架构");
        return portalMapper.selectDeptTree(teacherOnly);
    }

    @Override
    public List<Map<String, Object>> officialCourses(String keyword) {
        com.ruoyi.education.security.EduActor.requireTeacher();
        return portalMapper.selectOfficialCourses(keyword,isAdmin()?null:organizationId());
    }

    /* ================= 课程空间 ================= */

    @Override
    public List<Map<String, Object>> courseList(String keyword) {
        Long userId = currentUserId();
        if (isAdmin()) {
            return portalMapper.selectCourseList(null, null, keyword);
        }
        if (hasRole(ROLE_TEACHER)) {
            List<Map<String,Object>> rows=portalMapper.selectCourseList(userId,null,keyword);
            rows.forEach(row -> row.put("canDelete", userId.equals(longValue(row.get("ownerUserId")))));
            return rows;
        }
        if (hasRole(ROLE_STUDENT)) {
            List<Map<String,Object>> courses = portalMapper.selectCourseList(null, userId, keyword);
            for (Map<String,Object> course : courses) {
                List<Map<String,Object>> classes = portalMapper.selectStudentCourseBindings(longValue(course.get("id")), userId);
                course.put("className", classes.stream().map(c -> String.valueOf(c.get("name"))).collect(java.util.stream.Collectors.joining("、")));
                course.put("classCount", classes.size());
                course.put("semesterName", classes.isEmpty() ? null : classes.get(0).get("termName"));
                course.remove("studentCount");
            }
            return courses;
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
        boolean owner = hasRole(ROLE_TEACHER) && userId.equals(longValue(course.get("ownerUserId")));
        boolean teammate = hasRole(ROLE_TEACHER) && management.teamAccess(id, userId) != null;
        if (!admin && !owner && !teammate) {
            boolean studentCanRead = hasRole(ROLE_STUDENT)
                    && "ACTIVE".equals(String.valueOf(course.get("status")))
                    && portalMapper.countStudentInCourse(id, userId) > 0;
            if (!studentCanRead) {
                throw new EduForbiddenException("你没有这门课程的访问权限");
            }
        }
        course.put("units", unitTree(portalMapper.selectCourseUnits(id)));
        course.put("knowledgePoints", portalMapper.selectCourseKnowledgePoints(id));
        course.put("classes", (!admin && hasRole(ROLE_STUDENT))
                ? portalMapper.selectStudentCourseBindings(id, userId) : portalMapper.selectCourseBindings(id));
        if (!admin && hasRole(ROLE_STUDENT)) {
            course.remove("studentCount");
            @SuppressWarnings("unchecked") List<Map<String,Object>> ownClasses=(List<Map<String,Object>>)course.get("classes");
            course.put("classCount",ownClasses.size());
            course.put("className",ownClasses.stream().map(c->String.valueOf(c.get("name"))).collect(java.util.stream.Collectors.joining("、")));
            course.put("semesterName",ownClasses.isEmpty()?null:ownClasses.get(0).get("termName"));
        }
        course.put("canManage", admin || owner);
        course.put("canManageRoster", owner || (teammate && flag(management.teamAccess(id, userId).get("canManageStudents")) == 1));
        course.put("canDelete", admin || owner);
        return course;
    }

    @Override
    public Long createCourse(Map<String, Object> body) {
        com.ruoyi.education.security.EduActor.requireTeacher();
        Long userId = currentUserId();
        EduCourseSpace space = new EduCourseSpace();
        space.setOwnerUserId(userId);
        space.setTitle(required(body, "name", "课程名称不能为空"));
        space.setSummary(body.get("summary")==null?"":String.valueOf(body.get("summary")));
        space.setLearningObjectives(body.get("learningObjectives")==null?"":String.valueOf(body.get("learningObjectives")));
        space.setStatus("DRAFT");
        Long officialCourseId = longValue(body.get("officialCourseId"));
        space.setOfficialCourseId(officialCourseId);
        Long orgId = officialCourseId == null ? null
                : portalMapper.selectOrganizationIdByOfficialCourseId(officialCourseId);
        if(officialCourseId!=null && orgId==null) throw new ServiceException("正式课程不存在");
        if (orgId == null) orgId=organizationId();
        if(!isAdmin() && !organizationId().equals(orgId)) throw new EduForbiddenException("正式课程不属于当前组织");
        space.setOrganizationId(orgId);
        courseSpaceMapper.insert(space);
        return space.getId();
    }

    @Override
    public void updateCourse(Long id, Map<String, Object> body) {
        EduCourseSpace space = requireCourseOwnership(id, "修改课程");
        space.setTitle(required(body, "name", "课程名称不能为空"));
        space.setSummary(body.get("summary")==null?"":String.valueOf(body.get("summary")));
        space.setLearningObjectives(body.get("learningObjectives")==null?"":String.valueOf(body.get("learningObjectives")));
        Long officialCourseId = longValue(body.get("officialCourseId"));
        if(officialCourseId!=null && !space.getOrganizationId().equals(portalMapper.selectOrganizationIdByOfficialCourseId(officialCourseId))) throw new ServiceException("正式课程不属于当前组织");
        space.setOfficialCourseId(officialCourseId);
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
    @Transactional(rollbackFor = Exception.class)
    public Long createCourseClass(Long courseId, String name, Long termId) {
        EduCourseSpace course = courseSpaceMapper.selectByIdForUpdate(courseId);
        requireTeacherCourse(course);
        String className = name == null ? "" : name.strip();
        if (className.isEmpty() || className.length() > 160) {
            throw new ServiceException("班级名称须为 1 至 160 个字符");
        }
        if (portalMapper.countCourseClassByName(courseId, className) > 0) {
            throw new ServiceException("这门课程已有同名教学班");
        }
        Long selectedTerm = portalMapper.selectCourseClassTermId(course.getOrganizationId(), termId);
        if (selectedTerm == null) {
            throw new ServiceException(termId == null ? "本校尚未设置可用的当前学期，请在设置学期后重试"
                    : "所选学期不属于本校或已经结束");
        }

        EduTeachingClass teachingClass = new EduTeachingClass();
        teachingClass.setOrganizationId(course.getOrganizationId());
        teachingClass.setTermId(selectedTerm);
        teachingClass.setOfficialCourseId(course.getOfficialCourseId());
        teachingClass.setName(className);
        teachingClass.setCode("TC-" + UUID.randomUUID());
        teachingClass.setClassKind(course.getOfficialCourseId() == null ? "TRIAL" : "FORMAL");
        teachingClass.setStatus("ACTIVE");
        teachingClassMapper.insert(teachingClass);

        Long classId = teachingClass.getId();
        Long teacherId = currentUserId();
        portalMapper.insertClassTeacher(classId, teacherId, "LEAD", 1, 1, 1, null, teacherId);
        insertCourseBinding(course.getOrganizationId(), courseId, classId);
        return classId;
    }

    @Override
    public List<Map<String, Object>> courseRosterSources(Long courseId, Long classId) {
        EduCourseSpace course = courseSpaceMapper.selectById(courseId);
        requireCourseRosterAccess(course, classId);
        return portalMapper.selectCourseRosterSources(course.getOrganizationId(), currentUserId(), classId, null, null);
    }

    @Override
    public List<Map<String, Object>> courseRosterOrganizations(Long courseId, Long classId) {
        EduCourseSpace course = courseSpaceMapper.selectById(courseId);
        requireCourseRosterAccess(course, classId);
        return portalMapper.selectCourseRosterOrganizations(course.getOrganizationId());
    }

    @Override
    public List<Map<String, Object>> courseRosterCandidates(Long courseId, Long classId, String keyword,
            Long sourceCourseId, Long sourceClassId, Long sourceDeptId, boolean includeDescendants,
            int pageNum, int pageSize) {
        EduCourseSpace course = courseSpaceMapper.selectById(courseId);
        requireCourseRosterAccess(course, classId);
        requireCourseRosterSource(course, classId, sourceCourseId, sourceClassId);
        String deptType = requireRosterDepartment(course, sourceCourseId, sourceDeptId);
        String search = keyword == null ? "" : keyword.strip();
        if (search.length() > 100) {
            throw new ServiceException("搜索关键词不能超过 100 个字符");
        }
        PageHelper.startPage(Math.max(1, pageNum), Math.max(1, Math.min(pageSize, 100)));
        return portalMapper.selectCourseRosterCandidates(course.getOrganizationId(), classId, sourceClassId,
                sourceDeptId, includeDescendants && !"CLASS".equals(deptType), search);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public String addCourseRosterStudents(Long courseId, Long classId, List<Long> studentIds,
            Long sourceCourseId, Long sourceClassId, Long sourceDeptId, boolean includeDescendants, boolean wholeClass) {
        EduCourseSpace course = courseSpaceMapper.selectByIdForUpdate(courseId);
        management.lockClass(classId);
        requireCourseRosterAccess(course, classId);
        requireCourseRosterSource(course, classId, sourceCourseId, sourceClassId);
        String deptType = requireRosterDepartment(course, sourceCourseId, sourceDeptId);
        if (wholeClass) {
            if (!"CLASS".equals(deptType)) {
                throw new ServiceException("请选择一个行政班后再整班添加");
            }
            PageHelper.startPage(1, MAX_ROSTER_BATCH + 1, false);
            List<Long> eligibleIds = portalMapper.selectCourseRosterStudentIds(
                    course.getOrganizationId(), classId, null, sourceDeptId, false, null);
            if (eligibleIds.size() > MAX_ROSTER_BATCH) {
                throw new ServiceException("可添加学生超过 1000 人，请分批选择");
            }
            return rosterResult(insertRosterStudents(classId, eligibleIds), eligibleIds.size());
        }
        if (studentIds == null || studentIds.isEmpty() || studentIds.size() > MAX_ROSTER_BATCH
                || studentIds.stream().anyMatch(id -> id == null || id <= 0)) {
            throw new ServiceException("每批请选择 1 至 1000 名有效学生");
        }
        List<Long> uniqueIds = new ArrayList<>(new LinkedHashSet<>(studentIds));
        List<Long> eligibleIds = portalMapper.selectCourseRosterStudentIds(
                course.getOrganizationId(), classId, sourceClassId, sourceDeptId,
                includeDescendants && !"CLASS".equals(deptType), uniqueIds);
        if (eligibleIds.size() != uniqueIds.size()) {
            throw new ServiceException("部分学生已不在可添加范围，请刷新名单后重试");
        }
        return rosterResult(insertRosterStudents(classId, eligibleIds), studentIds.size());
    }

    private String requireRosterDepartment(EduCourseSpace course, Long sourceCourseId, Long sourceDeptId) {
        if (sourceDeptId == null) {
            return null;
        }
        if (sourceCourseId != null) {
            throw new ServiceException("请只选择一种学生来源");
        }
        String type = portalMapper.selectCourseRosterDepartmentType(course.getOrganizationId(), sourceDeptId);
        if (type == null) {
            throw new EduForbiddenException("该组织已停用或不在本校学生库内");
        }
        return type;
    }

    private void requireTeacherCourse(EduCourseSpace course) {
        if (!hasRole(ROLE_TEACHER)) {
            throw new EduForbiddenException("只有课程负责人可以管理教学班");
        }
        requireCourseOwner(course, "管理教学班");
        if ("ARCHIVED".equals(course.getStatus())) {
            throw new ServiceException("已归档课程只允许查看");
        }
        if (!course.getOrganizationId().equals(organizationId())) {
            throw new EduForbiddenException("这门课程不属于你当前所在的学校");
        }
    }

    private void requireCourseRosterAccess(EduCourseSpace course, Long classId) {
        if (course == null) throw new ServiceException("课程不存在");
        Map<String, Object> team = management.teamAccess(course.getId(), currentUserId());
        if (!hasRole(ROLE_TEACHER) || team == null) {
            requireTeacherCourse(course);
        } else if ("ARCHIVED".equals(course.getStatus())) {
            throw new ServiceException("已归档课程只允许查看");
        }
        if (portalMapper.countActiveBinding(course.getId(), classId) == 0) {
            throw new EduForbiddenException("该教学班不属于这门课程");
        }
        if (!rosterPermissions(classId).get("canImport")) {
            throw new EduForbiddenException("仅开放教学班的主讲教师可以添加学生");
        }
    }

    private void requireCourseRosterSource(EduCourseSpace course, Long classId, Long sourceCourseId, Long sourceClassId) {
        if (sourceCourseId == null && sourceClassId == null) {
            return;
        }
        if (sourceCourseId == null || sourceClassId == null) {
            throw new ServiceException("请选择来源课程和教学班");
        }
        if (portalMapper.selectCourseRosterSources(
                course.getOrganizationId(), currentUserId(), classId, sourceCourseId, sourceClassId).isEmpty()) {
            throw new EduForbiddenException("所选班级不属于你可复制名单的课程范围");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void bindClass(Long courseId, Long classId, String publishedAt) {
        if (classId == null) {
            throw new ServiceException("教学班不能为空");
        }
        EduCourseSpace space = courseSpaceMapper.selectByIdForUpdate(courseId);
        requireCourseOwner(space, "绑定教学班");
        management.lockClass(classId);
        if ("ARCHIVED".equals(space.getStatus())) throw new ServiceException("已归档课程只允许查看");
        Map<String, Object> teachingClass = portalMapper.selectTeachingClassById(classId);
        if (teachingClass == null) {
            throw new ServiceException("教学班不存在");
        }
        if (!"ACTIVE".equals(String.valueOf(teachingClass.get("status")))) throw new ServiceException("教学班未开放");
        if (!space.getOrganizationId().equals(longValue(teachingClass.get("organizationId")))) throw new EduForbiddenException("课程与教学班不属于同一组织");
        if (!isAdmin() && portalMapper.countActiveTeacher(classId, currentUserId()) == 0) {
            throw new EduForbiddenException("只能把课程绑定到自己任教的教学班");
        }
        if (portalMapper.countActiveBinding(courseId, classId) > 0) {
            throw new ServiceException("该课程已绑定这个教学班");
        }
        try {
            insertCourseBinding(space.getOrganizationId(), courseId, classId);
        } catch (DuplicateKeyException e) {
            throw new ServiceException("该课程已绑定此教学班，请刷新后重试");
        }
    }

    private void insertCourseBinding(Long organizationId, Long courseId, Long classId) {
        EduCourseClassBinding binding = new EduCourseClassBinding();
        binding.setCourseSpaceId(courseId);
        binding.setTeachingClassId(classId);
        binding.setOrganizationId(organizationId);
        binding.setBoundByUserId(currentUserId());
        bindingMapper.insert(binding);
    }

    @Override
    public void unbindClass(Long courseId, Long bindingId) {
        requireCourseOwnership(courseId, "解绑教学班");
        if (portalMapper.endBinding(bindingId, courseId, currentUserId()) == 0) {
            throw new ServiceException("绑定不存在或已解绑");
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void deleteCourse(Long courseId) {
        EduCourseSpace course = courseSpaceMapper.selectByIdForUpdate(courseId);
        requireCourseOwner(course, "删除课程");
        courseSpaceMapper.softDelete(courseId);
        portalMapper.endCourseBindings(courseId, currentUserId());
    }

    private EduCourseSpace requireCourseOwnership(Long courseId, String action) {
        EduCourseSpace space = courseSpaceMapper.selectById(courseId);
        requireCourseOwner(space, action);
        if ("ARCHIVED".equals(space.getStatus())) throw new ServiceException("已归档课程只允许查看");
        return space;
    }

    private void requireCourseOwner(EduCourseSpace space, String action) {
        if (space == null) throw new ServiceException("课程不存在或已删除");
        if (!isAdmin() && (!hasRole(ROLE_TEACHER) || !currentUserId().equals(space.getOwnerUserId()))) {
            throw new EduForbiddenException("只有课程负责人可以" + action);
        }
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
