package com.ruoyi.education.course.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.dto.StudentAnnouncementResponseMapper;
import com.ruoyi.education.course.dto.TeacherAnnouncementRequestParser;
import com.ruoyi.education.course.service.CourseAnnouncementPage;
import com.ruoyi.education.course.service.StudentAnnouncementService;

/**
 * 学生公告只读 HTTP 接口。学生身份和成员资格由 Service 校验。
 */
@RestController
@RequestMapping("/education/student/announcements")
public class StudentAnnouncementController extends BaseController
{
    private final StudentAnnouncementService announcementService;

    public StudentAnnouncementController(StudentAnnouncementService announcementService)
    {
        this.announcementService = announcementService;
    }

    @PreAuthorize("@ss.hasRole('student') and @ss.lacksRole('admin') and @ss.lacksRole('teacher')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) String bindingId,
            @RequestParam(required = false) String pageNum,
            @RequestParam(required = false) String pageSize)
    {
        Long parsedBindingId = bindingId == null
                ? null : TeacherAnnouncementRequestParser.requiredId(bindingId, "bindingId");
        int parsedPageNum = TeacherAnnouncementRequestParser.pageNumber(
                pageNum, "pageNum", 1, 1, Integer.MAX_VALUE);
        int parsedPageSize = TeacherAnnouncementRequestParser.pageNumber(
                pageSize, "pageSize", 10, 1, 100);

        CourseAnnouncementPage page = announcementService.list(
                parsedBindingId, parsedPageNum, parsedPageSize);
        TableDataInfo result = new TableDataInfo();
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        result.setTotal(page.total());
        result.setRows(page.rows().stream()
                .map(StudentAnnouncementResponseMapper::toListItem)
                .toList());
        return result;
    }

    @PreAuthorize("@ss.hasRole('student') and @ss.lacksRole('admin') and @ss.lacksRole('teacher')")
    @GetMapping("/{announcementId}")
    public AjaxResult get(@PathVariable String announcementId)
    {
        Long id = TeacherAnnouncementRequestParser.requiredId(announcementId, "announcementId");
        CourseAnnouncement announcement = announcementService.get(id);
        return success(StudentAnnouncementResponseMapper.toDetail(announcement));
    }
}
