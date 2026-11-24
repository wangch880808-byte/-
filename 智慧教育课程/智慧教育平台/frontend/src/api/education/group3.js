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

/** 创建外链资料（添加网址 / 在线图书） */
export function createLinkResource(data) {
  return request({ url: '/education/group3/resources/link', method: 'post', data })
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

/** 在线播放资料版本（返回 Blob，不受下载开关限制） */
export function getVersionMedia(versionId, bindingId) {
  return request({
    url: `/education/group3/versions/${versionId}/media`,
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

/** 转换失败的版本基于原文件重新转换 */
export function retryVersionConversion(versionId, bindingId) {
  return request({ url: `/education/group3/versions/${versionId}/retry-conversion`, method: 'post', params: { bindingId } })
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

/** 智能组卷：按题型/难度/知识点/标签筛选并抽题 */
export function composeQuestions(data) {
  return request({ url: '/education/group3/questions/compose', method: 'post', data })
}

/** 题目资源库：发布题目（body：bindingId、questionIds） */
export function shareQuestions(data) {
  return request({ url: '/education/group3/questions/library/share', method: 'post', data })
}

/** 题目资源库：撤回共享（body：bindingId、shareIds） */
export function withdrawSharedQuestions(data) {
  return request({ url: '/education/group3/questions/library/withdraw', method: 'post', data })
}

/** 题目资源库：检索共享题目 */
export function listQuestionLibrary(params) {
  return request({ url: '/education/group3/questions/library', method: 'get', params })
}

/** 题目资源库：可选学院列表 */
export function listQuestionLibraryColleges() {
  return request({ url: '/education/group3/questions/library/colleges', method: 'get' })
}

/** 题目资源库：共享题目详情 */
export function getQuestionLibraryDetail(shareId) {
  return request({ url: `/education/group3/questions/library/${shareId}`, method: 'get' })
}

/** 题目资源库：引入当前课程题库（body：bindingId、folderId、shareIds） */
export function importQuestionLibrary(data) {
  return request({ url: '/education/group3/questions/library/import', method: 'post', data })
}

/** 修改资料增强设置：下载开关、水印、任务点（body：bindingId 等） */
export function updateResourceEnhance(resourceId, data) {
  return request({ url: `/education/group3/resources/${resourceId}/enhance`, method: 'patch', data })
}

/** 查询学生在本教学班的观看进度 */
export function listWatchProgress(bindingId) {
  return request({ url: '/education/group3/student/progress', method: 'get', params: { bindingId } })
}

/** 上报学生视频观看进度（body：bindingId、watchedSeconds） */
export function reportWatchProgress(resourceId, data) {
  return request({ url: `/education/group3/student/materials/${resourceId}/watch`, method: 'post', data })
}

/** 查询学生学习进度看板（任务点、完成率、待办） */
export function getStudentDashboard(bindingId) {
  return request({ url: '/education/group3/student/dashboard', method: 'get', params: { bindingId } })
}

/** 查询作答回看与错题本（仅解析已公布的提交） */
export function getStudentAnswerReview(bindingId) {
  return request({ url: '/education/group3/student/answer-review', method: 'get', params: { bindingId } })
}

/** 查询本人个人笔记 */
export function getStudentNote(bindingId) {
  return request({ url: '/education/group3/student/note', method: 'get', params: { bindingId } })
}

/** 保存本人个人笔记 */
export function saveStudentNote(data) {
  return request({ url: '/education/group3/student/note', method: 'put', data })
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

/* ---------- 课堂讨论 ---------- */

/** 查询教学班讨论话题列表（params：view、keyword、folderId、sort、scope） */
export function listDiscussions(bindingId, params) {
  return request({ url: '/education/group3/discussions', method: 'get', params: { bindingId, ...(params || {}) } })
}

/** 获取讨论话题详情（含帖子与楼中楼） */
export function getDiscussion(topicId, bindingId) {
  return request({ url: `/education/group3/discussions/${topicId}`, method: 'get', params: { bindingId } })
}

/** 发布讨论话题 */
export function createDiscussion(data) {
  return request({ url: '/education/group3/discussions', method: 'post', data })
}

/** 在话题下发帖 / 回复（parentPostId 为空时为主帖） */
export function createDiscussionPost(topicId, data) {
  return request({ url: `/education/group3/discussions/${topicId}/posts`, method: 'post', data })
}

/** 讨论话题 / 帖子操作，action 取 pin、feature、unpin、unfeature、archive、restore、remove-post、like、folder、move、score */
export function discussionAction(data) {
  return request({ url: '/education/group3/discussions/action', method: 'post', data })
}

/** 查询讨论文件夹列表 */
export function listDiscussionFolders(bindingId) {
  return request({ url: '/education/group3/discussion-folders', method: 'get', params: { bindingId } })
}

/** 新建讨论文件夹（body：bindingId、name） */
export function createDiscussionFolder(data) {
  return request({ url: '/education/group3/discussion-folders', method: 'post', data })
}

/** 修改环节设置（body：bindingId、kind、id、title、instructions、privateNotes、opensAt、closesAt、answerOpensAt） */
export function updateStage(data) {
  return request({ url: '/education/group3/stages/update', method: 'post', data })
}

/* ---------- 学生端 ---------- */

/** 查询学生可见的资料列表（课程建设发布） */
export function listStudentMaterials(bindingId) {
  return request({ url: '/education/group3/student/materials', method: 'get', params: { bindingId } })
}

/** 查询学生可见的课前准备资料列表 */
export function listStudentPrepMaterials(bindingId) {
  return request({ url: '/education/group3/student/prep-materials', method: 'get', params: { bindingId } })
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

/** 章节详情：内容、知识点、资料、题目与学习进度 */
export function getOutlineUnitDetail(unitId, bindingId) {
  return request({ url: `/education/group3/outline/units/${unitId}/detail`, method: 'get', params: { bindingId } })
}

/** 知识点详情：学习目标说明与关联题目 */
export function getKnowledgePointDetail(pointId, bindingId) {
  return request({ url: `/education/group3/outline/knowledge-points/${pointId}/detail`, method: 'get', params: { bindingId } })
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

/** 题库文件夹归档 / 取消归档（body：bindingId、kind=QUESTION、archived） */
export function archiveQuestionFolder(folderId, data) {
  return request({ url: `/education/group3/folders/${folderId}/archive`, method: 'post', data })
}

/** 删除文件夹，kind 取 RESOURCE / QUESTION */
export function deleteFolder(folderId, bindingId, kind) {
  return request({
    url: `/education/group3/folders/${folderId}`,
    method: 'delete',
    params: { bindingId, kind }
  })
}

/** 文件夹整体上架 */
export function publishFolder(folderId, bindingId) {
  return request({ url: `/education/group3/folders/${folderId}/release`, method: 'post', params: { bindingId } })
}

/** 文件夹整体下架 */
export function revokeFolder(folderId, bindingId) {
  return request({ url: `/education/group3/folders/${folderId}/revoke`, method: 'post', params: { bindingId } })
}

/** 打包下载文件夹 */
export function downloadFolder(folderId, bindingId) {
  return request({
    url: `/education/group3/folders/${folderId}/download`,
    method: 'get',
    params: { bindingId },
    responseType: 'blob'
  })
}

/** 批量移动到文件夹（body：bindingId、kind、folderId、ids） */
export function moveToFolder(data) {
  return request({ url: '/education/group3/folders/move', method: 'post', data })
}

/* ---------- 公共资源库 ---------- */

/** 获取公共资源分类 */
export function listPublicCategories(kind = 'RESOURCE') {
  return request({ url: '/education/group3/public/categories', method: 'get', params: { kind } })
}

/** 获取公共资源列表 */
export function listPublicResources(params) {
  return request({ url: '/education/group3/public/resources', method: 'get', params })
}

/** 获取公共资源详情 */
export function getPublicResource(id) {
  return request({ url: `/education/group3/public/resources/${id}`, method: 'get' })
}

/** 上传文件到公共资源库 */
export function uploadPublicResource(data) {
  return request({ url: '/education/group3/public/resources/upload', method: 'post', data, headers: { 'Content-Type': 'multipart/form-data' } })
}

/** 上传外链到公共资源库 */
export function uploadPublicLink(data) {
  return request({ url: '/education/group3/public/resources/link', method: 'post', data })
}

/** 获取公共资源预览页列表 */
export function getPublicVersionPages(versionId) {
  return request({ url: `/education/group3/public/versions/${versionId}/pages`, method: 'get' })
}

/** 下载公共资源 */
export function downloadPublicResource(versionId) {
  return request({ url: `/education/group3/public/versions/${versionId}/download`, method: 'get', responseType: 'blob' })
}

/** 收藏/取消收藏公共资源 */
export function togglePublicFavorite(id) {
  return request({ url: `/education/group3/public/resources/${id}/favorite`, method: 'post' })
}

/** 我的收藏 */
export function listMyPublicFavorites() {
  return request({ url: '/education/group3/public/favorites', method: 'get' })
}

/** 我的上传 */
export function listMyPublicResources(params) {
  return request({ url: '/education/group3/public/my-resources', method: 'get', params })
}

/** 引用公共资源到课程资料 */
export function importPublicResource(id, data) {
  return request({ url: `/education/group3/public/resources/${id}/import`, method: 'post', data })
}
