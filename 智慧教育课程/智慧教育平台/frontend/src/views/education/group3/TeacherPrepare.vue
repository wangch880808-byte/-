<template>
  <div class="group3-prepare">
    <Group3ContextBar
      v-if="!contextBindingId"
      :list="list"
      :binding-id="bindingId"
      :loading="loading"
      :error="error"
      @select="select"
      @refresh="refresh"
    />

    <el-alert v-if="errorText" :title="errorText" type="warning" show-icon :closable="false" class="mb8" />

    <el-empty
      v-if="!bindingId && !loading"
      description="暂无获授权的课程，请联系教务管理员确认教学班任课关系"
    />

    <div v-else v-loading="pageLoading || loading">
      <div class="prepare-head">
        <div>
          <h2 class="prepare-title">课前准备</h2>
          <p class="prepare-sub">
            {{ current ? `${current.courseTitle} · ${current.className}` : '请选择课程与教学班' }}
          </p>
          <p class="prepare-help">
            环节按「课程 / 教学班 / 上课日期」分成一堂堂课，每堂课内部从 1 重新编号；切换上方课堂或教学班即可编排另一堂课。
          </p>
        </div>
        <div class="prepare-actions">
          <el-button @click="openCreate('plan')">保存备课方案</el-button>
          <el-button @click="openMaterialCreate">发布资料阅读</el-button>
          <el-button type="primary" @click="openCreate('quiz')">发布预习任务</el-button>
        </div>
      </div>

      <el-row :gutter="12" class="stat-row">
        <el-col v-for="item in statCards" :key="item.label" :xs="12" :sm="6">
          <el-card shadow="never" class="stat-card" :class="{ warn: item.warn }">
            <div class="stat-label">{{ item.label }}</div>
            <div class="stat-value">{{ item.value }}</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="16">
        <el-col :xs="24" :md="10">
          <el-card shadow="never" class="panel">
            <template #header>
              <div class="panel-head">
                <span class="panel-title">时间轴</span>
                <div class="panel-tools">
                  <span class="edu-hint">按开放时间排列</span>
                  <el-select v-model="filter" size="small" style="width: 140px">
                    <el-option
                      v-for="item in filterOptions"
                      :key="item.value"
                      :label="item.label"
                      :value="item.value"
                    />
                  </el-select>
                  <el-radio-group v-model="viewMode" size="small">
                    <el-radio-button value="timeline">时间轴</el-radio-button>
                    <el-radio-button value="list">列表</el-radio-button>
                  </el-radio-group>
                </div>
              </div>
            </template>

            <template v-if="viewMode === 'timeline'">
              <div v-if="nodeGroups.length" class="timeline">
                <div v-for="group in nodeGroups" :key="group.key" class="day-group">
                  <div class="day-head">
                    <span class="day-dot"></span>
                    <strong>{{ group.label }}</strong>
                    <span class="day-count">{{ groupCountText(group) }}</span>
                    <el-tag v-if="isTodayGroup(group.key)" size="small" type="success">现在</el-tag>
                  </div>

                  <div v-for="node in group.nodes" :key="node.key" class="tl-item">
                    <div class="tl-time">
                      <strong>{{ formatClock(node.time) }}</strong>
                      <span>{{ node.closesAt ? '至 ' + formatClock(node.closesAt) : '开放' }}</span>
                    </div>
                    <div class="tl-dot" :class="'tone-' + kindOf(node).tone"></div>
                    <div
                      class="tl-card"
                      :class="['tone-' + kindOf(node).tone, { active: node.key === selectedKey }]"
                      role="button"
                      tabindex="0"
                      @click="selectNode(node)"
                      @keyup.enter="selectNode(node)"
                    >
                      <div class="tl-card-head">
                        <el-tag size="small" effect="plain">
                          {{ kindOf(node).icon }} {{ kindOf(node).label }}
                        </el-tag>
                        <span class="tl-seq" :class="{ muted: !node.seq }">
                          {{ node.seq ? `第 ${node.seq} / ${node.total} 环节` : '不计入序号' }}
                        </span>
                        <el-tag size="small" :type="stateTagType(node.state)">{{ stateLabel(node.state) }}</el-tag>
                        <el-tag v-if="node.private" size="small" type="info">仅自己可见</el-tag>
                      </div>
                      <div class="tl-title">{{ node.title }}</div>
                      <div v-if="node.subtitle" class="tl-subtitle">{{ node.subtitle }}</div>
                      <div v-if="node.tags.length" class="tl-tags">
                        <el-tag v-for="tag in node.tags" :key="tag" size="small" type="info" effect="plain">
                          {{ tag }}
                        </el-tag>
                      </div>
                      <div class="tl-actions" @click.stop>
                        <el-button
                          v-if="canPreview(node)"
                          link
                          type="primary"
                          @click="openNodePreview(node)"
                        >
                          预览
                        </el-button>
                        <el-button
                          v-if="showVersions(node)"
                          link
                          type="primary"
                          @click="openVersions(node)"
                        >
                          版本
                        </el-button>
                        <el-button
                          v-if="canPublishDraft(node)"
                          link
                          type="primary"
                          @click="openPublish(node)"
                        >
                          设置开放并发布
                        </el-button>
                        <el-button
                          v-if="canReupload(node)"
                          link
                          type="primary"
                          @click="openReupload(node)"
                        >
                          重新上传
                        </el-button>
                        <el-button
                          v-if="canRevoke(node)"
                          link
                          type="danger"
                          @click="revokeNode(node)"
                        >
                          撤回
                        </el-button>
                        <el-button
                          link
                          type="danger"
                          @click="handleRemove(node)"
                        >
                          删除环节
                        </el-button>
                        <el-button link type="primary" @click="selectNode(node)">查看详情</el-button>
                      </div>
                    </div>
                  </div>
                </div>
              </div>

              <el-empty
                v-else
                description="当前筛选下没有环节"
              >
                <p class="edu-hint">调整筛选条件，或用右上角「发布预习任务」把资料与习题放到时间轴上</p>
              </el-empty>
            </template>

            <el-table
              v-else
              :data="visibleNodes"
              border
              size="small"
              class="node-list"
              row-key="key"
              :highlight-current-row="true"
              :current-row-key="selectedKey"
              @row-click="selectNode"
            >
              <el-table-column label="标题 / 类型" min-width="200">
                <template #default="{ row }">
                  <div class="list-title-cell">
                    <el-tag size="small" effect="plain">
                      {{ kindOf(row).icon }} {{ kindOf(row).label }}
                    </el-tag>
                    <span class="list-title">{{ row.title }}</span>
                    <el-tag v-if="row.private" size="small" type="info">仅自己可见</el-tag>
                  </div>
                  <div v-if="row.subtitle" class="list-subtitle">{{ row.subtitle }}</div>
                </template>
              </el-table-column>
              <el-table-column label="所属天" width="140">
                <template #default="{ row }">
                  {{ row.time && dayKeyOf(row.time) ? dayLabelOf(row.time, serverNow) : '待编排' }}
                </template>
              </el-table-column>
              <el-table-column label="状态" width="100">
                <template #default="{ row }">
                  <el-tag size="small" :type="stateTagType(row.state)">{{ stateLabel(row.state) }}</el-tag>
                </template>
              </el-table-column>
              <el-table-column label="开放时间" width="110">
                <template #default="{ row }">{{ row.opensAt ? formatTime(row.opensAt) : '未设置' }}</template>
              </el-table-column>
              <el-table-column label="截止时间" width="110">
                <template #default="{ row }">{{ row.closesAt ? formatTime(row.closesAt) : '不限' }}</template>
              </el-table-column>
              <el-table-column label="操作" width="260" fixed="right">
                <template #default="{ row }">
                  <el-button
                    v-if="canPreview(row)"
                    link
                    type="primary"
                    @click.stop="openNodePreview(row)"
                  >
                    预览
                  </el-button>
                  <el-button
                    v-if="showVersions(row)"
                    link
                    type="primary"
                    @click.stop="openVersions(row)"
                  >
                    版本
                  </el-button>
                  <el-button
                    v-if="canPublishDraft(row)"
                    link
                    type="primary"
                    @click.stop="openPublish(row)"
                  >
                    发布
                  </el-button>
                  <el-button
                    v-if="canReupload(row)"
                    link
                    type="primary"
                    @click.stop="openReupload(row)"
                  >
                    重新上传
                  </el-button>
                  <el-button
                    v-if="canRevoke(row)"
                    link
                    type="danger"
                    @click.stop="revokeNode(row)"
                  >
                    撤回
                  </el-button>
                  <el-button
                    link
                    type="danger"
                    @click.stop="handleRemove(row)"
                  >
                    删除
                  </el-button>
                  <el-button link type="primary" @click.stop="selectNode(row)">查看详情</el-button>
                </template>
              </el-table-column>
              <template #empty>
                <span>当前筛选下没有环节，调整筛选条件或发布预习任务</span>
              </template>
            </el-table>
          </el-card>
        </el-col>

        <el-col :xs="24" :md="14">
          <el-card shadow="never" class="panel detail-card">
            <template #header>
              <div class="panel-head">
                <span class="panel-title">环节详情</span>
                <template v-if="selectedNode">
                  <el-tag size="small">{{ kindOf(selectedNode).label }}</el-tag>
                  <el-tag size="small" :type="stateTagType(selectedNode.state)">
                    {{ stateLabel(selectedNode.state) }}
                  </el-tag>
                </template>
              </div>
            </template>

            <div class="stage-track">
              <div class="stage-track-label">
                <strong>{{ stageTrackTitle }}</strong>
                <span>{{ stageTrackHint }}</span>
              </div>
              <div v-for="group in displayStageGroups" :key="group.key" class="stage-group">
                <div class="stage-group-label">
                  <strong>{{ group.label }}</strong>
                  <span>本堂课 {{ group.nodes.length }} 个环节 · 独立编号 1–{{ group.nodes.length }}</span>
                </div>
                <div class="stage-track-steps">
                  <button
                    v-for="node in group.nodes"
                    :key="node.key"
                    type="button"
                    class="stage-step"
                    :class="['tone-' + kindOf(node).tone, { active: node.key === selectedKey }]"
                    :title="`第 ${node.seq} 环节 · ${node.title}`"
                    @click="selectNode(node)"
                  >
                    <span class="stage-step-no">{{ node.seq }}</span>
                    <span class="stage-step-text">
                      <strong>{{ node.title }}</strong>
                      <small>{{ stepHint(node) }}</small>
                    </span>
                  </button>
                </div>
              </div>
              <el-empty v-if="!displayStageGroups.length" description="还没有已编号的环节" :image-size="60" />
            </div>

            <el-divider />

            <template v-if="!selectedNode">
              <el-empty description="选择一个环节">
                <p class="edu-hint">点击时间轴上的卡片，查看内容、发布范围、完成情况和可执行操作</p>
              </el-empty>
            </template>

            <template v-else>
              <div class="detail-title">{{ selectedNode.title }}</div>
              <div v-if="selectedNode.subtitle" class="edu-hint">{{ selectedNode.subtitle }}</div>
              <el-alert
                v-if="failureMessage"
                :title="`处理结果：${failureMessage}`"
                type="error"
                show-icon
                :closable="false"
                class="mb8"
              >
                <template #default>
                  此版本不会对学生开放。可上传新版本替换，或检查文件后重新上传。
                </template>
              </el-alert>
              <el-alert
                v-if="privateNotes"
                title="教师私有备注"
                type="warning"
                show-icon
                :closable="false"
                class="mb8"
              >
                <template #default>
                  {{ privateNotes }}<br />
                  该内容只保存在备课包中，不会推送到学生时间轴。
                </template>
              </el-alert>

              <div class="detail-rows">
                <div v-for="row in detailRows" :key="row[0]" class="detail-row">
                  <span>{{ row[0] }}</span>
                  <strong>{{ row[1] }}</strong>
                </div>
              </div>

              <div v-if="selectedNode.tags.length" class="tl-tags">
                <el-tag v-for="tag in selectedNode.tags" :key="tag" size="small" type="info" effect="plain">
                  {{ tag }}
                </el-tag>
              </div>

              <el-divider />

              <div v-if="selectedNode.kind === 'QUIZ'" class="detail-section">
                <h4>本环节题目</h4>
                <div v-if="quizQuestionRows.length" class="question-list">
                  <div v-for="(row, index) in quizQuestionRows" :key="row.assignmentItemId || index" class="question-row">
                    <span class="q-no">{{ index + 1 }}</span>
                    <span>{{ row.stem || row.title || '题目' }}</span>
                  </div>
                </div>
                <p v-else class="edu-hint">该任务未返回题目明细，可在「课程建设」题库中查看版本。</p>
              </div>

              <div v-else-if="selectedNode.kind === 'PLAN'" class="detail-section">
                <h4>备课包内容（仅教师可见）</h4>
                <div v-if="planMaterials.length" class="plan-list">
                  <div v-for="item in planMaterials" :key="item.resourceVersionId" class="plan-row">
                    <span>{{ item.title || '未命名材料' }} · V{{ item.versionNo || 1 }}</span>
                    <el-button link type="primary" @click="openPreview(item.resourceVersionId, item.title)">预览</el-button>
                  </div>
                </div>
                <p v-else class="edu-hint">暂无纳入材料</p>
                <div v-if="planTemplates.length" class="plan-list">
                  <div v-for="item in planTemplates" :key="item.templateId" class="plan-row">
                    <span>{{ item.title || '课堂活动' }} · {{ item.durationSeconds || 0 }} 秒</span>
                  </div>
                </div>
              </div>

              <div class="detail-actions">
                <template v-if="selectedNode.kind === 'QUIZ'">
                  <el-button type="primary" @click="openCreate('quiz')">继续发布预习</el-button>
                </template>
                <template v-else-if="selectedNode.kind === 'PLAN'">
                  <el-button type="primary" @click="openCreate('plan')">新建备课包</el-button>
                </template>
                <template v-else>
                  <el-button
                    v-if="canPublishDraft(selectedNode)"
                    type="primary"
                    @click="openPublish(selectedNode)"
                  >
                    设置开放并发布
                  </el-button>
                  <el-button
                    v-if="canReupload(selectedNode)"
                    type="primary"
                    @click="openReupload(selectedNode)"
                  >
                    重新上传
                  </el-button>
                  <el-button
                    v-if="selectedNode.state !== 'FAILED'"
                    :type="canPublishDraft(selectedNode) ? 'default' : 'primary'"
                    @click="openNodePreview(selectedNode)"
                  >
                    预览资料
                  </el-button>
                  <el-button @click="openVersions(selectedNode)">版本记录</el-button>
                  <el-button
                    v-if="selectedNode.state !== 'FAILED'"
                    @click="downloadNode(selectedNode)"
                  >
                    下载原文件
                  </el-button>
                  <el-button
                    v-if="canRevoke(selectedNode)"
                    type="danger"
                    @click="revokeNode(selectedNode)"
                  >
                    撤回发布
                  </el-button>
                  <el-button
                    type="danger"
                    plain
                    @click="handleRemove(selectedNode)"
                  >
                    删除环节
                  </el-button>
                  <el-button @click="openCreate('quiz')">继续编排预习</el-button>
                </template>
              </div>

              <p class="edu-hint">
                发布范围由页面顶部「课程 / 教学班」决定；开放、截止与解析公布均以服务端时间为准。
              </p>
            </template>
          </el-card>
        </el-col>
      </el-row>
    </div>

    <!-- 资料版本记录 -->
    <el-dialog v-model="versionsVisible" title="资料版本记录" width="760px" append-to-body>
      <el-table v-loading="versionsLoading" :data="versionRows" border size="small">
        <el-table-column label="版本" width="90">
          <template #default="{ row }">V{{ row.versionNo }}</template>
        </el-table-column>
        <el-table-column label="处理状态" width="110">
          <template #default="{ row }">
            <el-tag size="small" :type="processTagType(row.processingStatus)">
              {{ processLabel(row.processingStatus) }}
            </el-tag>
          </template>
        </el-table-column>
        <el-table-column label="预览页" width="100">
          <template #default="{ row }">{{ row.pageCount || 0 }} 页</template>
        </el-table-column>
        <el-table-column label="创建时间" min-width="140">
          <template #default="{ row }">{{ formatTime(row.createdAt) }}</template>
        </el-table-column>
        <el-table-column label="操作" width="150">
          <template #default="{ row }">
            <el-button link type="primary" @click="openPreview(row.versionId, `资料预览 · V${row.versionNo}`)">预览</el-button>
            <el-button link type="primary" @click="downloadFile(row.versionId, `V${row.versionNo}`)">下载</el-button>
          </template>
        </el-table-column>
        <template #empty>
          <span>暂无版本</span>
        </template>
      </el-table>
    </el-dialog>

    <!-- 资料预览 -->
    <el-dialog
      v-model="previewVisible"
      :title="previewTitle"
      width="900px"
      append-to-body
      destroy-on-close
      @closed="clearPreviewUrls"
    >
      <div v-loading="previewLoading" class="preview-body">
        <el-alert v-if="previewError" :title="previewError" type="error" show-icon :closable="false" />
        <template v-else-if="!previewLoading">
          <div v-if="previewKind === 'link'" class="preview-note">
            该资料为外部链接。
            <a :href="previewLink" target="_blank" rel="noopener noreferrer">打开链接</a>
          </div>
          <div v-else-if="previewKind === 'video'" class="preview-box">
            <video controls autoplay :src="previewImages[0]"></video>
          </div>
          <div v-else-if="previewKind === 'empty'" class="preview-note">
            该资料没有分页预览，可直接下载原文件阅读。
          </div>
          <template v-else>
            <div v-if="previewImages.length" class="preview-box">
              <img :src="previewImages[previewPage]" :alt="`第 ${previewPage + 1} 页`" />
            </div>
            <div v-if="previewImages.length > 1" class="preview-nav">
              <el-button :disabled="previewPage === 0" @click="previewPage -= 1">上一页</el-button>
              <span class="edu-hint">{{ previewPage + 1 }} / {{ previewImages.length }}</span>
              <el-button :disabled="previewPage === previewImages.length - 1" @click="previewPage += 1">下一页</el-button>
            </div>
          </template>
        </template>
      </div>
      <template #footer>
        <el-button @click="previewVisible = false">关闭</el-button>
        <el-button type="primary" @click="downloadPreview">下载原文件</el-button>
      </template>
    </el-dialog>

    <!-- 创建流程：发布预习任务 / 保存备课方案 -->
    <el-dialog
      v-model="createVisible"
      :title="createKind === 'quiz' ? '发布预习任务' : '保存备课方案'"
      width="900px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-steps :active="createStep" finish-status="success" align-center class="create-steps">
        <el-step
          v-for="step in createSteps"
          :key="step.title"
          :title="step.title"
          :description="step.hint"
        />
      </el-steps>

      <div v-show="createStep === 0" class="create-panel">
        <el-form v-if="createKind === 'quiz'" label-width="90px">
          <el-form-item label="环节名称" required>
            <el-input v-model="createForm.title" maxlength="200" placeholder="例如：第 3 章课前预习" />
          </el-form-item>
          <el-form-item label="关联课次">
            <el-select v-model="createForm.lessonId" clearable placeholder="不关联课次" style="width: 100%">
              <el-option
                v-for="item in lessonOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="导学说明">
            <el-input
              v-model="createForm.instructions"
              type="textarea"
              :rows="3"
              placeholder="告诉学生先看什么、要完成什么、何时截止"
            />
          </el-form-item>
        </el-form>

        <div class="picker-lead">
          <strong>{{ createKind === 'quiz' ? '同步开放的资料' : '选择资料版本' }}</strong>
          <span>{{ materialOptions.length }} 个可用版本</span>
        </div>
        <div class="picker">
          <div class="picker-head">
            <el-input v-model="materialKeyword" placeholder="搜索资料名称或文件类型" clearable style="width: 260px" />
            <div class="picker-tools">
              <span class="edu-hint">已选 {{ createForm.materialVersionIds.length }} 项</span>
              <el-button link type="primary" @click="selectAllMaterials">全选</el-button>
              <el-button link type="primary" @click="createForm.materialVersionIds = []">清空</el-button>
            </div>
          </div>
          <el-checkbox-group v-model="createForm.materialVersionIds" class="picker-list">
            <el-checkbox
              v-for="item in filteredMaterials"
              :key="item.value"
              :value="item.value"
              class="picker-item"
            >
              <span class="picker-item-main">
                <span class="picker-item-title">
                  {{ item.title }}
                  <el-tag size="small" type="info" effect="plain">V{{ item.versionNo || 1 }}</el-tag>
                </span>
                <span class="picker-item-meta">{{ item.meta }}</span>
              </span>
            </el-checkbox>
          </el-checkbox-group>
          <el-empty
            v-if="!filteredMaterials.length"
            description="课程资源库里还没有可复用的资料版本"
            :image-size="60"
          />
        </div>
        <p class="edu-hint">
          {{ createKind === 'quiz'
            ? '勾选的资料按同一开放窗口按班发布；答案与解析在公布时间到达前不会返回给学生。'
            : '这里只列出可复用的资料版本；保存时直接引用版本 ID，不会重新上传文件。' }}
        </p>
      </div>

      <div v-show="createStep === 1" class="create-panel">
        <div class="picker-lead">
          <strong>{{ createKind === 'quiz' ? '从题库导入题目' : '随堂题预设（单选一道）' }}</strong>
          <span>{{ questionOptions.length }} 个可用版本</span>
          <el-button
            v-if="createKind === 'plan' && createForm.quizQuestionVersionId"
            link
            type="primary"
            @click="createForm.quizQuestionVersionId = null"
          >
            清空
          </el-button>
        </div>
        <div class="picker">
          <div class="picker-head">
            <el-input v-model="questionKeyword" placeholder="搜索题目名称、题干或题型" clearable style="width: 260px" />
            <div class="picker-tools">
              <span class="edu-hint">
                {{ createKind === 'quiz'
                  ? `已选 ${createForm.questionVersionIds.length} 项`
                  : (createForm.quizQuestionVersionId ? '已选 1 项' : '不预设') }}
              </span>
              <template v-if="createKind === 'quiz'">
                <el-button link type="primary" @click="selectAllQuestions">全选</el-button>
                <el-button link type="primary" @click="createForm.questionVersionIds = []">清空</el-button>
              </template>
            </div>
          </div>

          <el-checkbox-group
            v-if="createKind === 'quiz'"
            v-model="createForm.questionVersionIds"
            class="picker-list"
          >
            <el-checkbox
              v-for="item in filteredQuestions"
              :key="item.value"
              :value="item.value"
              class="picker-item"
            >
              <span class="picker-item-main">
                <span class="picker-item-title">{{ item.title }}</span>
                <span class="picker-item-meta">{{ item.meta }}</span>
                <span v-if="item.stem" class="picker-item-desc">{{ item.stem }}</span>
              </span>
            </el-checkbox>
          </el-checkbox-group>

          <el-radio-group v-else v-model="createForm.quizQuestionVersionId" class="picker-list">
            <el-radio
              v-for="item in filteredQuestions"
              :key="item.value"
              :value="item.value"
              class="picker-item"
            >
              <span class="picker-item-main">
                <span class="picker-item-title">{{ item.title }}</span>
                <span class="picker-item-meta">{{ item.meta }}</span>
                <span v-if="item.stem" class="picker-item-desc">{{ item.stem }}</span>
              </span>
            </el-radio>
          </el-radio-group>

          <el-empty
            v-if="!filteredQuestions.length"
            description="题库里还没有可用题目版本"
            :image-size="60"
          />
        </div>
        <p class="edu-hint">
          {{ createKind === 'quiz'
            ? '发布接口每条预习任务只绑定一道题：勾选多道题时按「每题一个独立环节」依次发布，共享同一开放窗口与导学说明；未勾选任何题目时为纯资料导学环节。'
            : '随堂题从现有题库直接选用，不会重新录入；不选择则不预设活动。' }}
        </p>
      </div>

      <div v-show="createStep === 2" class="create-panel">
        <el-form v-if="createKind === 'quiz'" label-width="110px">
          <el-form-item label="开放时间" required>
            <el-date-picker v-model="createForm.opensAt" type="datetime" placeholder="请选择开放时间" style="width: 100%" />
          </el-form-item>
          <el-form-item label="截止时间" required>
            <el-date-picker v-model="createForm.closesAt" type="datetime" placeholder="请选择截止时间" style="width: 100%" />
          </el-form-item>
          <el-form-item label="解析公布时间" required>
            <el-date-picker v-model="createForm.answerOpensAt" type="datetime" placeholder="请选择解析公布时间" style="width: 100%" />
          </el-form-item>
          <el-form-item label="提交规则">
            <span class="edu-hint">重复提交由服务端幂等处理</span>
          </el-form-item>
        </el-form>

        <el-form v-else label-width="110px">
          <el-form-item label="关联课次" required>
            <el-select v-model="createForm.lessonId" placeholder="请选择课次" style="width: 100%">
              <el-option
                v-for="item in lessonOptions"
                :key="item.value"
                :label="item.label"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
          <el-form-item label="备课包名称" required>
            <el-input v-model="createForm.title" maxlength="160" placeholder="例如：第 3 章第 1 课时备课包" />
          </el-form-item>
          <el-form-item label="活动名称">
            <el-input v-model="createForm.quizTitle" maxlength="80" />
          </el-form-item>
          <el-form-item label="答题时长（秒）">
            <el-input-number v-model="createForm.durationSeconds" :min="60" :step="30" style="width: 100%" />
          </el-form-item>
          <el-form-item label="教师私有备注">
            <el-input
              v-model="createForm.privateNotes"
              type="textarea"
              :rows="3"
              placeholder="课堂节奏、提问设计、易错点……仅教师可见"
            />
          </el-form-item>
          <el-form-item label="保存为可用方案">
            <el-switch v-model="createForm.markReady" />
          </el-form-item>
        </el-form>
        <p v-if="createKind === 'plan'" class="edu-hint">
          备课包是时间轴上「仅教师可见」的节点，不设开放时间，也不会进入学生端。
        </p>
      </div>

      <div v-show="createStep === createSteps.length - 1" class="create-panel">
        <div class="summary-grid">
          <div v-for="row in confirmRows" :key="row[0]">
            <span>{{ row[0] }}</span>
            <strong>{{ row[1] }}</strong>
          </div>
        </div>
        <div v-if="confirmMaterials.length" class="summary-list">
          <span>选用的资料</span>
          <ul>
            <li v-for="item in confirmMaterials" :key="item.value">{{ item.title }}</li>
          </ul>
        </div>
        <div v-if="confirmQuestions.length" class="summary-list">
          <span>选用的题目</span>
          <ul>
            <li v-for="item in confirmQuestions" :key="item.value">{{ item.title }}</li>
          </ul>
        </div>
        <el-alert
          v-if="createKind === 'quiz' && !createForm.questionVersionIds.length"
          title="未勾选题目，将生成一个纯资料导学环节"
          type="info"
          show-icon
          :closable="false"
        />
        <el-alert
          v-if="createKind === 'plan'"
          title="学生时间轴不会收到私有备注与未公布答案；备课包环节不计入学生环节序号"
          type="info"
          show-icon
          :closable="false"
        />
      </div>

      <template #footer>
        <el-button @click="createVisible = false">取消</el-button>
        <el-button v-if="createStep > 0" @click="prevCreateStep">上一步</el-button>
        <el-button v-if="createStep < createSteps.length - 1" type="primary" @click="nextCreateStep">
          下一步
        </el-button>
        <el-button v-else type="primary" :loading="submitting" @click="submitCreate">
          {{ createKind === 'quiz' ? '确认发布' : '保存备课包' }}
        </el-button>
      </template>
    </el-dialog>

    <!-- 设置开放时间并发布（待编排资料） -->
    <el-dialog
      v-model="publishVisible"
      title="设置开放时间并发布"
      width="520px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-width="90px">
        <el-form-item label="资料">
          <strong>{{ publishNode ? publishNode.title : '' }}</strong>
        </el-form-item>
        <el-form-item label="开放时间" required>
          <el-date-picker v-model="publishForm.opensAt" type="datetime" placeholder="请选择开放时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="截止时间" required>
          <el-date-picker v-model="publishForm.closesAt" type="datetime" placeholder="请选择截止时间" style="width: 100%" />
        </el-form-item>
      </el-form>
      <p class="edu-hint">发布到当前教学班；学生只在开放窗口内看到该版本，撤回后旧访问地址立即失效。</p>
      <template #footer>
        <el-button @click="publishVisible = false">取消</el-button>
        <el-button type="primary" :loading="publishing" @click="submitPublish">确认发布</el-button>
      </template>
    </el-dialog>

    <!-- 创建资料阅读环节 -->
    <el-dialog
      v-model="materialVisible"
      title="添加资料阅读环节"
      width="720px"
      append-to-body
      :close-on-click-modal="false"
    >
      <el-form label-width="100px">
        <el-form-item label="开放时间" required>
          <el-date-picker v-model="materialForm.opensAt" type="datetime" placeholder="请选择开放时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="截止时间" required>
          <el-date-picker v-model="materialForm.closesAt" type="datetime" placeholder="请选择截止时间" style="width: 100%" />
        </el-form-item>
        <el-form-item label="资料来源" required>
          <el-radio-group v-model="materialForm.source">
            <el-radio-button value="existing">选择已有资料</el-radio-button>
            <el-radio-button value="upload">上传新资料</el-radio-button>
          </el-radio-group>
        </el-form-item>
        <template v-if="materialForm.source === 'existing'">
          <el-form-item label="选择资料" required>
            <el-select
              v-model="materialForm.versionId"
              filterable
              placeholder="从本课程已有的资料版本中选择"
              style="width: 100%"
            >
              <el-option
                v-for="item in materialOptions"
                :key="item.value"
                :label="`${item.title} · V${item.versionNo || 1}`"
                :value="item.value"
              />
            </el-select>
          </el-form-item>
        </template>
        <template v-else>
          <el-form-item label="环节标题">
            <el-input v-model="materialForm.title" maxlength="200" placeholder="不填写时使用文件名" />
          </el-form-item>
          <el-form-item label="选择文件" required>
            <el-upload
              :auto-upload="false"
              :limit="1"
              :on-change="onMaterialFileChange"
              :on-remove="onMaterialFileRemove"
              :file-list="materialFileList"
            >
              <el-button>选择文件</el-button>
              <template #tip>
                <span class="edu-hint">支持 .pptx / .pdf / 图片 / 视频 / .docx / .txt / .md，上传后自动转码</span>
              </template>
            </el-upload>
          </el-form-item>
        </template>
      </el-form>
      <p class="edu-hint">
        环节按开放时间归入对应上课日期并自动编号；选择已有资料直接复用版本，不会重复上传文件。
      </p>
      <template #footer>
        <el-button @click="materialVisible = false">取消</el-button>
        <el-button type="primary" :loading="materialSubmitting" @click="submitMaterialCreate">
          加入时间轴
        </el-button>
      </template>
    </el-dialog>

    <input
      ref="reuploadInput"
      type="file"
      class="hidden-file"
      accept=".pptx,.pdf,.png,.jpg,.jpeg,.gif,.webp,.mp4,.webm,.mov,.docx,.txt,.md"
      @change="onReuploadFile"
    />
    <a ref="downloadAnchor" class="download-anchor" :href="downloadHref" :download="downloadName">下载</a>
  </div>
</template>

<script setup name="EduTeacherPrepare">
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref, watch } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import Group3ContextBar from './Group3ContextBar.vue'
import { useGroup3Binding } from './useGroup3Binding'
import {
  createPlan,
  createPreviewTask,
  createRelease,
  createResource,
  createResourceVersion,
  downloadVersion,
  getOverview,
  getVersion,
  getVersionPage,
  listPlans,
  listPreviewTasks,
  listResourceVersions,
  removeStage,
  revokeRelease
} from '@/api/education/group3'

const KINDS = {
  MATERIAL: { label: '资料阅读', tone: 'blue', icon: '▤' },
  QUIZ: { label: '预习习题', tone: 'teal', icon: '✎' },
  PLAN: { label: '备课包', tone: 'amber', icon: '◈' },
  DRAFT: { label: '待编排资料', tone: 'slate', icon: '◌' }
}

const STATE_LABELS = {
  DRAFT: '待编排',
  FAILED: '处理失败',
  UPCOMING: '未开放',
  OPEN: '进行中',
  CLOSED: '已截止',
  ARCHIVED: '已归档',
  PRIVATE: '仅教师可见'
}

const PROCESS_LABELS = { READY: '可用', PENDING: '处理中', FAILED: '处理失败' }

const QUESTION_KIND_LABELS = {
  SINGLE: '单选题',
  MULTIPLE: '多选题',
  TRUE_FALSE: '判断题',
  SHORT_TEXT: '简答题'
}

const FILTER_OPTIONS = [
  { value: 'all', label: '全部' },
  { value: 'DRAFT', label: '待编排' },
  { value: 'UPCOMING', label: '未开放' },
  { value: 'OPEN', label: '进行中' },
  { value: 'CLOSED', label: '已截止' },
  { value: 'ARCHIVED', label: '已归档' },
  { value: 'PRIVATE', label: '仅教师可见' }
]

const filterOptions = FILTER_OPTIONS

const props = defineProps({ contextBindingId: { type: String, default: '' } })
const { list, bindingId, current, loading, error, load, select } = useGroup3Binding(() => props.contextBindingId)

const overview = ref({})
const taskRows = ref([])
const planRows = ref([])
const nodes = ref([])
const serverNow = ref('')
const selectedKey = ref('')
const filter = ref('all')
const viewMode = ref('timeline')
const pageLoading = ref(false)
const errorText = ref('')

let syncing = false

watch(bindingId, () => {
  if (!syncing) loadData()
})

onMounted(() => {
  refresh()
})

onBeforeUnmount(() => {
  clearPreviewUrls()
})

async function refresh() {
  syncing = true
  try {
    await load()
  } finally {
    syncing = false
  }
  await loadData()
}

async function loadData() {
  selectedKey.value = ''
  clearPreviewUrls()
  if (!bindingId.value) {
    overview.value = {}
    taskRows.value = []
    planRows.value = []
    nodes.value = []
    return
  }
  pageLoading.value = true
  errorText.value = ''
  const requested = String(bindingId.value)
  try {
    const [overviewRes, taskRes, planRes] = await Promise.all([
      getOverview(bindingId.value),
      listPreviewTasks(bindingId.value),
      listPlans(bindingId.value)
    ])
    if (String(bindingId.value) !== requested) return
    overview.value = overviewRes.data || {}
    taskRows.value = Array.isArray(taskRes.data) ? taskRes.data : []
    planRows.value = Array.isArray(planRes.data) ? planRes.data : []
    serverNow.value = overview.value.serverTime || ''
    nodes.value = numberStages(
      collectTeacherNodes(overview.value, taskRows.value, planRows.value),
      current.value
    )
  } catch (e) {
    if (!e || !e.cancelled) {
      errorText.value = e && e.message ? e.message : '课前时间轴加载失败'
      overview.value = {}
      taskRows.value = []
      planRows.value = []
      nodes.value = []
    }
  } finally {
    pageLoading.value = false
  }
}

/* ---------- 时间与文案 ---------- */

function formatClock(value) {
  if (!value) return '--:--'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '--:--'
  const pad = number => String(number).padStart(2, '0')
  return `${pad(date.getHours())}:${pad(date.getMinutes())}`
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

function dayKeyOf(value) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return ''
  return `${date.getFullYear()}-${String(date.getMonth() + 1).padStart(2, '0')}-${String(date.getDate()).padStart(2, '0')}`
}

function dayLabelOf(value, now) {
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return '未安排时间'
  const today = dayKeyOf(now || new Date())
  const key = dayKeyOf(value)
  const tomorrow = new Date(date.getTime())
  tomorrow.setDate(tomorrow.getDate() - 1)
  const yesterday = new Date(date.getTime())
  yesterday.setDate(yesterday.getDate() + 1)
  if (key === today) return '今天'
  if (dayKeyOf(tomorrow) === today) return '明天'
  if (dayKeyOf(yesterday) === today) return '昨天'
  const week = ['周日', '周一', '周二', '周三', '周四', '周五', '周六'][date.getDay()]
  return `${date.getMonth() + 1} 月 ${date.getDate()} 日 · ${week}`
}

function stateLabel(state) {
  return STATE_LABELS[state] || state || '未知'
}

function stateTagType(state) {
  if (state === 'OPEN') return 'success'
  if (state === 'UPCOMING') return 'warning'
  if (state === 'FAILED') return 'danger'
  if (state === 'DRAFT' || state === 'PRIVATE' || state === 'ARCHIVED' || state === 'CLOSED') return 'info'
  return ''
}

function processLabel(status) {
  const value = String(status || '').toUpperCase()
  return PROCESS_LABELS[value] || status || '未知'
}

function processTagType(status) {
  const value = String(status || '').toUpperCase()
  if (value === 'READY') return 'success'
  if (value === 'FAILED') return 'danger'
  return 'warning'
}

function kindOf(node) {
  return KINDS[node.kind] || KINDS.MATERIAL
}

/* ---------- 节点模型 ---------- */

function timeState(opensAt, closesAt, now) {
  const currentTime = now ? new Date(now).getTime() : Date.now()
  const opens = opensAt ? new Date(opensAt).getTime() : null
  const closes = closesAt ? new Date(closesAt).getTime() : null
  if (opens != null && Number.isFinite(opens) && currentTime < opens) return 'UPCOMING'
  if (closes != null && Number.isFinite(closes) && currentTime >= closes) return 'CLOSED'
  return 'OPEN'
}

function makeNode(fields) {
  return Object.assign(
    {
      key: '',
      kind: 'MATERIAL',
      id: null,
      title: '',
      subtitle: '',
      time: null,
      opensAt: null,
      closesAt: null,
      state: 'OPEN',
      tags: [],
      private: false,
      meta: {}
    },
    fields
  )
}

function collectTeacherNodes(ov, taskList, planList) {
  const now = ov.serverTime
  const lessons = ov.lessons || []
  const lessonTime = new Map(lessons.map(lesson => [String(lesson.lessonId), lesson.scheduledAt]))
  const result = []

  ;(ov.resources || []).forEach(item => {
    const status = String(item.processingStatus || 'PENDING').toUpperCase()
    const released = Boolean(item.releaseId)
    const tags = [`V${item.versionNo || 1}`, `${Number(item.pageCount || 0)} 页`]
    if (item.mediaKind) tags.push(item.mediaKind)
    const base = {
      id: item.versionId,
      resourceId: item.resourceId,
      releaseId: item.releaseId || null,
      title: item.title || '未命名资料',
      subtitle: item.processingMessage || '',
      opensAt: item.opensAt || null,
      closesAt: item.closesAt || null,
      time: item.opensAt || item.createdAt || null,
      tags,
      meta: item
    }
    if (status === 'FAILED') {
      result.push(makeNode({
        ...base,
        kind: 'DRAFT',
        key: `draft-${item.resourceId}`,
        state: 'FAILED',
        time: null,
        private: true
      }))
    } else if (released) {
      result.push(makeNode({
        ...base,
        kind: 'MATERIAL',
        key: `material-${item.resourceId}`,
        state: timeState(item.opensAt, item.closesAt, now)
      }))
    } else {
      const reason = status === 'PENDING' ? '资料处理中，暂不能发布' : '尚未设置开放时间，学生看不到'
      result.push(makeNode({
        ...base,
        kind: 'DRAFT',
        key: `draft-${item.resourceId}`,
        state: 'DRAFT',
        subtitle: reason,
        time: null,
        private: true
      }))
    }
  })

  const quizMap = new Map()
  ;(taskList || []).forEach(row => {
    const key = String(row.assignmentId)
    if (!quizMap.has(key)) quizMap.set(key, { row, items: [] })
    if (row.assignmentItemId) quizMap.get(key).items.push(row)
  })
  quizMap.forEach(({ row, items }) => {
    const state = String(row.status || '').toUpperCase() === 'ARCHIVED'
      ? 'ARCHIVED'
      : timeState(row.opensAt, row.closesAt, now)
    const tags = [`${items.length} 道题`]
    if (row.lessonId && lessonTime.get(String(row.lessonId))) tags.push('已关联课次')
    result.push(makeNode({
      key: `quiz-${row.assignmentId}`,
      kind: 'QUIZ',
      id: row.assignmentId,
      title: row.title || '预习任务',
      subtitle: row.instructions || row.stem || '',
      opensAt: row.opensAt,
      closesAt: row.closesAt,
      time: row.opensAt,
      state,
      tags,
      meta: {
        ...row,
        itemCount: items.length,
        submissionCount: row.submissionCount,
        targetCount: row.targetCount,
        answerOpensAt: row.answerOpensAt
      }
    }))
  })

  ;(planList || []).forEach(plan => {
    const scheduled = lessonTime.get(String(plan.lessonId)) || null
    result.push(makeNode({
      key: `plan-${plan.planId}`,
      kind: 'PLAN',
      id: plan.planId,
      title: plan.title || '备课方案',
      subtitle: plan.privateNotes || '教师私有备注不进入学生端',
      time: scheduled || plan.createdAt || null,
      state: 'PRIVATE',
      private: true,
      tags: [
        plan.lessonTitle || '未关联课次',
        `V${plan.versionNo || 1}`,
        `${(plan.materials || []).length} 份材料`
      ],
      meta: plan
    }))
  })

  return result
}

/* ---------- 堂课编号 ---------- */

function isNumberedStage(node) {
  return !node.private
    && (node.kind === 'MATERIAL' || node.kind === 'QUIZ')
    && node.state !== 'ARCHIVED'
}

function stageGroupKey(node, context) {
  const dateKey = dayKeyOf(node.opensAt || node.time)
  if (!dateKey) return ''
  return `${dateKey}|${context ? context.bindingId : bindingId.value}`
}

function stageGroupLabel(node, context) {
  const date = new Date(node.opensAt || node.time)
  const dateText = Number.isNaN(date.getTime())
    ? '未安排时间'
    : `${date.getMonth() + 1} 月 ${date.getDate()} 日`
  return context ? `${dateText} · ${context.courseTitle} · ${context.className}` : dateText
}

function numberStages(nodeList, context) {
  const rank = { MATERIAL: 0, QUIZ: 1 }
  const stamp = node => {
    const value = new Date(node.opensAt || node.time || 0).getTime()
    return Number.isFinite(value) ? value : 0
  }
  const buckets = new Map()
  nodeList.filter(isNumberedStage).forEach(node => {
    const key = stageGroupKey(node, context)
    if (!key) return
    if (!buckets.has(key)) {
      buckets.set(key, { key, label: stageGroupLabel(node, context), nodes: [] })
    }
    buckets.get(key).nodes.push(node)
  })
  const meta = new Map()
  buckets.forEach(bucket => {
    bucket.nodes.sort((a, b) => {
      if (stamp(a) !== stamp(b)) return stamp(a) - stamp(b)
      if (rank[a.kind] !== rank[b.kind]) return rank[a.kind] - rank[b.kind]
      return String(a.key).localeCompare(String(b.key))
    })
    bucket.nodes.forEach((node, index) => meta.set(node.key, {
      seq: index + 1,
      total: bucket.nodes.length,
      groupKey: bucket.key,
      groupLabel: bucket.label
    }))
  })
  return nodeList.map(node => Object.assign(
    {},
    node,
    meta.get(node.key) || { seq: null, total: null, groupKey: '', groupLabel: '' }
  ))
}

function buildStageGroups(nodeList) {
  const groups = []
  const index = new Map()
  nodeList
    .filter(node => node.seq)
    .slice()
    .sort((a, b) => String(a.groupKey).localeCompare(String(b.groupKey)) || a.seq - b.seq)
    .forEach(node => {
      if (!index.has(node.groupKey)) {
        index.set(node.groupKey, groups.length)
        groups.push({ key: node.groupKey, label: node.groupLabel, nodes: [] })
      }
      groups[index.get(node.groupKey)].nodes.push(node)
    })
  return groups
}

/* ---------- 时间轴分组 ---------- */

function filterNodes(nodeList, value) {
  if (!value || value === 'all') return nodeList.slice()
  if (value === 'DRAFT') {
    return nodeList.filter(node => node.state === 'DRAFT' || node.state === 'FAILED')
  }
  return nodeList.filter(node => node.state === value)
}

function groupNodesByDay(nodeList, now) {
  const dated = nodeList.filter(node => node.time && dayKeyOf(node.time))
  const undated = nodeList.filter(node => !node.time || !dayKeyOf(node.time))
  dated.sort((a, b) => new Date(a.time) - new Date(b.time))
  const groups = []
  const index = new Map()
  dated.forEach(node => {
    const key = dayKeyOf(node.time)
    if (!index.has(key)) {
      index.set(key, groups.length)
      groups.push({ key, label: dayLabelOf(node.time, now), nodes: [] })
    }
    groups[index.get(key)].nodes.push(node)
  })
  if (undated.length) {
    groups.unshift({ key: 'unscheduled', label: '待编排 · 学生不可见', nodes: undated })
  }
  return groups
}

/* ---------- 视图数据 ---------- */

const visibleNodes = computed(() => filterNodes(nodes.value, filter.value))
const nodeGroups = computed(() => groupNodesByDay(visibleNodes.value, serverNow.value))
const stageGroups = computed(() => buildStageGroups(nodes.value))
const stageTotal = computed(() => stageGroups.value.reduce((sum, group) => sum + group.nodes.length, 0))
const nodeIndex = computed(() => {
  const map = {}
  nodes.value.forEach(node => { map[node.key] = node })
  return map
})
const selectedNode = computed(() => nodeIndex.value[selectedKey.value] || null)
const selectedGroup = computed(() => {
  if (!selectedNode.value || !selectedNode.value.groupKey) return null
  return stageGroups.value.find(group => group.key === selectedNode.value.groupKey) || null
})

const displayStageGroups = computed(() => {
  if (selectedGroup.value) return [selectedGroup.value]
  return stageGroups.value
})

const stageTrackTitle = computed(() => (selectedGroup.value ? '本堂课环节' : '按堂课编排'))

const stageTrackHint = computed(() => (selectedGroup.value
  ? `共 ${selectedGroup.value.nodes.length} 个环节 · 序号只在这一堂课内排序`
  : `共 ${stageGroups.value.length} 堂课 / ${stageTotal.value} 个环节 · 换班级、换课程、换日期都从 1 重新编号`))

const statCards = computed(() => [
  { label: '本堂课环节（已编号）', value: nodes.value.filter(node => node.seq).length, warn: false },
  { label: '资料阅读', value: nodes.value.filter(node => node.kind === 'MATERIAL').length, warn: false },
  { label: '预习习题', value: nodes.value.filter(node => node.kind === 'QUIZ').length, warn: false },
  { label: '待编排 / 失败', value: nodes.value.filter(node => node.kind === 'DRAFT').length, warn: nodes.value.some(node => node.kind === 'DRAFT') }
])

const detailRows = computed(() => {
  const node = selectedNode.value
  if (!node) return []
  const meta = node.meta || {}
  const rows = []
  rows.push(['环节序号', node.seq ? `第 ${node.seq} / ${node.total} 环节` : '不计入序号（教师私有 / 待编排）'])
  rows.push(['环节类型', kindOf(node).label])
  rows.push(['当前状态', stateLabel(node.state)])
  rows.push(['开放时间', node.opensAt ? formatTime(node.opensAt) : '未设置'])
  rows.push(['截止时间', node.closesAt ? formatTime(node.closesAt) : '不限'])
  if (node.kind === 'QUIZ') {
    rows.push(['解析公布', meta.answerOpensAt ? formatTime(meta.answerOpensAt) : '未设置'])
    rows.push(['提交进度', `${Number(meta.submissionCount || 0)} / ${Number(meta.targetCount || 0)} 人`])
    rows.push(['题目数量', `${Number(meta.itemCount || 0)} 道`])
  }
  if (node.kind === 'MATERIAL' || node.kind === 'DRAFT') {
    rows.push(['资料版本', `V${meta.versionNo || 1}`])
    rows.push(['处理状态', processLabel(meta.processingStatus)])
  }
  if (node.kind === 'PLAN') {
    rows.push(['关联课次', meta.lessonTitle || '未关联'])
    rows.push(['备课材料', `${(meta.materials || []).length} 份`])
    rows.push(['活动预设', `${(meta.templates || []).length} 项`])
  }
  return rows
})

const failureMessage = computed(() => {
  const node = selectedNode.value
  if (!node || node.state !== 'FAILED') return ''
  return (node.meta && node.meta.processingMessage) || '资料转换失败'
})

const privateNotes = computed(() => {
  const node = selectedNode.value
  if (!node || node.kind !== 'PLAN') return ''
  return (node.meta && node.meta.privateNotes) || ''
})

const quizQuestionRows = computed(() => {
  const node = selectedNode.value
  if (!node || node.kind !== 'QUIZ') return []
  return taskRows.value.filter(row =>
    String(row.assignmentId) === String(node.id) && row.assignmentItemId)
})

const planMaterials = computed(() => {
  const node = selectedNode.value
  if (!node || node.kind !== 'PLAN') return []
  return (node.meta && node.meta.materials) || []
})

const planTemplates = computed(() => {
  const node = selectedNode.value
  if (!node || node.kind !== 'PLAN') return []
  return (node.meta && node.meta.templates) || []
})

function isTodayGroup(key) {
  return key === dayKeyOf(serverNow.value || new Date())
}

function groupCountText(group) {
  if (group.key === 'unscheduled') return `${group.nodes.length} 个条目`
  const numbered = group.nodes.filter(node => node.seq).length
  return `本堂课 ${numbered} 个环节${numbered ? ` · 1–${numbered} 号` : ''}`
    + (group.nodes.length > numbered ? ` · 另有 ${group.nodes.length - numbered} 条待编排` : '')
}

function stepHint(node) {
  const parts = [kindOf(node).label]
  if (node.kind === 'QUIZ' && node.meta && node.meta.submitted) parts.push('已提交')
  if (node.state === 'UPCOMING') parts.push('未开放')
  return parts.filter(Boolean).join(' · ')
}

function selectNode(node) {
  selectedKey.value = node.key
}

function canPreview(node) {
  return Boolean(node.meta && node.meta.versionId) && node.state !== 'FAILED'
}

function showVersions(node) {
  return (node.kind === 'MATERIAL' || node.kind === 'DRAFT') && Boolean(node.meta && node.meta.resourceId)
}

function canRevoke(node) {
  return node.kind === 'MATERIAL' && Boolean(node.meta && node.meta.releaseId)
}

function canPublishDraft(node) {
  return node.kind === 'DRAFT'
    && node.state === 'DRAFT'
    && String(node.meta && node.meta.processingStatus || '').toUpperCase() === 'READY'
    && Boolean(node.meta && node.meta.versionId)
}

function canReupload(node) {
  return node.state === 'FAILED' && Boolean(node.meta && node.meta.resourceId)
}

function stageRemoveTarget(node) {
  if (!node) return null
  const kind = (node.kind || '').toUpperCase()
  const meta = node.meta || {}
  if (kind === 'QUIZ') return { kind: 'QUIZ', id: node.id || node.assignmentId }
  if (kind === 'PLAN') return { kind: 'PLAN', id: node.id || node.planId }
  if (kind === 'MATERIAL') return { kind: 'MATERIAL', id: meta.resourceId || node.id }
  if (kind === 'DRAFT') return { kind: 'DRAFT', id: meta.resourceId || node.id }
  return null
}

const removing = ref(false)
async function handleRemove(node) {
  const target = stageRemoveTarget(node)
  if (!target || !target.id) {
    ElMessage.warning('无法确定该环节的目标 ID')
    return
  }
  try {
    await ElMessageBox.confirm(
      `确认删除“${node.title || '环节'}”？\n\n`
      + `· 删除为软删除，学生端立即不可见\n`
      + `· 已提交的预习答案、成绩等历史数据完整保留，不会被一并删除\n`
      + `· 本堂课其他环节的序号会重新编排`,
      '删除环节',
      {
        confirmButtonText: '确认删除',
        cancelButtonText: '取消',
        type: 'warning',
        confirmButtonClass: 'el-button--danger'
      }
    )
  } catch (_) {
    return
  }
  removing.value = true
  try {
    await removeStage({ bindingId: bindingId.value, kind: target.kind, id: target.id })
    ElMessage.success('环节已删除')
    selectedKey.value = null
    nodeDetail.value = null
    await loadData()
  } catch (e) {
    ElMessage.error(e?.message || '删除失败，请稍后重试')
  } finally {
    removing.value = false
  }
}

/* ---------- 对象 URL ---------- */

let objectUrls = []
const downloadAnchor = ref(null)
const downloadHref = ref('')
const downloadName = ref('resource')

function trackUrl(url) {
  objectUrls.push(url)
  return url
}

/* ---------- 资料预览 / 下载 ---------- */

const previewVisible = ref(false)
const previewLoading = ref(false)
const previewTitle = ref('')
const previewKind = ref('image')
const previewImages = ref([])
const previewPage = ref(0)
const previewLink = ref('')
const previewError = ref('')
const previewVersionId = ref(null)

function clearPreviewUrls() {
  objectUrls.forEach(url => {
    try { URL.revokeObjectURL(url) } catch { /* 已释放 */ }
  })
  objectUrls = []
  previewImages.value = []
}

async function openPreview(versionId, title) {
  if (!versionId) {
    ElMessage.warning('该环节没有可预览的资料版本')
    return
  }
  clearPreviewUrls()
  previewVisible.value = true
  previewTitle.value = title || '资料预览'
  previewLoading.value = true
  previewError.value = ''
  previewKind.value = 'image'
  previewPage.value = 0
  previewLink.value = ''
  previewVersionId.value = versionId
  try {
    const res = await getVersion(versionId, bindingId.value)
    const version = res.data || {}
    if (version.mediaKind === 'LINK' && version.externalUri) {
      previewKind.value = 'link'
      previewLink.value = version.externalUri
      return
    }
    if (version.mediaKind === 'VIDEO') {
      const blob = await downloadVersion(versionId, bindingId.value)
      previewKind.value = 'video'
      previewImages.value = [trackUrl(URL.createObjectURL(blob))]
      return
    }
    const pages = Array.isArray(version.pages) ? version.pages : []
    if (!pages.length) {
      previewKind.value = 'empty'
      return
    }
    for (const page of pages) {
      const blob = await getVersionPage(versionId, page.pageNo, bindingId.value)
      previewImages.value = previewImages.value.concat(trackUrl(URL.createObjectURL(blob)))
    }
  } catch (e) {
    if (e && e.cancelled) return
    previewError.value = e && e.message ? e.message : '预览失败'
  } finally {
    previewLoading.value = false
  }
}

function openNodePreview(node) {
  if (!node) return
  openPreview(node.meta && node.meta.versionId, node.title)
}

function downloadNode(node) {
  if (!node) return
  downloadFile(node.meta && node.meta.versionId, node.title)
}

function downloadPreview() {
  downloadFile(previewVersionId.value, previewTitle.value)
}

function extensionOf(blob) {
  const type = (blob && blob.type) || ''
  if (type.includes('pdf')) return '.pdf'
  if (type.includes('word')) return '.docx'
  if (type.includes('presentation')) return '.pptx'
  if (type.includes('video')) return '.mp4'
  if (type.startsWith('image/')) return `.${type.split('/')[1].split('+')[0]}`
  return ''
}

async function downloadFile(versionId, title) {
  if (!versionId) {
    ElMessage.warning('该资料没有可下载的版本')
    return
  }
  try {
    const blob = await downloadVersion(versionId, bindingId.value)
    const url = trackUrl(URL.createObjectURL(blob))
    downloadHref.value = url
    const ext = extensionOf(blob)
    const base = String(title || 'resource').replace(/[\\/:*?"<>|]/g, '_')
    downloadName.value = ext && !base.toLowerCase().endsWith(ext) ? `${base}${ext}` : base
    await nextTick()
    if (downloadAnchor.value) downloadAnchor.value.click()
  } catch (e) {
    if (!e || !e.cancelled) ElMessage.error(e && e.message ? e.message : '下载失败')
  }
}

/* ---------- 版本记录 ---------- */

const versionsVisible = ref(false)
const versionsLoading = ref(false)
const versionRows = ref([])

async function openVersions(node) {
  if (!node || !node.meta || !node.meta.resourceId) {
    ElMessage.warning('该环节没有可查询的版本记录')
    return
  }
  versionsVisible.value = true
  versionsLoading.value = true
  versionRows.value = []
  try {
    const res = await listResourceVersions(node.meta.resourceId, bindingId.value)
    versionRows.value = Array.isArray(res.data) ? res.data : []
  } catch (e) {
    if (!e || !e.cancelled) ElMessage.error(e && e.message ? e.message : '版本记录加载失败')
  } finally {
    versionsLoading.value = false
  }
}

/* ---------- 发布 / 撤回 / 重新上传 ---------- */

const publishVisible = ref(false)
const publishing = ref(false)
const publishNode = ref(null)
const publishForm = reactive({ opensAt: null, closesAt: null })

function openPublish(node) {
  if (!node || !node.meta || !node.meta.versionId) {
    ElMessage.warning('该环节没有可发布的资料版本')
    return
  }
  publishNode.value = node
  const now = Date.now()
  publishForm.opensAt = new Date(now)
  publishForm.closesAt = new Date(now + 7 * 86400000)
  publishVisible.value = true
}

async function submitPublish() {
  const node = publishNode.value
  if (!node) return
  if (!publishForm.opensAt) {
    ElMessage.warning('请选择开放时间')
    return
  }
  if (!publishForm.closesAt) {
    ElMessage.warning('请选择截止时间')
    return
  }
  if (new Date(publishForm.closesAt) <= new Date(publishForm.opensAt)) {
    ElMessage.warning('截止时间必须晚于开放时间')
    return
  }
  publishing.value = true
  try {
    await createRelease({
      bindingId: Number(bindingId.value),
      resourceVersionId: Number(node.meta.versionId),
      opensAt: publishForm.opensAt.toISOString(),
      closesAt: publishForm.closesAt.toISOString()
    })
    ElMessage.success('环节已发布到时间轴')
    publishVisible.value = false
    await loadData()
  } catch (e) {
    if (!e || !e.cancelled) ElMessage.error(e && e.message ? e.message : '发布失败')
  } finally {
    publishing.value = false
  }
}

async function revokeNode(node) {
  if (!node || !node.meta || !node.meta.releaseId) return
  try {
    await ElMessageBox.confirm(
      `确认撤回「${node.title}」的发布？撤回后学生旧链接立即失效。`,
      '撤回发布',
      { confirmButtonText: '确认撤回', cancelButtonText: '取消', type: 'warning' }
    )
  } catch {
    return
  }
  try {
    await revokeRelease(node.meta.releaseId, bindingId.value, '教师从课前时间轴撤回')
    ElMessage.success('资料已撤回，学生端不再可见')
    await loadData()
  } catch (e) {
    if (!e || !e.cancelled) ElMessage.error(e && e.message ? e.message : '撤回失败')
  }
}

const reuploadInput = ref(null)
const reuploadNode = ref(null)

function openReupload(node) {
  if (!node || !node.meta || !node.meta.resourceId) {
    ElMessage.warning('该环节没有可替换的资料')
    return
  }
  reuploadNode.value = node
  if (reuploadInput.value) {
    reuploadInput.value.value = ''
    reuploadInput.value.click()
  }
}

async function onReuploadFile(event) {
  const input = event.target
  const file = input && input.files && input.files[0]
  const node = reuploadNode.value
  if (!file || !node) return
  const data = new FormData()
  data.append('bindingId', String(bindingId.value))
  data.append('file', file)
  try {
    const res = await createResourceVersion(node.meta.resourceId, data)
    const result = res.data || {}
    const ready = String(result.processingStatus || '').toUpperCase() === 'READY'
    if (ready) {
      ElMessage.success('新版本已保存，处理完成后再设置开放时间发布')
    } else {
      ElMessage.warning(result.processingMessage || '新版本已提交，仍在处理中')
    }
    await loadData()
  } catch (e) {
    if (!e || !e.cancelled) ElMessage.error(e && e.message ? e.message : '重新上传失败')
  } finally {
    if (input) input.value = ''
  }
}

/* ---------- 创建流程 ---------- */

const createVisible = ref(false)
const createKind = ref('quiz')
const createStep = ref(0)
const submitting = ref(false)
const materialKeyword = ref('')
const questionKeyword = ref('')

const createForm = reactive({
  title: '',
  instructions: '',
  lessonId: null,
  materialVersionIds: [],
  questionVersionIds: [],
  quizQuestionVersionId: null,
  quizTitle: '课前测验',
  durationSeconds: 900,
  privateNotes: '',
  markReady: true,
  opensAt: new Date(),
  closesAt: new Date(Date.now() + 3 * 86400000),
  answerOpensAt: new Date(Date.now() + 4 * 86400000)
})

const createSteps = computed(() => (createKind.value === 'quiz'
  ? [
      { title: '资料与基本信息', hint: '名称 / 课次 / 资料版本' },
      { title: '选择题目', hint: '从题库导入' },
      { title: '设置时间', hint: '开放 / 截止 / 解析公布' },
      { title: '确认提交', hint: '核对发布内容' }
    ]
  : [
      { title: '选择资料版本', hint: '纳入备课包的课件' },
      { title: '选择题目', hint: '随堂题预设' },
      { title: '备课信息', hint: '课次 / 名称 / 私有备注' },
      { title: '确认提交', hint: '核对备课包内容' }
    ]))

const materialOptions = computed(() => (overview.value.materialVersions || []).map(item => ({
  value: Number(item.versionId),
  title: item.title || '未命名资料',
  versionNo: item.versionNo || 1,
  meta: `${item.mimeType || '文件'} · ${Number(item.pageCount || 0)} 页`
    + (item.createdAt ? ` · 上传于 ${formatTime(item.createdAt)}` : ''),
  search: `${item.title || ''} ${item.mimeType || ''}`.toLowerCase()
})))

const questionOptions = computed(() => (overview.value.questionVersions || []).map(item => ({
  value: Number(item.versionId),
  title: item.title || '未命名题目',
  meta: `${QUESTION_KIND_LABELS[item.questionKind] || item.questionKind || '题目'} · ${Number(item.maxScore || 0)} 分 · V${item.versionNo || 1}`,
  stem: item.stem || '',
  search: `${item.title || ''} ${item.stem || ''} ${QUESTION_KIND_LABELS[item.questionKind] || ''}`.toLowerCase()
})))

const lessonOptions = computed(() => (overview.value.lessons || []).map(item => ({
  value: Number(item.lessonId),
  label: `${item.title || '未命名课次'}${item.scheduledAt ? ` · ${formatTime(item.scheduledAt)}` : ''}`
})))

const filteredMaterials = computed(() => {
  const keyword = materialKeyword.value.trim().toLowerCase()
  if (!keyword) return materialOptions.value
  return materialOptions.value.filter(item => item.search.includes(keyword))
})

const filteredQuestions = computed(() => {
  const keyword = questionKeyword.value.trim().toLowerCase()
  if (!keyword) return questionOptions.value
  return questionOptions.value.filter(item => item.search.includes(keyword))
})

const confirmMaterials = computed(() =>
  materialOptions.value.filter(item => createForm.materialVersionIds.includes(item.value)))

const confirmQuestions = computed(() => {
  if (createKind.value === 'quiz') {
    return questionOptions.value.filter(item => createForm.questionVersionIds.includes(item.value))
  }
  return questionOptions.value.filter(item => item.value === createForm.quizQuestionVersionId)
})

const confirmRows = computed(() => {
  if (createKind.value === 'quiz') {
    const count = createForm.questionVersionIds.length
    return [
      ['环节名称', createForm.title || '未填写'],
      ['生成环节', `${count || 1} 个${count > 1 ? '（每题一个独立环节）' : ''}`],
      ['同步资料', `${createForm.materialVersionIds.length} 份`],
      ['导入题目', `${count} 道`],
      ['开放', formatTime(createForm.opensAt)],
      ['截止', formatTime(createForm.closesAt)],
      ['解析公布', formatTime(createForm.answerOpensAt)]
    ]
  }
  const lesson = lessonOptions.value.find(item => item.value === createForm.lessonId)
  return [
    ['备课包名称', createForm.title || '未填写'],
    ['关联课次', lesson ? lesson.label : '未选择'],
    ['纳入资料', `${createForm.materialVersionIds.length} 份`],
    ['随堂题预设', createForm.quizQuestionVersionId ? '已选择 1 道' : '不预设'],
    ['活动时长', `${Number(createForm.durationSeconds) || 0} 秒`],
    ['状态', createForm.markReady ? '可用方案' : '草稿']
  ]
})

function openCreate(kind) {
  if (!bindingId.value) {
    ElMessage.warning('请先选择课程与教学班')
    return
  }
  if (kind === 'plan' && !lessonOptions.value.length) {
    ElMessage.warning('当前教学班还没有课次，请先由教务侧维护课次后再保存备课包')
    return
  }
  createKind.value = kind
  const now = Date.now()
  Object.assign(createForm, {
    title: '',
    instructions: '',
    lessonId: null,
    materialVersionIds: [],
    questionVersionIds: [],
    quizQuestionVersionId: null,
    quizTitle: '课前测验',
    durationSeconds: 900,
    privateNotes: '',
    markReady: true,
    opensAt: new Date(now),
    closesAt: new Date(now + (kind === 'quiz' ? 3 : 14) * 86400000),
    answerOpensAt: new Date(now + 4 * 86400000)
  })
  materialKeyword.value = ''
  questionKeyword.value = ''
  createStep.value = 0
  createVisible.value = true
}

function selectAllMaterials() {
  createForm.materialVersionIds = filteredMaterials.value.map(item => item.value)
}

function selectAllQuestions() {
  createForm.questionVersionIds = filteredQuestions.value.map(item => item.value)
}

function validateCreateStep(step, silent) {
  const fail = message => {
    if (!silent) ElMessage.warning(message)
    return false
  }
  if (createKind.value === 'quiz') {
    if (step === 0 && !createForm.title.trim()) return fail('请填写环节名称')
    if (step === 2) {
      if (!createForm.opensAt) return fail('请选择开放时间')
      if (!createForm.closesAt) return fail('请选择截止时间')
      if (new Date(createForm.closesAt) <= new Date(createForm.opensAt)) return fail('截止时间必须晚于开放时间')
      if (!createForm.answerOpensAt) return fail('请选择解析公布时间')
      if (new Date(createForm.answerOpensAt) < new Date(createForm.closesAt)) return fail('解析公布时间不能早于截止时间')
    }
  } else if (step === 2) {
    if (!createForm.title.trim()) return fail('请填写备课包名称')
    if (!createForm.lessonId) return fail('请选择课次')
    if (Number(createForm.durationSeconds) < 60) return fail('答题时长不能少于 60 秒')
  }
  return true
}

function nextCreateStep() {
  if (!validateCreateStep(createStep.value, false)) return
  createStep.value = Math.min(createSteps.value.length - 1, createStep.value + 1)
}

function prevCreateStep() {
  createStep.value = Math.max(0, createStep.value - 1)
}

function validateAllSteps() {
  for (let index = 0; index < createSteps.value.length; index += 1) {
    if (!validateCreateStep(index, true)) {
      createStep.value = index
      validateCreateStep(index, false)
      return false
    }
  }
  return true
}

async function submitCreate() {
  if (!validateAllSteps()) return
  submitting.value = true
  let published = 0
  let expected = 1
  try {
    if (createKind.value === 'quiz') {
      const base = {
        bindingId: Number(bindingId.value),
        lessonId: createForm.lessonId ? Number(createForm.lessonId) : null,
        title: createForm.title.trim(),
        instructions: createForm.instructions.trim(),
        opensAt: createForm.opensAt ? createForm.opensAt.toISOString() : null,
        closesAt: createForm.closesAt ? createForm.closesAt.toISOString() : null,
        answerOpensAt: createForm.answerOpensAt ? createForm.answerOpensAt.toISOString() : null,
        resourceVersionIds: createForm.materialVersionIds.map(Number)
      }
      const questionIds = createForm.questionVersionIds.map(Number)
      const tasks = questionIds.length ? questionIds : [null]
      expected = tasks.length
      for (let index = 0; index < tasks.length; index += 1) {
        const body = {
          ...base,
          title: tasks.length > 1 ? `${base.title} · 第 ${index + 1} / ${tasks.length} 题` : base.title,
          questionVersionId: tasks[index],
          resourceVersionIds: index === 0 ? base.resourceVersionIds : []
        }
        await createPreviewTask(body)
        published += 1
      }
      ElMessage.success(tasks.length > 1
        ? `已从题库导入 ${questionIds.length} 道题，生成 ${tasks.length} 个独立预习环节`
        : '预习习题环节已加入时间轴')
    } else {
      const body = {
        bindingId: Number(bindingId.value),
        lessonId: createForm.lessonId ? Number(createForm.lessonId) : null,
        title: createForm.title.trim(),
        privateNotes: createForm.privateNotes.trim(),
        materialVersionIds: createForm.materialVersionIds.map(Number),
        quizQuestionVersionId: createForm.quizQuestionVersionId ? Number(createForm.quizQuestionVersionId) : null,
        quizTitle: createForm.quizTitle.trim(),
        durationSeconds: Number(createForm.durationSeconds) || 900,
        markReady: createForm.markReady
      }
      await createPlan(body)
      ElMessage.success('备课包已加入时间轴（仅教师可见）')
    }
    createVisible.value = false
    selectedKey.value = ''
    await loadData()
  } catch (e) {
    if (e && e.cancelled) return
    const message = e && e.message ? e.message : '提交失败，请稍后重试'
    ElMessage.error(createKind.value === 'quiz' && published > 0
      ? `已发布 ${published} / ${expected} 个环节，剩余未发布：${message}`
      : message)
    if (published > 0) await loadData()
  } finally {
    submitting.value = false
  }
}

/* ---------- 创建资料阅读环节 ---------- */

const materialVisible = ref(false)
const materialSubmitting = ref(false)
const materialFileList = ref([])
const materialFile = ref(null)

const materialForm = reactive({
  title: '',
  opensAt: new Date(),
  closesAt: new Date(Date.now() + 7 * 86400000),
  source: 'existing',
  versionId: null
})

function openMaterialCreate() {
  if (!bindingId.value) {
    ElMessage.warning('请先选择课程与教学班')
    return
  }
  const now = Date.now()
  Object.assign(materialForm, {
    title: '',
    opensAt: new Date(now),
    closesAt: new Date(now + 7 * 86400000),
    source: materialOptions.value.length ? 'existing' : 'upload',
    versionId: null
  })
  materialFile.value = null
  materialFileList.value = []
  materialVisible.value = true
}

function onMaterialFileChange(file, files) {
  materialFile.value = file && file.raw ? file.raw : null
  materialFileList.value = files.slice(-1)
}

function onMaterialFileRemove() {
  materialFile.value = null
  materialFileList.value = []
}

function selectNodeByVersionId(versionId) {
  if (!versionId) return
  const key = `material-${versionId}`
  const hit = nodes.value.find(node => node.key === key || String(node.id) === String(versionId))
  if (hit) {
    selectedKey.value = hit.key
    return
  }
  const byMeta = nodes.value.find(node => node.meta && String(node.meta.versionId) === String(versionId))
  if (byMeta) selectedKey.value = byMeta.key
}

async function submitMaterialCreate() {
  if (!materialForm.opensAt) {
    ElMessage.warning('请选择开放时间')
    return
  }
  if (!materialForm.closesAt) {
    ElMessage.warning('请选择截止时间')
    return
  }
  if (new Date(materialForm.closesAt) <= new Date(materialForm.opensAt)) {
    ElMessage.warning('截止时间必须晚于开放时间')
    return
  }
  if (materialForm.source === 'existing' && !materialForm.versionId) {
    ElMessage.warning('请选择要发布的资料版本')
    return
  }
  if (materialForm.source === 'upload' && !materialFile.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  const opensAt = materialForm.opensAt.toISOString()
  const closesAt = materialForm.closesAt.toISOString()
  materialSubmitting.value = true
  try {
    let versionId = materialForm.versionId ? Number(materialForm.versionId) : null
    if (materialForm.source === 'upload') {
      const data = new FormData()
      data.append('bindingId', String(bindingId.value))
      data.append('title', materialForm.title.trim())
      data.append('file', materialFile.value)
      const res = await createResource(data)
      const uploaded = res.data || {}
      if (String(uploaded.processingStatus || '').toUpperCase() !== 'READY' || !uploaded.versionId) {
        ElMessage.warning(uploaded.processingMessage || '资料已上传但处理失败，已放入「待编排」，请重新上传新版本')
        materialVisible.value = false
        await loadData()
        return
      }
      versionId = Number(uploaded.versionId)
    }
    await createRelease({
      bindingId: Number(bindingId.value),
      resourceVersionId: versionId,
      opensAt,
      closesAt
    })
    ElMessage.success('资料阅读环节已加入时间轴')
    materialVisible.value = false
    await loadData()
    selectNodeByVersionId(versionId)
  } catch (e) {
    if (!e || !e.cancelled) ElMessage.error(e && e.message ? e.message : '发布失败，请稍后重试')
  } finally {
    materialSubmitting.value = false
  }
}
</script>

<style scoped>
.group3-prepare {
  padding-bottom: 24px;
}
.mb8 {
  margin-bottom: 12px;
}
.prepare-head {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  gap: 16px;
  flex-wrap: wrap;
  margin-bottom: 12px;
}
.prepare-title {
  margin: 0;
  font-size: 20px;
  color: #303133;
}
.prepare-sub {
  margin: 6px 0 0;
  color: #606266;
  font-size: 14px;
}
.prepare-help {
  margin: 6px 0 0;
  color: #909399;
  font-size: 12px;
  max-width: 760px;
}
.prepare-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.stat-row {
  margin-bottom: 4px;
}
.stat-card {
  margin-bottom: 12px;
  border-radius: 6px;
}
.stat-card.warn {
  border-color: #f3d19e;
  background: #fdf6ec;
}
.stat-label {
  font-size: 12px;
  color: #909399;
}
.stat-value {
  margin-top: 6px;
  font-size: 24px;
  font-weight: 600;
  color: #303133;
}
.panel {
  border-radius: 6px;
  margin-bottom: 16px;
}
.panel-head {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
}
.panel-title {
  font-weight: 600;
  color: #303133;
}
.panel-tools {
  margin-left: auto;
  display: flex;
  align-items: center;
  gap: 8px;
}
.edu-hint {
  font-size: 12px;
  color: #909399;
}
.timeline {
  max-height: 760px;
  overflow-y: auto;
  padding-right: 4px;
}
.day-group + .day-group {
  margin-top: 16px;
}
.day-head {
  display: flex;
  align-items: center;
  gap: 8px;
  margin-bottom: 8px;
  color: #303133;
  font-size: 13px;
}
.day-dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: #409eff;
}
.day-count {
  font-size: 12px;
  color: #909399;
}
.tl-item {
  display: grid;
  grid-template-columns: 58px 14px 1fr;
  gap: 6px;
  margin-bottom: 10px;
}
.tl-time {
  text-align: right;
  padding-top: 6px;
  color: #606266;
}
.tl-time strong {
  display: block;
  font-size: 13px;
}
.tl-time span {
  font-size: 11px;
  color: #909399;
}
.tl-dot {
  width: 10px;
  height: 10px;
  border-radius: 50%;
  margin-top: 10px;
  margin-left: 2px;
  background: #c0c4cc;
}
.tl-dot.tone-blue {
  background: #409eff;
}
.tl-dot.tone-teal {
  background: #13c2c2;
}
.tl-dot.tone-amber {
  background: #e6a23c;
}
.tl-dot.tone-slate {
  background: #909399;
}
.tl-card {
  border: 1px solid #ebeef5;
  border-left: 4px solid #c0c4cc;
  border-radius: 6px;
  padding: 10px 12px;
  background: #fff;
  cursor: pointer;
  transition: box-shadow 0.2s, border-color 0.2s;
}
.tl-card:hover {
  box-shadow: 0 2px 10px rgba(0, 0, 0, 0.08);
}
.tl-card.active {
  border-color: #409eff;
  background: #f5f9ff;
}
.tl-card.tone-blue {
  border-left-color: #409eff;
}
.tl-card.tone-teal {
  border-left-color: #13c2c2;
}
.tl-card.tone-amber {
  border-left-color: #e6a23c;
}
.tl-card.tone-slate {
  border-left-color: #909399;
}
.tl-card-head {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.tl-seq {
  font-size: 12px;
  color: #606266;
}
.tl-seq.muted {
  color: #c0c4cc;
}
.tl-title {
  margin-top: 6px;
  font-size: 14px;
  font-weight: 600;
  color: #303133;
}
.tl-subtitle {
  margin-top: 4px;
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.tl-tags {
  display: flex;
  gap: 6px;
  flex-wrap: wrap;
  margin-top: 6px;
}
.tl-actions {
  display: flex;
  gap: 12px;
  flex-wrap: wrap;
  margin-top: 6px;
  padding-top: 6px;
  border-top: 1px dashed #ebeef5;
}
.stage-track {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
}
.stage-track-label {
  display: flex;
  align-items: baseline;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.stage-track-label strong {
  color: #303133;
  font-size: 14px;
}
.stage-track-label span {
  color: #909399;
  font-size: 12px;
}
.stage-group + .stage-group {
  margin-top: 10px;
}
.stage-group-label {
  display: flex;
  align-items: baseline;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 6px;
}
.stage-group-label strong {
  font-size: 13px;
  color: #606266;
}
.stage-group-label span {
  font-size: 12px;
  color: #909399;
}
.stage-track-steps {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
}
.stage-step {
  display: inline-flex;
  align-items: center;
  gap: 8px;
  padding: 6px 10px;
  border: 1px solid #dcdfe6;
  border-radius: 6px;
  background: #fff;
  cursor: pointer;
  text-align: left;
  max-width: 240px;
}
.stage-step.active {
  border-color: #409eff;
  background: #ecf5ff;
}
.stage-step-no {
  width: 22px;
  height: 22px;
  border-radius: 50%;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  font-size: 12px;
  color: #fff;
  background: #c0c4cc;
  flex: none;
}
.stage-step.tone-blue .stage-step-no {
  background: #409eff;
}
.stage-step.tone-teal .stage-step-no {
  background: #13c2c2;
}
.stage-step-text {
  display: flex;
  flex-direction: column;
  min-width: 0;
}
.stage-step-text strong {
  font-size: 12px;
  color: #303133;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.stage-step-text small {
  font-size: 11px;
  color: #909399;
}
.detail-title {
  font-size: 16px;
  font-weight: 600;
  color: #303133;
}
.detail-rows {
  margin-top: 10px;
}
.detail-row {
  display: flex;
  justify-content: space-between;
  gap: 12px;
  padding: 7px 0;
  border-bottom: 1px dashed #ebeef5;
  font-size: 13px;
}
.detail-row span {
  color: #909399;
}
.detail-row strong {
  color: #303133;
  font-weight: 500;
  text-align: right;
}
.detail-section h4 {
  margin: 0 0 8px;
  font-size: 14px;
  color: #303133;
}
.question-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
}
.question-row {
  display: flex;
  gap: 8px;
  align-items: flex-start;
  font-size: 13px;
  color: #606266;
}
.q-no {
  width: 20px;
  height: 20px;
  border-radius: 50%;
  background: #f0f2f5;
  color: #909399;
  font-size: 12px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  flex: none;
}
.plan-list {
  display: flex;
  flex-direction: column;
  gap: 4px;
  margin-bottom: 8px;
}
.plan-row {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 12px;
  font-size: 13px;
  color: #606266;
}
.detail-actions {
  display: flex;
  gap: 8px;
  flex-wrap: wrap;
  margin: 12px 0 8px;
}
.preview-body {
  min-height: 160px;
}
.preview-note {
  padding: 24px 0;
  text-align: center;
  color: #606266;
  font-size: 14px;
}
.preview-box {
  display: flex;
  justify-content: center;
  background: #f5f7fa;
  border-radius: 6px;
  padding: 8px;
}
.preview-box img,
.preview-box video {
  max-width: 100%;
  max-height: 68vh;
  object-fit: contain;
}
.preview-nav {
  display: flex;
  align-items: center;
  justify-content: center;
  gap: 14px;
  margin-top: 10px;
}
.create-steps {
  margin-bottom: 18px;
}
.create-panel {
  min-height: 220px;
}
.picker-lead {
  display: flex;
  align-items: baseline;
  gap: 10px;
  margin: 8px 0;
  flex-wrap: wrap;
}
.picker-lead strong {
  font-size: 14px;
  color: #303133;
}
.picker-lead span {
  font-size: 12px;
  color: #909399;
}
.picker {
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px;
}
.picker-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 8px;
}
.picker-tools {
  display: flex;
  align-items: center;
  gap: 8px;
}
.picker-list {
  display: flex;
  flex-direction: column;
  gap: 6px;
  max-height: 320px;
  overflow-y: auto;
}
.picker-item {
  width: 100%;
  height: auto;
  margin: 0;
  padding: 8px 10px;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  align-items: flex-start;
}
.picker-item :deep(.el-checkbox__label),
.picker-item :deep(.el-radio__label) {
  width: 100%;
  white-space: normal;
}
.picker-item-main {
  display: flex;
  flex-direction: column;
  gap: 2px;
}
.picker-item-title {
  font-size: 13px;
  color: #303133;
  display: inline-flex;
  align-items: center;
  gap: 6px;
}
.picker-item-meta {
  font-size: 12px;
  color: #909399;
}
.picker-item-desc {
  font-size: 12px;
  color: #606266;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  overflow: hidden;
}
.summary-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(200px, 1fr));
  gap: 10px;
}
.summary-grid > div {
  background: #fafafa;
  border: 1px solid #ebeef5;
  border-radius: 6px;
  padding: 10px 12px;
}
.summary-grid span {
  display: block;
  font-size: 12px;
  color: #909399;
}
.summary-grid strong {
  display: block;
  margin-top: 4px;
  font-size: 14px;
  color: #303133;
}
.summary-list {
  margin-top: 12px;
  font-size: 13px;
  color: #606266;
}
.summary-list ul {
  margin: 6px 0 0;
  padding-left: 18px;
}
.summary-list li {
  line-height: 1.8;
}
.download-anchor {
  display: none;
}
.hidden-file {
  display: none;
}
.node-list {
  max-height: 760px;
}
.list-title-cell {
  display: flex;
  align-items: center;
  gap: 6px;
  flex-wrap: wrap;
}
.list-title {
  font-size: 13px;
  font-weight: 600;
  color: #303133;
}
.list-subtitle {
  margin-top: 2px;
  font-size: 12px;
  color: #909399;
  overflow: hidden;
  text-overflow: ellipsis;
  white-space: nowrap;
}
.node-list :deep(.el-table__row) {
  cursor: pointer;
}
</style>
