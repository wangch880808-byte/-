<script setup>
import OverviewBoard from '@/components/education/OverviewBoard.vue'

/**
 * 管理员待办页面配置。
 *
 * 当前后端只返回 admin.todo.pending 的固定 NO_DATA，来源为
 * pending_product_confirmation。只有 source、value、sampleCount、actionPath
 * 和状态全部符合该契约时，才展示“待办口径待确认”；任何字段违约都降级为
 * 数据异常，不能显示成 0 条或“全部完成”。
 */
const config = Object.freeze({
  endpoint: 'adminTodos',
  timeFilter: true,
  classFilter: false,
  requireNoData: true,
  expectedSource: 'pending_product_confirmation',
  requireNullActionPath: true,
  requireNullSampleCount: true,
  groups: [
    { key: 'todos', label: '待办事项' }
  ],
  metrics: [
    {
      code: 'admin.todo.pending',
      group: 'todos',
      title: '管理员待办',
      note: '产品尚未确认来源，不生成数量或列表',
      source: 'pending_product_confirmation',
      unitLabel: '',
      windowed: false,
      noDataText: '待办口径待确认'
    }
  ]
})
</script>

<template>
  <OverviewBoard :config="config" />
</template>
