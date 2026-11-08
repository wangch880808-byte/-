/**
 * 管理员端「管理总览」视图配置（6-03，第 6 组 6A/6B）。
 *
 * 只声明 #24 当前支持的 5 个指标；组织、教师、课程、资源数量未提供，
 * 不显示假卡片或 0 值。
 */
(function (global) {
  'use strict';

  global.EduViews.register('admin', 0, 0, global.EduOverviewPage.create({
    endpoint: 'admin',
    timeFilter: true,
    classFilter: false,
    showWindow: true,
    groups: [
      { key: 'scale', label: '教学规模' },
      { key: 'runtime', label: '教学运行' },
      { key: 'pending', label: '待处理' }
    ],
    metrics: [
      {
        code: 'admin.class.count',
        group: 'scale',
        title: '授权班级范围',
        note: '按授权范围计数，未按教学班去重',
        source: 'course',
        unitLabel: '个',
        windowed: false
      },
      {
        code: 'admin.student.count',
        group: 'scale',
        title: '学生数（按班汇总）',
        note: '各范围学生数求和，未跨班去重',
        source: 'course',
        unitLabel: '人',
        windowed: false
      },
      {
        code: 'admin.session.running',
        group: 'runtime',
        title: '活跃课堂',
        note: '含进行中与暂停课堂',
        source: 'classroom',
        unitLabel: '个',
        windowed: true
      },
      {
        code: 'admin.assignment.pending',
        group: 'pending',
        title: '待提交人次',
        note: '各绑定待提交人数合计，非跨任务去重人数，非任务数',
        source: 'assignment',
        unitLabel: '人次',
        windowed: true
      },
      {
        code: 'admin.grade.pending',
        group: 'pending',
        title: '待处理成绩',
        note: '待评分 + 待发布，直接相加',
        source: 'grade',
        unitLabel: '条',
        windowed: false
      }
    ]
  }));
})(window);
