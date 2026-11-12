import request from '@/utils/request'

/* ---------- 课程 / 教学班上下文 ---------- */

/** 获取当前登录人可见的课程 / 教学班上下文列表 */
export function listContexts() {
  return request({ url: '/education/group3/contexts', method: 'get' })
}

/** 获取指定教学班绑定的资源与备课总览 */
export function getOverview(bindingId) {
  return request({ url: '/education/group3/overview', method: 'get', params: { bindingId } })
}

/** 上传资料（multipart：bindingId、title、unitId、file） */
export function createResource(data) {
  return request({ url: '/education/group3/resources', method: 'post', data })
}

/** 为已有资料上传新版本（multipart：bindingId、file） */
export function createResourceVersion(resourceId, data) {
  return request({ url: `/education/group3/resources/${resourceId}/versions`, method: 'post', data })
}

/** 查询某份资料的版本记录 */
export function listResourceVersions(resourceId, bindingId) {
  return request({ url: `/education/group3/resources/${resourceId}/versions`, method: 'get', params: { bindingId } })
}

/** 获取资料版本详情（含分页列表与媒体类型） */
export function getVersion(versionId, bindingId) {
  return request({ url: `/education/group3/versions/${versionId}`, method: 'get', params: { bindingId } })
}

/** 获取资料版本的指定预览页图片（返回 Blob） */
export function getVersionPage(versionId, pageNo, bindingId) {
  return request({
    url: `/education/group3/versions/${versionId}/pages/${pageNo}`,
    method: 'get',
    params: { bindingId },
    responseType: 'blob'
  })
}

/** 下载资料版本原文件（返回 Blob） */
export function downloadVersion(versionId, bindingId) {
  return request({
    url: `/education/group3/versions/${versionId}/download`,
    method: 'get',
    params: { bindingId },
    responseType: 'blob'
  })
}

/** 发布资料版本到教学班（bindingId、resourceVersionId、opensAt、closesAt） */
export function createRelease(data) {
  return request({ url: '/education/group3/releases', method: 'post', data })
}

/** 撤回已发布的资料，可附撤回原因 */
export function revokeRelease(releaseId, bindingId, reason) {
  return request({ url: `/education/group3/releases/${releaseId}/revoke`, method: 'post', params: { bindingId }, data: { reason } })
}

/** 修改资料名称（body：bindingId、title） */
export function updateResource(resourceId, data) {
  return request({ url: `/education/group3/resources/${resourceId}`, method: 'patch', data })
}

/** 修改资料状态，status 取 DRAFT / ACTIVE / ARCHIVED */
export function updateResourceStatus(resourceId, data) {
  return request({ url: `/education/group3/resources/${resourceId}/status`, method: 'patch', data })
}

/* ---------- 题库 ---------- */

/** 查询教学班题库列表 */
export function listQuestions(bindingId) {
  return request({ url: '/education/group3/questions', method: 'get', params: { bindingId } })
}

/** 新建题目 */
export function createQuestion(data) {
  return request({ url: '/education/group3/questions', method: 'post', data })
}

/** 为已有题目创建新版本 */
export function createQuestionVersion(questionId, data) {
  return request({ url: `/education/group3/questions/${questionId}/versions`, method: 'post', data })
}

/** 修改题目状态（body：status） */
export function updateQuestionStatus(questionId, bindingId, data) {
  return request({ url: `/education/group3/questions/${questionId}/status`, method: 'patch', params: { bindingId }, data })
}

/* ---------- 备课方案 ---------- */

/** 查询备课方案列表 */
export function listPlans(bindingId) {
  return request({ url: '/education/group3/plans', method: 'get', params: { bindingId } })
}

/** 保存备课方案 */
export function createPlan(data) {
  return request({ url: '/education/group3/plans', method: 'post', data })
}

/* ---------- 预习任务（教师） ---------- */

/** 查询当前用户可见的预习任务列表 */
export function listPreviewTasks(bindingId) {
  return request({ url: '/education/group3/preview-tasks', method: 'get', params: { bindingId } })
}

/** 发布预习任务 */
export function createPreviewTask(data) {
  return request({ url: '/education/group3/preview-tasks', method: 'post', data })
}

/* ---------- 学生端 ---------- */

/** 查询学生可见的资料列表 */
export function listStudentMaterials(bindingId) {
  return request({ url: '/education/group3/student/materials', method: 'get', params: { bindingId } })
}

/** 查询学生可见的预习任务列表 */
export function listStudentPreviews(bindingId) {
  return request({ url: '/education/group3/student/previews', method: 'get', params: { bindingId } })
}

/** 获取学生预习任务详情（含题目与提交记录） */
export function getStudentPreview(assignmentId, bindingId) {
  return request({ url: `/education/group3/student/previews/${assignmentId}`, method: 'get', params: { bindingId } })
}

/** 提交学生预习作答（body：answers 以题目项 ID 为键） */
export function submitStudentPreview(assignmentId, bindingId, data) {
  return request({ url: `/education/group3/student/previews/${assignmentId}/submit`, method: 'post', params: { bindingId }, data })
}

/* ---------- 对外契约 ---------- */

/** 按契约查询已发布资源列表 */
export function getPublishedResourcesContract(bindingId) {
  return request({ url: `/education/group3/contracts/published-resources/${bindingId}`, method: 'get' })
}

/** 按契约查询资料版本详情 */
export function getResourceVersionContract(versionId, bindingId) {
  return request({ url: `/education/group3/contracts/resource-versions/${versionId}`, method: 'get', params: { bindingId } })
}

/** 按契约查询预习任务列表 */
export function getPreviewTasksContract(bindingId) {
  return request({ url: `/education/group3/contracts/preview-tasks/${bindingId}`, method: 'get' })
}

/** 模块健康检查与当前绑定信息 */
export function health(bindingId) {
  return request({ url: '/education/group3/health', method: 'get', params: { bindingId } })
}

/* ---------- 课程大纲：章 / 节 ---------- */

/** 新建章或小节（body：bindingId、unitKind、title、parentUnitId、sortOrder） */
export function createOutlineUnit(data) {
  return request({ url: '/education/group3/outline/units', method: 'post', data })
}

/** 修改章节标题、父级或排序号 */
export function updateOutlineUnit(unitId, data) {
  return request({ url: `/education/group3/outline/units/${unitId}`, method: 'patch', data })
}

/** 删除章节及其子项 */
export function deleteOutlineUnit(unitId, bindingId) {
  return request({ url: `/education/group3/outline/units/${unitId}`, method: 'delete', params: { bindingId } })
}

/** 调整章节排序（body：bindingId、sortOrder） */
export function reorderOutlineUnit(unitId, data) {
  return request({ url: `/education/group3/outline/units/${unitId}/sort`, method: 'patch', data })
}

/* ---------- 课程大纲：知识点 ---------- */

/** 新建知识点（body：bindingId、code、name、unitId） */
export function createKnowledgePoint(data) {
  return request({ url: '/education/group3/outline/knowledge-points', method: 'post', data })
}

/** 修改知识点编码、名称或所属小节 */
export function updateKnowledgePoint(pointId, data) {
  return request({ url: `/education/group3/outline/knowledge-points/${pointId}`, method: 'patch', data })
}

/** 删除知识点 */
export function deleteKnowledgePoint(pointId, bindingId) {
  return request({ url: `/education/group3/outline/knowledge-points/${pointId}`, method: 'delete', params: { bindingId } })
}

/* ---------- 教学班详情 ---------- */

/** 查询教学班详情（学生数、任课教师、开放范围） */
export function listClassrooms(bindingId) {
  return request({ url: '/education/group3/classrooms', method: 'get', params: { bindingId } })
}

/** 软删除环节（归档）。kind 取 QUIZ（预习）、PLAN（备课包）、MATERIAL / DRAFT（资料） */
export function removeStage(data) {
  return request({ url: '/education/group3/stages/remove', method: 'post', data })
}

/* ---------- 题库批量导入导出 ---------- */

/** 导出题库为 Excel（返回 Blob） */
export function exportQuestionsExcel(bindingId) {
  return request({
    url: '/education/group3/questions/export',
    method: 'get',
    params: { bindingId },
    responseType: 'blob'
  })
}

/** 上传 Excel 并解析预览（multipart：file） */
export function previewQuestionImport(bindingId, data) {
  return request({
    url: '/education/group3/questions/import/preview',
    method: 'post',
    params: { bindingId },
    data
  })
}

/** 按预览结果落库（body：bindingId、questions） */
export function importQuestions(data) {
  return request({ url: '/education/group3/questions/import', method: 'post', data })
}

/* ---------- 资料 ZIP 批量导入导出 ---------- */

/** 导出全部资料为 ZIP（返回 Blob） */
export function exportResourcesZip(bindingId) {
  return request({
    url: '/education/group3/resources/export',
    method: 'get',
    params: { bindingId },
    responseType: 'blob'
  })
}

/** 上传 ZIP 压缩包并导入资料（multipart：file） */
export function importResourcesZip(bindingId, data) {
  return request({
    url: '/education/group3/resources/import',
    method: 'post',
    params: { bindingId },
    data
  })
}

/* ---------- 资料 / 题库文件夹 ---------- */

/** 查询文件夹列表，kind 取 RESOURCE / QUESTION */
export function listFolders(bindingId, kind) {
  return request({ url: '/education/group3/folders', method: 'get', params: { bindingId, kind } })
}

/** 新建文件夹（body：bindingId、kind、name、visibility） */
export function createFolder(data) {
  return request({ url: '/education/group3/folders', method: 'post', data })
}

/** 修改文件夹名称与可见性 */
export function updateFolder(folderId, data) {
  return request({ url: `/education/group3/folders/${folderId}`, method: 'patch', data })
}

/** 删除文件夹，kind 取 RESOURCE / QUESTION */
export function deleteFolder(folderId, bindingId, kind) {
  return request({
    url: `/education/group3/folders/${folderId}`,
    method: 'delete',
    params: { bindingId, kind }
  })
}

/** 批量移动到文件夹（body：bindingId、kind、folderId、ids） */
export function moveToFolder(data) {
  return request({ url: '/education/group3/folders/move', method: 'post', data })
}
