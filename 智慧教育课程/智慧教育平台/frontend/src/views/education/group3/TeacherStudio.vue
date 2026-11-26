<template>
  <div class="studio-page">
    <Group3ContextBar
      v-if="!contextBindingId"
      :list="list"
      :binding-id="bindingId"
      :loading="loading"
      :error="error"
      @select="handleSelect"
      @refresh="refresh"
    />

    <el-empty v-if="!bindingId" description="请选择课程与教学班" />

    <div v-else v-loading="overviewLoading">
      <div class="studio-head">
        <div>
          <h2>课程建设</h2>
          <p class="studio-sub">{{ current?.courseTitle }} · {{ current?.className }}</p>
        </div>
        <el-button @click="openClassroomSettings">教学班设置</el-button>
      </div>

      <el-tabs v-model="studioTab">
        <!-- ============ 资料 ============ -->
        <el-tab-pane label="资料" name="resources">
          <div class="toolbar">
            <el-dropdown trigger="click" @command="handleSourceCommand">
              <el-button type="primary">+ 添加资料</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="label in resourceSources" :key="label" :command="label">
                    {{ label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button @click="createResourceFolder">新建文件夹</el-button>
            <el-button :loading="resourceImporting" @click="triggerResourceImport">导入</el-button>
            <el-button :disabled="!current?.canExport" @click="exportResources">导出</el-button>
            <input
              ref="resourceZipInputRef"
              type="file"
              accept=".zip"
              style="display: none"
              @change="onResourceZipSelected"
            />
            <div class="spacer"></div>
            <el-input v-model="resourceSearch" placeholder="搜索" clearable style="width: 220px" />
          </div>

          <div class="breadcrumb">
            <template v-if="activeFolder">
              <el-link type="primary" @click="backToAll">全部文件</el-link>
              <span>›</span>
              <span>{{ activeFolder.name }}</span>
              <span class="cell-meta">当前页共 {{ resourceTableRows.length }} 个</span>
            </template>
            <template v-else>
              <strong>全部文件</strong>
              <span class="cell-meta">当前页共 {{ folders.length + resourceList.length }} 个</span>
            </template>
          </div>

          <div v-if="selectedRows.length" class="batch-bar">
            <span class="batch-info">已选 {{ selectedRows.length }} 项</span>
            <el-dropdown trigger="click" @command="moveSelectedToFolder">
              <el-button>移动到</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="">全部文件（未分组）</el-dropdown-item>
                  <el-dropdown-item v-for="folder in moveTargetFolders" :key="folder.id" :command="folder.id">
                    {{ folder.name }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button @click="batchDownload">下载</el-button>
            <el-button type="danger" @click="batchDelete">删除</el-button>
            <el-button link @click="clearResourceSelection">取消选择</el-button>
          </div>

          <el-table
            ref="resourceTableRef"
            :data="resourceTableRows"
            border
            row-key="key"
            :empty-text="resourceEmptyText"
            @selection-change="onResourceSelectionChange"
          >
            <el-table-column type="selection" width="46" :selectable="row => row.rowType === 'resource'" />
            <el-table-column label="文件名" min-width="340">
              <template #default="{ row }">
                <div v-if="row.rowType === 'folder'">
                  <el-tag size="small" type="info">文件夹</el-tag>
                  <el-link type="primary" class="ml6" @click="enterFolder(row.folder.id)">
                    {{ row.folder.name }}
                  </el-link>
                  <el-tag v-if="folderVisibilityLabel(row.folder.visibility)" size="small" type="warning" class="ml6">
                    {{ folderVisibilityLabel(row.folder.visibility) }}
                  </el-tag>
                  <div class="cell-meta">
                    {{ folderCount(row.folder) }} 个资料{{ row.folder.demo ? ' · 演示文件夹' : '' }}
                  </div>
                </div>
                <div v-else class="file-title">
                  <el-tag size="small" effect="plain">{{ resourceIcon(row.resource) }}</el-tag>
                  <div>
                    <el-link
                      type="primary"
                      @click="openPreview(row.resource.versionId, row.resource.bindingId || bindingId)"
                    >
                      {{ row.resource.title }}
                    </el-link>
                    <div class="cell-meta">{{ resourceMeta(row.resource) }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="大小" width="110" align="center">
              <template #default="{ row }">
                {{ row.rowType === 'resource' && Number(row.resource.byteSize) > 0 ? formatFileSize(Number(row.resource.byteSize)) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="创建者" width="120">
              <template #default="{ row }">{{ row.rowType === 'resource' ? (row.resource.creatorName || '-') : '-' }}</template>
            </el-table-column>
            <el-table-column label="创建日期" width="150">
              <template #default="{ row }">
                {{ formatTime(row.rowType === 'resource' ? row.resource.createdAt : row.folder.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="270" fixed="right">
              <template #default="{ row }">
                <div v-if="row.rowType === 'resource'" class="row-actions">
                  <el-button link type="primary" @click="openRenameResource(row.resource)">修改</el-button>
                  <el-button link type="primary" @click="handleDownload(row.resource.versionId, row.resource.bindingId || bindingId)">
                    下载
                  </el-button>
                  <el-dropdown trigger="click" @command="command => handleResourceCommand(command, row.resource)">
                    <el-button link type="primary">更多</el-button>
                    <template #dropdown>
                      <el-dropdown-menu>
                        <el-dropdown-item command="preview">预览</el-dropdown-item>
                        <el-dropdown-item command="versions">版本记录</el-dropdown-item>
                        <el-dropdown-item command="enhance">设置（下载/水印/任务点）</el-dropdown-item>
                        <el-dropdown-item v-if="row.resource.studioReleaseId" command="revoke">下架</el-dropdown-item>
                        <el-dropdown-item v-else command="publish">上架</el-dropdown-item>
                        <el-dropdown-item v-if="row.resource.processingStatus === 'FAILED'" command="retry">重试转换</el-dropdown-item>
                        <el-dropdown-item v-if="row.resource.resourceKind === 'FILE'" command="newVersion">新版本</el-dropdown-item>
                        <el-dropdown-item :command="row.resource.resourceStatus === 'ARCHIVED' ? 'unarchive' : 'archive'" divided>
                          {{ row.resource.resourceStatus === 'ARCHIVED' ? '取消归档' : '归档' }}
                        </el-dropdown-item>
                      </el-dropdown-menu>
                    </template>
                  </el-dropdown>
                </div>
                <div v-else class="row-actions">
                  <el-button link type="primary" @click="openRenameFolder(row.folder)">修改</el-button>
                  <el-button link type="primary" @click="handleDownloadFolder(row.folder)">下载</el-button>
                  <el-button link type="primary" @click="toggleFolderRelease(row.folder)">
                    {{ folderReleased(row.folder) ? '下架' : '上架' }}
                  </el-button>
                  <el-button link type="danger" @click="deleteFolderWithConfirm(row.folder, 'RESOURCE')">
                    删除
                  </el-button>
                </div>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 题库 ============ -->
        <el-tab-pane label="题库" name="questions">
          <div class="toolbar">
            <el-dropdown trigger="click" :disabled="questionReadonly" @command="handleQuestionCreateCommand">
              <el-button type="primary" :disabled="questionReadonly">+ 创建题目</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item command="manual">手动创建</el-dropdown-item>
                  <el-dropdown-item command="library">题目资源库</el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-dropdown trigger="click" :disabled="questionReadonly" @command="handleQuestionImportCommand">
              <el-button :disabled="questionReadonly">批量导入</el-button>
              <template #dropdown>
                <el-dropdown-menu>
                  <el-dropdown-item v-for="label in questionImportLabels" :key="label" :command="label">
                    {{ label }}
                  </el-dropdown-item>
                </el-dropdown-menu>
              </template>
            </el-dropdown>
            <el-button :disabled="questionReadonly" @click="createQuestionFolder">新建文件夹</el-button>
            <el-button
              :disabled="questionReadonly || questionSelection.length === 0"
              type="success"
              plain
              @click="batchShareSelectedQuestions"
            >
              批量发布到资源库{{ questionSelection.length ? `（${questionSelection.length}）` : '' }}
            </el-button>
            <el-button :disabled="questionReadonly" type="success" plain @click="openCompose">智能组卷</el-button>
            <el-checkbox v-model="showQuestionDetail">显示题目详情</el-checkbox>
            <el-button :disabled="!current?.canExport" @click="exportQuestions">导出全部</el-button>
            <el-tag v-if="questionReadonly" type="warning" size="small">
              正在查看其他教学班题库，仅可查看；新增/编辑请切回当前课程
            </el-tag>
            <div class="spacer"></div>
            <el-input v-model="questionSearch" placeholder="搜索" clearable style="width: 220px" />
          </div>

          <div class="toolbar">
            <span class="filter-label">课程</span>
            <el-select v-model="questionBindingFilter" placeholder="当前课程" style="width: 260px">
              <el-option label="当前课程" value="" />
              <el-option
                v-for="item in questionCourseOptions"
                :key="item.bindingId"
                :label="`${item.courseTitle} / ${item.className}`"
                :value="String(item.bindingId)"
              />
            </el-select>
            <span class="filter-label">题型</span>
            <el-select v-model="questionKind" placeholder="全部题型" clearable style="width: 160px">
              <el-option v-for="item in questionKindOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <span class="filter-label">难度</span>
            <el-select v-model="questionDifficulty" placeholder="全部难度" clearable style="width: 130px">
              <el-option v-for="item in difficultyOptions" :key="item.value" :label="item.label" :value="item.value" />
            </el-select>
            <span class="filter-label">知识点</span>
            <el-select v-model="questionPoint" placeholder="请选择" clearable style="width: 200px">
              <el-option v-for="item in knowledgePoints" :key="pointId(item)" :label="item.name" :value="item.name" />
            </el-select>
          </div>

          <div class="breadcrumb">
            <template v-if="activeQuestionFolder">
              <el-link type="primary" @click="backToAllQuestions">全部题目</el-link>
              <span>›</span>
              <span>{{ activeQuestionFolder.name }}</span>
              <span class="cell-meta">共 {{ filteredQuestions.length }} 题</span>
            </template>
            <template v-else>
              <strong>全部题目</strong>
              <span class="cell-meta">共 {{ filteredQuestions.length }} 题</span>
            </template>
          </div>

          <el-table
            ref="questionTableRef"
            v-loading="externalQuestionsLoading"
            :data="questionTableRows"
            border
            row-key="key"
            empty-text="题库为空"
            @row-click="onQuestionRowClick"
            @selection-change="handleQuestionSelectionChange"
          >
            <el-table-column type="selection" width="46" :selectable="row => row.rowType === 'question'" />
            <el-table-column label="序号" width="70" align="center">
              <template #default="{ row }">{{ row.rowType === 'question' ? row.index + 1 : '-' }}</template>
            </el-table-column>
            <el-table-column label="文件夹 / 题目" min-width="380">
              <template #default="{ row }">
                <div v-if="row.rowType === 'folder'">
                  <el-tag size="small" type="info">文件夹</el-tag>
                  <el-link type="primary" class="ml6" @click.stop="enterQuestionFolder(row.folder.id)">
                    {{ row.folder.name }}
                  </el-link>
                  <el-tag v-if="folderVisibilityLabel(row.folder.visibility)" size="small" type="warning" class="ml6">
                    {{ folderVisibilityLabel(row.folder.visibility) }}
                  </el-tag>
                  <el-tag v-if="row.folder.archivedAt" size="small" type="info" effect="plain" class="ml6">已归档</el-tag>
                  <div class="cell-meta">
                    共 {{ questionFolderCount(row.folder) }} 题 · {{ row.folder.visibility === 'PRIVATE' ? '私密' : '公开' }}
                  </div>
                </div>
                <div v-else class="file-title">
                  <el-tag size="small">题</el-tag>
                  <div>
                    <div>{{ row.question.title }}</div>
                    <div class="cell-meta">
                      V{{ row.question.versionNo }} · {{ row.question.maxScore }} 分 · 答案
                      {{ formatAnswer(row.question.answer, row.question.questionKind) }}
                      <span v-if="row.question.knowledgePoints"> · {{ row.question.knowledgePoints }}</span>
                    </div>
                    <div v-if="showQuestionDetail" class="question-stem">{{ row.question.stem }}</div>
                  </div>
                </div>
              </template>
            </el-table-column>
            <el-table-column label="题型" width="100">
              <template #default="{ row }">
                {{ row.rowType === 'question' ? questionKindLabel(row.question.questionKind) : '-' }}
              </template>
            </el-table-column>
            <el-table-column label="难易" width="80" align="center">
              <template #default="{ row }">
                <el-tag v-if="row.rowType === 'question'" size="small" :type="difficultyTagType(row.question.difficulty)">
                  {{ difficultyLabel(row.question.difficulty) }}
                </el-tag>
                <span v-else>-</span>
              </template>
            </el-table-column>
            <el-table-column label="使用量" width="90" align="center">
              <template #default>-</template>
            </el-table-column>
            <el-table-column label="正确率" width="90" align="center">
              <template #default>-</template>
            </el-table-column>
            <el-table-column label="创建者" width="110">
              <template #default="{ row }">{{ row.rowType === 'question' ? (row.question.creatorName || '-') : '-' }}</template>
            </el-table-column>
            <el-table-column label="创建时间" width="150">
              <template #default="{ row }">
                {{ formatTime(row.rowType === 'question' ? row.question.createdAt : row.folder.createdAt) }}
              </template>
            </el-table-column>
            <el-table-column label="操作" width="340" fixed="right">
              <template #default="{ row }">
                <template v-if="row.rowType === 'folder'">
                  <el-button
                    link
                    type="primary"
                    :disabled="questionReadonly"
                    @click.stop="toggleQuestionFolderArchive(row.folder)"
                  >
                    {{ row.folder.archivedAt ? '取消归档' : '归档' }}
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    :disabled="questionReadonly"
                    @click.stop="deleteFolderWithConfirm(row.folder, 'QUESTION')"
                  >
                    删除
                  </el-button>
                </template>
                <template v-if="row.rowType === 'question'">
                  <el-button link type="primary" :disabled="questionReadonly" @click.stop="openQuestionVersion(row.question)">
                    新版本
                  </el-button>
                  <el-button
                    v-if="row.question.status !== 'ARCHIVED'"
                    link
                    type="danger"
                    :disabled="questionReadonly"
                    @click.stop="archiveQuestion(row.question)"
                  >
                    归档
                  </el-button>
                  <el-button
                    v-if="row.question.status !== 'ARCHIVED' && row.question.shareStatus !== 'PUBLISHED'"
                    link
                    type="success"
                    @click.stop="shareQuestionToLibrary(row.question)"
                  >
                    发布到资源库
                  </el-button>
                  <el-button
                    v-else-if="row.question.shareStatus === 'PUBLISHED'"
                    link
                    type="warning"
                    @click.stop="withdrawQuestionFromLibrary(row.question)"
                  >
                    撤回共享
                  </el-button>
                </template>
              </template>
            </el-table-column>
          </el-table>
        </el-tab-pane>

        <!-- ============ 章节与知识点 ============ -->
        <el-tab-pane label="章节与知识点" name="outline">
          <div class="toolbar">
            <el-button v-if="canManageOutline" type="primary" @click="openCreateUnit('CHAPTER', null)">+ 新建章</el-button>
            <el-button v-if="canManageOutline" @click="openCreateKnowledgePoint(null)">+ 新建知识点</el-button>
            <el-button v-if="canManageOutline" @click="editorOpen = true">批量编辑</el-button>
            <div class="spacer"></div>
            <el-input v-model="outlineSearch" placeholder="搜索" clearable style="width: 220px" />
          </div>

          <div class="outline-head">
            <span></span>
            <span>目录</span>
            <span>内容概览</span>
            <span>学生进度</span>
            <span>操作</span>
          </div>

          <div v-if="!outlineRows.length" class="outline-empty">暂无章节，章节与知识点结构建立后会显示在这里</div>
          <template v-else>
            <div v-for="row in outlineRows" :key="row.key" :class="['outline-row', row.rowType]">
            <template v-if="row.rowType === 'chapter'">
              <span class="outline-num">{{ row.number }}</span>
              <span class="outline-title outline-link" @click="openUnitDetail(row.unit)">{{ row.title }}</span>
              <span class="outline-stats">{{ unitStats(row.unit) }}</span>
              <span class="outline-progress" @click="openUnitDetail(row.unit)">
                <el-progress
                  v-if="row.unit.completionRate !== null && row.unit.completionRate !== undefined"
                  :percentage="Number(row.unit.completionRate)"
                  :stroke-width="6"
                />
                <span v-else class="outline-muted">—</span>
              </span>
              <span class="outline-actions">
                <el-button link type="primary" @click="openEditUnit(row.unit)">编辑</el-button>
                <el-button link @click="moveUnit(row.unit, -1)">上移</el-button>
                <el-button link @click="moveUnit(row.unit, 1)">下移</el-button>
                <el-button link type="primary" @click="openCreateUnit('SECTION', row.unit.unitId)">+节</el-button>
                <el-button link type="primary" @click="openCreateKnowledgePoint(row.unit.unitId)">+点</el-button>
                <el-button link type="danger" @click="confirmDeleteUnit(row.unit)">删除</el-button>
                <el-button link @click="toggleChapter(row.unit.unitId)">
                  {{ isCollapsed(row.unit.unitId) ? '展开' : '收起' }}
                </el-button>
              </span>
            </template>
            <template v-else-if="row.rowType === 'section'">
              <span class="outline-num">{{ row.number }}</span>
              <span class="outline-title outline-link" @click="openUnitDetail(row.unit)">
                {{ row.title }}
              </span>
              <span class="outline-stats">{{ unitStats(row.unit) }}</span>
              <span class="outline-progress" @click="openUnitDetail(row.unit)">
                <el-progress
                  v-if="row.unit.completionRate !== null && row.unit.completionRate !== undefined"
                  :percentage="Number(row.unit.completionRate)"
                  :stroke-width="6"
                />
                <span v-else class="outline-muted">—</span>
              </span>
              <span class="outline-actions">
                <el-button link type="primary" @click="openEditUnit(row.unit)">编辑</el-button>
                <el-button link type="primary" @click="openCreateKnowledgePoint(row.unit.unitId)">+点</el-button>
                <el-button link type="danger" @click="confirmDeleteUnit(row.unit)">删除</el-button>
              </span>
            </template>
            <template v-else>
              <span class="outline-num"></span>
              <span class="outline-title outline-point outline-link" @click="openPointDetail(row.point)">
                <span v-if="row.point.code" class="outline-code">{{ row.point.code }}</span>{{ row.point.name }}
              </span>
              <span class="outline-stats">{{ pointStats(row.point) }}</span>
              <span class="outline-muted">—</span>
              <span class="outline-actions">
                <el-button v-if="canManageOutline" link type="primary" @click="openEditPoint(row.point)">编辑</el-button>
                <el-button link type="danger" @click="confirmDeletePoint(row.point)">删除</el-button>
              </span>
            </template>
            </div>
          </template>
        </el-tab-pane>
      </el-tabs>
    </div>

    <!-- ============ 上传资料 ============ -->
    <el-dialog v-model="uploadOpen" title="本地上传" width="620px" append-to-body>
      <div class="upload-notes">
        <div>注：</div>
        <ol>
          <li>文件数量：每次最多50个文件，超出部分将不会被导入。</li>
          <li>文件大小：最大支持1g文件，超出大小文件将上传失败，建议通过云盘上传。</li>
          <li>文件名称：名称不能包含特殊字符：`~!@#$%^&amp;*+&lt;&gt;?:&quot;{}/\\`</li>
        </ol>
      </div>
      <el-form label-width="90px">
        <el-form-item label="归档章节">
          <el-select v-model="uploadUnitId" placeholder="不归档章节" clearable style="width: 100%">
            <el-option v-for="item in unitOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="选择文件">
          <el-upload
            ref="uploadRef"
            v-model:file-list="uploadFiles"
            drag
            multiple
            :auto-upload="false"
            :show-file-list="false"
            accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"
          >
            <div class="upload-drag">将文件拖拽至此区域</div>
            <el-button type="primary">选择本地文件</el-button>
          </el-upload>
          <el-button class="upload-folder-btn" @click="triggerFolderSelect">上传文件夹</el-button>
          <input
            ref="folderInputRef"
            type="file"
            webkitdirectory
            multiple
            accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"
            style="display: none"
            @change="onFolderSelected"
          />
          <div v-if="uploadFiles.length" class="upload-list">
            <div v-for="(file, index) in uploadFiles" :key="file.name + index" class="upload-file-item">
              <span class="upload-file-name">{{ file.name }}</span>
              <span class="cell-meta">{{ formatFileSize(file.size) }}</span>
              <el-button link type="danger" @click="removeUploadFile(index)">移除</el-button>
            </div>
          </div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="uploadOpen = false">取 消</el-button>
        <el-button type="primary" :loading="uploading" @click="submitUpload">开始上传</el-button>
      </template>
    </el-dialog>

    <!-- ============ 添加外链（网址/在线图书） ============ -->
    <el-dialog v-model="linkOpen" :title="linkKind === 'BOOK' ? '添加在线图书' : '添加网址'" width="520px" append-to-body>
      <el-form label-width="90px">
        <el-form-item :label="linkKind === 'BOOK' ? '图书名称' : '资料名称'">
          <el-input v-model="linkForm.title" maxlength="200" :placeholder="linkKind === 'BOOK' ? '请输入图书名称' : '请输入资料名称'" show-word-limit />
        </el-form-item>
        <el-form-item :label="linkKind === 'BOOK' ? '阅读链接' : '链接地址'">
          <el-input
            v-model="linkForm.externalUri"
            maxlength="1024"
            type="textarea"
            :rows="2"
            :placeholder="linkKind === 'BOOK' ? '请输入图书在线阅读链接，以 http:// 或 https:// 开头' : '请输入链接地址，以 http:// 或 https:// 开头'"
            show-word-limit
          />
        </el-form-item>
        <el-form-item label="归档章节">
          <el-select v-model="linkForm.unitId" placeholder="不归档章节" clearable style="width: 100%">
            <el-option v-for="item in unitOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="linkOpen = false">取 消</el-button>
        <el-button type="primary" :loading="linkLoading" @click="submitLink">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ============ 公共资源库 ============ -->
    <PublicLibrary
      v-model="libraryVisible"
      :binding-id="bindingId"
      :folder-id="currentFolderId"
    />

    <!-- ============ 课程思政素材库 ============ -->
    <PublicLibrary
      v-model="ideologyVisible"
      kind="IDEOLOGY"
      :binding-id="bindingId"
      :folder-id="currentFolderId"
    />

    <!-- ============ 新建文件夹 ============ -->
    <el-dialog v-model="folderDialogOpen" title="新建文件夹" width="460px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="名称">
          <el-input v-model="folderForm.name" maxlength="60" placeholder="请输入文件夹名称" />
        </el-form-item>
        <el-form-item label="可见性">
          <el-radio-group v-model="folderForm.visibility">
            <el-radio value="PUBLIC">共享教学团队</el-radio>
            <el-radio value="PRIVATE">仅自己</el-radio>
            <el-radio value="PARTIAL">部分可见</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="folderDialogOpen = false">取 消</el-button>
        <el-button type="primary" @click="submitFolder">确 定</el-button>
      </template>
    </el-dialog>

    <el-dialog v-model="questionImportOpen" title="题库模板导入" width="980px" top="7vh" append-to-body>
      <div style="display: flex; align-items: center; gap: 12px; margin-bottom: 12px">
        <el-button type="primary" @click="triggerQuestionImportFile">选择 Excel 文件</el-button>
        <span class="cell-meta">{{ questionImportFileName || '请选择按“导出全部”生成的 .xlsx 文件' }}</span>
        <input
          ref="questionImportInputRef"
          type="file"
          accept=".xlsx,.xls"
          style="display: none"
          @change="onQuestionImportFileSelected"
        />
        <div style="flex: 1"></div>
        <span v-if="questionImportRows.length" class="cell-meta">
          可导入 {{ questionImportValidRows.length }} 题
          <span v-if="questionImportInvalidCount">，{{ questionImportInvalidCount }} 题需修正</span>
        </span>
      </div>
      <el-table
        v-loading="questionImportLoading"
        :data="questionImportRows"
        border
        max-height="460"
        empty-text="请选择要导入的 Excel 文件"
      >
        <el-table-column prop="rowNo" label="行号" width="70" align="center" />
        <el-table-column prop="title" label="题目名称" min-width="180" show-overflow-tooltip />
        <el-table-column prop="questionKindLabel" label="题型" width="90" />
        <el-table-column label="难度" width="80" align="center">
          <template #default="{ row }">{{ difficultyLabel(row.difficulty) }}</template>
        </el-table-column>
        <el-table-column prop="maxScore" label="满分" width="80" align="center" />
        <el-table-column prop="stem" label="题干" min-width="240" show-overflow-tooltip />
        <el-table-column label="状态" width="90" align="center">
          <template #default="{ row }">
            <el-tag :type="row.valid ? 'success' : 'danger'" size="small">
              {{ row.valid ? '可导入' : '需修正' }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="问题" min-width="200">
          <template #default="{ row }">
            <span v-if="row.valid" class="cell-meta">-</span>
            <span v-else style="color: #f56c6c">{{ (row.errors || []).join('；') }}</span>
          </template>
        </el-table-column>
      </el-table>
      <template #footer>
        <el-button @click="questionImportOpen = false">取 消</el-button>
        <el-button
          type="primary"
          :loading="questionImportSaving"
          :disabled="!questionImportValidRows.length"
          @click="submitQuestionImport"
        >
          导入 {{ questionImportValidRows.length }} 题
        </el-button>
      </template>
    </el-dialog>

    <!-- ============ 上传新版本 ============ -->
    <el-dialog v-model="versionOpen" :title="`上传新版本 · ${versionTarget?.title || ''}`" width="560px" append-to-body>
      <el-form label-width="90px">
        <el-form-item label="新文件">
          <el-upload
            :auto-upload="false"
            :show-file-list="false"
            :on-change="onVersionFileChange"
            accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"
          >
            <el-button type="primary">选择要上传的新文件</el-button>
          </el-upload>
          <div v-if="versionFile" class="cell-meta">
            {{ versionFile.name }}（{{ formatFileSize(versionFile.size) }}）
          </div>
        </el-form-item>
        <el-form-item label="提示">
          <span class="cell-meta">上传后学生端「课程资料」即显示该新版本，旧版本自动失效。</span>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="versionOpen = false">取 消</el-button>
        <el-button type="primary" :loading="versionSaving" @click="submitNewVersion">保存新版本</el-button>
      </template>
    </el-dialog>

    <!-- ============ 版本记录 ============ -->
    <el-dialog v-model="versionsOpen" :title="`资料版本记录 · ${versionsResourceTitle}`" width="760px" append-to-body>
      <el-table v-loading="versionsLoading" :data="versions" border size="small" empty-text="暂无版本">
        <el-table-column label="版本" width="90">
          <template #default="{ row }">V{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="处理状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="statusTagType(row.processingStatus)">
              {{ statusLabelOf(row.processingStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预览页" width="90">
          <template #default="{ row }">{{ row.pageCount || 0 }} 页</template>
        </el-table-column>
        <el-table-column label="创建时间" width="150">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPreview(row.versionId, bindingId)">预览</el-button>
          </template>
        </el-table-column>
      </el-table>
    </el-dialog>

    <!-- ============ 资料预览 ============ -->
    <el-dialog
      v-model="previewOpen"
      :title="previewTitle || '资料预览'"
      width="900px"
      top="6vh"
      append-to-body
      @closed="onPreviewClosed"
    >
      <div v-loading="previewLoading" class="preview-body">
        <el-alert v-if="previewError" :title="previewError" type="error" show-icon :closable="false" />
        <template v-else-if="!previewLoading">
          <div v-if="previewMode === 'link'">
            该资料为外部链接。
            <el-link type="primary" :href="previewLink" target="_blank" rel="noopener noreferrer">打开链接</el-link>
          </div>
          <div v-else-if="previewMode === 'video'" class="preview-watermark-host">
            <video :src="previewVideoUrl" controls autoplay class="preview-video" />
            <div v-if="previewWatermark" class="preview-watermark">{{ previewWatermark }}</div>
          </div>
          <div v-else-if="previewMode === 'empty'" class="preview-empty">
            <strong>该资料没有分页预览</strong>
            <p class="cell-meta">可使用受控下载打开原文件。</p>
            <el-button @click="handleDownload(previewVersionId, bindingId)">下载原文件</el-button>
          </div>
          <template v-else>
            <div class="preview-watermark-host">
              <img
                :src="previewPages[previewPageIndex]"
                :alt="'第 ' + (previewPageIndex + 1) + ' 页'"
                class="preview-image"
              />
              <div v-if="previewWatermark" class="preview-watermark">{{ previewWatermark }}</div>
            </div>
            <div class="preview-nav">
              <el-button :disabled="previewPageIndex === 0" @click="previewPageIndex -= 1">上一页</el-button>
              <span class="cell-meta">{{ previewPageIndex + 1 }} / {{ previewPages.length }}</span>
              <el-button :disabled="previewPageIndex >= previewPages.length - 1" @click="previewPageIndex += 1">
                下一页
              </el-button>
            </div>
          </template>
        </template>
      </div>
      <template #footer>
        <el-button v-if="previewMode !== 'link'" @click="handleDownload(previewVersionId, bindingId)">下载原文件</el-button>
        <el-button @click="previewOpen = false">关 闭</el-button>
      </template>
    </el-dialog>

    <!-- ============ 新建题目 / 新版本 ============ -->
    <el-dialog
      v-model="questionOpen"
      :title="questionEdit ? `新建题目版本 · ${questionEdit.title}` : '新建题目'"
      width="720px"
      top="6vh"
      append-to-body
    >
      <el-form label-width="110px">
        <el-form-item v-if="!questionEdit" label="题目名称">
          <el-input v-model="questionForm.title" maxlength="200" placeholder="请输入题目名称" />
        </el-form-item>
        <el-form-item label="题型">
          <el-select v-model="questionForm.questionKind" style="width: 220px" @change="onQuestionKindChange">
            <el-option v-for="item in questionKindOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="满分">
          <el-input-number v-model="questionForm.maxScore" :min="0" :step="0.5" />
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="questionForm.difficulty" style="width: 220px">
            <el-option v-for="item in difficultyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="questionForm.tags" placeholder="多个标签用逗号分隔，如：期中,重点" />
        </el-form-item>
        <el-form-item label="题干">
          <el-input v-model="questionForm.stem" type="textarea" :rows="3" placeholder="请输入题干" />
        </el-form-item>
        <el-form-item v-if="showOptionsField" label="选项">
          <el-input v-model="questionForm.options" type="textarea" :rows="4" placeholder="每行一个选项" />
        </el-form-item>
        <el-form-item label="答案">
          <el-input v-model="questionForm.answer" :placeholder="answerPlaceholder" />
          <div class="cell-meta">{{ answerHelp }}</div>
        </el-form-item>
        <el-form-item label="解析">
          <el-input v-model="questionForm.explanation" type="textarea" :rows="2" placeholder="请输入答案解析" />
        </el-form-item>
        <el-form-item v-if="showRubricField" label="评分标准">
          <el-input
            v-model="questionForm.scoringRubric"
            type="textarea"
            :rows="3"
            placeholder="请输入评分标准，例如：回答包含 3 个要点各得 3 分；语句通顺 1 分"
          />
          <div class="cell-meta">仅供简答题使用。设置评分细则后，教师可据此进行主观题评分。</div>
        </el-form-item>
        <el-form-item label="关联知识点">
          <el-checkbox-group v-model="questionForm.knowledgePointIds">
            <el-checkbox v-for="item in knowledgePoints" :key="pointId(item)" :value="Number(item.knowledgePointId)">
              {{ item.name }}
            </el-checkbox>
          </el-checkbox-group>
          <div v-if="!knowledgePoints.length" class="cell-meta">当前课程尚未维护知识点</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="questionOpen = false">取 消</el-button>
        <el-button type="primary" :loading="questionSaving" @click="submitQuestion">保存题目</el-button>
      </template>
    </el-dialog>

    <!-- ============ 题目预览 ============ -->
    <el-dialog v-model="questionPreviewOpen" title="题目预览" width="760px" top="8vh" append-to-body>
      <div v-if="questionPreviewCurrent" class="qpreview">
        <div class="qpreview-head">
          <span class="cell-meta">（{{ questionKindLabel(questionPreviewCurrent.questionKind) }}）</span>
          <div>
            <el-button :disabled="questionPreviewIndex === 0" @click="questionPreviewIndex -= 1">上一题</el-button>
            <el-button
              type="primary"
              :disabled="questionPreviewIndex >= questionPreviewList.length - 1"
              @click="questionPreviewIndex += 1"
            >
              下一题
            </el-button>
          </div>
        </div>
        <div class="qpreview-stem">{{ questionPreviewCurrent.stem || questionPreviewCurrent.title }}</div>
        <p><strong>答案：</strong>{{ questionPreviewAnswer }}</p>
        <p><strong>答案解析:</strong></p>
        <p>{{ questionPreviewAnalysis }}</p>
        <p class="cell-meta">难易：{{ difficultyLabel(questionPreviewCurrent.difficulty) }}</p>
        <p class="cell-meta">标签：{{ questionPreviewCurrent.tags || '无' }}</p>
        <p class="cell-meta">知识点：{{ questionPreviewCurrent.knowledgePoints || '未关联知识点' }}</p>
      </div>
    </el-dialog>

    <!-- ============ 智能组卷 ============ -->
    <el-dialog v-model="composeOpen" title="智能组卷" width="900px" top="6vh" append-to-body>
      <el-form label-width="80px" inline>
        <el-form-item label="题型">
          <el-select v-model="composeForm.questionKind" placeholder="全部" clearable style="width: 140px">
            <el-option v-for="item in questionKindOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="难度">
          <el-select v-model="composeForm.difficulty" placeholder="全部" clearable style="width: 130px">
            <el-option v-for="item in difficultyOptions" :key="item.value" :label="item.label" :value="item.value" />
          </el-select>
        </el-form-item>
        <el-form-item label="知识点">
          <el-select v-model="composeForm.knowledgePoint" placeholder="全部" clearable style="width: 180px">
            <el-option v-for="item in knowledgePoints" :key="pointId(item)" :label="item.name" :value="item.name" />
          </el-select>
        </el-form-item>
        <el-form-item label="标签">
          <el-input v-model="composeForm.tag" placeholder="标签关键字" clearable style="width: 140px" />
        </el-form-item>
        <el-form-item label="题量">
          <el-input-number v-model="composeForm.count" :min="1" :max="100" />
        </el-form-item>
        <el-form-item label="方式">
          <el-select v-model="composeForm.mode" style="width: 120px">
            <el-option label="随机" value="RANDOM" />
            <el-option label="顺序" value="SEQUENTIAL" />
          </el-select>
        </el-form-item>
        <el-form-item>
          <el-button type="primary" :loading="composeLoading" @click="submitCompose">生成试卷</el-button>
        </el-form-item>
      </el-form>
      <div v-if="composeResult" class="compose-summary">
        共 {{ composeResult.count }} 题 · 合计 {{ composeResult.totalScore }} 分
      </div>
      <el-table v-if="composeResult" :data="composeResult.questions" border max-height="440" size="small" empty-text="没有符合条件的题目">
        <el-table-column type="index" label="序号" width="60" align="center" />
        <el-table-column label="题型" width="90">
          <template #default="{ row }">{{ questionKindLabel(row.questionKind) }}</template>
        </el-table-column>
        <el-table-column label="难度" width="80">
          <template #default="{ row }">{{ difficultyLabel(row.difficulty) }}</template>
        </el-table-column>
        <el-table-column label="题干" min-width="300" show-overflow-tooltip>
          <template #default="{ row }">{{ row.stem }}</template>
        </el-table-column>
        <el-table-column label="分值" width="70" align="center" prop="maxScore" />
      </el-table>
      <template #footer>
        <el-button @click="composeOpen = false">关 闭</el-button>
        <el-button
          type="primary"
          :disabled="!composeResult || !composeResult.count"
          @click="openPaperPublish"
        >发布为整卷</el-button>
      </template>
    </el-dialog>

    <!-- ============ 整卷发布 ============ -->
    <el-dialog v-model="paperPublishOpen" title="发布整卷到课前学习" width="620px" top="8vh" append-to-body>
      <el-alert
        type="info"
        :closable="false"
        show-icon
        class="paper-publish-tip"
        :title="`共 ${paperPublishCount} 道题，将作为一份整卷发布到当前教学班，学生统一作答、一次提交。`"
      />
      <el-form label-width="110px" class="paper-publish-form">
        <el-form-item label="试卷名称" required>
          <el-input v-model="paperForm.title" maxlength="200" placeholder="例如：第 3 章课前自测卷" />
        </el-form-item>
        <el-form-item label="导学说明">
          <el-input
            v-model="paperForm.instructions"
            type="textarea"
            :rows="2"
            maxlength="500"
            placeholder="可填写作答要求、提示等（学生可见）"
          />
        </el-form-item>
        <el-form-item label="开放时间" required>
          <el-date-picker v-model="paperForm.opensAt" type="datetime" placeholder="选择开放时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="截止时间" required>
          <el-date-picker v-model="paperForm.closesAt" type="datetime" placeholder="选择截止时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="解析公布" required>
          <el-date-picker v-model="paperForm.answerOpensAt" type="datetime" placeholder="选择解析公布时间" style="width: 100%" />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="paperPublishOpen = false">取 消</el-button>
        <el-button type="primary" :loading="paperPublishing" @click="submitPaperPublish">确认发布</el-button>
      </template>
    </el-dialog>

    <QuestionLibraryDialog
      v-model="questionLibraryVisible"
      :binding-id="bindingId"
      :folder-id="currentQuestionFolderId"
      :folder-name="activeQuestionFolder ? activeQuestionFolder.name : ''"
      @imported="handleQuestionLibraryImported"
    />

    <!-- ============ 资料增强设置 ============ -->
    <el-dialog v-model="enhanceOpen" :title="`资料设置 · ${enhanceTarget?.title || ''}`" width="560px" append-to-body>
      <el-form label-width="120px">
        <el-form-item label="允许下载原文件">
          <el-switch v-model="enhanceForm.downloadEnabled" />
          <span class="cell-meta ml6">关闭后学生端无法下载原文件，仅可在线预览</span>
        </el-form-item>
        <el-form-item label="预览水印">
          <el-input v-model="enhanceForm.watermarkText" placeholder="留空表示不叠加水印" maxlength="200" />
          <div class="cell-meta">预览页将平铺显示该文字，用于防截图泄露</div>
        </el-form-item>
        <el-form-item label="设为视频任务点">
          <el-switch v-model="enhanceForm.taskEnabled" />
        </el-form-item>
        <el-form-item v-if="enhanceForm.taskEnabled" label="要求观看秒数">
          <el-input-number v-model="enhanceForm.taskRequiredSeconds" :min="0" :step="10" />
          <div class="cell-meta">填 0 时默认要求观看 30 秒；达到时长即标记为完成</div>
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="enhanceOpen = false">取 消</el-button>
        <el-button type="primary" :loading="enhanceSaving" @click="submitEnhance">保 存</el-button>
      </template>
    </el-dialog>

    <!-- ============ 章节编辑（批量编辑） ============ -->
    <el-dialog v-model="editorOpen" title="编辑章节" width="820px" top="6vh" append-to-body>
      <div class="toolbar">
        <el-button @click="openCreateUnit('CHAPTER', null)">+ 新建章</el-button>
        <span class="cell-meta">删除章节前请先处理其下的小节、知识点和关联资料。</span>
      </div>
      <div class="editor-tree">
        <div v-for="row in editorRows" :key="row.key" :class="['editor-row', row.rowType]">
          <span class="outline-num">{{ row.number || '' }}</span>
          <span class="editor-title">{{ row.title }}</span>
          <span class="outline-actions">
            <template v-if="row.rowType === 'chapter'">
              <el-button link type="primary" @click="openEditUnit(row.unit)">编辑</el-button>
              <el-button link type="primary" @click="openCreateUnit('SECTION', row.unit.unitId)">+ 小节</el-button>
              <el-button link type="primary" @click="openCreateKnowledgePoint(row.unit.unitId)">+ 知识点</el-button>
              <el-button link type="danger" @click="confirmDeleteUnit(row.unit)">删除</el-button>
            </template>
            <template v-else-if="row.rowType === 'section'">
              <el-button link type="primary" @click="openEditUnit(row.unit)">编辑</el-button>
              <el-button link type="primary" @click="openCreateKnowledgePoint(row.unit.unitId)">+ 知识点</el-button>
              <el-button link type="danger" @click="confirmDeleteUnit(row.unit)">删除</el-button>
            </template>
            <template v-else>
              <el-button link type="primary" @click="openEditPoint(row.point)">编辑</el-button>
              <el-button link type="danger" @click="confirmDeletePoint(row.point)">删除</el-button>
            </template>
          </span>
        </div>
        <div v-if="!editorRows.length" class="outline-empty">暂无章节</div>
      </div>
      <template #footer>
        <el-button @click="editorOpen = false">完 成</el-button>
      </template>
    </el-dialog>

    <!-- ============ 章 / 节表单 ============ -->
    <el-dialog
      v-model="unitDialogOpen"
      :title="unitDialogMode === 'create' ? (unitDialogKind === 'CHAPTER' ? '新建章' : '新建小节') : '编辑章节'"
      width="460px"
      append-to-body
    >
      <el-form label-width="90px">
        <el-form-item label="标题">
          <el-input v-model="unitForm.title" maxlength="160" placeholder="请输入标题" />
        </el-form-item>
        <el-form-item label="排序号">
          <el-input-number v-model="unitForm.sortOrder" :min="0" />
        </el-form-item>
        <el-form-item label="章节说明">
          <el-input
            v-model="unitForm.contentText"
            type="textarea"
            :rows="5"
            maxlength="4000"
            show-word-limit
            placeholder="学习目标、重点难点、课时安排等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="unitDialogOpen = false">取 消</el-button>
        <el-button type="primary" :loading="unitSaving" @click="submitUnit">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ============ 知识点表单 ============ -->
    <el-dialog
      v-model="pointDialogOpen"
      :title="pointForm.knowledgePointId ? '编辑知识点' : '新建知识点'"
      width="460px"
      append-to-body
    >
      <el-form label-width="90px">
        <el-form-item label="编码">
          <el-input v-model="pointForm.code" maxlength="60" placeholder="如 KP001" />
        </el-form-item>
        <el-form-item label="名称">
          <el-input v-model="pointForm.name" maxlength="160" placeholder="请输入知识点名称" />
        </el-form-item>
        <el-form-item label="学习目标">
          <el-input
            v-model="pointForm.contentText"
            type="textarea"
            :rows="5"
            maxlength="4000"
            show-word-limit
            placeholder="该知识点的学习目标、重难点说明等"
          />
        </el-form-item>
      </el-form>
      <template #footer>
        <el-button @click="pointDialogOpen = false">取 消</el-button>
        <el-button type="primary" :loading="pointSaving" @click="submitPoint">确 定</el-button>
      </template>
    </el-dialog>

    <!-- ============ 大纲详情（章节 / 知识点） ============ -->
    <el-dialog
      v-model="outlineDetailOpen"
      :title="outlineDetailType === 'unit' ? '章节详情' : '知识点详情'"
      width="900px"
      append-to-body
    >
      <div v-loading="outlineDetailLoading" class="outline-detail">
        <template v-if="outlineDetail">
          <template v-if="outlineDetailType === 'unit'">
            <h3 class="detail-title">{{ outlineDetail.unit.title }}</h3>
            <p class="cell-meta">
              {{ outlineDetail.unit.unitKind === 'CHAPTER' ? '章' : '节' }}
              · 资料 {{ outlineDetail.unit.materialCount || 0 }}
              · 题目 {{ outlineDetail.unit.questionCount || 0 }}
              · 知识点 {{ outlineDetail.unit.pointCount || 0 }}
              <span v-if="outlineDetail.unit.completionRate !== null && outlineDetail.unit.completionRate !== undefined">
                · 班级完成率 {{ outlineDetail.unit.completionRate }}%
              </span>
            </p>
          </template>
          <template v-else>
            <h3 class="detail-title">
              <span v-if="outlineDetail.point.code" class="outline-code">{{ outlineDetail.point.code }}</span>
              {{ outlineDetail.point.name }}
            </h3>
            <p class="cell-meta">
              所属章节：{{ pointUnitTitle }} · 关联题目 {{ outlineDetail.point.questionCount || 0 }}
            </p>
          </template>

          <el-tabs v-model="outlineDetailTab">
            <el-tab-pane label="大纲内容" name="content">
              <el-input
                v-model="outlineDetailContent"
                type="textarea"
                :rows="8"
                maxlength="4000"
                show-word-limit
                placeholder="填写学习目标、重难点、课时安排等说明，学生端大纲会展示这里的内容"
              />
              <div class="detail-content-actions">
                <el-button type="primary" :loading="outlineContentSaving" @click="saveOutlineDetailContent">
                  保存内容
                </el-button>
              </div>
            </el-tab-pane>
            <el-tab-pane v-if="outlineDetailType === 'unit'" label="知识点" name="points">
              <el-table :data="outlineDetail.points" size="small" empty-text="该章节暂无知识点">
                <el-table-column prop="code" label="编码" width="140" />
                <el-table-column prop="name" label="名称" min-width="220" />
                <el-table-column prop="questionCount" label="关联题目" width="100" />
              </el-table>
            </el-tab-pane>
            <el-tab-pane v-if="outlineDetailType === 'unit'" label="资料" name="materials">
              <el-table :data="outlineDetail.materials" size="small" empty-text="该章节暂无归档资料">
                <el-table-column prop="title" label="资料名称" min-width="240" show-overflow-tooltip />
                <el-table-column label="类型" width="90">
                  <template #default="{ row }">{{ resourceIcon(row) }}</template>
                </el-table-column>
                <el-table-column label="状态" width="110">
                  <template #default="{ row }">{{ row.resourceStatus === 'ARCHIVED' ? '已归档' : '正常' }}</template>
                </el-table-column>
                <el-table-column label="创建时间" width="130">
                  <template #default="{ row }">{{ fmtDate(row.createdAt) }}</template>
                </el-table-column>
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="题目" name="questions">
              <el-table :data="outlineDetail.questions" size="small" empty-text="暂无关联题目，可在题库中把题目关联到本章节知识点">
                <el-table-column prop="title" label="题目" min-width="240" show-overflow-tooltip />
                <el-table-column label="题型" width="100">
                  <template #default="{ row }">{{ questionKindLabel(row.questionKind) }}</template>
                </el-table-column>
                <el-table-column label="难度" width="90">
                  <template #default="{ row }">{{ difficultyLabel(row.difficulty) }}</template>
                </el-table-column>
                <el-table-column prop="maxScore" label="分值" width="80" />
                <el-table-column prop="knowledgePoints" label="知识点" min-width="150" show-overflow-tooltip />
              </el-table>
            </el-tab-pane>
            <el-tab-pane label="学习数据" name="stats">
              <template v-if="outlineDetailType === 'unit'">
                <el-descriptions :column="2" border size="small">
                  <el-descriptions-item label="资料数">{{ outlineDetail.unit.materialCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="知识点数">{{ outlineDetail.unit.pointCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="关联题目">{{ outlineDetail.unit.questionCount || 0 }}</el-descriptions-item>
                  <el-descriptions-item label="班级完成率">
                    {{ outlineDetail.unit.completionRate !== null && outlineDetail.unit.completionRate !== undefined
                      ? outlineDetail.unit.completionRate + '%' : '暂无数据' }}
                  </el-descriptions-item>
                </el-descriptions>
                <p class="cell-meta outline-detail-tip">
                  完成率按已归档资料任务点的学生完成情况统计；未发布或未开启任务点的资料不计入。
                </p>
              </template>
              <p v-else class="cell-meta">该知识点暂无学习数据，掌握度统计将在后续版本提供。</p>
            </el-tab-pane>
          </el-tabs>
        </template>
      </div>
    </el-dialog>

    <!-- ============ 教学班设置 ============ -->
    <el-dialog v-model="classroomOpen" title="教学班设置" width="760px" append-to-body>
      <div v-loading="classroomLoading">
        <template v-if="classroom">
          <el-descriptions :column="2" border size="small" title="课程信息">
            <el-descriptions-item label="课程名称">{{ classroom.courseTitle || '-' }}</el-descriptions-item>
            <el-descriptions-item label="课程简介">{{ classroom.courseSummary || '-' }}</el-descriptions-item>
            <el-descriptions-item label="教学班名称">{{ classroom.className || '-' }}</el-descriptions-item>
            <el-descriptions-item label="教学班代码">{{ classroom.classCode || '-' }}</el-descriptions-item>
            <el-descriptions-item label="授课学期">
              {{ classroom.termName || '-' }}（{{ fmtDate(classroom.termStartsOn) }} ~ {{ fmtDate(classroom.termEndsOn) }}）
            </el-descriptions-item>
            <el-descriptions-item label="开放范围">{{ classroom.openScope || '-' }}</el-descriptions-item>
            <el-descriptions-item label="学生人数">{{ classroom.studentCount ?? 0 }} 人</el-descriptions-item>
            <el-descriptions-item label="我的角色">{{ teacherRoleLabel(classroom.teacherRole) }}</el-descriptions-item>
          </el-descriptions>

          <div class="section-title">任课/协同教师（{{ (classroom.teachers || []).length }}）</div>
          <el-table :data="classroom.teachers || []" border size="small" empty-text="暂无任课教师记录">
            <el-table-column label="姓名" min-width="140">
              <template #default="{ row }">{{ row.nickName || row.userName || `用户 #${row.userId}` }}</template>
            </el-table-column>
            <el-table-column label="角色" width="120">
              <template #default="{ row }">{{ teacherRoleLabel(row.teacherRole) }}</template>
            </el-table-column>
            <el-table-column label="权限" min-width="180">
              <template #default="{ row }">
                <el-tag v-if="row.canPublish" size="small" class="mr6">发布</el-tag>
                <el-tag v-if="row.canGrade" size="small" class="mr6">批改</el-tag>
                <el-tag v-if="row.canExport" size="small" class="mr6">导出</el-tag>
                <span v-if="!row.canPublish && !row.canGrade && !row.canExport">-</span>
              </template>
            </el-table-column>
          </el-table>
        </template>
      </div>
      <template #footer>
        <el-button @click="classroomOpen = false">关 闭</el-button>
      </template>
    </el-dialog>
  </div>
</template>

<script setup name="TeacherStudio">
import { computed, onBeforeUnmount, onMounted, reactive, ref, watch, defineAsyncComponent } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { saveAs } from 'file-saver'
import {
  getOverview,
  createResource,
  createLinkResource,
  createResourceVersion,
  listResourceVersions,
  getVersion,
  getVersionPage,
  downloadVersion,
  createRelease,
  revokeRelease,
  retryVersionConversion,
  updateResource,
  updateResourceStatus,
  createQuestion,
  createQuestionVersion,
  listQuestions,
  updateQuestionStatus,
  createOutlineUnit,
  updateOutlineUnit,
  deleteOutlineUnit,
  reorderOutlineUnit,
  getOutlineUnitDetail,
  createKnowledgePoint,
  updateKnowledgePoint,
  deleteKnowledgePoint,
  getKnowledgePointDetail,
  listClassrooms,
  exportQuestionsExcel,
  previewQuestionImport,
  importQuestions,
  exportResourcesZip,
  importResourcesZip,
  listFolders,
  createFolder,
  updateFolder,
  deleteFolder,
  archiveQuestionFolder,
  publishFolder,
  revokeFolder,
  downloadFolder,
  moveToFolder,
  composeQuestions,
  shareQuestions,
  withdrawSharedQuestions,
  createPreviewTask,
  updateResourceEnhance
} from '@/api/education/group3'
import { useGroup3Binding } from './useGroup3Binding'
import { PROCESSING_LABELS, PROCESSING_STATUS, RELEASE_SOURCE } from './constants'
import Group3ContextBar from './Group3ContextBar.vue'
import QuestionLibraryDialog from './QuestionLibraryDialog.vue'

const props = defineProps({
  contextBindingId: { type: String, default: '' },
  canManageOutline: { type: Boolean, default: false }
})
const { list, bindingId, current, loading, error, load, select } = useGroup3Binding(() => props.contextBindingId)

const resourceSources = ['本地上传', '云盘', '添加网址', '在线图书', '教学资源库', '跨课导入', '课程思政']
const questionImportLabels = ['模板导入', '智能导入', '跨课导入']
const questionKindOptions = [
  { value: 'SINGLE', label: '单选题' },
  { value: 'MULTIPLE', label: '多选题' },
  { value: 'TRUE_FALSE', label: '判断题' },
  { value: 'SHORT_TEXT', label: '简答题' }
]
const difficultyOptions = [
  { value: 'EASY', label: '简单' },
  { value: 'MEDIUM', label: '中等' },
  { value: 'HARD', label: '困难' }
]
const labels = {
  SINGLE: '单选题',
  MULTIPLE: '多选题',
  TRUE_FALSE: '判断题',
  SHORT_TEXT: '简答题',
  READY: '可用',
  PENDING: '处理中',
  FAILED: '处理失败',
  ACTIVE: '启用',
  ARCHIVED: '已归档',
  DRAFT: '草稿',
  UPCOMING: '未开放',
  OPEN: '进行中',
  CLOSED: '已截止',
  PRIVATE: '仅教师可见'
}
const roleLabels = { LEAD: '主讲教师', PRIMARY: '主讲教师', CO_TEACHER: '协同教师', ASSIST: '助教', ASSISTANT: '助教', GUEST: '客座' }

const studioTab = ref('resources')
const overview = ref(null)
const overviewLoading = ref(false)

const resourceSearch = ref('')
const folders = ref([])
const currentFolderId = ref(null)

const folderDialogOpen = ref(false)
const folderDialogType = ref('resource')
const folderForm = reactive({ name: '', visibility: 'PUBLIC' })
const resourceTableRef = ref()
const selectedRows = ref([])

const uploadOpen = ref(false)
const uploading = ref(false)
const uploadRef = ref()
const folderInputRef = ref()
const uploadFiles = ref([])
const uploadUnitId = ref(null)

const linkOpen = ref(false)
const linkLoading = ref(false)
const linkKind = ref('URL') // URL 或 BOOK
const linkForm = reactive({
  title: '',
  externalUri: '',
  unitId: null
})

/* 公共资源库 */
const PublicLibrary = defineAsyncComponent(() => import('./PublicLibrary.vue'))
const libraryVisible = ref(false)
const ideologyVisible = ref(false)

const versionOpen = ref(false)
const versionSaving = ref(false)
const versionTarget = ref(null)
const versionFile = ref(null)

const versionsOpen = ref(false)
const versionsLoading = ref(false)
const versions = ref([])
const versionsResourceTitle = ref('')

const previewOpen = ref(false)
const previewLoading = ref(false)
const previewError = ref('')
const previewMode = ref('pages')
const previewTitle = ref('')
const previewVersionId = ref(null)
const previewPages = ref([])
const previewPageIndex = ref(0)
const previewLink = ref('')
const previewVideoUrl = ref('')
const previewWatermark = ref('')
let previewObjectUrls = []

const questionSearch = ref('')
const questionKind = ref('')
const questionDifficulty = ref('')
const questionPoint = ref('')
const questionFolders = ref([])
const currentQuestionFolderId = ref(null)
const showQuestionDetail = ref(true)
const questionBindingFilter = ref('')
const externalQuestions = ref([])
const externalQuestionFolders = ref([])
const externalQuestionsLoading = ref(false)

const questionImportOpen = ref(false)
const questionImportLoading = ref(false)
const questionImportSaving = ref(false)
const questionImportRows = ref([])
const questionImportFileName = ref('')
const questionImportInputRef = ref(null)
const resourceImporting = ref(false)
const resourceZipInputRef = ref(null)

const questionOpen = ref(false)
const questionSaving = ref(false)
const questionEdit = ref(null)
const questionForm = reactive({
  title: '',
  questionKind: 'SINGLE',
  maxScore: 10,
  stem: '',
  options: '选项 A\n选项 B\n选项 C',
  answer: '',
  explanation: '',
  scoringRubric: '',
  knowledgePointIds: [],
  difficulty: 'MEDIUM',
  tags: ''
})

const questionPreviewOpen = ref(false)
const questionPreviewList = ref([])
const questionPreviewIndex = ref(0)

const questionLibraryVisible = ref(false)
const questionTableRef = ref(null)
const questionSelection = ref([])
const composeOpen = ref(false)
const composeLoading = ref(false)
const composeResult = ref(null)
const composeForm = reactive({
  questionKind: '',
  difficulty: '',
  knowledgePoint: '',
  tag: '',
  count: 10,
  mode: 'RANDOM'
})

const paperPublishOpen = ref(false)
const paperPublishing = ref(false)
const paperForm = reactive({
  title: '',
  instructions: '',
  opensAt: null,
  closesAt: null,
  answerOpensAt: null
})
const paperPublishCount = computed(() => {
  const questions = composeResult.value?.questions
  return Array.isArray(questions) ? questions.length : 0
})

const enhanceOpen = ref(false)
const enhanceSaving = ref(false)
const enhanceTarget = ref(null)
const enhanceForm = reactive({
  downloadEnabled: true,
  watermarkText: '',
  taskEnabled: false,
  taskRequiredSeconds: 0
})

const outlineSearch = ref('')
const collapsedChapters = ref([])
const editorOpen = ref(false)
const detailPoint = ref(null)
const outlineDetailOpen = ref(false)
const outlineDetailType = ref('unit')
const outlineDetailLoading = ref(false)
const outlineDetail = ref(null)
const outlineDetailTab = ref('content')
const outlineDetailContent = ref('')
const outlineContentSaving = ref(false)

const unitDialogOpen = ref(false)
const unitDialogMode = ref('create')
const unitDialogKind = ref('CHAPTER')
const unitSaving = ref(false)
const unitForm = reactive({ unitId: null, title: '', sortOrder: 0, parentUnitId: null, contentText: '' })

const pointDialogOpen = ref(false)
const pointSaving = ref(false)
const pointForm = reactive({ knowledgePointId: null, unitId: null, code: '', name: '', contentText: '' })

const classroomOpen = ref(false)
const classroomLoading = ref(false)
const classroom = ref(null)

const resourceList = computed(() =>
  (overview.value?.resources || []).filter(item => String(item.origin || 'STUDIO').toUpperCase() !== 'PREPARE')
)
const knowledgePoints = computed(() => overview.value?.knowledgePoints || [])

const questionReadonly = computed(
  () => !!questionBindingFilter.value && String(questionBindingFilter.value) !== String(bindingId.value)
)
const questionCourseOptions = computed(() =>
  list.value.filter(item => String(item.bindingId) !== String(bindingId.value))
)
const viewedQuestionFolders = computed(() =>
  questionReadonly.value ? externalQuestionFolders.value : questionFolders.value
)

const questionList = computed(() => {
  const source = questionReadonly.value ? externalQuestions.value : overview.value?.questions || []
  return source.map(question => ({
    ...question,
    options: Array.isArray(question.options) ? question.options : parseJson(question.optionsJson) || [],
    answer: question.answer != null ? question.answer : parseJson(question.answerJson)
  }))
})

const activeFolder = computed(() => folders.value.find(folder => folder.id === currentFolderId.value) || null)
const activeQuestionFolder = computed(
  () => viewedQuestionFolders.value.find(folder => folder.id === currentQuestionFolderId.value) || null
)

const visibleResources = computed(() => {
  const keyword = resourceSearch.value.trim().toLowerCase()
  let rows = resourceList.value
  if (activeFolder.value) {
    rows = rows.filter(item => String(item.folderId || '') === String(activeFolder.value.id))
  } else {
    // 根目录只展示未归档资料，已移入文件夹的不再重复出现
    rows = rows.filter(item => !item.folderId)
  }
  if (keyword) rows = rows.filter(item => String(item.title || '').toLowerCase().includes(keyword))
  return rows
})

const visibleFolders = computed(() => {
  if (activeFolder.value) return []
  const keyword = resourceSearch.value.trim().toLowerCase()
  if (!keyword) return folders.value
  return folders.value.filter(folder => String(folder.name || '').toLowerCase().includes(keyword))
})

const resourceTableRows = computed(() => {
  const rows = visibleFolders.value.map(folder => ({
    rowType: 'folder',
    folder,
    key: `folder-${folder.id}`
  }))
  visibleResources.value.forEach(item => {
    rows.push({ rowType: 'resource', resource: item, key: `resource-${item.resourceId}` })
  })
  return rows
})

const resourceEmptyText = computed(() =>
  activeFolder.value ? '此文件夹暂无资料，返回“全部文件”可查看课程全部资料' : '课程还没有资料，点击“添加资料”上传 PPTX、PDF、视频、图片或文档'
)

const moveTargetFolders = computed(() => folders.value.filter(folder => !folder.demo))

const unitOptions = computed(() => {
  const units = overview.value?.outline || []
  const options = []
  units
    .filter(unit => !unit.parentUnitId)
    .forEach(chapter => {
      options.push({ value: String(chapter.unitId), label: chapter.title })
      units
        .filter(unit => String(unit.parentUnitId) === String(chapter.unitId))
        .forEach(section => {
          options.push({ value: String(section.unitId), label: `　├ ${chapter.title} / ${section.title}` })
        })
    })
  return options
})

const filteredQuestions = computed(() => {
  let rows = questionList.value
  if (activeQuestionFolder.value) {
    rows = rows.filter(question => String(question.folderId || '') === String(activeQuestionFolder.value.id))
  } else {
    // 根目录只展示未归档题目，已放入文件夹的题目不再重复出现
    rows = rows.filter(question => !question.folderId)
  }
  if (questionKind.value) rows = rows.filter(question => question.questionKind === questionKind.value)
  if (questionDifficulty.value) rows = rows.filter(question => question.difficulty === questionDifficulty.value)
  if (questionPoint.value) rows = rows.filter(question => String(question.knowledgePoints || '').includes(questionPoint.value))
  const keyword = questionSearch.value.trim().toLowerCase()
  if (keyword) {
    rows = rows.filter(
      question =>
        String(question.title || '').toLowerCase().includes(keyword) ||
        String(question.stem || '').toLowerCase().includes(keyword)
    )
  }
  return rows
})

const showQuestionFolders = computed(
  () => !activeQuestionFolder.value && !questionKind.value && !questionDifficulty.value && !questionPoint.value
)

const questionTableRows = computed(() => {
  const rows = []
  if (showQuestionFolders.value) {
    const keyword = questionSearch.value.trim().toLowerCase()
    viewedQuestionFolders.value
      .filter(folder => !keyword || String(folder.name || '').toLowerCase().includes(keyword))
      .forEach(folder => rows.push({ rowType: 'folder', folder, key: `question-folder-${folder.id}` }))
  }
  filteredQuestions.value.forEach((question, index) => {
    rows.push({ rowType: 'question', question, index, key: `question-${question.questionId}` })
  })
  return rows
})

const outlineTree = computed(() => {
  const units = overview.value?.outline || []
  const points = knowledgePoints.value
  const pointsOf = unitId => points.filter(point => String(point.unitId) === String(unitId))
  const chapters = units
    .filter(unit => !unit.parentUnitId)
    .map((chapter, index) => ({
      ...chapter,
      number: index + 1,
      sortOrder: chapter.sortOrder ?? index,
      points: pointsOf(chapter.unitId),
      sections: units
        .filter(unit => String(unit.parentUnitId) === String(chapter.unitId))
        .map((section, sectionIndex) => ({
          ...section,
          code: `${index + 1}.${sectionIndex + 1}`,
          sortOrder: section.sortOrder ?? sectionIndex,
          points: pointsOf(section.unitId)
        }))
    }))
  const orphanPoints = points.filter(point => !units.some(unit => String(unit.unitId) === String(point.unitId)))
  return { chapters, orphanPoints }
})

const outlineRows = computed(() => {
  const keyword = outlineSearch.value.trim().toLowerCase()
  const hit = text => String(text || '').toLowerCase().includes(keyword)
  const rows = []
  outlineTree.value.chapters.forEach(chapter => {
    const chapterHit = !keyword || hit(chapter.title)
    const sections = chapter.sections
      .map(section => ({
        ...section,
        points: chapterHit ? section.points : section.points.filter(point => hit(point.name))
      }))
      .filter(section => chapterHit || hit(section.title) || section.points.length)
    const ownPoints = chapterHit ? chapter.points : chapter.points.filter(point => hit(point.name))
    if (keyword && !chapterHit && !sections.length && !ownPoints.length) return
    rows.push({ key: `chapter-${chapter.unitId}`, rowType: 'chapter', unit: chapter, title: chapter.title, number: chapter.number })
    if (!keyword && isCollapsed(chapter.unitId)) return
    sections.forEach(section => {
      rows.push({
        key: `section-${section.unitId}`,
        rowType: 'section',
        unit: section,
        title: section.title,
        number: section.code
      })
      section.points.forEach(point =>
        rows.push({ key: `point-${pointId(point)}`, rowType: 'point', point })
      )
    })
    ownPoints.forEach(point => rows.push({ key: `point-${pointId(point)}`, rowType: 'point', point }))
  })
  outlineTree.value.orphanPoints
    .filter(point => !keyword || hit(point.name))
    .forEach(point => rows.push({ key: `point-${pointId(point)}`, rowType: 'point', point }))
  return rows
})

const editorRows = computed(() => {
  const rows = []
  outlineTree.value.chapters.forEach(chapter => {
    rows.push({ key: `editor-chapter-${chapter.unitId}`, rowType: 'chapter', unit: chapter, title: chapter.title, number: chapter.number })
    chapter.sections.forEach(section => {
      rows.push({
        key: `editor-section-${section.unitId}`,
        rowType: 'section',
        unit: section,
        title: section.title,
        number: section.code
      })
      section.points.forEach(point =>
        rows.push({ key: `editor-point-${pointId(point)}`, rowType: 'point', point, title: point.name })
      )
    })
    chapter.points.forEach(point =>
      rows.push({ key: `editor-point-${pointId(point)}`, rowType: 'point', point, title: point.name })
    )
  })
  outlineTree.value.orphanPoints.forEach(point =>
    rows.push({ key: `editor-point-${pointId(point)}`, rowType: 'point', point, title: point.name })
  )
  return rows
})

const questionPreviewCurrent = computed(() => questionPreviewList.value[questionPreviewIndex.value] || null)
const questionPreviewAnswer = computed(() => {
  const question = questionPreviewCurrent.value
  return question ? formatAnswer(question.answer, question.questionKind) : ''
})
const questionPreviewAnalysis = computed(() => {
  const question = questionPreviewCurrent.value
  const answer = question?.answer || {}
  return answer.analysis || answer.explanation || '暂无答案解析'
})

const showOptionsField = computed(
  () => questionForm.questionKind === 'SINGLE' || questionForm.questionKind === 'MULTIPLE'
)
const showRubricField = computed(() => questionForm.questionKind === 'SHORT_TEXT')
const answerPlaceholder = computed(() => {
  if (questionForm.questionKind === 'TRUE_FALSE') return '填写 true 或 false'
  if (questionForm.questionKind === 'SHORT_TEXT') return '填写参考答案'
  if (questionForm.questionKind === 'MULTIPLE') return '例如：1,3'
  return '例如：2'
})
const answerHelp = computed(() => {
  if (questionForm.questionKind === 'TRUE_FALSE') return '判断题填写 true 或 false。'
  if (questionForm.questionKind === 'SHORT_TEXT') return '简答题填写参考答案，供教师端使用。'
  if (questionForm.questionKind === 'MULTIPLE') return '多选题填写逗号分隔的选项序号。'
  return '单选题填写一个选项序号，从 1 开始。'
})

const detailPointUnitTitle = computed(() => {
  const point = detailPoint.value
  if (!point) return '-'
  const unit = (overview.value?.outline || []).find(item => String(item.unitId) === String(point.unitId))
  return unit ? unit.title : '未归档章节'
})

/* ---------- 通用工具 ---------- */

function parseJson(value) {
  if (value == null) return null
  if (typeof value === 'object') return value
  try {
    return JSON.parse(value)
  } catch (e) {
    return null
  }
}

function formatFileSize(bytes) {
  const size = Number(bytes) || 0
  if (size < 1024) return size + ' B'
  if (size < 1024 * 1024) return (size / 1024).toFixed(1) + ' KB'
  if (size < 1024 * 1024 * 1024) return (size / 1024 / 1024).toFixed(1) + ' MB'
  return (size / 1024 / 1024 / 1024).toFixed(2) + ' GB'
}

function formatTime(value) {
  if (!value) return '不限'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return new Intl.DateTimeFormat('zh-CN', {
    month: '2-digit',
    day: '2-digit',
    hour: '2-digit',
    minute: '2-digit',
    hour12: false
  }).format(date)
}

function fmtDate(value) {
  return value ? String(value).slice(0, 10) : '-'
}

function resourceIcon(item) {
  const mime = String(item?.mimeType || '').toLowerCase()
  if (mime.includes('presentation') || mime.includes('powerpoint')) return 'PPT'
  if (mime.includes('pdf')) return 'PDF'
  if (mime.startsWith('video/')) return 'VID'
  if (mime.startsWith('image/')) return 'IMG'
  if (item?.resourceKind === 'LINK') return 'URL'
  return 'DOC'
}

function resourceMeta(item) {
  const status = item.processingStatus || PROCESSING_STATUS.PENDING
  const parts = [
    status === PROCESSING_STATUS.READY ? (item.studioReleaseId ? '学生端可见' : '已下架') : statusLabelOf(status),
    `V${item.versionNo || 1}`,
    `${Number(item.pageCount || 0)} 页`
  ]
  return parts.join(' · ')
}

function statusLabelOf(status) {
  const key = String(status || '').toUpperCase()
  return PROCESSING_LABELS[key] || labels[key] || status || '未知'
}

function statusTagType(status) {
  const value = String(status || '').toUpperCase()
  if (['READY', 'ACTIVE', 'PUBLISHED'].includes(value)) return 'success'
  if (['PENDING', 'UPCOMING', 'DRAFT'].includes(value)) return 'warning'
  if (['FAILED', 'CLOSED', 'ARCHIVED'].includes(value)) return 'info'
  return 'info'
}

function teacherRoleLabel(role) {
  return roleLabels[role] || role || '-'
}

function questionKindLabel(kind) {
  return labels[kind] || kind || '-'
}

function difficultyLabel(value) {
  const key = String(value || '').toUpperCase()
  return difficultyOptions.find(item => item.value === key)?.label || key || '-'
}

function difficultyTagType(value) {
  const key = String(value || '').toUpperCase()
  if (key === 'HARD') return 'danger'
  if (key === 'EASY') return 'success'
  return 'warning'
}

function pointId(point) {
  return point?.knowledgePointId || point?.pointId || point?.name || ''
}

function formatAnswer(answer, kind) {
  if (answer == null) return '未设置'
  if (kind === 'TRUE_FALSE') return answer.correctBoolean ? '正确' : '错误'
  if (kind === 'SINGLE') return `选项 ${Number(answer.choiceIndex) + 1}`
  if (kind === 'MULTIPLE') return `选项 ${(answer.choiceIndices || []).map(index => Number(index) + 1).join('、')}`
  if (kind === 'SHORT_TEXT') return answer.referenceAnswer || '参考答案'
  return JSON.stringify(answer)
}

function handleActionError(e, fallback = '操作失败') {
  if (e?.cancelled || e === 'cancel' || e === 'close') return
  ElMessage.error(e?.message || fallback)
}

/* ---------- 上下文与总览 ---------- */

async function refresh() {
  await load()
  resetStudioState()
  await loadOverview()
}

function handleSelect(value) {
  select(value)
  resetStudioState()
  loadOverview()
}

function resetStudioState() {
  resourceSearch.value = ''
  currentFolderId.value = null
  selectedRows.value = []
  questionSearch.value = ''
  questionKind.value = ''
  questionDifficulty.value = ''
  questionPoint.value = ''
  questionBindingFilter.value = ''
  externalQuestions.value = []
  externalQuestionFolders.value = []
  currentQuestionFolderId.value = null
  outlineSearch.value = ''
  collapsedChapters.value = []
}

async function loadFilterQuestions() {
  if (!questionReadonly.value) {
    externalQuestions.value = []
    externalQuestionFolders.value = []
    return
  }
  externalQuestionsLoading.value = true
  currentQuestionFolderId.value = null
  try {
    const res = await listQuestions(questionBindingFilter.value)
    externalQuestions.value = Array.isArray(res.data) ? res.data : []
    await loadExternalQuestionFolders(questionBindingFilter.value)
  } catch (e) {
    externalQuestions.value = []
    externalQuestionFolders.value = []
    handleActionError(e, '题库加载失败')
  } finally {
    externalQuestionsLoading.value = false
  }
}

watch(questionBindingFilter, loadFilterQuestions)

async function loadOverview() {
  if (!bindingId.value) {
    overview.value = null
    return
  }
  overviewLoading.value = true
  try {
    const res = await getOverview(bindingId.value)
    overview.value = res.data || {}
    await Promise.all([loadResourceFolders(), loadQuestionFolders()])
  } catch (e) {
    overview.value = null
    handleActionError(e, '课程总览加载失败')
  } finally {
    overviewLoading.value = false
  }
}

// 上传后后端异步转换，轮询总览直到没有 PENDING 的资料/版本
async function pollPendingConversion() {
  const hasPending = () => {
    const o = overview.value
    if (!o) return false
    const check = list => Array.isArray(list) && list.some(i => i.processingStatus === 'PENDING')
    return check(o.resources) || check(o.materialVersions)
  }
  let tries = 0
  while (hasPending() && tries < 60) {
    await new Promise(r => setTimeout(r, 3000))
    await loadOverview()
    tries += 1
  }
}

/* ---------- 文件夹（持久化到课程资料库） ---------- */

function normalizeFolder(folder) {
  return {
    id: String(folder.id),
    name: folder.name,
    visibility: folder.visibility || 'PUBLIC',
    createdAt: folder.createdAt,
    archivedAt: folder.archivedAt || ''
  }
}

async function loadResourceFolders() {
  if (!bindingId.value) {
    folders.value = []
    return
  }
  try {
    const res = await listFolders(bindingId.value, 'RESOURCE')
    folders.value = (Array.isArray(res.data) ? res.data : []).map(normalizeFolder)
  } catch (e) {
    folders.value = []
  }
}

async function loadQuestionFolders() {
  if (!bindingId.value) {
    questionFolders.value = []
    return
  }
  try {
    const res = await listFolders(bindingId.value, 'QUESTION')
    questionFolders.value = (Array.isArray(res.data) ? res.data : []).map(normalizeFolder)
  } catch (e) {
    questionFolders.value = []
  }
}

async function loadExternalQuestionFolders(keyBindingId) {
  if (!keyBindingId) {
    externalQuestionFolders.value = []
    return
  }
  try {
    const res = await listFolders(keyBindingId, 'QUESTION')
    externalQuestionFolders.value = (Array.isArray(res.data) ? res.data : []).map(normalizeFolder)
  } catch (e) {
    externalQuestionFolders.value = []
  }
}

function folderCount(folder) {
  return resourceList.value.filter(item => String(item.folderId || '') === String(folder.id)).length
}

function questionFolderCount(folder) {
  return questionList.value.filter(item => String(item.folderId || '') === String(folder.id)).length
}

function enterFolder(id) {
  currentFolderId.value = id
  resourceSearch.value = ''
}

function backToAll() {
  currentFolderId.value = null
}

function createResourceFolder() {
  openFolderDialog('resource')
}

function openFolderDialog(type) {
  folderDialogType.value = type
  Object.assign(folderForm, { name: '新建文件夹', visibility: 'PUBLIC' })
  folderDialogOpen.value = true
}

async function submitFolder() {
  const name = folderForm.name.trim()
  if (!name) {
    ElMessage.warning('文件夹名称不能为空')
    return
  }
  const resourceFolder = folderDialogType.value === 'resource'
  try {
    await createFolder({
      bindingId: Number(bindingId.value),
      kind: resourceFolder ? 'RESOURCE' : 'QUESTION',
      name,
      visibility: folderForm.visibility
    })
    if (resourceFolder) {
      await loadResourceFolders()
      currentFolderId.value = null
    } else {
      await loadQuestionFolders()
      currentQuestionFolderId.value = null
    }
    folderDialogOpen.value = false
    ElMessage.success('文件夹已创建')
  } catch (e) {
    handleActionError(e, '文件夹创建失败')
  }
}

function folderVisibilityLabel(visibility) {
  if (visibility === 'PRIVATE') return '仅自己'
  if (visibility === 'PARTIAL') return '部分可见'
  return ''
}

/* ---------- 资料批量操作 ---------- */

function onResourceSelectionChange(rows) {
  selectedRows.value = (rows || []).filter(row => row.rowType === 'resource')
}

function clearResourceSelection() {
  resourceTableRef.value?.clearSelection()
  selectedRows.value = []
}

async function batchDownload() {
  const rows = selectedRows.value.slice()
  const tasks = rows.map(row =>
    row.resource.versionId
      ? Promise.resolve(
          handleDownload(row.resource.versionId, row.resource.bindingId || bindingId.value)
        ).then(
          () => ({ ok: true }),
          () => ({ ok: false })
        )
      : Promise.resolve({ ok: false })
  )
  const results = await Promise.allSettled(tasks)
  const failed = results.filter(item => item.status === 'rejected' || !item.value?.ok).length
  if (failed) ElMessage.warning(`批量下载完成：成功 ${results.length - failed} 个，失败 ${failed} 个`)
  else ElMessage.success(`已下载 ${results.length} 个资料`)
  clearResourceSelection()
}

function batchDelete() {
  const rows = selectedRows.value.slice()
  if (!rows.length) return
  const names = rows.map(row => row.resource.title || '未命名资料')
  const preview = names.slice(0, 5).join('、') + (names.length > 5 ? ` 等 ${names.length} 项` : '')
  ElMessageBox.confirm(
    `将从列表中移除以下资料：${preview}。删除后不可在默认列表中查看。`,
    `确认删除选中的 ${rows.length} 项资料？`,
    { type: 'warning', confirmButtonText: '删除', cancelButtonText: '取消' }
  )
    .then(() =>
      Promise.allSettled(
        rows.map(row =>
          updateResourceStatus(row.resource.resourceId, { bindingId: Number(bindingId.value), status: 'ARCHIVED' })
        )
      )
    )
    .then(results => {
      const failed = results.filter(item => item.status === 'rejected').length
      if (failed) ElMessage.warning(`批量删除完成：成功 ${results.length - failed} 项，失败 ${failed} 项`)
      else ElMessage.success(`已删除 ${results.length} 项资料`)
      clearResourceSelection()
      loadOverview()
    })
    .catch(e => handleActionError(e))
}

async function moveSelectedToFolder(targetId) {
  const rows = selectedRows.value.slice()
  if (!rows.length) return
  const target = targetId ? folders.value.find(folder => folder.id === String(targetId)) : null
  if (targetId && !target) {
    ElMessage.error('目标文件夹不存在')
    return
  }
  try {
    await moveToFolder({
      bindingId: Number(bindingId.value),
      kind: 'RESOURCE',
      folderId: target ? Number(target.id) : null,
      ids: rows.map(row => Number(row.resource.resourceId))
    })
    clearResourceSelection()
    await loadOverview()
    ElMessage.success(`已将 ${rows.length} 项资料移动到“${target ? target.name : '全部文件'}”`)
  } catch (e) {
    handleActionError(e, '移动失败')
  }
}

/* ---------- 资料操作 ---------- */

function handleSourceCommand(label) {
  if (label === '本地上传') {
    uploadUnitId.value = null
    uploadFiles.value = []
    uploadRef.value?.clearFiles()
    uploadOpen.value = true
    return
  }
  if (label === '添加网址') {
    linkKind.value = 'URL'
    linkForm.title = ''
    linkForm.externalUri = ''
    linkForm.unitId = null
    linkOpen.value = true
    return
  }
  if (label === '在线图书') {
    linkKind.value = 'BOOK'
    linkForm.title = ''
    linkForm.externalUri = ''
    linkForm.unitId = null
    linkOpen.value = true
    return
  }
  if (label === '教学资源库') {
    libraryVisible.value = true
    return
  }
  if (label === '课程思政') {
    ideologyVisible.value = true
    return
  }
  ElMessage.info(`“${label}”来源暂未接入，当前请使用本地上传、添加网址或在线图书`)
}

async function submitLink() {
  if (!linkForm.title.trim()) {
    ElMessage.warning(linkKind.value === 'BOOK' ? '请输入图书名称' : '请输入资料名称')
    return
  }
  if (!linkForm.externalUri.trim()) {
    ElMessage.warning('请输入链接地址')
    return
  }
  if (!/^https?:\/\/.*/.test(linkForm.externalUri.trim())) {
    ElMessage.warning('链接地址必须以 http:// 或 https:// 开头')
    return
  }
  linkLoading.value = true
  try {
    await createLinkResource({
      bindingId: bindingId.value,
      title: linkForm.title.trim(),
      externalUri: linkForm.externalUri.trim(),
      unitId: linkForm.unitId,
      folderId: currentFolderId.value || null,
      linkKind: linkKind.value,
      releaseSource: 'NONE',
      origin: 'STUDIO'
    })
    ElMessage.success(linkKind.value === 'BOOK' ? '在线图书已添加' : '网址已添加')
    linkOpen.value = false
    await loadOverview()
  } catch (e) {
    // 错误信息已由拦截器处理
  } finally {
    linkLoading.value = false
  }
}

function removeUploadFile(index) {
  uploadFiles.value.splice(index, 1)
}

function triggerFolderSelect() {
  folderInputRef.value?.click()
}

function onFolderSelected(event) {
  const files = Array.from(event.target.files || [])
  files.forEach(file => {
    uploadFiles.value.push({
      name: file.webkitRelativePath || file.name,
      size: file.size,
      raw: file
    })
  })
  event.target.value = ''
}

async function submitUpload() {
  const files = uploadFiles.value.map(item => item.raw).filter(Boolean)
  if (!files.length) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  uploading.value = true
  let done = 0
  try {
    for (const file of files) {
      const formData = new FormData()
      formData.append('bindingId', bindingId.value)
      formData.append('releaseSource', RELEASE_SOURCE.STUDIO)
      formData.append('title', file.name.replace(/\.[^.]+$/, ''))
      if (uploadUnitId.value) formData.append('unitId', uploadUnitId.value)
      if (currentFolderId.value) formData.append('folderId', currentFolderId.value)
      formData.append('file', file)
      await createResource(formData)
      done += 1
    }
    ElMessage.success(`已上传 ${done} 个文件，学生端课程资料可见`)
    uploadOpen.value = false
    uploadFiles.value = []
    uploadRef.value?.clearFiles()
    await loadOverview()
    await pollPendingConversion()
  } catch (e) {
    ElMessage.error(e?.message || '上传失败')
  } finally {
    uploading.value = false
  }
}

function openNewVersion(resource) {
  versionTarget.value = resource
  versionFile.value = null
  versionOpen.value = true
}

function onVersionFileChange(uploadFile) {
  versionFile.value = uploadFile?.raw || null
}

async function submitNewVersion() {
  if (!versionTarget.value?.resourceId) return
  if (!versionFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  versionSaving.value = true
  try {
    const formData = new FormData()
    formData.append('bindingId', bindingId.value)
    formData.append('releaseSource', RELEASE_SOURCE.STUDIO)
    formData.append('file', versionFile.value)
    const res = await createResourceVersion(versionTarget.value.resourceId, formData)
    const data = res.data || {}
    if (!data.processingStatus || data.processingStatus === 'READY') {
      ElMessage.success('新版本已保存')
    } else if (data.processingStatus === 'PENDING') {
      ElMessage.info('新版本已上传，正在转换')
    } else {
      ElMessage.warning(data.processingMessage || '新版本已保存，但转换失败')
    }
    versionOpen.value = false
    await loadOverview()
    await pollPendingConversion()
  } catch (e) {
    ElMessage.error(e?.message || '新版本上传失败')
  } finally {
    versionSaving.value = false
  }
}

function openRenameResource(resource) {
  ElMessageBox.prompt('请输入新的资料名称', '修改资料', {
    inputValue: resource.title || '',
    inputPattern: /\S+/,
    inputErrorMessage: '资料名称不能为空',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(({ value }) =>
      updateResource(resource.resourceId, { bindingId: Number(bindingId.value), title: value.trim() })
    )
    .then(() => {
      ElMessage.success('资料已更新')
      loadOverview()
    })
    .catch(e => handleActionError(e))
}

function openRenameFolder(folder) {
  ElMessageBox.prompt('请输入新的文件夹名称', '修改文件夹', {
    inputValue: folder.name || '',
    inputPattern: /\S+/,
    inputErrorMessage: '文件夹名称不能为空',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(({ value }) =>
      updateFolder(folder.id, {
        bindingId: Number(bindingId.value),
        kind: 'RESOURCE',
        name: value.trim(),
        visibility: folder.visibility || 'PUBLIC'
      })
    )
    .then(() => {
      ElMessage.success('文件夹已更新')
      loadOverview()
    })
    .catch(e => handleActionError(e))
}

function folderReleased(folder) {
  return resourceList.value.some(
    item => String(item.folderId || '') === String(folder.id) && item.studioReleaseId
  )
}

async function handleDownloadFolder(folder) {
  try {
    const blob = await downloadFolder(folder.id, bindingId.value)
    saveAs(blob, `${folder.name || '文件夹资料'}.zip`)
  } catch (e) {
    ElMessage.error(e?.message || '下载失败')
  }
}

async function toggleFolderRelease(folder) {
  const released = folderReleased(folder)
  try {
    if (released) {
      await revokeFolder(folder.id, bindingId.value)
      ElMessage.success('文件夹已下架，学生端不再可见')
    } else {
      await publishFolder(folder.id, bindingId.value)
      ElMessage.success('文件夹已上架，学生端可见')
    }
    await loadOverview()
  } catch (e) {
    handleActionError(e)
  }
}

function confirmArchive(resource, targetStatus) {
  const actionText = targetStatus === 'ARCHIVED' ? '归档' : '取消归档'
  const bodyText =
    targetStatus === 'ARCHIVED'
      ? `归档后“${resource.title || '该资料'}”将不再出现在默认列表中。`
      : `取消归档后“${resource.title || '该资料'}”将恢复为可用状态。`
  ElMessageBox.confirm(bodyText, `确认${actionText}“${resource.title || '该资料'}”？`, {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(() =>
      updateResourceStatus(resource.resourceId, { bindingId: Number(bindingId.value), status: targetStatus })
    )
    .then(() => {
      ElMessage.success(`资料已${actionText}`)
      loadOverview()
    })
    .catch(e => handleActionError(e))
}

function confirmRevoke(resource) {
  ElMessageBox.confirm(
    '下架后学生端立即不可见，之后可随时重新上架。',
    `确认下架“${resource.title}”在 ${current.value?.className || '当前教学班'} 的发布？`,
    { type: 'warning', confirmButtonText: '确定', cancelButtonText: '取消' }
  )
    .then(() => revokeRelease(resource.studioReleaseId, bindingId.value, '教师下架课程资料'))
    .then(() => {
      ElMessage.success('资料已下架，学生端不再可见')
      loadOverview()
    })
    .catch(e => handleActionError(e))
}

async function publishResource(resource) {
  try {
    await createRelease({
      bindingId: Number(bindingId.value),
      resourceVersionId: resource.versionId,
      opensAt: new Date().toISOString(),
      closesAt: null,
      source: RELEASE_SOURCE.STUDIO
    })
    ElMessage.success(
      resource.processingStatus === PROCESSING_STATUS.READY ? '资料已上架，学生端课程资料可见' : '资料已上架，转换完成后学生可见'
    )
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '上架失败')
  }
}

async function retryConversion(resource) {
  try {
    await retryVersionConversion(resource.versionId, Number(bindingId.value))
    ElMessage.success('已重新提交转换')
    await loadOverview()
    pollPendingConversion()
  } catch (e) {
    ElMessage.error(e?.message || '重试转换失败')
  }
}

function handleResourceCommand(command, resource) {
  if (command === 'preview') openPreview(resource.versionId, resource.bindingId || bindingId.value)
  if (command === 'versions') openVersions(resource)
  if (command === 'enhance') openEnhance(resource)
  if (command === 'revoke') confirmRevoke(resource)
  if (command === 'publish') publishResource(resource)
  if (command === 'retry') retryConversion(resource)
  if (command === 'newVersion') openNewVersion(resource)
  if (command === 'archive') confirmArchive(resource, 'ARCHIVED')
  if (command === 'unarchive') confirmArchive(resource, 'ACTIVE')
}

function openEnhance(resource) {
  enhanceTarget.value = resource
  Object.assign(enhanceForm, {
    downloadEnabled: resource.downloadEnabled !== 0,
    watermarkText: resource.watermarkText || '',
    taskEnabled: resource.taskEnabled === 1,
    taskRequiredSeconds: Number(resource.taskRequiredSeconds || 0)
  })
  enhanceOpen.value = true
}

async function submitEnhance() {
  if (!enhanceTarget.value?.resourceId) return
  enhanceSaving.value = true
  try {
    await updateResourceEnhance(enhanceTarget.value.resourceId, {
      bindingId: Number(bindingId.value),
      downloadEnabled: enhanceForm.downloadEnabled,
      watermarkText: enhanceForm.watermarkText,
      taskEnabled: enhanceForm.taskEnabled,
      taskRequiredSeconds: enhanceForm.taskEnabled ? Number(enhanceForm.taskRequiredSeconds) : 0
    })
    ElMessage.success('资料设置已保存')
    enhanceOpen.value = false
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    enhanceSaving.value = false
  }
}

async function openVersions(resource) {
  versionsResourceTitle.value = resource.title
  versionsOpen.value = true
  versionsLoading.value = true
  versions.value = []
  try {
    const res = await listResourceVersions(resource.resourceId, bindingId.value)
    versions.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    ElMessage.error(e?.message || '版本记录加载失败')
  } finally {
    versionsLoading.value = false
  }
}

/* ---------- 预览与下载 ---------- */

function rememberPreviewUrl(url) {
  previewObjectUrls.push(url)
  return url
}

function revokePreviewUrls() {
  previewObjectUrls.forEach(url => URL.revokeObjectURL(url))
  previewObjectUrls = []
}

async function openPreview(versionId, binding) {
  if (!versionId) {
    ElMessage.warning('该资料尚未生成版本')
    return
  }
  const bid = binding || bindingId.value
  revokePreviewUrls()
  previewOpen.value = true
  previewLoading.value = true
  previewError.value = ''
  previewMode.value = 'pages'
  previewPages.value = []
  previewPageIndex.value = 0
  previewLink.value = ''
  previewVideoUrl.value = ''
  previewWatermark.value = ''
  previewVersionId.value = versionId
  try {
    const res = await getVersion(versionId, bid)
    const version = res.data || {}
    previewTitle.value = version.title || '资料预览'
    previewWatermark.value = version.watermarkText || ''
    if (version.mediaKind === 'LINK' && version.externalUri) {
      previewMode.value = 'link'
      previewLink.value = version.externalUri
      return
    }
    if (version.mediaKind === 'VIDEO') {
      const blob = await downloadVersion(versionId, bid)
      previewVideoUrl.value = rememberPreviewUrl(URL.createObjectURL(blob))
      previewMode.value = 'video'
      return
    }
    const pages = version.pages || []
    if (!pages.length) {
      previewMode.value = 'empty'
      return
    }
    previewPages.value = await Promise.all(
      pages.map(page =>
        getVersionPage(versionId, page.pageNo, bid).then(blob => rememberPreviewUrl(URL.createObjectURL(blob)))
      )
    )
  } catch (e) {
    previewError.value = e?.message || '预览失败'
  } finally {
    previewLoading.value = false
  }
}

function onPreviewClosed() {
  revokePreviewUrls()
  previewPages.value = []
  previewVideoUrl.value = ''
  previewWatermark.value = ''
  previewPageIndex.value = 0
}

function extensionOf(blob, fileName) {
  const name = String(fileName || '')
  if (/\.[a-z0-9]+$/i.test(name)) return ''
  const type = (blob && blob.type) || ''
  if (type.includes('pdf')) return '.pdf'
  if (type.includes('word')) return '.docx'
  if (type.includes('presentation') || type.includes('powerpoint')) return '.pptx'
  if (type.includes('excel') || type.includes('spreadsheet')) return '.xlsx'
  if (type.includes('video')) return '.mp4'
  if (type.startsWith('image/')) return `.${type.split('/')[1].split('+')[0]}`
  return ''
}

async function handleDownload(versionId, binding) {
  if (!versionId) {
    ElMessage.warning('该资料尚未生成版本')
    return
  }
  const bid = binding || bindingId.value
  try {
    const res = await getVersion(versionId, bid)
    const version = res.data || {}
    const blob = await downloadVersion(versionId, bid)
    const base = version.fileName || version.title || `resource-${versionId}`
    saveAs(blob, `${base}${extensionOf(blob, version.fileName)}`)
  } catch (e) {
    ElMessage.error(e?.message || '下载失败')
  }
}

async function exportResources() {
  if (!resourceList.value.length) {
    ElMessage.warning('当前没有可导出的资料')
    return
  }
  try {
    const blob = await exportResourcesZip(bindingId.value)
    saveAs(blob, '课程资料.zip')
    ElMessage.success('已导出 课程资料.zip')
  } catch (e) {
    ElMessage.error(e?.message || '导出失败')
  }
}

function triggerResourceImport() {
  resourceZipInputRef.value?.click()
}

async function onResourceZipSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  try {
    await ElMessageBox.confirm(
      `将从压缩包导入资料：${file.name}。压缩包内受支持的文件会作为课程资料逐个创建，是否继续？`,
      '导入课程资料',
      { type: 'warning', confirmButtonText: '导入', cancelButtonText: '取消' }
    )
  } catch (e) {
    return
  }
  const formData = new FormData()
  formData.append('file', file)
  resourceImporting.value = true
  try {
    const res = await importResourcesZip(bindingId.value, formData)
    const data = res.data || {}
    if (Number(data.failed || 0) > 0) {
      ElMessage.warning(`导入完成：成功 ${data.imported || 0} 个，失败 ${data.failed} 个`)
    } else {
      ElMessage.success(`导入完成：成功 ${data.imported || 0} 个`)
    }
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    resourceImporting.value = false
  }
}

/* ---------- 题库 ---------- */

function handleQuestionCreateCommand(command) {
  if (command === 'manual') {
    openCreateQuestion()
    return
  }
  if (command === 'library') {
    questionLibraryVisible.value = true
  }
}

async function shareQuestionToLibrary(question) {
  try {
    const response = await shareQuestions({
      bindingId: Number(bindingId.value),
      questionIds: [Number(question.questionId)]
    })
    const result = response.data || {}
    if (result.published || result.reopened) {
      ElMessage.success('已发布到题目资源库')
    } else {
      ElMessage.info(result.skipped ? '该题目已在资源库中' : '发布失败，请刷新后重试')
    }
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '发布到资源库失败')
  }
}

function handleQuestionSelectionChange(selection) {
  questionSelection.value = selection
}

async function batchShareSelectedQuestions() {
  if (questionSelection.value.length === 0) return
  try {
    const response = await shareQuestions({
      bindingId: Number(bindingId.value),
      questionIds: questionSelection.value.map(row => Number(row.question.questionId))
    })
    const result = response.data || {}
    const parts = []
    if (result.published) parts.push(`新发布 ${result.published} 道`)
    if (result.reopened) parts.push(`重新发布 ${result.reopened} 道`)
    if (result.skipped) parts.push(`跳过 ${result.skipped} 道（已共享或已归档）`)
    ElMessage.success(parts.length ? parts.join('，') : '没有需要发布的题目')
    questionSelection.value = []
    questionTableRef.value?.clearSelection()
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '批量发布失败')
  }
}

async function withdrawQuestionFromLibrary(question) {
  if (!question.shareId) return
  try {
    await ElMessageBox.confirm(
      `确认撤回「${question.title}」的共享？已引入到其他课程的副本不受影响。`,
      '撤回共享',
      { type: 'warning', confirmButtonText: '撤回', cancelButtonText: '取消' }
    )
  } catch {
    return
  }
  try {
    await withdrawSharedQuestions({
      bindingId: Number(bindingId.value),
      shareIds: [Number(question.shareId)]
    })
    ElMessage.success('已撤回共享')
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '撤回失败')
  }
}

async function handleQuestionLibraryImported() {
  await loadOverview()
  await loadQuestionFolders()
}

function openCompose() {
  composeResult.value = null
  composeOpen.value = true
}

async function submitCompose() {
  composeLoading.value = true
  try {
    const res = await composeQuestions({
      bindingId: Number(bindingId.value),
      questionKind: composeForm.questionKind || null,
      difficulty: composeForm.difficulty || null,
      knowledgePoint: composeForm.knowledgePoint || null,
      tag: composeForm.tag || null,
      count: Number(composeForm.count),
      mode: composeForm.mode
    })
    composeResult.value = res.data || { questions: [], count: 0, totalScore: 0 }
    if (!composeResult.value.count) ElMessage.info('没有符合条件的题目，请放宽筛选条件')
  } catch (e) {
    composeResult.value = null
    ElMessage.error(e?.message || '组卷失败')
  } finally {
    composeLoading.value = false
  }
}

function openPaperPublish() {
  if (!composeResult.value || !composeResult.value.count) return
  const now = Date.now()
  paperForm.title = `智能组卷 · ${composeResult.value.count} 题`
  paperForm.instructions = ''
  paperForm.opensAt = new Date(now)
  paperForm.closesAt = new Date(now + 7 * 24 * 3600 * 1000)
  paperForm.answerOpensAt = new Date(now + 7 * 24 * 3600 * 1000 + 60 * 60 * 1000)
  paperPublishOpen.value = true
}

async function submitPaperPublish() {
  if (!paperForm.title.trim()) {
    ElMessage.warning('请填写试卷名称')
    return
  }
  if (!paperForm.opensAt || !paperForm.closesAt || !paperForm.answerOpensAt) {
    ElMessage.warning('请完整设置开放、截止与解析公布时间')
    return
  }
  if (paperForm.closesAt.getTime() <= paperForm.opensAt.getTime()) {
    ElMessage.warning('截止时间必须晚于开放时间')
    return
  }
  if (paperForm.answerOpensAt.getTime() < paperForm.closesAt.getTime()) {
    ElMessage.warning('解析公布时间不能早于截止时间')
    return
  }
  const questionVersionIds = (composeResult.value?.questions || []).map(item => Number(item.versionId))
  if (!questionVersionIds.length) {
    ElMessage.warning('当前试卷没有题目')
    return
  }
  paperPublishing.value = true
  try {
    const res = await createPreviewTask({
      bindingId: Number(bindingId.value),
      lessonId: null,
      title: paperForm.title.trim(),
      instructions: paperForm.instructions.trim(),
      opensAt: paperForm.opensAt.toISOString(),
      closesAt: paperForm.closesAt.toISOString(),
      answerOpensAt: paperForm.answerOpensAt.toISOString(),
      questionVersionIds,
      resourceVersionIds: []
    })
    const count = Number(res?.data?.itemCount ?? questionVersionIds.length)
    ElMessage.success(`整卷已发布：${count} 道题，合计 ${res?.data?.totalScore ?? '—'} 分，学生端「课前学习」可见`)
    paperPublishOpen.value = false
    composeOpen.value = false
  } catch (e) {
    ElMessage.error(e?.message || '发布整卷失败')
  } finally {
    paperPublishing.value = false
  }
}

function handleQuestionImportCommand(label) {
  if (label !== '模板导入') {
    ElMessage.info(`${label}暂未接入`)
    return
  }
  questionImportRows.value = []
  questionImportFileName.value = ''
  questionImportOpen.value = true
}

function triggerQuestionImportFile() {
  questionImportInputRef.value?.click()
}

async function onQuestionImportFileSelected(event) {
  const file = event.target.files?.[0]
  event.target.value = ''
  if (!file) return
  questionImportFileName.value = file.name
  questionImportLoading.value = true
  try {
    const formData = new FormData()
    formData.append('file', file)
    const res = await previewQuestionImport(bindingId.value, formData)
    questionImportRows.value = Array.isArray(res.data) ? res.data : []
    if (!questionImportRows.value.length) {
      ElMessage.warning('表格中没有可导入的题目')
    }
  } catch (e) {
    questionImportRows.value = []
    ElMessage.error(e?.message || '解析失败')
  } finally {
    questionImportLoading.value = false
  }
}

function createQuestionFolder() {
  openFolderDialog('question')
}

function enterQuestionFolder(id) {
  currentQuestionFolderId.value = id
}

function backToAllQuestions() {
  currentQuestionFolderId.value = null
}

function answerToInput(question) {
  const answer = question.answer || {}
  if (question.questionKind === 'SINGLE') {
    return answer.choiceIndex != null ? String(Number(answer.choiceIndex) + 1) : ''
  }
  if (question.questionKind === 'MULTIPLE') {
    return (answer.choiceIndices || []).map(index => Number(index) + 1).join(',')
  }
  if (question.questionKind === 'TRUE_FALSE') return answer.correctBoolean ? 'true' : 'false'
  if (question.questionKind === 'SHORT_TEXT') return answer.referenceAnswer || ''
  return ''
}

function openCreateQuestion() {
  questionEdit.value = null
  Object.assign(questionForm, {
    title: '',
    questionKind: 'SINGLE',
    maxScore: 10,
    stem: '',
    options: '选项 A\n选项 B\n选项 C',
    answer: '',
    explanation: '',
    scoringRubric: '',
    knowledgePointIds: [],
    difficulty: 'MEDIUM',
    tags: ''
  })
  questionOpen.value = true
}

function openQuestionVersion(question) {
  questionEdit.value = question
  const pointNames = String(question.knowledgePoints || '')
    .split(/[,，、;；]/)
    .map(value => value.trim())
    .filter(Boolean)
  Object.assign(questionForm, {
    title: question.title || '',
    questionKind: question.questionKind || 'SINGLE',
    maxScore: Number(question.maxScore ?? 10),
    stem: question.stem || '',
    options:
      Array.isArray(question.options) && question.options.length
        ? question.options.join('\n')
        : '选项 A\n选项 B\n选项 C',
    answer: answerToInput(question),
    explanation: question.explanation || '',
    scoringRubric: question.scoringRubric || '',
    knowledgePointIds: knowledgePoints.value
      .filter(point => pointNames.includes(point.name))
      .map(point => Number(point.knowledgePointId))
      .filter(Boolean),
    difficulty: question.difficulty || 'MEDIUM',
    tags: question.tags || ''
  })
  questionOpen.value = true
}

function onQuestionKindChange(kind) {
  if (kind === 'TRUE_FALSE') questionForm.answer = 'true'
  else if (kind === 'SHORT_TEXT') questionForm.answer = ''
}

function buildAnswer(kind) {
  const value = String(questionForm.answer ?? '').trim()
  if (kind === 'SINGLE') return { choiceIndex: Number(value) - 1 }
  if (kind === 'MULTIPLE') {
    return {
      choiceIndices: value
        .split(',')
        .map(item => Number(item.trim()) - 1)
        .filter(Number.isInteger)
    }
  }
  if (kind === 'TRUE_FALSE') return { correctBoolean: value.toLowerCase() === 'true' }
  return { referenceAnswer: value }
}

async function submitQuestion() {
  const kind = questionForm.questionKind
  if (!questionEdit.value && !questionForm.title.trim()) {
    ElMessage.warning('请填写题目名称')
    return
  }
  if (!questionForm.stem.trim()) {
    ElMessage.warning('请填写题干')
    return
  }
  if (!String(questionForm.answer ?? '').trim()) {
    ElMessage.warning('请填写答案')
    return
  }
  const optionValues = questionForm.options
    .split('\n')
    .map(value => value.trim())
    .filter(Boolean)
  const payload = {
    bindingId: Number(bindingId.value),
    folderId: currentQuestionFolderId.value ? Number(currentQuestionFolderId.value) : null,
    questionKind: kind,
    stem: questionForm.stem.trim(),
    options: kind === 'TRUE_FALSE' ? ['正确', '错误'] : kind === 'SHORT_TEXT' ? [] : optionValues,
    answer: buildAnswer(kind),
    explanation: questionForm.explanation.trim(),
    scoringRubric: kind === 'SHORT_TEXT' ? questionForm.scoringRubric.trim() : null,
    maxScore: Number(questionForm.maxScore),
    knowledgePointIds: questionForm.knowledgePointIds,
    difficulty: questionForm.difficulty,
    tags: questionForm.tags.trim()
  }
  questionSaving.value = true
  try {
    if (questionEdit.value) {
      await createQuestionVersion(questionEdit.value.questionId, payload)
      ElMessage.success('题目新版本已保存')
    } else {
      await createQuestion({ ...payload, title: questionForm.title.trim() })
      ElMessage.success('题目已创建')
    }
    questionOpen.value = false
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '题目保存失败')
  } finally {
    questionSaving.value = false
  }
}

function deleteFolderWithConfirm(folder, kind) {
  const isQuestion = kind === 'QUESTION'
  const count = isQuestion ? questionFolderCount(folder) : folderCount(folder)
  const unit = isQuestion ? '道题' : '个资料'
  const tip = count > 0 ? `文件夹内 ${count} ${unit}将移回根目录，不会被删除。` : '该文件夹为空。'
  ElMessageBox.confirm(`确认删除文件夹「${folder.name}」？${tip}`, '删除文件夹', {
    type: 'warning',
    confirmButtonText: '删除',
    cancelButtonText: '取消'
  })
    .then(async () => {
      try {
        await deleteFolder(folder.id, bindingId.value, kind)
        ElMessage.success('文件夹已删除')
        if (isQuestion) {
          if (String(currentQuestionFolderId.value || '') === String(folder.id)) currentQuestionFolderId.value = null
          await loadQuestionFolders()
        } else {
          if (String(currentFolderId.value || '') === String(folder.id)) currentFolderId.value = null
          await loadResourceFolders()
        }
        await loadOverview()
      } catch (e) {
        handleActionError(e, '文件夹删除失败')
      }
    })
    .catch(() => {})
}

function toggleQuestionFolderArchive(folder) {
  const archived = !folder.archivedAt
  const run = () =>
    archiveQuestionFolder(folder.id, {
      bindingId: Number(bindingId.value),
      kind: 'QUESTION',
      archived
    })
      .then(() => {
        ElMessage.success(archived ? '文件夹已归档' : '文件夹已取消归档')
        loadQuestionFolders()
      })
      .catch(e => handleActionError(e))
  if (!archived) {
    run()
    return
  }
  ElMessageBox.confirm(`确认归档文件夹「${folder.name}」？归档后仍可查看和取消归档。`, '归档文件夹', {
    type: 'warning',
    confirmButtonText: '归档',
    cancelButtonText: '取消'
  })
    .then(run)
    .catch(() => {})
}

function archiveQuestion(question) {
  ElMessageBox.confirm('确认归档该题目？', '归档题目', {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(() => updateQuestionStatus(question.questionId, bindingId.value, { status: 'ARCHIVED' }))
    .then(() => {
      ElMessage.success('题目已归档')
      loadOverview()
    })
    .catch(e => handleActionError(e))
}

function onQuestionRowClick(row, column) {
  if (column?.type === 'selection') return
  if (row.rowType !== 'question') return
  questionPreviewList.value = filteredQuestions.value.slice()
  questionPreviewIndex.value = row.index
  questionPreviewOpen.value = true
}

async function exportQuestions() {
  if (!questionList.value.length) {
    ElMessage.warning('当前没有可导出的题目')
    return
  }
  try {
    const blob = await exportQuestionsExcel(bindingId.value)
    saveAs(blob, '课程题库.xlsx')
    ElMessage.success('已导出 课程题库.xlsx')
  } catch (e) {
    ElMessage.error(e?.message || '导出失败')
  }
}

const questionImportValidRows = computed(() => questionImportRows.value.filter(row => row.valid))
const questionImportInvalidCount = computed(
  () => questionImportRows.value.filter(row => !row.valid).length
)

async function submitQuestionImport() {
  const rows = questionImportValidRows.value
  if (!rows.length) {
    ElMessage.warning('没有可导入的题目')
    return
  }
  questionImportSaving.value = true
  try {
    const res = await importQuestions({
      bindingId: Number(bindingId.value),
      questions: rows.map(row => ({
        rowNo: row.rowNo,
        folderId: currentQuestionFolderId.value ? Number(currentQuestionFolderId.value) : null,
        title: row.title,
        questionKind: row.questionKind,
        stem: row.stem,
        options: row.options,
        answer: row.answer,
        explanation: row.explanation,
        scoringRubric: row.scoringRubric,
        maxScore: row.maxScore,
        knowledgePointIds: row.knowledgePointIds,
        difficulty: row.difficulty,
        tags: row.tags
      }))
    })
    const data = res.data || {}
    if (Number(data.failed || 0) > 0) {
      ElMessage.warning(`导入完成：成功 ${data.imported || 0} 题，失败 ${data.failed} 题`)
    } else {
      ElMessage.success(`导入完成：成功 ${data.imported || 0} 题`)
    }
    questionImportOpen.value = false
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '导入失败')
  } finally {
    questionImportSaving.value = false
  }
}

/* ---------- 大纲：章 / 节 / 知识点 ---------- */

function isCollapsed(unitId) {
  return collapsedChapters.value.includes(String(unitId))
}

function toggleChapter(unitId) {
  const key = String(unitId)
  if (isCollapsed(key)) collapsedChapters.value = collapsedChapters.value.filter(item => item !== key)
  else collapsedChapters.value = [...collapsedChapters.value, key]
}

function openCreateUnit(unitKind, parentUnitId) {
  const parent = parentUnitId ? Number(parentUnitId) : null
  const siblings = (overview.value?.outline || []).filter(unit => {
    const unitParent = unit.parentUnitId ? Number(unit.parentUnitId) : null
    return unitParent === parent
  })
  const maxSort = siblings.reduce((max, unit) => Math.max(max, Number(unit.sortOrder ?? 0)), -1)
  unitDialogMode.value = 'create'
  unitDialogKind.value = unitKind
  Object.assign(unitForm, {
    unitId: null,
    title: '',
    sortOrder: maxSort + 1,
    parentUnitId: parent,
    contentText: ''
  })
  unitDialogOpen.value = true
}

function openEditUnit(unit) {
  if (!unit?.unitId) return
  unitDialogMode.value = 'edit'
  unitDialogKind.value = unit.unitKind || (unit.parentUnitId ? 'SECTION' : 'CHAPTER')
  Object.assign(unitForm, {
    unitId: unit.unitId,
    title: unit.title || '',
    sortOrder: Number(unit.sortOrder ?? 0),
    parentUnitId: unit.parentUnitId ?? null,
    contentText: unit.contentText || ''
  })
  unitDialogOpen.value = true
}

async function submitUnit() {
  const title = unitForm.title.trim()
  if (!title) {
    ElMessage.warning('请填写标题')
    return
  }
  unitSaving.value = true
  try {
    if (unitDialogMode.value === 'create') {
      await createOutlineUnit({
        bindingId: Number(bindingId.value),
        unitKind: unitDialogKind.value,
        title,
        parentUnitId: unitForm.parentUnitId,
        sortOrder: Number(unitForm.sortOrder) || 0,
        contentText: unitForm.contentText
      })
      ElMessage.success(unitDialogKind.value === 'CHAPTER' ? '章节已创建' : '小节已创建')
    } else {
      await updateOutlineUnit(unitForm.unitId, {
        bindingId: Number(bindingId.value),
        title,
        sortOrder: Number(unitForm.sortOrder) || 0,
        contentText: unitForm.contentText
      })
      ElMessage.success('章节已更新')
    }
    unitDialogOpen.value = false
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    unitSaving.value = false
  }
}

async function moveUnit(unit, delta) {
  const currentSort = Number(unit.sortOrder ?? 0)
  const nextSort = currentSort + delta
  if (nextSort < 0) {
    ElMessage.warning('已经在最前')
    return
  }
  try {
    await reorderOutlineUnit(unit.unitId, { bindingId: Number(bindingId.value), sortOrder: nextSort })
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '排序失败')
  }
}

function confirmDeleteUnit(unit) {
  ElMessageBox.confirm('请先处理该章节的下级内容和业务引用。', `确认删除“${unit.title || '该章节'}”？`, {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(() => deleteOutlineUnit(unit.unitId, bindingId.value))
    .then(() => {
      ElMessage.success('章节已删除')
      loadOverview()
    })
    .catch(e => handleActionError(e))
}

function openCreateKnowledgePoint(unitId) {
  Object.assign(pointForm, {
    knowledgePointId: null,
    unitId: unitId ? Number(unitId) : null,
    code: '',
    name: '',
    contentText: ''
  })
  pointDialogOpen.value = true
}

function openEditPoint(point) {
  Object.assign(pointForm, {
    knowledgePointId: point?.knowledgePointId || point?.pointId || null,
    unitId: point?.unitId ?? null,
    code: point?.code || '',
    name: point?.name || '',
    contentText: point?.contentText || ''
  })
  pointDialogOpen.value = true
}

async function submitPoint() {
  const code = pointForm.code.trim()
  const name = pointForm.name.trim()
  if (!code) {
    ElMessage.warning('知识点编码不能为空')
    return
  }
  if (!name) {
    ElMessage.warning('知识点名称不能为空')
    return
  }
  pointSaving.value = true
  try {
    if (pointForm.knowledgePointId) {
      await updateKnowledgePoint(pointForm.knowledgePointId, {
        bindingId: Number(bindingId.value),
        code,
        name,
        contentText: pointForm.contentText
      })
      ElMessage.success('知识点已更新')
    } else {
      await createKnowledgePoint({
        bindingId: Number(bindingId.value),
        code,
        name,
        unitId: pointForm.unitId,
        contentText: pointForm.contentText
      })
      ElMessage.success('知识点已创建')
    }
    pointDialogOpen.value = false
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    pointSaving.value = false
  }
}

function confirmDeletePoint(point) {
  const id = point?.knowledgePointId || point?.pointId
  if (!id) return
  ElMessageBox.confirm('此操作不可恢复。', `确认删除知识点“${point.name || '该知识点'}”？`, {
    type: 'warning',
    confirmButtonText: '确定',
    cancelButtonText: '取消'
  })
    .then(() => deleteKnowledgePoint(id, bindingId.value))
    .then(() => {
      ElMessage.success('知识点已删除')
      loadOverview()
    })
    .catch(e => handleActionError(e))
}

function unitStats(unit) {
  if (!unit) return ''
  return `资料 ${Number(unit.materialCount || 0)} · 题目 ${Number(unit.questionCount || 0)} · 知识点 ${Number(unit.pointCount || 0)}`
}

function pointStats(point) {
  return `题目 ${Number(point?.questionCount || 0)}`
}

async function openUnitDetail(unit) {
  if (!unit?.unitId) return
  outlineDetailType.value = 'unit'
  outlineDetail.value = null
  outlineDetailTab.value = 'content'
  outlineDetailContent.value = unit.contentText || ''
  outlineDetailOpen.value = true
  outlineDetailLoading.value = true
  try {
    const response = await getOutlineUnitDetail(unit.unitId, bindingId.value)
    outlineDetail.value = response.data
    outlineDetailContent.value = response.data?.unit?.contentText || unit.contentText || ''
  } catch (e) {
    ElMessage.error(e?.message || '章节详情加载失败')
    outlineDetailOpen.value = false
  } finally {
    outlineDetailLoading.value = false
  }
}

async function openPointDetail(point) {
  if (!point) return
  detailPoint.value = point
  outlineDetailType.value = 'point'
  outlineDetail.value = null
  outlineDetailTab.value = 'content'
  outlineDetailContent.value = point.contentText || ''
  outlineDetailOpen.value = true
  outlineDetailLoading.value = true
  try {
    const pointId = point.knowledgePointId || point.pointId
    const response = await getKnowledgePointDetail(pointId, bindingId.value)
    outlineDetail.value = response.data
    outlineDetailContent.value = response.data?.point?.contentText || ''
  } catch (e) {
    ElMessage.error(e?.message || '知识点详情加载失败')
    outlineDetailOpen.value = false
  } finally {
    outlineDetailLoading.value = false
  }
}

async function saveOutlineDetailContent() {
  if (!outlineDetail.value) return
  outlineContentSaving.value = true
  try {
    if (outlineDetailType.value === 'unit') {
      await updateOutlineUnit(outlineDetail.value.unit.unitId, {
        bindingId: Number(bindingId.value),
        contentText: outlineDetailContent.value
      })
      outlineDetail.value.unit.contentText = outlineDetailContent.value
    } else {
      await updateKnowledgePoint(outlineDetail.value.point.knowledgePointId, {
        bindingId: Number(bindingId.value),
        contentText: outlineDetailContent.value
      })
      outlineDetail.value.point.contentText = outlineDetailContent.value
    }
    ElMessage.success('大纲内容已保存')
    await loadOverview()
  } catch (e) {
    ElMessage.error(e?.message || '保存失败')
  } finally {
    outlineContentSaving.value = false
  }
}

/* ---------- 教学班设置 ---------- */

async function openClassroomSettings() {
  if (!bindingId.value) return
  classroomOpen.value = true
  classroomLoading.value = true
  classroom.value = null
  try {
    const res = await listClassrooms(bindingId.value)
    classroom.value = res.data || null
  } catch (e) {
    ElMessage.error(e?.message || '教学班信息加载失败')
  } finally {
    classroomLoading.value = false
  }
}

onMounted(refresh)

onBeforeUnmount(() => {
  revokePreviewUrls()
})
</script>

<style scoped>
/* 操作列的「修改 / 下载 / 更多」统一为一行并垂直居中。 */
.row-actions {
  display: flex;
  align-items: center;
  gap: 12px;
}
.row-actions :deep(.el-button + .el-button) {
  margin-left: 0;
}
.row-actions :deep(.el-dropdown) {
  display: inline-flex;
  align-items: center;
}
.studio-head {
  display: flex;
  align-items: flex-start;
  justify-content: space-between;
  gap: 12px;
  margin-bottom: 8px;
}
.studio-head h2 {
  margin: 0;
  font-size: 18px;
  font-weight: 600;
}
.studio-sub {
  margin: 4px 0 0;
  font-size: 13px;
  color: #909399;
}
.toolbar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
}
.toolbar .spacer {
  flex: 1;
}
.filter-label {
  font-size: 13px;
  color: #606266;
}
.breadcrumb {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  font-size: 13px;
  color: #606266;
}
.batch-bar {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 10px;
  padding: 6px 10px;
  border: 1px solid #e4e7ed;
  border-radius: 4px;
  background: #f5f7fa;
}
.batch-info {
  font-size: 13px;
  color: #606266;
}
.cell-meta {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
.file-title {
  display: flex;
  align-items: flex-start;
  gap: 8px;
}
.ml6 {
  margin-left: 6px;
}
.mr6 {
  margin-right: 6px;
}
.question-stem {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
}
.upload-notes {
  margin-bottom: 12px;
  padding: 8px 12px;
  border-radius: 4px;
  background: #f5f7fa;
  font-size: 12px;
  color: #606266;
}
.upload-notes ol {
  margin: 4px 0 0;
  padding-left: 18px;
}
.upload-drag {
  margin-bottom: 8px;
  font-size: 13px;
  color: #909399;
}
.upload-folder-btn {
  margin-top: 8px;
  margin-left: 0;
}
.upload-list {
  width: 100%;
  margin-top: 8px;
}
.upload-file-item {
  display: flex;
  align-items: center;
  gap: 8px;
  padding: 2px 0;
}
.upload-file-name {
  flex: 1;
  overflow: hidden;
  font-size: 13px;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.mt12 {
  margin-top: 12px;
}
.preview-body {
  min-height: 160px;
  max-height: 72vh;
  overflow: auto;
  text-align: center;
}
.preview-image {
  width: 100%;
}
.preview-video {
  width: 100%;
  max-height: 68vh;
}
.preview-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 12px;
  padding: 8px 0;
}
.preview-empty {
  padding: 24px 0;
}
.qpreview-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  margin-bottom: 12px;
}
.qpreview-stem {
  margin-bottom: 12px;
  font-size: 15px;
  font-weight: 600;
  line-height: 1.6;
}
.outline-head {
  display: grid;
  grid-template-columns: 56px 1fr 210px 120px 340px;
  gap: 8px;
  padding: 8px 12px;
  border-bottom: 1px solid #ebeef5;
  font-size: 12px;
  color: #909399;
}
.outline-row {
  display: grid;
  grid-template-columns: 56px 1fr 210px 120px 340px;
  gap: 8px;
  align-items: center;
  padding: 6px 12px;
  border-bottom: 1px solid #f2f6fc;
  font-size: 13px;
  color: #606266;
}
.outline-row.chapter {
  background: #fafafa;
  font-weight: 600;
  color: #303133;
}
.outline-row.section .outline-title {
  padding-left: 18px;
}
.outline-row.point .outline-title {
  padding-left: 40px;
  color: #606266;
}
.outline-num {
  font-size: 12px;
  color: #909399;
}
.outline-stats {
  font-size: 12px;
  color: #909399;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.outline-progress {
  cursor: pointer;
}
.outline-link {
  cursor: pointer;
}
.outline-link:hover {
  color: var(--el-color-primary);
}
.outline-muted {
  font-size: 12px;
  color: #c0c4cc;
}
.detail-content-actions {
  margin-top: 12px;
  text-align: right;
}
.outline-code {
  margin-right: 6px;
  font-size: 12px;
  color: #909399;
}
.outline-actions {
  display: flex;
  flex-wrap: wrap;
  gap: 2px;
}
.outline-point {
  cursor: pointer;
}
.outline-empty {
  padding: 24px 0;
  text-align: center;
  font-size: 13px;
  color: #909399;
}
.editor-tree {
  max-height: 60vh;
  overflow: auto;
}
.editor-row {
  display: grid;
  grid-template-columns: 56px 1fr 260px;
  gap: 8px;
  align-items: center;
  padding: 6px 4px;
  border-bottom: 1px solid #f2f6fc;
  font-size: 13px;
}
.editor-row.chapter {
  font-weight: 600;
}
.editor-row.section .editor-title {
  padding-left: 18px;
}
.editor-row.point .editor-title {
  padding-left: 40px;
  color: #606266;
}
.detail-title {
  margin: 0 0 6px;
  font-size: 16px;
}
.detail-body {
  margin-top: 12px;
  font-size: 13px;
  line-height: 1.8;
  color: #606266;
}
.section-title {
  margin: 16px 0 8px;
  font-size: 14px;
  font-weight: 600;
}
.preview-watermark-host { position: relative; overflow: hidden; }
.preview-watermark {
  position: absolute; inset: 0; display: flex; align-items: center; justify-content: center;
  pointer-events: none; user-select: none; z-index: 5;
  font-size: 40px; font-weight: 600; color: rgba(0, 0, 0, 0.12);
  white-space: nowrap; transform: rotate(-24deg);
  text-shadow: 0 0 1px rgba(255, 255, 255, 0.4);
}
.compose-summary {
  margin: 6px 0 12px;
  font-size: 13px;
  color: #67c23a;
  font-weight: 600;
}
.paper-publish-tip {
  margin-bottom: 14px;
}
</style>
