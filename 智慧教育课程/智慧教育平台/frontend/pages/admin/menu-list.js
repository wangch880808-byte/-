(function () {
  'use strict';
  function choices(labels) { return labels.map(function (label, index) { return { value: String(index), label: label }; }); }
  var page = AdminTreePage.create({
    kind: 'menu', title: '菜单', id: 'menuId', label: 'menuName', base: '/system/menu', permission: 'system:menu', route: 'system/menu', rootAllowed: true, defaultParent: '0',
    defaults: { menuType: 'M', orderNum: 0, isFrame: '1', isCache: '1', visible: '0', status: '0', icon: '#' },
    columns: [
      { key: 'menuName', title: '菜单名称' },
      { key: 'menuType', title: '类型', format: function (type) { return { M: '目录', C: '菜单', F: '按钮' }[type] || type; } },
      { key: 'orderNum', title: '排序' }, { key: 'path', title: '路由地址' }, { key: 'perms', title: '权限标识' },
      { key: 'visible', title: '显示', format: function (value) { return String(value) === '0' ? '显示' : '隐藏'; } },
      { key: 'status', title: '状态', format: function (value) { return String(value) === '0' ? '正常' : '停用'; } }
    ],
    fields: [
      { key: 'parentId', title: '上级菜单', parent: true, required: true, full: true },
      { key: 'menuType', title: '菜单类型', required: true, options: [{ value: 'M', label: '目录' }, { value: 'C', label: '菜单' }, { value: 'F', label: '按钮' }] },
      { key: 'menuName', title: '菜单名称', required: true, max: 50 },
      { key: 'orderNum', title: '显示顺序', type: 'number', required: true },
      { key: 'status', title: '状态', options: choices(['正常', '停用']) },
      { key: 'path', title: '路由地址', max: 200, types: ['M', 'C'] },
      { key: 'component', title: '组件路径', max: 200, types: ['C'] },
      { key: 'routeName', title: '路由名称', max: 100, types: ['M', 'C'] },
      { key: 'query', title: '路由参数', max: 255, types: ['C'] },
      { key: 'perms', title: '权限标识', max: 100, types: ['C', 'F'] },
      { key: 'icon', title: '图标名称', max: 100, types: ['M', 'C'] },
      { key: 'isFrame', title: '是否外链', options: choices(['外链', '站内']), types: ['M', 'C'] },
      { key: 'isCache', title: '缓存', options: choices(['缓存', '不缓存']), types: ['C'] },
      { key: 'visible', title: '显示', options: choices(['显示', '隐藏']), types: ['M', 'C'] }
    ]
  });
  window.EDU_PAGES = window.EDU_PAGES || [];
  window.EDU_PAGES.push({ g: 6, i: 2, page: page });
})();
