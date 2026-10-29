(function () {
  'use strict';
  var page = AdminTreePage.create({
    kind: 'dept', title: '部门', id: 'deptId', label: 'deptName', base: '/system/dept', permission: 'system:dept', route: 'system/dept', rootAllowed: false,
    defaults: { orderNum: 0, status: '0' },
    columns: [
      { key: 'deptName', title: '部门名称' }, { key: 'orderNum', title: '排序' }, { key: 'leader', title: '负责人' },
      { key: 'phone', title: '联系电话' }, { key: 'email', title: '邮箱' },
      { key: 'status', title: '状态', format: function (value) { return String(value) === '0' ? '正常' : '停用'; } }
    ],
    fields: [
      { key: 'parentId', title: '上级部门', parent: true, required: true, full: true },
      { key: 'deptName', title: '部门名称', required: true, max: 30 },
      { key: 'orderNum', title: '显示顺序', type: 'number', required: true },
      { key: 'leader', title: '负责人', max: 20 }, { key: 'phone', title: '联系电话', max: 11 },
      { key: 'email', title: '邮箱', max: 50 },
      { key: 'status', title: '状态', options: [{ value: '0', label: '正常' }, { value: '1', label: '停用' }] }
    ]
  });
  window.EDU_PAGES = window.EDU_PAGES || [];
  window.EDU_PAGES.push({ g: 1, i: 0, page: page });
})();
