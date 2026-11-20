package com.ruoyi.web.controller.system;

import java.io.InputStream;
import java.util.List;
import java.util.Map;
import java.util.Set;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.poi.ExcelUtil;
import com.ruoyi.education.service.IEduPortalService;
import com.ruoyi.system.domain.SysDeptTransferRow;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.ISysDeptService.BranchReferences;
import com.ruoyi.system.service.SysDeptTransferService;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.web.core.util.ExcelImportSupport;

/**
 * 部门信息
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/dept")
public class SysDeptController extends BaseController
{
    @Autowired
    private ISysDeptService deptService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private SysDeptTransferService transferService;

    @Autowired
    private IEduPortalService educationService;

    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    @Log(title = "组织导出", businessType = BusinessType.EXPORT)
    @PostMapping("/export")
    public void export(HttpServletResponse response, SysDept filter)
    {
        new ExcelUtil<>(SysDeptTransferRow.class).exportExcel(response,
                transferService.exportDepartments(filter), "组织数据");
    }

    @PreAuthorize("@ss.hasPermi('system:dept:list') and @ss.hasPermi('system:dept:add')")
    @PostMapping("/importTemplate")
    public void importTemplate(HttpServletResponse response)
    {
        new ExcelUtil<>(SysDeptTransferRow.class).importTemplateExcel(response, "组织导入模板");
    }

    @PreAuthorize("@ss.hasPermi('system:dept:list') and @ss.hasPermi('system:dept:add')")
    @Log(title = "组织导入", businessType = BusinessType.IMPORT, isSaveRequestData = false)
    @PostMapping("/importData")
    public AjaxResult importData(MultipartFile file, boolean updateSupport) throws Exception
    {
        ExcelImportSupport.checkFile(file);
        SysDeptTransferService.ImportResult result;
        try (InputStream input = file.getInputStream(); Workbook workbook = openImportWorkbook(input))
        {
            result = transferService.importWorkbook(workbook, updateSupport, getUsername());
        }
        if (result.updated() > 0) tokenService.invalidateNonAdminSessions();
        return success(result.message());
    }

    private Workbook openImportWorkbook(InputStream input)
    {
        try { return WorkbookFactory.create(input); }
        catch (Exception error) { throw new ServiceException("无法读取Excel，请使用未加密的有效.xls或.xlsx文件", 400); }
    }

    /**
     * 获取部门列表
     */
    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    @GetMapping("/list")
    public AjaxResult list(SysDept dept)
    {
        List<SysDept> depts = deptService.selectDeptList(dept);
        return success(depts);
    }

    /**
     * 查询部门列表（排除节点）
     */
    @PreAuthorize("@ss.hasPermi('system:dept:list')")
    @GetMapping("/list/exclude/{deptId}")
    public AjaxResult excludeChild(@PathVariable(value = "deptId", required = false) Long deptId)
    {
        List<SysDept> depts = deptService.selectDeptList(new SysDept());
        depts.removeIf(d -> d.getDeptId().intValue() == deptId || ArrayUtils.contains(StringUtils.split(d.getAncestors(), ","), deptId + ""));
        return success(depts);
    }

    /**
     * 根据部门编号获取详细信息
     */
    @PreAuthorize("@ss.hasPermi('system:dept:query')")
    @GetMapping(value = "/{deptId}")
    public AjaxResult getInfo(@PathVariable Long deptId)
    {
        deptService.checkDeptDataScope(deptId);
        return success(deptService.selectDeptById(deptId));
    }

    /**
     * 新增部门
     */
    @PreAuthorize("@ss.hasPermi('system:dept:add')")
    @Log(title = "部门管理", businessType = BusinessType.INSERT)
    @PostMapping
    public AjaxResult add(@Validated @RequestBody SysDept dept)
    {
        if (dept.getParentId() == null) return error("请选择上级组织");
        dept.setDeptId(null);
        if (!deptService.checkDeptNameUnique(dept))
        {
            return error("新增部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        dept.setCreateBy(getUsername());
        return toAjax(deptService.insertDept(dept));
    }

    /**
     * 修改部门
     */
    @PreAuthorize("@ss.hasPermi('system:dept:edit')")
    @Log(title = "部门管理", businessType = BusinessType.UPDATE)
    @PutMapping
    public AjaxResult edit(@Validated @RequestBody SysDept dept)
    {
        Long deptId = dept.getDeptId();
        if (deptId == null || dept.getParentId() == null) return error("组织编号和上级组织不能为空");
        deptService.checkDeptDataScope(deptId);
        if (!deptService.checkDeptNameUnique(dept))
        {
            return error("修改部门'" + dept.getDeptName() + "'失败，部门名称已存在");
        }
        else if (dept.getParentId().equals(deptId))
        {
            return error("修改部门'" + dept.getDeptName() + "'失败，上级部门不能是自己");
        }
        else if (StringUtils.equals(UserConstants.DEPT_DISABLE, dept.getStatus()) && deptService.selectNormalChildrenDeptById(deptId) > 0)
        {
            return error("该部门包含未停用的子部门！");
        }
        dept.setUpdateBy(getUsername());
        int rows = deptService.updateDept(dept);
        if (rows > 0) tokenService.invalidateNonAdminSessions();
        return toAjax(rows);
    }

    /**
     * 保存部门排序
     */
    @PreAuthorize("@ss.hasPermi('system:dept:edit')")
    @Log(title = "保存部门排序", businessType = BusinessType.UPDATE)
    @PutMapping("/updateSort")
    public AjaxResult updateSort(@RequestBody Map<String, String> params)
    {
        String[] deptIds = params.get("deptIds").split(",");
        String[] orderNums = params.get("orderNums").split(",");
        deptService.updateDeptSort(deptIds, orderNums);
        return success();
    }

    @PreAuthorize("@ss.hasPermi('system:dept:remove')")
    @GetMapping("/{deptId}/delete-preview")
    public AjaxResult removalPreview(@PathVariable Long deptId)
    {
        List<Long> ids = deptService.getDeptBranchForRemoval(deptId, false);
        return success(removalImpact(ids, false));
    }

    /** 逻辑删除确认过的整条组织分支。 */
    @PreAuthorize("@ss.hasPermi('system:dept:remove')")
    @Log(title = "删除组织分支", businessType = BusinessType.DELETE)
    @DeleteMapping("/{deptId}")
    @Transactional(rollbackFor = Exception.class)
    public AjaxResult remove(@PathVariable Long deptId, @RequestParam(required = false) String expectedScope,
            @RequestParam(defaultValue = "false") boolean force)
    {
        List<Long> ids = deptService.getDeptBranchForRemoval(deptId, true);
        RemovalImpact impact = removalImpact(ids, true);
        if ((expectedScope != null && !expectedScope.equals(impact.scope())) || (force && expectedScope == null))
        {
            throw new ServiceException("删除范围或关联已变化，请关闭窗口后重新确认", 409);
        }
        if (impact.forceRequired() && !force)
        {
            throw new ServiceException("该组织存在下级或关联数据，请查看影响后确认强制删除", 409);
        }
        int rows = deptService.deleteDeptBranch(ids, force, getUsername());
        if (impact.userCount() > 0)
        {
            Set<Long> removedOrganizations = Set.copyOf(ids);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization()
            {
                @Override
                public void afterCommit()
                {
                    try
                    {
                        tokenService.invalidateOrganizationSessions(removedOrganizations);
                    }
                    catch (RuntimeException error)
                    {
                        logger.warn("组织已删除，刷新相关在线账号失败", error);
                    }
                }
            });
        }
        return success("已删除 " + rows + " 个组织");
    }

    private RemovalImpact removalImpact(List<Long> ids, boolean lock)
    {
        BranchReferences references = deptService.getBranchReferences(ids, lock);
        boolean teachingRecords = educationService.hasOrganizationReferences(ids);
        String scope = DigestUtils.sha256Hex(ids + "|" + references.userIds() + "|" + references.roleIds() + "|" + teachingRecords);
        boolean forceRequired = ids.size() > 1 || !references.userIds().isEmpty() || !references.roleIds().isEmpty() || teachingRecords;
        return new RemovalImpact(ids.size(), references.userIds().size(), references.roleIds().size(),
                teachingRecords, forceRequired, scope);
    }

    public record RemovalImpact(int count, int userCount, int roleCount, boolean teachingRecords,
            boolean forceRequired, String scope) {}
}
