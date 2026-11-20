package com.ruoyi.system.service;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import jakarta.validation.Validator;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.SysDeptType;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.exception.ForbiddenException;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.system.domain.SysDeptTransferRow;
import com.ruoyi.system.mapper.SysDeptMapper;

@Service
public class SysDeptTransferService
{
    private static final List<String> HEADERS = List.of("组织路径", "负责人", "显示顺序", "组织状态", "联系电话", "邮箱");
    private static final String TYPE_HEADER = "组织类型";
    private static final List<String> CLASS_HEADERS = List.of("所属年级", "教育层次", "学制", "备注");
    private final SysDeptMapper mapper;
    private final SysDeptTransferScope scope;
    private final Validator validator;
    private final SysDeptHierarchy hierarchy;

    public SysDeptTransferService(SysDeptMapper mapper, SysDeptTransferScope scope, Validator validator, SysDeptHierarchy hierarchy)
    {
        this.mapper = mapper;
        this.scope = scope;
        this.validator = validator;
        this.hierarchy = hierarchy;
    }

    public List<SysDeptTransferRow> exportDepartments(SysDept filter)
    {
        List<SysDept> visible = scope.visible(new SysDept());
        Map<String, SysDept> byPath = index(visible);
        Set<Long> selected = ids(scope.visible(filter));
        List<SysDeptTransferRow> result = new ArrayList<>();
        for (Map.Entry<String, SysDept> entry : byPath.entrySet())
        {
            SysDept dept = entry.getValue();
            if (!selected.contains(dept.getDeptId())) continue;
            SysDeptTransferRow row = new SysDeptTransferRow();
            row.setPath(entry.getKey());
            row.setLeader(dept.getLeader());
            row.setOrderNum(String.valueOf(dept.getOrderNum() == null ? 0 : dept.getOrderNum()));
            row.setStatus("0".equals(dept.getStatus()) ? "开启" : "停用");
            row.setPhone(dept.getPhone());
            row.setEmail(dept.getEmail());
            SysDeptType type = SysDeptType.parse(dept.getDeptType());
            row.setDeptType(type == null ? null : type.getLabel());
            if (type == SysDeptType.CLASS)
            {
                Integer year = SysAdministrativeClassService.displayGrade(dept);
                row.setGradeYear(year == null ? null : year.toString());
                row.setEducationLevel(dept.getEducationLevel());
                row.setStudyYears(dept.getStudyYears() == null ? null : dept.getStudyYears().toString());
                row.setClassRemark(dept.getClassRemark());
            }
            result.add(row);
        }
        return result;
    }

    /** 先验证整批和最终上下级状态，再按层级写入；任何写入异常均回滚。 */
    @Transactional(rollbackFor = Exception.class)
    public ImportResult importWorkbook(Workbook workbook, boolean updateSupport, String operator)
    {
        if (!SecurityUtils.hasPermi("system:dept:list") || !SecurityUtils.hasPermi("system:dept:add"))
            throw new ForbiddenException("没有导入组织的权限");
        if (updateSupport && !SecurityUtils.hasPermi("system:dept:edit"))
            throw new ForbiddenException("没有更新已有组织的权限");
        List<Plan> plans = readWorkbook(workbook);
        Map<String, SysDept> existing = canonicalIndex(index(scope.visible(new SysDept())));
        Set<Long> addable = ids(scope.addable(new SysDept()));
        Set<Long> editable = updateSupport ? ids(scope.editable(new SysDept())) : Set.of();
        Map<String, Plan> planned = new LinkedHashMap<>();
        Map<Long, SysDept> replacements = new HashMap<>();
        for (Plan plan : plans)
        {
            String key = key(plan.path);
            if (planned.putIfAbsent(key, plan) != null) throw invalid(plan.line, "文件中组织路径重复");
            SysDept current = existing.get(key);
            if (current != null)
            {
                plan.skip = !updateSupport;
                if (plan.skip) { plan.dept = current; continue; }
                if (!editable.contains(current.getDeptId())) throw invalid(plan.line, "该组织不在可修改范围内");
                plan.dept.setDeptId(current.getDeptId());
                plan.dept.setDeptName(current.getDeptName());
                plan.dept.setParentId(current.getParentId());
                plan.dept.setAncestors(current.getAncestors());
                if (plan.dept.getDeptType() == null) plan.dept.setDeptType(current.getDeptType());
                replacements.put(current.getDeptId(), plan.dept);
            }
            else if (!plan.path.contains("/"))
            {
                plan.dept.setParentId(0L);
            }
        }
        // 父节点可以位于文件的任意行。只有变更过的节点才需要检查写权限。
        for (Plan plan : plans)
        {
            if (plan.skip) continue;
            SysDept parent = resolveParent(plan, planned, existing);
            // 可见范围从学院开始时，仍按真实上级校验层级。
            if (parent == null && plan.dept.getParentId() != null && plan.dept.getParentId() != 0)
                parent = actualDept(plan.dept.getParentId(), replacements);
            validateHierarchy(plan, parent, planned, existing, replacements);
            if (plan.dept.getDeptId() == null)
            {
                if (parent == null && !SecurityUtils.isAdmin())
                    throw invalid(plan.line, "只能在有新增权限的已有组织下创建下级组织");
                if (parent != null && parent.getDeptId() != null && !addable.contains(parent.getDeptId()))
                    throw invalid(plan.line, "上级组织不在可新增下级的范围内");
                if (parent != null && parent.getDeptId() != null && !"0".equals(parent.getStatus()))
                    throw invalid(plan.line, "上级组织已停用，不能新增下级组织");
            }
            if ("0".equals(plan.dept.getStatus()) && parent != null && !"0".equals(parent.getStatus()))
                throw invalid(plan.line, "开启的组织不能放在停用的上级组织下");
            if (parent == null && plan.dept.getParentId() != null && plan.dept.getParentId() != 0
                    && "0".equals(plan.dept.getStatus()))
            {
                throw invalid(plan.line, "实际的上级组织不存在");
            }
            if (plan.dept.getDeptId() != null && "1".equals(plan.dept.getStatus()))
            {
                for (SysDept child : mapper.selectChildrenDeptById(plan.dept.getDeptId()))
                {
                    if ("2".equals(child.getDelFlag())) continue;
                    SysDept finalChild = replacements.getOrDefault(child.getDeptId(), child);
                    if ("0".equals(finalChild.getStatus()))
                        throw invalid(plan.line, "该组织仍有开启的下级，请将下级一并停用后再导入");
                }
            }
        }
        plans.sort(Comparator.comparingInt(plan -> plan.path.split("/", -1).length));
        int added = 0, updated = 0, skipped = 0;
        for (Plan plan : plans)
        {
            if (plan.skip) { skipped++; continue; }
            SysDept dept = plan.dept;
            if (dept.getDeptId() == null)
            {
                SysDept parent = resolveParent(plan, planned, existing);
                dept.setParentId(parent == null ? 0L : parent.getDeptId());
                dept.setAncestors(parent == null ? "0" : parent.getAncestors() + "," + parent.getDeptId());
                if (dept.getAncestors().length() > 50) throw invalid(plan.line, "组织层级超出数据库支持长度，请减少层级");
                // 在数据库实际排序规则下再次查重，避免大小写/重音等差异绕过路径去重。
                if (mapper.checkDeptNameUnique(dept.getDeptName(), dept.getParentId()) != null)
                    throw invalid(plan.line, "同级组织已存在，名称可能有大小写等差异，请按导出文件填写");
                dept.setCreateBy(operator);
                if (mapper.insertDept(dept) != 1 || dept.getDeptId() == null) throw invalid(plan.line, "新增失败，本批次已撤销");
                added++;
            }
            else
            {
                dept.setUpdateBy(operator);
                // 路径不变，不移动组织，也不自动修改不在导入文件中的上级状态。
                if (mapper.updateDept(dept) != 1) throw invalid(plan.line, "更新失败，本批次已撤销");
                updated++;
            }
        }
        return new ImportResult(added, updated, skipped);
    }

    public record ImportResult(int added, int updated, int skipped)
    {
        public String message()
        {
            return "导入完成，新增 " + added + " 个、更新 " + updated + " 个、跳过 " + skipped + " 个已有组织";
        }
    }

    private SysDept resolveParent(Plan plan, Map<String, Plan> plans, Map<String, SysDept> existing)
    {
        int separator = plan.path.lastIndexOf('/');
        if (separator < 0) return null;
        String parentPath = key(plan.path.substring(0, separator));
        Plan plannedParent = plans.get(parentPath);
        SysDept parent = plannedParent == null ? existing.get(parentPath) : plannedParent.dept;
        if (parent == null) throw invalid(plan.line, "找不到上级组织，请按导出的路径填写，或在文件中补齐上级组织");
        return parent;
    }

    private void validateHierarchy(Plan plan, SysDept parent, Map<String, Plan> plans,
            Map<String, SysDept> existing, Map<Long, SysDept> replacements)
    {
        SysDept parentParent = null;
        int split = plan.path.lastIndexOf('/');
        if (split > 0)
        {
            String parentPath = plan.path.substring(0, split);
            int parentSplit = parentPath.lastIndexOf('/');
            if (parentSplit > 0)
            {
                String key = key(parentPath.substring(0, parentSplit));
                Plan planned = plans.get(key);
                parentParent = planned == null ? existing.get(key) : planned.dept;
            }
        }
        if (parentParent == null && parent != null) parentParent = actualDept(parent.getParentId(), replacements);
        try
        {
            hierarchy.validatePlacement(plan.dept, parent != null, SysDeptType.effectiveType(parent, parentParent));
            if (plan.dept.getGradeYear() != null || plan.dept.getEducationLevel() != null
                    || plan.dept.getStudyYears() != null || plan.dept.getClassRemark() != null)
            {
                if (SysDeptType.effectiveType(plan.dept, parent) != SysDeptType.CLASS)
                    throw new ServiceException("年级、教育层次、学制和备注只适用于班级行");
                SysAdministrativeClassService.validateMetadata(plan.dept);
            }
            if (plan.dept.getDeptId() != null)
            {
                SysDept current = existing.get(key(plan.path));
                SysDeptType before = SysDeptType.effectiveType(current, actualDept(current.getParentId(), Map.of()));
                SysDeptType after = SysDeptType.effectiveType(plan.dept, parent);
                hierarchy.validateTypeChange(current, before, plan.dept, after,
                        child -> replacements.getOrDefault(child.getDeptId(), child));
            }
        }
        catch (ServiceException error)
        {
            throw invalid(plan.line, error.getMessage());
        }
    }

    private SysDept actualDept(Long id, Map<Long, SysDept> replacements)
    {
        if (id == null || id == 0L) return null;
        SysDept planned = replacements.get(id);
        if (planned != null) return planned;
        SysDept actual = mapper.selectDeptById(id);
        return actual == null || "2".equals(actual.getDelFlag()) ? null : actual;
    }

    private List<Plan> readWorkbook(Workbook workbook)
    {
        if (workbook.getNumberOfSheets() == 0) throw invalid(0, "Excel 中没有工作表");
        Sheet sheet = workbook.getSheetAt(0);
        Row header = sheet.getRow(0);
        if (header == null) throw invalid(1, "缺少模板表头");
        if (sheet.getLastRowNum() > 10000) throw invalid(0, "工作表超过10000行，请清理多余空行；每次最多导入1000个组织");
        DataFormatter formatter = new DataFormatter(Locale.ROOT);
        Map<String, Integer> columns = new HashMap<>();
        for (Cell cell : header)
        {
            String name = formatter.formatCellValue(cell).trim();
            if ((HEADERS.contains(name) || TYPE_HEADER.equals(name) || CLASS_HEADERS.contains(name))
                    && columns.putIfAbsent(name, cell.getColumnIndex()) != null)
                throw invalid(1, "存在重复表头，请使用组织导入模板");
        }
        if (!columns.keySet().containsAll(HEADERS)) throw invalid(1, "请使用组织导入模板并保留全部六列表头");
        List<Plan> result = new ArrayList<>();
        for (int i = 1; i <= sheet.getLastRowNum(); i++)
        {
            Row row = sheet.getRow(i);
            if (row == null) continue;
            List<String> values = new ArrayList<>();
            for (String name : HEADERS)
            {
                values.add(readCell(row, columns.get(name), formatter));
            }
            String type = readCell(row, columns.get(TYPE_HEADER), formatter);
            List<String> classValues = CLASS_HEADERS.stream().map(name -> readCell(row, columns.get(name), formatter)).toList();
            if (type.isEmpty() && values.stream().allMatch(String::isEmpty) && classValues.stream().allMatch(String::isEmpty))
                continue;
            if (result.size() >= 1000) throw invalid(i + 1, "每次最多导入1000个组织");
            String[] parts = values.get(0).split("/", -1);
            for (int p = 0; p < parts.length; p++)
            {
                parts[p] = parts[p].trim();
                if (parts[p].isEmpty() || parts[p].length() > 30)
                    throw invalid(i + 1, "组织路径的每一级名称须为1-30个字符，不可有连续或首尾的斜杠");
            }
            if (parts.length > 25) throw invalid(i + 1, "组织路径层级过多");
            SysDept dept = new SysDept();
            dept.setDeptName(parts[parts.length - 1]);
            try
            {
                SysDeptType parsed = SysDeptType.parse(type);
                dept.setDeptType(parsed == null ? null : parsed.name());
            }
            catch (ServiceException error)
            {
                throw invalid(i + 1, error.getMessage());
            }
            dept.setLeader(values.get(1));
            if (dept.getLeader().length() > 20) throw invalid(i + 1, "负责人不能超过20个字符");
            String sort = values.get(2);
            try
            {
                if (!sort.isEmpty() && !sort.matches("[0-9]+")) throw new NumberFormatException();
                dept.setOrderNum(sort.isEmpty() ? 0 : Integer.parseInt(sort));
            }
            catch (NumberFormatException error) { throw invalid(i + 1, "显示顺序须为0至2147483647之间的整数"); }
            dept.setStatus(switch (values.get(3))
            {
                case "", "0", "开启", "正常" -> "0";
                case "1", "停用" -> "1";
                default -> throw invalid(i + 1, "组织状态请填写开启或停用");
            });
            dept.setPhone(values.get(4));
            dept.setEmail(values.get(5));
            dept.setGradeYear(number(classValues.get(0), "级", "所属年级", i + 1));
            dept.setEducationLevel(optionalText(classValues.get(1)));
            dept.setStudyYears(number(classValues.get(2), "年", "学制", i + 1));
            dept.setClassRemark(optionalText(classValues.get(3)));
            Set<String> errors = validator.validate(dept).stream().map(v -> v.getMessage()).collect(Collectors.toSet());
            if (!errors.isEmpty()) throw invalid(i + 1, String.join("；", errors));
            result.add(new Plan(i + 1, String.join("/", parts), dept));
        }
        if (result.isEmpty()) throw invalid(0, "请在模板中填写要导入的组织");
        return result;
    }

    private static Integer number(String value, String suffix, String label, int line)
    {
        if (value.isEmpty()) return null;
        try
        {
            String digits = value.endsWith(suffix) ? value.substring(0, value.length() - 1).trim() : value;
            return Integer.valueOf(digits);
        }
        catch (NumberFormatException error)
        {
            throw invalid(line, label + "请填写整数，可以带“" + suffix + "”");
        }
    }

    private static String optionalText(String value)
    {
        return value.isEmpty() ? null : value;
    }

    private String readCell(Row row, Integer column, DataFormatter formatter)
    {
        if (column == null) return "";
        Cell cell = row.getCell(column);
        if (cell != null && cell.getCellType() == CellType.FORMULA)
            throw invalid(row.getRowNum() + 1, "不支持公式，请将公式粘贴为文本或数值");
        return formatter.formatCellValue(cell).trim();
    }

    private Map<String, SysDept> index(List<SysDept> departments)
    {
        Map<Long, SysDept> byId = departments.stream().collect(Collectors.toMap(SysDept::getDeptId, d -> d));
        Map<String, SysDept> result = new LinkedHashMap<>();
        Set<String> names = new HashSet<>();
        for (SysDept dept : departments)
        {
            List<String> parts = new ArrayList<>();
            Set<Long> visited = new HashSet<>();
            SysDept node = dept;
            while (node != null)
            {
                if (!visited.add(node.getDeptId())) throw invalid(0, "现有组织上下级关系存在循环，请先修复组织结构");
                if (node.getDeptName() == null || node.getDeptName().contains("/") || node.getDeptName().trim().isEmpty())
                    throw invalid(0, "现有组织名称为空或包含斜杠，请先修改名称后再导入导出");
                parts.add(0, node.getDeptName().trim());
                node = byId.get(node.getParentId());
            }
            String path = String.join("/", parts);
            if (!names.add(key(path))) throw invalid(0, "可见范围内存在相同组织路径，请先调整重复名称或由上级管理员操作");
            result.put(path, dept);
        }
        return result;
    }

    private Map<String, SysDept> canonicalIndex(Map<String, SysDept> source)
    {
        Map<String, SysDept> result = new HashMap<>();
        source.forEach((path, dept) -> result.put(key(path), dept));
        return result;
    }

    private static Set<Long> ids(List<SysDept> departments)
    { return departments.stream().map(SysDept::getDeptId).collect(Collectors.toSet()); }
    private static String key(String path) { return path.toLowerCase(Locale.ROOT); }
    private static ServiceException invalid(int line, String message)
    { return new ServiceException((line > 0 ? "第 " + line + " 行：" : "") + message, 400); }

    private static final class Plan
    {
        final int line;
        final String path;
        SysDept dept;
        boolean skip;
        Plan(int line, String path, SysDept dept) { this.line = line; this.path = path; this.dept = dept; }
    }
}
