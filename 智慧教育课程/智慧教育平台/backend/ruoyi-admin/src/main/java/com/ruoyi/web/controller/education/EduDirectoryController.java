package com.ruoyi.web.controller.education;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ForbiddenException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.xss.Xss;
import com.ruoyi.education.service.IEduPortalService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysUserService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 教学基础数据：正式课程、教师、学生、组织架构。
 * 管理员端「教师管理 / 学生管理 / 组织架构管理」和教师端建课下拉使用。
 */
@RestController
public class EduDirectoryController extends BaseController {

    @Autowired
    private IEduPortalService portalService;

    @Autowired
    private ISysUserService userService;

    @Autowired
    private TokenService tokenService;

    @GetMapping("/education/official-course/list")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasAnyPermi('education:course:list,education:class:list')")
    public TableDataInfo officialCourses(String keyword) {
        startPage();
        return getDataTable(portalService.officialCourses(keyword));
    }

    @GetMapping("/education/teacher/list")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public TableDataInfo teachers(String keyword, Long deptId, Long schoolId,
            @RequestParam(defaultValue = "false") boolean unassigned) {
        SysUser filter = directoryFilter(keyword, deptId, schoolId, unassigned);
        startPage();
        return getDataTable(portalService.teacherList(filter));
    }

    @GetMapping("/education/teacher/{userId}/classes")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public TableDataInfo teacherClasses(@PathVariable Long userId) {
        return getDataTable(portalService.teacherClasses(userId));
    }

    @GetMapping("/education/student/list")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public TableDataInfo students(String keyword, Long deptId) {
        startPage();
        return getDataTable(portalService.studentList(keyword, deptId));
    }

    @GetMapping("/education/student/accounts")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public TableDataInfo studentAccounts(String keyword, Long deptId, Long schoolId,
            @RequestParam(defaultValue = "false") boolean unassigned) {
        requireStudentManagement();
        SysUser filter = directoryFilter(keyword, deptId, schoolId, unassigned);
        startPage();
        return getDataTable(userService.selectStudentAccounts(filter));
    }

    private SysUser directoryFilter(String keyword, Long deptId, Long schoolId, boolean unassigned) {
        SysUser filter = new SysUser();
        filter.setUserName(StringUtils.trimToEmpty(keyword));
        filter.setDeptId(deptId);
        filter.getParams().put("schoolId", schoolId);
        filter.getParams().put("unassigned", unassigned);
        return filter;
    }

    @PutMapping("/education/student/accounts/{userId}")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list') and @ss.hasPermi('system:user:edit')")
    @Log(title = "学生管理", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    public AjaxResult editStudentAccount(@PathVariable Long userId, @Valid @RequestBody StudentAccountEdit body) {
        requireStudentManagement();
        SysUser student = new SysUser();
        student.setUserId(userId);
        student.setDeptId(body.deptId());
        student.setNickName(body.nickName().trim());
        student.setPhonenumber(StringUtils.trimToEmpty(body.phonenumber()));
        student.setEmail(StringUtils.trimToEmpty(body.email()));
        student.setSex(body.sex());
        student.setRemark(StringUtils.trimToEmpty(body.remark()));
        student.setUpdateBy(getUsername());
        int rows = userService.updateStudentAccount(student);
        if (rows > 0) tokenService.deleteByUserId(userId);
        return toAjax(rows);
    }

    @PutMapping("/education/student/accounts/{userId}/disable")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list') and @ss.hasPermi('system:user:edit')")
    @Log(title = "停用学生账号", businessType = BusinessType.UPDATE)
    public AjaxResult disableStudentAccount(@PathVariable Long userId) {
        requireStudentManagement();
        int rows = userService.disableStudentAccount(userId, getUsername());
        if (rows > 0) tokenService.deleteByUserId(userId);
        return toAjax(rows);
    }

    private void requireStudentManagement() {
        if (!portalService.isAdmin()) throw new ForbiddenException("没有权限管理学生账号");
    }

    public record StudentAccountEdit(
            Long deptId,
            @NotBlank(message = "请填写姓名") @Size(max = 30, message = "姓名不能超过30个字符")
            @Xss(message = "姓名不能包含脚本字符") String nickName,
            @Pattern(regexp = "(?:1[3-9][0-9]{9})?", message = "手机号格式不正确") String phonenumber,
            @Email(message = "邮箱格式不正确") @Size(max = 50, message = "邮箱不能超过50个字符") String email,
            @NotBlank(message = "请选择性别") @Pattern(regexp = "[012]", message = "性别无效") String sex,
            @Size(max = 500, message = "备注不能超过500个字符") String remark) { }

    @GetMapping("/education/student/{userId}/classes")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:directory:list')")
    public TableDataInfo studentClasses(@PathVariable Long userId) {
        return getDataTable(portalService.studentClasses(userId));
    }

    @GetMapping("/education/organization/tree")
    @org.springframework.security.access.prepost.PreAuthorize("@ss.hasAnyPermi('education:directory:list,education:class:list')")
    public AjaxResult organizationTree(@RequestParam(defaultValue = "false") boolean teacherOnly) {
        return success(portalService.deptTree(teacherOnly));
    }
}
