/**
 * 管理员端「待办事项」视图配置（6-03，第 6 组 6A/6B）。
 *
 * 必须真实调用 /education/admin/todos。只有收到合法 NO_DATA 结果时，
 * 才显示“待办来源尚未确定，当前暂无数据”；401/403、接口失败、
 * 响应违约由共享组件显示对应错误，不能用固定空态遮盖。
 */
(function (global) {
  'use strict';

  global.EduViews.register('admin', 0, 1, global.EduOverviewPage.create({
    endpoint: 'adminTodos',
    timeFilter: false,
    classFilter: false,
    showWindow: false,
    requireNoData: true,
    expectedSource: 'pending_product_confirmation',
    requireNullActionPath: true,
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
        noDataText: '待办来源尚未确定，当前暂无数据'
      }
    ]
  }));
})(window);
