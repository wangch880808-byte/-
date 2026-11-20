package com.ruoyi.web.controller.education;

import java.io.IOException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.page.TableDataInfo;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ForbiddenException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.education.security.EduActor;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.domain.AdministrativeClassRosterRow;
import com.ruoyi.system.service.SysAdministrativeClassService;
import com.ruoyi.web.core.util.ExcelImportSupport;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/education/administrative-class")
public class AdministrativeClassController extends BaseController
{
    private final SysAdministrativeClassService classes;
    private final TokenService tokens;

    public AdministrativeClassController(SysAdministrativeClassService classes, TokenService tokens)
    {
        this.classes = classes;
        this.tokens = tokens;
    }

    @GetMapping("/list")
    @PreAuthorize("@ss.hasPermi('education:class:list')")
    public TableDataInfo list(Long orgId, String keyword, String status)
    {
        requireAdmin();
        SysDept filter = new SysDept();
        filter.setDeptName(keyword);
        filter.setParentId(orgId);
        filter.setStatus(status);
        startPage();
        return getDataTable(classes.list(filter));
    }

    @PutMapping("/{id}")
    @PreAuthorize("@ss.hasPermi('education:class:edit')")
    @Log(title = "行政班信息", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    public AjaxResult update(@PathVariable Long id, @Valid @RequestBody Edit body)
    {
        requireAdmin();
        if (body.name().contains("/")) throw new ServiceException("班级名称不能包含斜杠");
        SysDept changes = new SysDept();
        changes.setDeptName(body.name().trim());
        changes.setParentId(body.parentId());
        changes.setGradeYear(body.gradeYear());
        changes.setEducationLevel(body.educationLevel());
        changes.setStudyYears(body.studyYears());
        changes.setStatus(body.status());
        changes.setClassRemark(body.remark() == null ? "" : body.remark().trim());
        changes.setUpdateBy(getUsername());
        classes.update(id, changes);
        return success();
    }

    @GetMapping("/{id}/roster")
    @PreAuthorize("@ss.hasPermi('education:class:member')")
    public TableDataInfo roster(@PathVariable Long id, @RequestParam(defaultValue = "false") boolean members,
            String keyword, Long orgId, Integer grade)
    {
        requireAdmin();
        SysUser filter = classes.rosterFilter(id, members, keyword, orgId, grade);
        startPage();
        return getDataTable(classes.roster(filter));
    }

    @PutMapping("/{id}/roster")
    @PreAuthorize("@ss.hasPermi('education:class:member')")
    @Log(title = "行政班成员", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    public AjaxResult move(@PathVariable Long id, @Valid @RequestBody Move body)
    {
        requireAdmin();
        List<Long> affected = classes.move(id, body.userIds(), body.adding(), getUsername());
        affected.forEach(tokens::deleteByUserId);
        return success((body.adding() ? "已添加 " : "已移除 ") + affected.size() + " 名学生");
    }

    @PostMapping("/{id}/roster/export")
    @PreAuthorize("@ss.hasPermi('education:class:member')")
    @Log(title = "行政班花名册", businessType = BusinessType.EXPORT, isSaveResponseData = false)
    public void export(@PathVariable Long id, HttpServletResponse response)
    {
        requireAdmin();
        SysUser filter = classes.rosterFilter(id, true, null, null, null);
        List<AdministrativeClassRosterRow> rows = classes.roster(filter).stream().map(AdministrativeClassRosterRow::new).toList();
        new ExcelUtil<>(AdministrativeClassRosterRow.class).exportExcel(response, rows, "行政班花名册");
    }

    @PostMapping("/{id}/roster/import")
    @PreAuthorize("@ss.hasPermi('education:class:member')")
    @Log(title = "行政班花名册", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    public AjaxResult importRoster(@PathVariable Long id, @RequestParam("file") MultipartFile file)
    {
        requireAdmin();
        ExcelImportSupport.checkFile(file);
        List<String> accounts;
        try (var input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input))
        {
            accounts = readAccounts(workbook);
        }
        catch (IOException | IllegalArgumentException error)
        {
            throw new ServiceException("无法读取 Excel，请使用花名册文件", 400);
        }
        List<Long> affected = classes.importRoster(id, accounts, getUsername());
        affected.forEach(tokens::deleteByUserId);
        return success("已添加 " + affected.size() + " 名学生，跳过 " + (accounts.size() - affected.size()) + " 名已在本班的学生");
    }

    private static List<String> readAccounts(Workbook workbook)
    {
        if (workbook.getNumberOfSheets() == 0) throw new ServiceException("Excel 中没有工作表", 400);
        Sheet sheet = workbook.getSheetAt(0);
        Row header = sheet.getRow(0);
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        if (header == null || !Set.of("学号", "学生学号").contains(formatter.formatCellValue(header.getCell(0)).trim()))
            throw new ServiceException("请将第一列的表头填写为“学号”", 400);
        if (sheet.getLastRowNum() > 10000) throw new ServiceException("请清理文件中的多余空行", 400);
        Set<String> accounts = new LinkedHashSet<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++)
        {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            Cell cell = row.getCell(0);
            if (cell != null && cell.getCellType() == CellType.FORMULA)
                throw new ServiceException("第 " + (i + 1) + " 行的学号是公式，请粘贴为文本", 400);
            String account = formatter.formatCellValue(cell).trim();
            if (account.isEmpty()) continue;
            if (account.length() > 30 || !accounts.add(account))
                throw new ServiceException("第 " + (i + 1) + " 行的学号重复或超长", 400);
            if (accounts.size() > 1000) throw new ServiceException("每次最多导入1000名学生", 400);
        }
        if (accounts.isEmpty()) throw new ServiceException("请填写需要入班的学生学号", 400);
        return List.copyOf(accounts);
    }

    private static void requireAdmin()
    {
        if (!EduActor.admin()) throw new ForbiddenException("只有管理员可以管理行政班");
    }

    public record Edit(@NotBlank @Size(max = 30) String name, @NotNull @Positive Long parentId,
            @Min(1900) @Max(2100) Integer gradeYear, @Size(max = 20) String educationLevel,
            @Min(1) @Max(10) Integer studyYears, @Size(max = 500) String remark,
            @Pattern(regexp = "[01]", message = "班级状态须为开启或停用") String status) {}

    public record Move(boolean adding, @NotEmpty @Size(max = 1000) Set<@NotNull @Positive Long> userIds) {}
}
