package com.ruoyi.education.course.controller;

import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.education.course.domain.NoticeKind;
import com.ruoyi.education.course.dto.StudentNoticeRequestParser;
import com.ruoyi.education.course.dto.StudentNoticeResponseMapper;
import com.ruoyi.education.course.dto.TeacherAnnouncementRequestParser;
import com.ruoyi.education.course.service.StudentNoticePage;
import com.ruoyi.education.course.service.StudentNoticeService;

/**
 * 学生通知只读与已读接口。学生身份、成员范围、可见性和已读幂等由
 * {@link StudentNoticeService} 负责；本类只做 HTTP 参数/响应转换。
 */
@RestController
@RequestMapping("/education/student/notices")
public class StudentNoticeController extends BaseController
{
    private static final String LOG_TITLE = "学生通知";

    private final StudentNoticeService noticeService;

    public StudentNoticeController(StudentNoticeService noticeService)
    {
        this.noticeService = noticeService;
    }

    @PreAuthorize("@ss.hasRole('student') and @ss.lacksRole('admin') and @ss.lacksRole('teacher')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) String bindingId,
            @RequestParam(required = false) String kind,
            @RequestParam(required = false) String unreadOnly,
            @RequestParam(required = false) String pageNum,
            @RequestParam(required = false) String pageSize)
    {
        Long parsedBindingId = StudentNoticeRequestParser.optionalPositiveId(bindingId, "bindingId");
        NoticeKind parsedKind = StudentNoticeRequestParser.optionalKind(kind);
        boolean parsedUnreadOnly = StudentNoticeRequestParser.optionalBoolean(
                unreadOnly, "unreadOnly", false);
        int parsedPageNum = TeacherAnnouncementRequestParser.pageNumber(
                pageNum, "pageNum", 1, 1, Integer.MAX_VALUE);
        int parsedPageSize = TeacherAnnouncementRequestParser.pageNumber(
                pageSize, "pageSize", 10, 1, 100);

        StudentNoticePage page = noticeService.list(
                parsedBindingId, parsedKind, parsedUnreadOnly, parsedPageNum, parsedPageSize);
        TableDataInfo result = new TableDataInfo();
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        result.setTotal(page.total());
        result.setRows(page.rows().stream()
                .map(StudentNoticeResponseMapper::toResponse)
                .toList());
        return result;
    }

    @PreAuthorize("@ss.hasRole('student') and @ss.lacksRole('admin') and @ss.lacksRole('teacher')")
    @GetMapping("/unread-count")
    public AjaxResult unreadCount(@RequestParam(required = false) String bindingId)
    {
        Long parsedBindingId = StudentNoticeRequestParser.optionalPositiveId(bindingId, "bindingId");
        return success(StudentNoticeResponseMapper.toUnreadCount(
                noticeService.unreadCount(parsedBindingId)));
    }

    @PreAuthorize("@ss.hasRole('student') and @ss.lacksRole('admin') and @ss.lacksRole('teacher')")
    @GetMapping("/{noticeId}")
    public AjaxResult get(@PathVariable String noticeId)
    {
        Long id = TeacherAnnouncementRequestParser.requiredId(noticeId, "noticeId");
        return success(StudentNoticeResponseMapper.toResponse(noticeService.get(id)));
    }

    @PreAuthorize("@ss.hasRole('student') and @ss.lacksRole('admin') and @ss.lacksRole('teacher')")
    @Log(title = LOG_TITLE, businessType = BusinessType.UPDATE)
    @PostMapping("/{noticeId}/read")
    public AjaxResult read(@PathVariable String noticeId)
    {
        Long id = TeacherAnnouncementRequestParser.requiredId(noticeId, "noticeId");
        return success(StudentNoticeResponseMapper.toRead(noticeService.markRead(id)));
    }

    @PreAuthorize("@ss.hasRole('student') and @ss.lacksRole('admin') and @ss.lacksRole('teacher')")
    @Log(title = LOG_TITLE, businessType = BusinessType.UPDATE)
    @PutMapping("/read-all")
    public AjaxResult readAll()
    {
        return success(StudentNoticeResponseMapper.toReadAll(noticeService.markAllRead()));
    }
}
