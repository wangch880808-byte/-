<script setup>
import OverviewBoard from '@/components/education/OverviewBoard.vue'

/**
 * 学生学习总览 / 今日待办页面配置。
 *
 * 只声明 #24 当前支持的 4 个指标；学生端点只发送 from/to，不携带班级、
 * binding 或身份参数。assignment.due 是待办记录条数，不是人数求和。
 */
const config = Object.freeze({
  endpoint: 'student',
  timeFilter: true,
  classFilter: false,
  groups: [
    { key: 'class', label: '我的课程' },
    { key: 'todayClass', label: '今日课堂', customLabel: '时间窗内课堂' },
    { key: 'todayTodo', label: '今日待办', customLabel: '时间窗内待办' },
    { key: 'notice', label: '消息' }
  ],
  metrics: [
    {
      code: 'student.class.count',
      group: 'class',
      title: '在修班级范围',
      note: '按授权范围计数，未按教学班去重',
      source: 'course',
      unitLabel: '个',
      windowed: false
    },
    {
      code: 'student.session.active',
      group: 'todayClass',
      title: '活跃课堂',
      note: '含进行中与暂停课堂，不表示一定可加入',
      source: 'classroom',
      unitLabel: '个',
      windowed: true
    },
    {
      code: 'student.assignment.due',
      group: 'todayTodo',
      title: '待办记录',
      note: '端口返回的待办记录条数，入选规则待第 5 组',
      source: 'assignment',
      unitLabel: '条',
      windowed: true
    },
    {
      code: 'student.notice.unread',
      group: 'notice',
      title: '未读通知',
      note: '本人全部未读；通知页按当前班级上下文显示',
      source: 'notice',
      unitLabel: '条',
      windowed: false
    }
  ]
})
</script>

<template>
  <OverviewBoard :config="config" />
</template>
