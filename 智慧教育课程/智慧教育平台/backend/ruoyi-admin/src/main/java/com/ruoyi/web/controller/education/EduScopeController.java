package com.ruoyi.web.controller.education;

import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.education.course.api.EduClassBindingRef;
import com.ruoyi.education.course.api.EduClassMembership;
import com.ruoyi.education.course.api.EduClassRef;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.course.api.EduMemberRef;
import com.ruoyi.education.course.api.EduTermRef;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.service.IEduPortalService;
import com.ruoyi.education.mapper.EduPortalMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import com.ruoyi.education.security.EduActor;
import org.springframework.web.bind.annotation.RestController;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 跨组共用只读作用域接口：第 3、4、5、6 组做资源发布、课堂加入、作业目标、公告范围时使用。
 *
 * <p>与 {@code /education/teaching-class/**} 的区别：那边是管理员的「班级管理」写页面，
 * 返回含已退出成员、授权历史等管理字段；这里是给其他业务域消费的最小只读视图，
 * 只返回当前有效数据，且<b>不接受前端传入的 userId 冒充身份</b>。</p>
 *
 * <p>所有接口的「当前用户」都来自服务端登录态。唯一例外是
 * {@code /users/{userId}/classes}，只允许管理员调用，用于管理端只读查看。</p>
 */
@RestController
@RequestMapping("/education/scope")
@org.springframework.security.access.prepost.PreAuthorize("@ss.hasPermi('education:scope:read')")
public class EduScopeController extends BaseController {

    @Autowired
    private EduCourseAccessService courseAccess;
    @Autowired
    private IEduPortalService portalService;
    @Autowired
    private EduPortalMapper portalMapper;

    /** 当前学期；未设置时 data 为 null，前端显示「未设置当前学期」而不是报错。 */
    @GetMapping("/term/current")
    public AjaxResult currentTerm() {
        EduTermRef term = courseAccess.currentTerm();
        return success(term == null ? null : termMap(term));
    }

    /**
     * 当前账号可教、可学的教学班。
     * 同一账号既是教师又是学生时按班级合并，用 teacher / student 两个布尔位表达。
     */
    @GetMapping("/my/classes")
    public AjaxResult myClasses() {
        Long userId = getUserId();
        Map<Long, Map<String, Object>> merged = new LinkedHashMap<>();

        for (Map<String, Object> row : portalMapper.selectTeachingClassByTeacher(userId)) {
            if (!"teacher".equals(EduActor.role()) && !EduActor.admin()) continue;
            Map<String, Object> item = classMap(row, number(row.get("id")));
            item.put("teacher", true);
            item.put("student", false);
            item.put("teacherRole", row.get("teacherRole"));
            item.put("canPublish", truthy(row.get("canPublish")));
            item.put("canGrade", truthy(row.get("canGrade")));
            item.put("canExport", truthy(row.get("canExport")));
            merged.put((Long) item.get("classId"), item);
        }

        for (Map<String, Object> row : portalMapper.selectStudentClasses(userId)) {
            if ((!"student".equals(EduActor.role()) && !EduActor.admin()) || !"ACTIVE".equals(row.get("status")) || !"ACTIVE".equals(row.get("classStatus"))) continue;
            Object classId = number(row.get("classId"));
            if (classId == null) {
                continue;
            }
            Map<String, Object> item = merged.get(classId);
            if (item == null) {
                item = classMap(row, (Long) classId);
                item.put("teacher", false);
                item.put("teacherRole", null);
                item.put("canPublish", false);
                item.put("canGrade", false);
                item.put("canExport", false);
                merged.put((Long) classId, item);
            }
            item.put("student", true);
        }

        return success(new ArrayList<>(merged.values()));
    }

    /** 当前账号在指定班级的身份与权限位；课堂加入校验用。 */
    @GetMapping("/classes/{classId}/me")
    public AjaxResult myMembership(@PathVariable Long classId) {
        EduClassMembership membership = courseAccess.membership(classId, getUserId());
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("classId", membership.classId());
        data.put("teacher", membership.teacher());
        data.put("student", membership.student());
        data.put("teacherRole", membership.teacherRole());
        data.put("canPublish", membership.canPublish());
        data.put("canGrade", membership.canGrade());
        data.put("canExport", membership.canExport());
        data.put("validTo", membership.validTo());
        return success(data);
    }

    /** 有效成员名单（仅管理员或本班教师）。用于发布任务、公告、签到名单快照。 */
    @GetMapping("/classes/{classId}/members")
    public AjaxResult members(@PathVariable Long classId) {
        requireAdminOrClassTeacher(classId);
        List<Map<String, Object>> data = new ArrayList<>();
        for (EduMemberRef member : courseAccess.activeMembers(classId)) {
            Map<String, Object> item = new LinkedHashMap<>();
            item.put("classId", member.classId());
            item.put("memberId", member.memberId());
            item.put("userId", member.userId());
            item.put("userName", member.userName());
            item.put("nickName", member.nickName());
            item.put("joinedAt", member.joinedAt());
            data.add(item);
        }
        return success(data);
    }

    /** 该班当前有效绑定的课程空间；未绑定时 data 为 null。 */
    @GetMapping("/classes/{classId}/binding")
    public AjaxResult binding(@PathVariable Long classId, @RequestParam(required=false) Long courseSpaceId) {
        requireAdminOrClassTeacher(classId);
        EduClassBindingRef binding = courseSpaceId == null ? courseAccess.activeBinding(classId)
            : courseAccess.activeBindings(classId).stream().filter(b -> courseSpaceId.equals(b.courseSpaceId())).findFirst().orElse(null);
        if (binding == null) {
            return success(null);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bindingId", binding.bindingId());
        data.put("classId", binding.classId());
        data.put("courseSpaceId", binding.courseSpaceId());
        data.put("publishedAt", binding.publishedAt());
        return success(data);
    }

    /** 多课程班级的有效绑定列表，避免将教学班误当成课程唯一入口。 */
    @GetMapping("/classes/{classId}/bindings")
    public AjaxResult bindings(@PathVariable Long classId) {
        requireAdminOrClassTeacher(classId);
        return success(courseAccess.activeBindings(classId));
    }

    /** 课程当前绑定的班级与人数（仅管理员或课程负责人）。 */
    @GetMapping("/courses/{courseId}/classes")
    public AjaxResult courseClasses(@PathVariable Long courseId) {
        courseAccess.requireCourseOwned(courseId, getUserId());
        List<Map<String, Object>> rows = portalMapper.selectCourseBindings(courseId);
        return success(rows);
    }

    /** 管理员只读查看某账号的班级；非管理员调用返回 403。 */
    @GetMapping("/users/{userId}/classes")
    public AjaxResult userClasses(@PathVariable Long userId) {
        if (!portalService.isAdmin()) {
            throw new EduForbiddenException("只有管理员可以查看他人班级");
        }
        Set<Long> seen = new LinkedHashSet<>();
        List<Map<String, Object>> data = new ArrayList<>();
        for (EduClassRef ref : courseAccess.classesOfTeacher(userId)) {
            data.add(classMap(ref));
            seen.add(ref.classId());
        }
        for (EduClassRef ref : courseAccess.classesOfStudent(userId)) {
            if (seen.add(ref.classId())) {
                data.add(classMap(ref));
            }
        }
        return success(data);
    }

    /* ================= 内部 ================= */

    private void requireAdminOrClassTeacher(Long classId) {
        if (portalService.isAdmin()) {
            return;
        }
        if (!"teacher".equals(EduActor.role()) || !courseAccess.isActiveTeacher(classId, getUserId())) {
            throw new EduForbiddenException("只有管理员或本班教师可以查看");
        }
    }

    private Map<String, Object> classMap(Map<String, Object> row, Long classId) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("classId", classId);
        item.put("name", row.get("name") != null ? row.get("name") : row.get("className"));
        item.put("code", row.get("code"));
        item.put("termId", number(row.get("termId")));
        item.put("termName", row.get("termName"));
        item.put("organizationId", number(row.get("organizationId")));
        item.put("status", row.get("classStatus") != null ? row.get("classStatus") : row.get("status"));
        item.put("classKind", row.get("classKind"));
        return item;
    }

    private Map<String, Object> classMap(EduClassRef ref) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("classId", ref.classId());
        item.put("name", ref.name());
        item.put("code", ref.code());
        item.put("termId", ref.termId());
        item.put("termName", ref.termName());
        item.put("organizationId", ref.organizationId());
        item.put("status", ref.status());
        item.put("classKind", ref.classKind());
        return item;
    }

    private Map<String, Object> termMap(EduTermRef term) {
        Map<String, Object> item = new LinkedHashMap<>();
        item.put("termId", term.termId());
        item.put("name", term.name());
        item.put("code", term.code());
        item.put("startsOn", term.startsOn());
        item.put("endsOn", term.endsOn());
        item.put("current", term.current());
        return item;
    }

    private static Long number(Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.longValue();
        }
        try {
            return Long.valueOf(String.valueOf(value).trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static boolean truthy(Object value) {
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return value != null && "1".equals(String.valueOf(value).trim());
    }
}
