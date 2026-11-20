package com.ruoyi.system.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.constant.UserConstants;
import com.ruoyi.common.constant.SysDeptType;
import com.ruoyi.common.core.domain.TreeSelect;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.text.Convert;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.common.exception.ForbiddenException;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.spring.SpringUtils;
import com.ruoyi.system.mapper.SysDeptMapper;
import com.ruoyi.system.mapper.SysRoleMapper;
import com.ruoyi.system.service.ISysDeptService;
import com.ruoyi.system.service.SysDeptHierarchy;

/**
 * 部门管理 服务实现
 * 
 * @author ruoyi
 */
@Service
public class SysDeptServiceImpl implements ISysDeptService
{
    @Autowired
    private SysDeptMapper deptMapper;

    @Autowired
    private SysRoleMapper roleMapper;

    @Autowired
    private SysDeptHierarchy hierarchy;

    /**
     * 查询部门管理数据
     * 
     * @param dept 部门信息
     * @return 部门信息集合
     */
    @Override
    @DataScope(deptAlias = "d")
    public List<SysDept> selectDeptList(SysDept dept)
    {
        return deptMapper.selectDeptList(dept);
    }

    /**
     * 查询部门树结构信息
     * 
     * @param dept 部门信息
     * @return 部门树信息集合
     */
    @Override
    public List<TreeSelect> selectDeptTreeList(SysDept dept)
    {
        List<SysDept> depts = SpringUtils.getAopProxy(this).selectDeptList(dept);
        return buildDeptTreeSelect(depts);
    }

    /**
     * 构建前端所需要树结构
     * 
     * @param depts 部门列表
     * @return 树结构列表
     */
    @Override
    public List<SysDept> buildDeptTree(List<SysDept> depts)
    {
        List<SysDept> returnList = new ArrayList<SysDept>();
        List<Long> tempList = depts.stream().map(SysDept::getDeptId).collect(Collectors.toList());
        for (SysDept dept : depts)
        {
            // 如果是顶级节点, 遍历该父节点的所有子节点
            if (!tempList.contains(dept.getParentId()))
            {
                recursionFn(depts, dept);
                returnList.add(dept);
            }
        }
        if (returnList.isEmpty())
        {
            returnList = depts;
        }
        return returnList;
    }

    /**
     * 构建前端所需要下拉树结构
     * 
     * @param depts 部门列表
     * @return 下拉树结构列表
     */
    @Override
    public List<TreeSelect> buildDeptTreeSelect(List<SysDept> depts)
    {
        List<SysDept> deptTrees = buildDeptTree(depts);
        return deptTrees.stream().map(TreeSelect::new).collect(Collectors.toList());
    }

    /**
     * 根据角色ID查询部门树信息
     * 
     * @param roleId 角色ID
     * @return 选中部门列表
     */
    @Override
    public List<Long> selectDeptListByRoleId(Long roleId)
    {
        SysRole role = roleMapper.selectRoleById(roleId);
        return deptMapper.selectDeptListByRoleId(roleId, role.isDeptCheckStrictly());
    }

    /**
     * 根据部门ID查询信息
     * 
     * @param deptId 部门ID
     * @return 部门信息
     */
    @Override
    public SysDept selectDeptById(Long deptId)
    {
        return deptMapper.selectDeptById(deptId);
    }

    /**
     * 根据ID查询所有子部门（正常状态）
     * 
     * @param deptId 部门ID
     * @return 子部门数
     */
    @Override
    public int selectNormalChildrenDeptById(Long deptId)
    {
        return deptMapper.selectNormalChildrenDeptById(deptId);
    }

    /**
     * 校验部门名称是否唯一
     * 
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public boolean checkDeptNameUnique(SysDept dept)
    {
        Long deptId = StringUtils.isNull(dept.getDeptId()) ? -1L : dept.getDeptId();
        SysDept info = deptMapper.checkDeptNameUnique(dept.getDeptName(), dept.getParentId());
        if (StringUtils.isNotNull(info) && info.getDeptId().longValue() != deptId.longValue())
        {
            return UserConstants.NOT_UNIQUE;
        }
        return UserConstants.UNIQUE;
    }

    /**
     * 校验部门是否有数据权限
     * 
     * @param deptId 部门id
     */
    @Override
    public void checkDeptDataScope(Long deptId)
    {
        if (!SecurityUtils.isAdmin() && StringUtils.isNotNull(deptId))
        {
            SysDept dept = new SysDept();
            dept.setDeptId(deptId);
            List<SysDept> depts = SpringUtils.getAopProxy(this).selectDeptList(dept);
            if (StringUtils.isEmpty(depts))
            {
                throw new com.ruoyi.common.exception.ForbiddenException("没有权限访问部门数据！");
            }
        }
    }

    /**
     * 新增保存部门信息
     * 
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    public int insertDept(SysDept dept)
    {
        SysDept parent = resolveWritableParent(dept.getParentId(), true);
        // 如果父节点不为正常状态,则不允许新增子节点
        if (parent != null && !UserConstants.DEPT_NORMAL.equals(parent.getStatus()))
        {
            throw new ServiceException("部门停用，不允许新增");
        }
        normalizeType(dept);
        hierarchy.validatePlacement(dept, parent != null, effectiveType(parent));
        dept.setAncestors(ancestorsOf(parent));
        // 创建时由数据库分配编号。
        dept.setDeptId(null);
        return deptMapper.insertDept(dept);
    }

    /**
     * 修改保存部门信息
     * 
     * @param dept 部门信息
     * @return 结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public int updateDept(SysDept dept)
    {
        SysDept oldDept = findActiveDept(dept.getDeptId());
        if (oldDept == null) throw new ServiceException("组织不存在");
        Long parentId = dept.getParentId() == null ? oldDept.getParentId() : dept.getParentId();
        boolean moved = !Objects.equals(parentId, oldDept.getParentId());
        SysDept parent = resolveWritableParent(parentId, moved);
        if (parent != null && (Objects.equals(parentId, dept.getDeptId())
                || Arrays.asList(parent.getAncestors().split(",")).contains(dept.getDeptId().toString())))
            throw new ServiceException("不能将组织移到自身或其下级");
        dept.setParentId(parentId);
        normalizeType(dept);
        if (dept.getDeptType() == null) dept.setDeptType(oldDept.getDeptType());
        hierarchy.validatePlacement(dept, parent != null, effectiveType(parent));
        hierarchy.validateTypeChange(oldDept, effectiveType(oldDept), dept, SysDeptType.effectiveType(dept, parent), child -> child);
        String newAncestors = ancestorsOf(parent);
        dept.setAncestors(newAncestors);
        if (!Objects.equals(newAncestors, oldDept.getAncestors()))
        {
            updateDeptChildren(dept.getDeptId(), newAncestors, oldDept.getAncestors());
        }
        int result = deptMapper.updateDept(dept);
        if (UserConstants.DEPT_NORMAL.equals(dept.getStatus()) && StringUtils.isNotEmpty(dept.getAncestors())
                && !StringUtils.equals("0", dept.getAncestors()))
        {
            // 如果该部门是启用状态，则启用该部门的所有上级部门
            updateParentDeptStatusNormal(dept);
        }
        return result;
    }

    private SysDept resolveWritableParent(Long id, boolean changing)
    {
        if (id == null) throw new ServiceException("请选择上级组织");
        if (id == 0L)
        {
            if (changing && !SecurityUtils.isAdmin()) throw new ForbiddenException("没有创建顶级组织的权限");
            return null;
        }
        if (changing) checkDeptDataScope(id);
        SysDept parent = findActiveDept(id);
        if (parent == null) throw new ServiceException("上级组织不存在");
        return parent;
    }

    private SysDept findActiveDept(Long id)
    {
        if (id == null || id == 0L) return null;
        SysDept dept = deptMapper.selectDeptById(id);
        return dept == null || "2".equals(dept.getDelFlag()) ? null : dept;
    }

    private SysDeptType effectiveType(SysDept dept)
    {
        return dept == null ? null : SysDeptType.effectiveType(dept, findActiveDept(dept.getParentId()));
    }

    private void normalizeType(SysDept dept)
    {
        SysDeptType type = SysDeptType.parse(dept.getDeptType());
        dept.setDeptType(type == null ? null : type.name());
    }

    private String ancestorsOf(SysDept parent)
    {
        String ancestors = parent == null ? "0" : parent.getAncestors() + "," + parent.getDeptId();
        if (ancestors.length() > 50) throw new ServiceException("组织层级超出数据库支持长度");
        return ancestors;
    }

    /**
     * 修改该部门的父级部门状态
     * 
     * @param dept 当前部门
     */
    private void updateParentDeptStatusNormal(SysDept dept)
    {
        String ancestors = dept.getAncestors();
        Long[] deptIds = Convert.toLongArray(ancestors);
        deptMapper.updateDeptStatusNormal(deptIds);
    }

    /**
     * 修改子元素关系
     * 
     * @param deptId 被修改的部门ID
     * @param newAncestors 新的父ID集合
     * @param oldAncestors 旧的父ID集合
     */
    public void updateDeptChildren(Long deptId, String newAncestors, String oldAncestors)
    {
        List<SysDept> children = deptMapper.selectChildrenDeptById(deptId);
        for (SysDept child : children)
        {
            String ancestors = newAncestors + child.getAncestors().substring(oldAncestors.length());
            if (ancestors.length() > 50) throw new ServiceException("下级组织层级超出数据库支持长度");
            child.setAncestors(ancestors);
        }
        if (children.size() > 0)
        {
            deptMapper.updateDeptChildren(children);
        }
    }

    /**
     * 保存部门排序
     *
     * @param deptIds 部门ID数组
     * @param orderNums 排序数组
     */
    @Override
    @Transactional
    public void updateDeptSort(String[] deptIds, String[] orderNums)
    {
        try
        {
            for (int i = 0; i < deptIds.length; i++)
            {
                SysDept dept = new SysDept();
                dept.setDeptId(Convert.toLong(deptIds[i]));
                dept.setOrderNum(Convert.toInt(orderNums[i]));
                deptMapper.updateDeptSort(dept);
            }
        }
        catch (Exception e)
        {
            throw new ServiceException("保存排序异常，请联系管理员");
        }
    }

    @Override
    public List<Long> getDeptBranchForRemoval(Long deptId, boolean lock)
    {
        if (deptId == null || deptId <= 0) throw new ServiceException("组织编号无效", 400);
        checkDeptDataScope(deptId);
        List<Long> ids = deptMapper.selectBranchIds(deptId, lock);
        if (!ids.contains(deptId)) throw new ServiceException("该组织已删除或不存在", 400);
        if (!SecurityUtils.isAdmin())
        {
            Set<Long> scopedIds = SpringUtils.getAopProxy(this).selectDeptList(new SysDept()).stream()
                    .map(SysDept::getDeptId).collect(Collectors.toSet());
            if (!scopedIds.containsAll(ids))
            {
                throw new ServiceException("该分支包含超出可删除范围的组织", 400);
            }
        }
        return ids;
    }

    @Override
    public BranchReferences getBranchReferences(List<Long> ids, boolean lock)
    {
        return new BranchReferences(deptMapper.selectBranchUsers(ids, lock),
                deptMapper.selectBranchRoleScopes(ids, lock).stream().distinct().toList());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public int deleteDeptBranch(List<Long> ids, boolean force, String operator)
    {
        if (ids.isEmpty()) throw new ServiceException("没有可删除的组织", 400);
        if (force)
        {
            deptMapper.clearBranchUserDepartments(ids, operator);
            deptMapper.deleteBranchRoleScopes(ids);
        }
        int rows = deptMapper.deleteDeptBranch(ids);
        if (rows != ids.size()) throw new ServiceException("组织分支已发生变化，请重新确认删除范围", 409);
        return rows;
    }

    /**
     * 递归列表
     */
    private void recursionFn(List<SysDept> list, SysDept t)
    {
        // 得到子节点列表
        List<SysDept> childList = getChildList(list, t);
        t.setChildren(childList);
        for (SysDept tChild : childList)
        {
            if (hasChild(list, tChild))
            {
                recursionFn(list, tChild);
            }
        }
    }

    /**
     * 得到子节点列表
     */
    private List<SysDept> getChildList(List<SysDept> list, SysDept t)
    {
        List<SysDept> tlist = new ArrayList<SysDept>();
        Iterator<SysDept> it = list.iterator();
        while (it.hasNext())
        {
            SysDept n = (SysDept) it.next();
            if (StringUtils.isNotNull(n.getParentId()) && n.getParentId().longValue() == t.getDeptId().longValue())
            {
                tlist.add(n);
            }
        }
        return tlist;
    }

    /**
     * 判断是否有子节点
     */
    private boolean hasChild(List<SysDept> list, SysDept t)
    {
        return getChildList(list, t).size() > 0;
    }
}
