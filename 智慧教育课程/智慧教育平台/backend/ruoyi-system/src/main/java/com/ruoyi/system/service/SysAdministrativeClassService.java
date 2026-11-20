package com.ruoyi.system.service;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.constant.SysDeptType;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysUserMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** 行政班使用组织节点，学生名单使用现有用户的组织归属。 */
@Service
public class SysAdministrativeClassService
{
    private static final Pattern GRADE_PREFIX = Pattern.compile("^([0-9]{4})\\s*级");
    private static final Set<String> LEVELS = Set.of("中专", "专科", "本科", "硕士", "博士", "其他");
    private final SysDeptMapper departments;
    private final SysUserMapper users;
    private final ISysDeptService departmentService;

    public SysAdministrativeClassService(SysDeptMapper departments, SysUserMapper users, ISysDeptService departmentService)
    {
        this.departments = departments;
        this.users = users;
        this.departmentService = departmentService;
    }

    @DataScope(deptAlias = "d", permission = "education:class:list")
    public List<SysDept> list(SysDept filter)
    {
        List<SysDept> classes = departments.selectAdministrativeClasses(filter);
        classes.forEach(dept -> dept.setGradeYear(displayGrade(dept)));
        return classes;
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, SysDept changes)
    {
        departmentService.checkDeptDataScope(id);
        SysDept current = requireClass(departments.lockDepartment(id));
        changes.setDeptId(id);
        changes.setDeptType(SysDeptType.CLASS.name());
        if (changes.getParentId() == null || changes.getParentId() <= 0)
            throw new ServiceException("请选择班级所属的学院或专业");
        if (Objects.equals(current.getStatus(), changes.getStatus())) changes.setStatus(null);
        boolean moved = !current.getParentId().equals(changes.getParentId());
        boolean enabling = UserConstants.DEPT_NORMAL.equals(changes.getStatus());
        if (moved) departmentService.checkDeptDataScope(changes.getParentId());
        if (moved || enabling)
        {
            SysDept newParent = departments.lockDepartment(changes.getParentId());
            if (newParent == null || !UserConstants.DEPT_NORMAL.equals(newParent.getStatus()))
                throw new ServiceException("请选择开启的上级组织");
            if (moved)
            {
                String schoolId = schoolFrom(current);
                String parentPath = newParent.getAncestors() + "," + newParent.getDeptId();
                if (!parentPath.startsWith("0," + schoolId + ",") && !parentPath.equals("0," + schoolId))
                    throw new ServiceException("班级只能在同一学校内调整归属组织");
            }
        }
        if (UserConstants.DEPT_DISABLE.equals(changes.getStatus()) && departmentService.selectNormalChildrenDeptById(id) > 0)
            throw new ServiceException("请先停用班级下仍处于开启状态的组织");
        validateMetadata(changes);
        if (!departmentService.checkDeptNameUnique(changes))
            throw new ServiceException("该归属组织下已有同名班级");
        departmentService.updateDept(changes);
    }

    public SysUser rosterFilter(Long id, boolean members, String keyword, Long orgId, Integer grade)
    {
        departmentService.checkDeptDataScope(id);
        SysDept classroom = requireClass(departments.selectDeptById(id));
        return rosterFilter(classroom, members, keyword, orgId, grade);
    }

    @DataScope(deptAlias = "d", userAlias = "u", permission = "education:class:member")
    public List<SysUser> roster(SysUser filter)
    {
        return users.selectAdministrativeRoster(filter);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Long> move(Long id, Collection<Long> userIds, boolean adding, String operator)
    {
        SysDept classroom = lockClass(id, adding);
        SysUser filter = rosterFilter(classroom, !adding, null, null, null);
        filter.getParams().put("ids", userIds);
        List<SysUser> students = SpringUtils.getAopProxy(this).roster(filter);
        if (students.size() != userIds.size())
            throw new ServiceException(adding ? "部分学生已入班或不在可添加范围内" : "部分学生已离班或不在可管理范围内");
        return updateMembership(classroom, students, adding, operator);
    }

    @Transactional(rollbackFor = Exception.class)
    public List<Long> importRoster(Long id, List<String> accounts, String operator)
    {
        SysDept classroom = lockClass(id, true);
        SysUser filter = rosterFilter(classroom, false, null, null, null);
        filter.getParams().put("accounts", accounts);
        List<SysUser> students = SpringUtils.getAopProxy(this).roster(filter);
        SysUser existingFilter = rosterFilter(classroom, true, null, null, null);
        existingFilter.getParams().put("accounts", accounts);
        int existing = SpringUtils.getAopProxy(this).roster(existingFilter).size();
        if (students.size() + existing != accounts.size())
            throw new ServiceException("部分学号不存在、不在可管理范围内、账号已停用或已归属其他班级");
        return updateMembership(classroom, students, true, operator);
    }

    private SysDept lockClass(Long id, boolean adding)
    {
        departmentService.checkDeptDataScope(id);
        SysDept classroom = requireClass(departments.lockDepartment(id));
        if (adding && !"0".equals(classroom.getStatus()))
            throw new ServiceException("班级已停用，无法添加学生");
        return classroom;
    }

    private List<Long> updateMembership(SysDept classroom, List<SysUser> students, boolean adding, String operator)
    {
        Long destination = adding ? classroom.getDeptId() : classroom.getParentId();
        departmentService.checkDeptDataScope(destination);
        if (!adding && departments.lockDepartment(destination) == null)
            throw new ServiceException("班级的上级组织不存在，无法移除学生");
        for (SysUser student : students)
        {
            if (users.moveAdministrativeStudent(student.getUserId(), student.getDeptId(), destination, adding, operator) != 1)
                throw new ServiceException("学生归属已变化，请刷新名单后重试");
        }
        return students.stream().map(SysUser::getUserId).toList();
    }

    private static SysUser rosterFilter(SysDept classroom, boolean members, String keyword, Long orgId, Integer grade)
    {
        SysUser filter = new SysUser();
        filter.setUserName(keyword);
        filter.setDeptId(orgId);
        filter.getParams().put("classId", classroom.getDeptId());
        filter.getParams().put("members", members);
        filter.getParams().put("schoolId", schoolFrom(classroom));
        filter.getParams().put("grade", grade);
        return filter;
    }

    private static SysDept requireClass(SysDept classroom)
    {
        if (classroom == null || !"0".equals(classroom.getDelFlag()) || !"CLASS".equals(classroom.getDeptType()))
            throw new ServiceException("行政班不存在或已删除", 404);
        return classroom;
    }

    private static String schoolFrom(SysDept classroom)
    {
        String[] ancestors = classroom.getAncestors().split(",");
        if (ancestors.length < 2) throw new ServiceException("班级未关联有效的学校");
        return ancestors[1];
    }

    static Integer displayGrade(SysDept classroom)
    {
        if (classroom.getGradeYear() != null) return classroom.getGradeYear();
        var match = GRADE_PREFIX.matcher(classroom.getDeptName());
        if (!match.find()) return null;
        int year = Integer.parseInt(match.group(1));
        return year >= 1900 && year <= 2100 ? year : null;
    }

    static void validateMetadata(SysDept classroom)
    {
        Integer grade = classroom.getGradeYear();
        if (grade != null && (grade < 1900 || grade > 2100)) throw new ServiceException("所属年级须介于1900至2100年");
        String level = classroom.getEducationLevel();
        if (level != null && !LEVELS.contains(level)) throw new ServiceException("请选择有效的教育层次");
        Integer years = classroom.getStudyYears();
        if (years != null && (years < 1 || years > 10)) throw new ServiceException("学制须介于1至10年");
        if (classroom.getClassRemark() != null && classroom.getClassRemark().length() > 500)
            throw new ServiceException("备注不能超过500个字符");
    }
}
