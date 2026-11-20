<script setup>
import TreeManagePage from '@/components/education/TreeManagePage.vue'
import { session } from '@/education/session.js'

const orgTypes = [
  { value: 'SCHOOL', label: '学校' },
  { value: 'COLLEGE', label: '学院' },
  { value: 'MAJOR', label: '专业' },
  { value: 'CLASS', label: '班级' },
  { value: 'OTHER', label: '其他' }
]

const config = {
  kind: 'dept',
  collapsible: true,
  title: '组织',
  idKey: 'deptId',
  labelKey: 'deptName',
  base: '/system/dept',
  permission: 'system:dept',
  rootAllowed: () => String(session.user?.userId) === '1',
  defaultParent: '0',
  defaults: { orderNum: 0, status: '0' },
  columns: [
    { key: 'deptName', title: '组织名称' },
    { key: 'deptType', title: '组织类型', format: (value) => orgTypes.find(type => type.value === value)?.label || '—' },
    { key: 'leader', title: '负责人' },
    { key: 'orderNum', title: '显示顺序' },
    { key: 'status', title: '组织状态', format: (value) => (String(value) === '0' ? '开启' : '停用') },
    { key: 'createTime', title: '创建时间' }
  ],
  fields: [
    { key: 'parentId', title: '上级组织', parent: true, required: true, full: true },
    { key: 'deptName', title: '组织名称', required: true, max: 30 },
    { key: 'deptType', title: '组织类型', options: [{ value: '', label: '未指定' }, ...orgTypes] },
    { key: 'orderNum', title: '显示顺序', type: 'number', required: true },
    { key: 'leader', title: '负责人', max: 20 },
    { key: 'phone', title: '联系电话', max: 11 },
    { key: 'email', title: '邮箱', max: 50 },
    { key: 'status', title: '组织状态', options: [{ value: '0', label: '开启' }, { value: '1', label: '停用' }] }
  ]
}
</script>

<template>
  <TreeManagePage :config="config" />
</template>
