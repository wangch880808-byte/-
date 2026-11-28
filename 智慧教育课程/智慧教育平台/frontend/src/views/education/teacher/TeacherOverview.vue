<script setup>
import OverviewBoard from '@/components/education/OverviewBoard.vue'

/**
 * 教师教学总览页面配置。
 *
 * 只声明 #24 当前支持的 6 个指标；pending 是待提交人次合计，不是任务条数；
 * class.count/student.count 未按教学班或跨班去重。页面不读取 actionPath。
 */
const config = Object.freeze({
  endpoint: 'teacher',
  timeFilter: true,
  classFilter: true,
  groups: [
    { key: 'class', label: '班级与学生' },
    { key: 'classroom', label: '课堂' },
    { key: 'tasks', label: '任务与成绩' },
    { key: 'notice', label: '消息' }
  ],
  metrics: [
    {
      code: 'teacher.class.count',
      group: 'class',
      title: '授权班级范围',
      note: '按授权范围计数，未按教学班去重',
      source: 'course',
      unitLabel: '个',
      windowed: false,
      scopeDetail: 'teacher',
      scopeDetailPermission: 'education:overview:teacher',
      scopeDetailShowsStudents: true
    },
    {
      code: 'teacher.student.count',
      group: 'class',
      title: '学生数（按班汇总）',
      note: '各范围学生数求和，未跨班去重',
      source: 'course',
      unitLabel: '人',
      windowed: false,
      scopeDetail: 'teacher',
      scopeDetailPermission: 'education:overview:teacher',
      scopeDetailShowsStudents: true
    },
    {
      code: 'teacher.session.running',
      group: 'classroom',
      title: '活跃课堂',
      note: '含进行中与暂停课堂',
      source: 'classroom',
      unitLabel: '个',
      windowed: true
    },
    {
      code: 'teacher.assignment.pending',
      group: 'tasks',
      title: '待提交人次',
      note: '各任务待提交人数合计，非跨任务去重人数，非任务数',
      source: 'assignment',
      unitLabel: '人次',
      windowed: true
    },
    {
      code: 'teacher.grade.pending',
      group: 'tasks',
      title: '待处理成绩',
      note: '待评分 + 待发布，直接相加',
      source: 'grade',
      unitLabel: '条',
      windowed: false
    },
    {
      code: 'teacher.notice.unread',
      group: 'notice',
      title: '未读通知',
      note: '本人维度，不随 classId 过滤',
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
