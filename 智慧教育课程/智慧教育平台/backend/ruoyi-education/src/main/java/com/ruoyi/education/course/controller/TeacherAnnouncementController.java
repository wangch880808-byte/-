package com.ruoyi.education.course.controller;

import java.time.Clock;
import java.time.LocalDateTime;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.education.course.domain.AnnouncementStatus;
import com.ruoyi.education.course.domain.CourseAnnouncement;
import com.ruoyi.education.course.dto.TeacherAnnouncementCreateRequest;
import com.ruoyi.education.course.dto.TeacherAnnouncementDetailResponse;
import com.ruoyi.education.course.dto.TeacherAnnouncementRequestParser;
import com.ruoyi.education.course.dto.TeacherAnnouncementResponseMapper;
import com.ruoyi.education.course.dto.TeacherAnnouncementUpdateRequest;
import com.ruoyi.education.course.exception.AnnouncementValidationException;
import com.ruoyi.education.course.service.CourseAnnouncementPage;
import com.ruoyi.education.course.service.CourseAnnouncementService;
import com.ruoyi.education.course.support.DatabaseTimePrecision;

/**
 * 教师公告 HTTP 接口。
 *
 * <p>身份、绑定、草稿权限、状态机、幂等、正文和时间规则由
 * {@link CourseAnnouncementService} 负责；本类只做 HTTP 参数/响应转换。</p>
 */
@RestController
@RequestMapping("/education/teacher/announcements")
public class TeacherAnnouncementController extends BaseController
{
    private static final String LOG_TITLE = "教师公告";

    private final CourseAnnouncementService announcementService;

    private final Clock clock;

    @Autowired
    public TeacherAnnouncementController(CourseAnnouncementService announcementService)
    {
        this(announcementService, Clock.systemUTC());
    }

    TeacherAnnouncementController(CourseAnnouncementService announcementService, Clock clock)
    {
        this.announcementService = announcementService;
        this.clock = clock;
    }

    @PreAuthorize("@ss.hasPermi('education:announcement:add')")
    @Log(title = LOG_TITLE, businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult create(@RequestBody(required = false) TeacherAnnouncementCreateRequest request)
    {
        if (request == null)
        {
            throw new AnnouncementValidationException("请求体不能为空");
        }
        Long bindingId = TeacherAnnouncementRequestParser.requiredId(request.bindingId(), "bindingId");
        LocalDateTime opensAt = TeacherAnnouncementRequestParser.optionalTime(request.opensAt(), "opensAt");
        LocalDateTime closesAt = TeacherAnnouncementRequestParser.optionalTime(request.closesAt(), "closesAt");
        CourseAnnouncement created = announcementService.createDraft(
                bindingId, request.title(), request.body(), opensAt, closesAt);
        return success(detail(created));
    }

    @PreAuthorize("@ss.hasPermi('education:announcement:edit')")
    @Log(title = LOG_TITLE, businessType = BusinessType.UPDATE)
    @PutMapping("/{announcementId}")
    public AjaxResult update(@PathVariable String announcementId,
            @RequestBody(required = false) TeacherAnnouncementUpdateRequest request)
    {
        if (request == null)
        {
            throw new AnnouncementValidationException("请求体不能为空");
        }
        Long id = TeacherAnnouncementRequestParser.requiredId(announcementId, "announcementId");
        LocalDateTime opensAt = TeacherAnnouncementRequestParser.optionalTime(request.opensAt(), "opensAt");
        LocalDateTime closesAt = TeacherAnnouncementRequestParser.optionalTime(request.closesAt(), "closesAt");
        CourseAnnouncement updated = announcementService.updateDraft(
                id, request.title(), request.body(), opensAt, closesAt);
        return success(detail(updated));
    }

    @PreAuthorize("@ss.hasPermi('education:announcement:publish')")
    @Log(title = LOG_TITLE, businessType = BusinessType.UPDATE)
    @PostMapping("/{announcementId}/publish")
    public AjaxResult publish(@PathVariable String announcementId)
    {
        Long id = TeacherAnnouncementRequestParser.requiredId(announcementId, "announcementId");
        return success(detail(announcementService.publish(id)));
    }

    @PreAuthorize("@ss.hasPermi('education:announcement:archive')")
    @Log(title = LOG_TITLE, businessType = BusinessType.UPDATE)
    @PostMapping("/{announcementId}/archive")
    public AjaxResult archive(@PathVariable String announcementId)
    {
        Long id = TeacherAnnouncementRequestParser.requiredId(announcementId, "announcementId");
        return success(detail(announcementService.archive(id)));
    }

    @PreAuthorize("@ss.hasPermi('education:announcement:list')")
    @GetMapping
    public TableDataInfo list(@RequestParam(required = false) String bindingId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String pageNum,
            @RequestParam(required = false) String pageSize)
    {
        Long parsedBindingId = TeacherAnnouncementRequestParser.requiredId(bindingId, "bindingId");
        AnnouncementStatus parsedStatus = TeacherAnnouncementRequestParser.optionalStatus(status);
        int parsedPageNum = TeacherAnnouncementRequestParser.pageNumber(
                pageNum, "pageNum", 1, 1, Integer.MAX_VALUE);
        int parsedPageSize = TeacherAnnouncementRequestParser.pageNumber(
                pageSize, "pageSize", 10, 1, 100);

        CourseAnnouncementPage page = announcementService.listTeacherAnnouncementsPage(
                parsedBindingId, parsedStatus, parsedPageNum, parsedPageSize);
        LocalDateTime now = nowForVisibility();
        TableDataInfo result = new TableDataInfo();
        result.setCode(HttpStatus.SUCCESS);
        result.setMsg("查询成功");
        result.setTotal(page.total());
        result.setRows(page.rows().stream()
                .map(announcement -> TeacherAnnouncementResponseMapper.toListItem(announcement, now))
                .toList());
        return result;
    }

    @PreAuthorize("@ss.hasPermi('education:announcement:list')")
    @GetMapping("/{announcementId}")
    public AjaxResult get(@PathVariable String announcementId)
    {
        Long id = TeacherAnnouncementRequestParser.requiredId(announcementId, "announcementId");
        return success(detail(announcementService.getTeacherAnnouncement(id)));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public AjaxResult handleUnreadableBody(HttpMessageNotReadableException e)
    {
        return AjaxResult.error(HttpStatus.BAD_REQUEST, "请求体格式错误");
    }

    private TeacherAnnouncementDetailResponse detail(CourseAnnouncement announcement)
    {
        return TeacherAnnouncementResponseMapper.toDetail(announcement, nowForVisibility());
    }

    private LocalDateTime nowForVisibility()
    {
        return DatabaseTimePrecision.toMillisecondPrecision(LocalDateTime.now(clock));
    }
}
