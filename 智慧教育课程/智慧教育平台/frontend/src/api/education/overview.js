/**
 * 6-03 三端总览 API（第 6 组 6A/6B）。
 *
 * 对应 #24 当前实现：
 *   GET /education/teacher/overview
 *   GET /education/student/today-todos
 *   GET /education/admin/overview
 *   GET /education/admin/todos
 *
 * 约定：
 * - 身份由服务端认证上下文取得，本模块不发送 userId/studentId/organizationId；
 * - 教师 classId 按字符串原样传递，不做 Number/parseInt，避免 Long 精度损失；
 * - 学生/管理员只发送 from/to；管理员待办也不发送客户端组织 ID；
 * - 不在这里补算或改写任何指标、时间窗或状态。
 */
(function (global) {
  'use strict';

  var http = global.EduHttp;
  var BASE = '/education';

  /**
   * @param {string} path 总览路径
   * @param {{from?:string,to?:string,classId?:string}} params 已校验的查询参数
   * @returns {Promise<object>} EduHttp 原始响应体
   */
  function request(path, query) {
    return http.get(BASE + path, query);
  }

  /**
   * 教师教学总览。
   *
   * @param {{from?:string,to?:string,classId?:string}} params 查询参数；classId 必须是字符串
   * @returns {Promise<object>} 总览响应
   */
  function teacher(params) {
    var p = params || {};
    return request('/teacher/overview', {
      from: p.from,
      to: p.to,
      classId: p.classId
    });
  }

  /**
   * 学生今日待办。
   *
   * @param {{from?:string,to?:string}} params 查询参数
   * @returns {Promise<object>} 总览响应
   */
  function student(params) {
    var p = params || {};
    // 学生端点不接收 classId；即使调用方误传也不向上发送。
    return request('/student/today-todos', { from: p.from, to: p.to });
  }

  /**
   * 管理员管理总览。
   *
   * @param {{from?:string,to?:string}} params 查询参数
   * @returns {Promise<object>} 总览响应
   */
  function admin(params) {
    var p = params || {};
    // 管理员端点不接收 classId/organizationId；组织范围由服务端上下文取得。
    return request('/admin/overview', { from: p.from, to: p.to });
  }

  /**
   * 管理员待办事项。
   *
   * 当前后端固定返回 NO_DATA；前端仍必须真实调用，不能用固定空态代替。
   *
   * @param {{from?:string,to?:string}} params 查询参数
   * @returns {Promise<object>} 总览响应
   */
  function adminTodos(params) {
    var p = params || {};
    return request('/admin/todos', { from: p.from, to: p.to });
  }

  global.EduOverviewApi = {
    teacher: teacher,
    student: student,
    admin: admin,
    adminTodos: adminTodos
  };
})(window);
