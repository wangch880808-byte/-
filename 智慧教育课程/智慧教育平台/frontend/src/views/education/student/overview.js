/**
 * 学生端「学习总览 / 今日待办」视图配置（6-03，第 6 组 6A/6B）。
 *
 * 只显示 #24 当前支持的 4 个计数；没有待办明细接口，不构造假列表。
 * 自定义时间窗时，“今日课堂/今日待办”改为“时间窗内课堂/时间窗内待办”。
 */
(function (global) {
  'use strict';

  global.EduViews.register('student', 0, 0, global.EduOverviewPage.create({
    endpoint: 'student',
    timeFilter: true,
    classFilter: false,
    showWindow: true,
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
        note: '含进行中与暂停课堂',
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
        note: '本人维度，可查看学习通知页',
        source: 'notice',
        unitLabel: '条',
        windowed: false,
        navigation: { label: '学生通知页' }
      }
    ]
  }));
})(window);
