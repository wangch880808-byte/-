/**
 * 教师端「教学总览」视图配置（6-03，第 6 组 6A/6B）。
 *
 * 只声明 #24 当前支持的 6 个指标；不补授课课程数、本周课次、
 * 预习提醒、教学进度等未提供数据。
 */
(function (global) {
  'use strict';

  global.EduViews.register('teacher', 0, 0, global.EduOverviewPage.create({
    endpoint: 'teacher',
    timeFilter: true,
    classFilter: true,
    showWindow: true,
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
        windowed: false
      },
      {
        code: 'teacher.student.count',
        group: 'class',
        title: '学生数（按班汇总）',
        note: '各范围学生数求和，未跨班去重',
        source: 'course',
        unitLabel: '人',
        windowed: false
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
  }));
})(window);
