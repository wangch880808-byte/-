package com.ruoyi.system.service;

import java.util.List;
import org.springframework.stereotype.Service;
import com.ruoyi.common.annotation.DataScope;
import com.ruoyi.common.core.domain.entity.SysDept;
import com.ruoyi.system.mapper.SysDeptMapper;

/** 固定各操作的数据权限，避免批量导入时把新增权限范围当成修改权限范围。 */
@Service
public class SysDeptTransferScope
{
    private final SysDeptMapper mapper;

    public SysDeptTransferScope(SysDeptMapper mapper) { this.mapper = mapper; }

    @DataScope(deptAlias = "d", permission = "system:dept:list")
    public List<SysDept> visible(SysDept filter) { return mapper.selectDeptList(filter); }

    @DataScope(deptAlias = "d", permission = "system:dept:add")
    public List<SysDept> addable(SysDept filter) { return mapper.selectDeptList(filter); }

    @DataScope(deptAlias = "d", permission = "system:dept:edit")
    public List<SysDept> editable(SysDept filter) { return mapper.selectDeptList(filter); }
}
