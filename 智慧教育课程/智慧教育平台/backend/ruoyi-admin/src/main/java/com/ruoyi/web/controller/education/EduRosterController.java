package com.ruoyi.web.controller.education;

import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.service.IEduPortalService;
import com.ruoyi.web.core.util.ExcelImportSupport;
import jakarta.servlet.http.HttpServletResponse;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/education/teaching-class/{id}/members")
@PreAuthorize("@ss.hasPermi('education:scope:read')")
public class EduRosterController {
    private final IEduPortalService portal;

    public EduRosterController(IEduPortalService portal) {
        this.portal = portal;
    }

    @GetMapping("/transfer-permissions")
    public AjaxResult permissions(@PathVariable Long id) {
        return AjaxResult.success(portal.rosterPermissions(id));
    }

    @PostMapping("/template")
    public void template(@PathVariable Long id, HttpServletResponse response) {
        requireImport(id);
        new ExcelUtil<>(EduRosterRow.class).importTemplateExcel(response, "学生名单导入模板");
    }

    @PostMapping("/import")
    @Log(title = "教学班学生名单", businessType = BusinessType.IMPORT, isSaveRequestData = false, isSaveResponseData = false)
    public AjaxResult importRoster(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        requireImport(id);
        ExcelImportSupport.checkFile(file);
        return AjaxResult.success(portal.importRoster(id, readAccounts(file)));
    }

    @PostMapping("/add")
    @Log(title = "教学班学生名单", businessType = BusinessType.INSERT, isSaveRequestData = false)
    public AjaxResult addStudent(@PathVariable Long id, @RequestBody Map<String, String> body) {
        String account = body.getOrDefault("account", "");
        if (account == null || account.isBlank() || account.strip().length() > 30) {
            throw new ServiceException("请输入有效的学生学号或账号");
        }
        return AjaxResult.success(portal.importRoster(id, Map.of(1, account.strip())));
    }

    @PostMapping("/export")
    @Log(title = "教学班学生名单", businessType = BusinessType.EXPORT, isSaveRequestData = false)
    public void export(@PathVariable Long id, HttpServletResponse response) {
        List<EduRosterRow> rows = portal.exportRoster(id).stream().map(row -> new EduRosterRow(
                text(row, "studentNo"), text(row, "studentName"), text(row, "joinedAt"))).toList();
        new ExcelUtil<>(EduRosterRow.class).exportExcel(response, rows, "学生名单");
    }

    private void requireImport(Long id) {
        if (!Boolean.TRUE.equals(portal.rosterPermissions(id).get("canImport"))) {
            throw new EduForbiddenException("仅开放教学班的主讲教师可以导入名单");
        }
    }

    private static String text(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? "" : value.toString();
    }

    private static Map<Integer, String> readAccounts(MultipartFile file) {
        try (InputStream input = file.getInputStream(); Workbook workbook = WorkbookFactory.create(input)) {
            if (workbook.getNumberOfSheets() == 0) throw new ServiceException("Excel 文件没有工作表");
            Sheet sheet = workbook.getSheetAt(0);
            DataFormatter formatter = new DataFormatter();
            Row header = sheet.getRow(0);
            if (header == null || !"学号/账号".equals(formatter.formatCellValue(header.getCell(0)).strip())) {
                throw new ServiceException("请使用模板，第一列标题应为“学号/账号”");
            }
            if (sheet.getLastRowNum() > 1000) throw new ServiceException("每批最多导入 1000 条记录");

            Map<Integer, String> accounts = new LinkedHashMap<>();
            for (int index = 1; index <= sheet.getLastRowNum(); index++) {
                Row row = sheet.getRow(index);
                Cell cell = row == null ? null : row.getCell(0);
                String value = formatter.formatCellValue(cell).strip();
                if (value.isEmpty()) continue;
                if (cell.getCellType() == CellType.FORMULA || cell.getCellType() == CellType.ERROR || value.length() > 30) {
                    throw new ServiceException("第 " + (index + 1) + " 行账号格式无效，请填写账号文本");
                }
                accounts.put(index + 1, value);
            }
            if (accounts.isEmpty()) throw new ServiceException("请在模板中至少填写一个学生账号");
            return accounts;
        } catch (ServiceException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new ServiceException("Excel 文件无法读取，请使用下载的导入模板");
        }
    }
}
