<template>
  <el-dialog
    v-model="visible"
    :title="isIdeology ? '课程思政素材库' : '公共教学资源库'"
    width="90%"
    top="5vh"
    :close-on-click-modal="false"
    class="public-library-dialog"
    destroy-on-close
  >
    <div class="public-library">
      <!-- 顶部搜索和上传 -->
      <div class="library-header">
        <div class="search-bar">
          <el-input
            v-model="searchForm.keyword"
            placeholder="搜索资源名称、描述、标签..."
            clearable
            size="large"
            style="width: 400px"
            @keyup.enter="handleSearch"
            @clear="handleSearch"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>
          <el-button type="primary" size="large" @click="handleSearch">搜索</el-button>
        </div>
        <div class="header-actions">
          <el-button type="primary" size="large" @click="openUploadDialog">
            <el-icon><Upload /></el-icon>
            {{ isIdeology ? '上传思政素材' : '上传资源' }}
          </el-button>
        </div>
      </div>

      <div class="library-body">
        <!-- 左侧分类 -->
        <div class="library-sidebar">
          <div class="sidebar-title">{{ isIdeology ? '思政主题' : '资源分类' }}</div>
          <ul class="category-list">
            <li
              :class="{ active: !searchForm.categoryId }"
              @click="selectCategory(null)"
            >
              <el-icon><Folder /></el-icon>
              {{ isIdeology ? '全部主题' : '全部资源' }}
            </li>
            <li
              v-for="cat in categories"
              :key="cat.id"
              :class="{ active: searchForm.categoryId === cat.id }"
              @click="selectCategory(cat.id)"
            >
              <el-icon><Folder /></el-icon>
              {{ cat.name }}
            </li>
          </ul>

          <div class="sidebar-section">
            <div class="sidebar-title">资源类型</div>
            <el-radio-group v-model="searchForm.resourceKind" size="small" @change="handleSearch">
              <el-radio-button value="">全部</el-radio-button>
              <el-radio-button value="FILE">文件</el-radio-button>
              <el-radio-button value="LINK">链接</el-radio-button>
            </el-radio-group>
          </div>

          <div v-if="!isIdeology" class="sidebar-section">
            <div class="sidebar-title">我的</div>
            <ul class="category-list">
              <li :class="{ active: activeTab === 'favorites' }" @click="switchTab('favorites')">
                <el-icon><Star /></el-icon>
                我的收藏
              </li>
              <li :class="{ active: activeTab === 'mine' }" @click="switchTab('mine')">
                <el-icon><User /></el-icon>
                我的上传
              </li>
            </ul>
          </div>
        </div>

        <!-- 右侧资源列表 -->
        <div class="library-content">
          <div v-loading="loading" class="resource-grid">
            <div
              v-for="item in resourceList"
              :key="item.id"
              class="resource-card"
              @click="openDetail(item)"
            >
              <div class="card-cover">
                <div class="cover-icon">
                  <el-icon :size="48">
                    <component :is="mediaIcon(item.mediaKind)" />
                  </el-icon>
                </div>
                <div class="card-badge">{{ kindLabel(item.resourceKind) }}</div>
              </div>
              <div class="card-body">
                <div class="card-title" :title="item.title">{{ item.title }}</div>
                <div class="card-meta">
                  <span>{{ item.categoryName || '未分类' }}</span>
                  <span>{{ item.submitterName || '未知' }}</span>
                </div>
                <div class="card-stats">
                  <span><el-icon><View /></el-icon> {{ item.viewCount || 0 }}</span>
                  <span><el-icon><Download /></el-icon> {{ item.downloadCount || 0 }}</span>
                </div>
              </div>
            </div>

            <el-empty v-if="!loading && resourceList.length === 0" description="暂无资源" />
          </div>

          <div v-if="total > 0" class="pagination-wrapper">
            <el-pagination
              v-model:current-page="searchForm.pageNum"
              v-model:page-size="searchForm.pageSize"
              :total="total"
              :page-sizes="[12, 20, 40, 60]"
              layout="total, sizes, prev, pager, next, jumper"
              background
              @size-change="handleSearch"
              @current-change="handleSearch"
            />
          </div>
        </div>
      </div>
    </div>

    <!-- 上传对话框 -->
    <el-dialog
      v-model="uploadVisible"
      :title="isIdeology
        ? (uploadForm.linkMode ? '上传思政外链素材' : '上传思政文件素材')
        : (uploadForm.linkMode ? '上传外链资源' : '上传文件资源')"
      width="560px"
      :close-on-click-modal="false"
      append-to-body
    >
      <el-form :model="uploadForm" label-width="100px" ref="uploadFormRef">
        <el-form-item label="资源名称" prop="title" :rules="[{ required: true, message: '请输入资源名称', trigger: 'blur' }]">
          <el-input v-model="uploadForm.title" maxlength="200" show-word-limit placeholder="请输入资源名称" />
        </el-form-item>

        <el-form-item label="资源分类" prop="categoryId">
          <el-select v-model="uploadForm.categoryId" placeholder="请选择分类" style="width: 100%">
            <el-option v-for="cat in categories" :key="cat.id" :label="cat.name" :value="cat.id" />
          </el-select>
        </el-form-item>

        <el-form-item label="上传方式">
          <el-radio-group v-model="uploadForm.linkMode">
            <el-radio :value="false">上传文件</el-radio>
            <el-radio :value="true">添加链接</el-radio>
          </el-radio-group>
        </el-form-item>

        <template v-if="!uploadForm.linkMode">
          <el-form-item label="选择文件" prop="file" :rules="[{ required: true, message: '请选择文件', trigger: 'change' }]">
            <el-upload
              v-model:file-list="fileList"
              :auto-upload="false"
              :limit="1"
              accept=""
              drag
            >
              <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
              <div class="el-upload__text">拖拽文件到此处，或<em>点击选择</em></div>
            </el-upload>
          </el-form-item>
        </template>

        <template v-else>
          <el-form-item label="链接地址" prop="externalUri" :rules="[
            { required: true, message: '请输入链接地址', trigger: 'blur' },
            { pattern: /^https?:\/\/.+/, message: '请输入有效的 http/https 链接', trigger: 'blur' }
          ]">
            <el-input v-model="uploadForm.externalUri" placeholder="https://example.com" />
          </el-form-item>
        </template>

        <el-form-item label="资源描述">
          <el-input v-model="uploadForm.description" type="textarea" :rows="3" maxlength="500" show-word-limit placeholder="简要描述资源内容" />
        </el-form-item>

        <el-form-item label="标签">
          <el-input v-model="uploadForm.tags" placeholder="多个标签用逗号分隔" />
        </el-form-item>

        <el-form-item label="难度">
          <el-radio-group v-model="uploadForm.difficulty">
            <el-radio value="EASY">简单</el-radio>
            <el-radio value="MEDIUM">中等</el-radio>
            <el-radio value="HARD">困难</el-radio>
          </el-radio-group>
        </el-form-item>
      </el-form>

      <template #footer>
        <el-button @click="uploadVisible = false">取消</el-button>
        <el-button type="primary" :loading="uploadLoading" @click="submitUpload">确认上传</el-button>
      </template>
    </el-dialog>

    <!-- 详情对话框 -->
    <el-dialog
      v-model="detailVisible"
      title="资源详情"
      width="720px"
      :close-on-click-modal="false"
      append-to-body
      destroy-on-close
    >
      <div v-if="detail" class="resource-detail">
        <div class="detail-header">
          <h3>{{ detail.title }}</h3>
          <div class="detail-tags">
            <el-tag size="small">{{ detail.categoryName || '未分类' }}</el-tag>
            <el-tag size="small" type="info">{{ kindLabel(detail.resourceKind) }}</el-tag>
            <el-tag v-if="detail.difficulty" size="small">{{ difficultyLabel(detail.difficulty) }}</el-tag>
          </div>
        </div>

        <div class="detail-info">
          <div class="info-row">
            <span>提交者：{{ detail.submitterName || '未知' }}</span>
            <span>浏览：{{ detail.viewCount || 0 }}</span>
            <span>下载：{{ detail.downloadCount || 0 }}</span>
          </div>
          <div v-if="detail.tags" class="info-row">
            <span class="label">标签：</span>
            <span>{{ detail.tags }}</span>
          </div>
        </div>

        <div v-if="detail.description" class="detail-desc">
          <div class="desc-label">资源描述</div>
          <div class="desc-content">{{ detail.description }}</div>
        </div>

        <div class="detail-preview">
          <div class="desc-label">预览</div>
          <div v-if="detail.resourceKind === 'LINK'" class="link-preview">
            <el-button type="primary" @click="openExternalLink(detail.externalUri)">
              <el-icon><Link /></el-icon>
              打开外部链接
            </el-button>
            <div class="link-url">{{ detail.externalUri }}</div>
          </div>
          <div v-else-if="detail.processingStatus === 'PENDING'" class="preview-empty">
            <el-icon :size="48"><Loading /></el-icon>
            <p>资源转换中，请稍后刷新查看</p>
          </div>
          <div v-else-if="detail.processingStatus === 'FAILED'" class="preview-empty">
            <el-icon :size="48"><Warning /></el-icon>
            <p>资源转换失败</p>
          </div>
          <div v-else-if="pageList.length > 0" class="preview-pages">
            <el-image
              v-for="page in pageList.slice(0, 6)"
              :key="page.pageNo"
              :src="page.url"
              fit="cover"
              class="preview-thumb"
              :preview-src-list="pageList.map(p => p.url)"
              :initial-index="page.pageNo - 1"
            />
          </div>
          <div v-else class="preview-empty">
            <el-icon :size="48"><Document /></el-icon>
            <p>暂无预览</p>
          </div>
        </div>
      </div>

      <template #footer>
        <el-button @click="toggleFavorite">
          <el-icon><component :is="detail?.favorited ? StarFilled : Star" /></el-icon>
          {{ detail?.favorited ? '已收藏' : '收藏' }}
        </el-button>
        <el-button
          v-if="detail?.resourceKind === 'LINK'"
          @click="openExternalLink(detail.externalUri)"
        >
          <el-icon><Link /></el-icon>
          访问链接
        </el-button>
        <el-button type="primary" :loading="importing" @click="handleImport">
          <el-icon><Download /></el-icon>
          添加到课程资料
        </el-button>
      </template>
    </el-dialog>
  </el-dialog>
</template>

<script setup name="PublicLibrary">
import { ref, reactive, computed, watch, onMounted } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import {
  Search, Upload, UploadFilled, Folder, Star, StarFilled, User,
  View, Download, Document, VideoCamera, Link, Loading, Warning, Picture, Files, Microphone
} from '@element-plus/icons-vue'
import {
  listPublicCategories, listPublicResources, getPublicResource,
  uploadPublicResource, uploadPublicLink, getPublicVersionPages,
  downloadPublicResource, togglePublicFavorite, listMyPublicFavorites,
  listMyPublicResources, importPublicResource
} from '@/api/education/group3'

const props = defineProps({
  modelValue: Boolean,
  bindingId: [Number, String],
  folderId: [Number, String],
  unitId: [Number, String],
  kind: { type: String, default: 'RESOURCE' }
})

const emit = defineEmits(['update:modelValue'])

const isIdeology = computed(() => props.kind === 'IDEOLOGY')

const visible = computed({
  get: () => props.modelValue,
  set: v => emit('update:modelValue', v)
})

// 数据
const loading = ref(false)
const categories = ref([])
const resourceList = ref([])
const total = ref(0)
const activeTab = ref('all') // all / favorites / mine

const searchForm = reactive({
  keyword: '',
  categoryId: null,
  resourceKind: '',
  pageNum: 1,
  pageSize: 12
})

// 上传
const uploadVisible = ref(false)
const uploadLoading = ref(false)
const uploadFormRef = ref(null)
const fileList = ref([])
const uploadForm = reactive({
  title: '',
  categoryId: null,
  linkMode: false,
  file: null,
  externalUri: '',
  description: '',
  tags: '',
  difficulty: 'MEDIUM'
})

// 详情
const detailVisible = ref(false)
const detail = ref(null)
const pageList = ref([])
const importing = ref(false)

// 加载分类
async function loadCategories() {
  try {
    const res = await listPublicCategories(props.kind)
    categories.value = res.data || []
  } catch (e) {
    console.error('加载分类失败', e)
  }
}

// 加载资源列表
async function loadResources() {
  loading.value = true
  try {
    if (activeTab.value === 'favorites') {
      const res = await listMyPublicFavorites()
      resourceList.value = res.data || []
      total.value = resourceList.value.length
    } else if (activeTab.value === 'mine') {
      const res = await listMyPublicResources({
        pageNum: searchForm.pageNum,
        pageSize: searchForm.pageSize
      })
      resourceList.value = res.data.list || []
      total.value = res.data.total || 0
    } else {
      const res = await listPublicResources({
        ...searchForm,
        categoryKind: isIdeology.value ? 'IDEOLOGY' : undefined,
        excludeCategoryKind: isIdeology.value ? undefined : 'IDEOLOGY'
      })
      resourceList.value = res.data.list || []
      total.value = res.data.total || 0
    }
  } catch (e) {
    console.error('加载资源失败', e)
  } finally {
    loading.value = false
  }
}

function handleSearch() {
  searchForm.pageNum = 1
  loadResources()
}

function selectCategory(id) {
  activeTab.value = 'all'
  searchForm.categoryId = id
  searchForm.pageNum = 1
  loadResources()
}

function switchTab(tab) {
  activeTab.value = tab
  searchForm.categoryId = null
  searchForm.keyword = ''
  searchForm.pageNum = 1
  loadResources()
}

// 上传
function openUploadDialog() {
  uploadForm.title = ''
  uploadForm.categoryId = categories.value[0]?.id || null
  uploadForm.linkMode = false
  uploadForm.externalUri = ''
  uploadForm.description = ''
  uploadForm.tags = ''
  uploadForm.difficulty = 'MEDIUM'
  fileList.value = []
  uploadVisible.value = true
}

async function submitUpload() {
  if (!uploadForm.title?.trim()) {
    ElMessage.warning('请输入资源名称')
    return
  }
  if (!uploadForm.linkMode && fileList.value.length === 0) {
    ElMessage.warning('请选择文件')
    return
  }
  if (uploadForm.linkMode && !uploadForm.externalUri?.trim()) {
    ElMessage.warning('请输入链接地址')
    return
  }
  if (uploadForm.linkMode && !/^https?:\/\/.+/.test(uploadForm.externalUri.trim())) {
    ElMessage.warning('请输入有效的 http/https 链接')
    return
  }

  uploadLoading.value = true
  try {
    if (uploadForm.linkMode) {
      await uploadPublicLink({
        title: uploadForm.title.trim(),
        categoryId: uploadForm.categoryId,
        description: uploadForm.description,
        tags: uploadForm.tags,
        difficulty: uploadForm.difficulty,
        externalUri: uploadForm.externalUri.trim()
      })
    } else {
      const formData = new FormData()
      formData.append('title', uploadForm.title.trim())
      if (uploadForm.categoryId) formData.append('categoryId', uploadForm.categoryId)
      if (uploadForm.description) formData.append('description', uploadForm.description)
      if (uploadForm.tags) formData.append('tags', uploadForm.tags)
      if (uploadForm.difficulty) formData.append('difficulty', uploadForm.difficulty)
      formData.append('file', fileList.value[0].raw)
      await uploadPublicResource(formData)
    }
    ElMessage.success('资源上传成功')
    uploadVisible.value = false
    loadResources()
  } catch (e) {
    ElMessage.error(e.message || '上传失败')
  } finally {
    uploadLoading.value = false
  }
}

// 详情
async function openDetail(item) {
  try {
    const res = await getPublicResource(item.id)
    detail.value = res.data
    pageList.value = []
    detailVisible.value = true
    if (detail.value.versionId && detail.value.resourceKind === 'FILE') {
      loadPages(detail.value.versionId)
    }
  } catch (e) {
    ElMessage.error('加载详情失败')
  }
}

async function loadPages(versionId) {
  try {
    const res = await getPublicVersionPages(versionId)
    pageList.value = res.data || []
  } catch (e) {
    pageList.value = []
  }
}

function openExternalLink(url) {
  if (url) window.open(url, '_blank')
}

// 下载
async function handleDownload() {
  if (!detail.value?.versionId) return
  try {
    const res = await downloadPublicResource(detail.value.versionId)
    const url = window.URL.createObjectURL(new Blob([res]))
    const link = document.createElement('a')
    link.href = url
    link.download = detail.value.title || 'resource'
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
    window.URL.revokeObjectURL(url)
  } catch (e) {
    ElMessage.error('下载失败')
  }
}

// 添加到课程资料
async function handleImport() {
  if (!detail.value?.id) return
  if (!props.bindingId) {
    ElMessage.warning('未找到教学班信息')
    return
  }
  importing.value = true
  try {
    const res = await importPublicResource(detail.value.id, {
      bindingId: props.bindingId,
      folderId: props.folderId || null,
      unitId: props.unitId || null
    })
    ElMessage.success(`"${res.data.title}" 已添加到课程资料`)
    detailVisible.value = false
  } catch (e) {
    // 错误已由拦截器提示
  } finally {
    importing.value = false
  }
}

// 收藏
async function toggleFavorite() {
  if (!detail.value?.id) return
  try {
    const res = await togglePublicFavorite(detail.value.id)
    detail.value.favorited = res.data.favorited
    ElMessage.success(res.data.message)
  } catch (e) {
    ElMessage.error('操作失败')
  }
}

// 图标
function mediaIcon(kind) {
  switch (kind) {
    case 'VIDEO': return VideoCamera
    case 'AUDIO': return Microphone
    case 'IMAGE': return Picture
    case 'LINK': return Link
    default: return Document
  }
}

function kindLabel(kind) {
  switch (kind) {
    case 'FILE': return '文件'
    case 'LINK': return '链接'
    default: return kind || '未知'
  }
}

function difficultyLabel(d) {
  switch (d) {
    case 'EASY': return '简单'
    case 'MEDIUM': return '中等'
    case 'HARD': return '困难'
    default: return d || '未知'
  }
}

watch(visible, v => {
  if (v) {
    loadCategories()
    loadResources()
  }
})
</script>

<style scoped>
.public-library-dialog :deep(.el-dialog__body) {
  padding: 0;
}

.public-library {
  display: flex;
  flex-direction: column;
  height: 80vh;
}

.library-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 16px 24px;
  border-bottom: 1px solid #ebeef5;
  background: #fafafa;
}

.search-bar {
  display: flex;
  gap: 8px;
}

.library-body {
  display: flex;
  flex: 1;
  overflow: hidden;
}

.library-sidebar {
  width: 220px;
  border-right: 1px solid #ebeef5;
  padding: 16px 0;
  overflow-y: auto;
  flex-shrink: 0;
}

.sidebar-title {
  font-size: 13px;
  color: #909399;
  padding: 0 16px 8px;
  font-weight: 600;
}

.sidebar-section {
  margin-top: 20px;
  padding: 0 16px;
}

.sidebar-section .sidebar-title {
  padding: 0 0 8px;
}

.category-list {
  list-style: none;
  padding: 0;
  margin: 0;
}

.category-list li {
  padding: 10px 16px;
  cursor: pointer;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 14px;
  color: #606266;
  transition: all 0.2s;
}

.category-list li:hover {
  background: #f5f7fa;
  color: #409eff;
}

.category-list li.active {
  background: #ecf5ff;
  color: #409eff;
  font-weight: 500;
}

.library-content {
  flex: 1;
  padding: 20px 24px;
  overflow-y: auto;
}

.resource-grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.resource-card {
  border: 1px solid #ebeef5;
  border-radius: 8px;
  overflow: hidden;
  cursor: pointer;
  transition: all 0.2s;
  background: #fff;
}

.resource-card:hover {
  box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
  transform: translateY(-2px);
  border-color: #409eff;
}

.card-cover {
  height: 140px;
  background: linear-gradient(135deg, #667eea 0%, #764ba2 100%);
  display: flex;
  align-items: center;
  justify-content: center;
  position: relative;
  color: #fff;
}

.card-cover .cover-icon {
  opacity: 0.9;
}

.card-badge {
  position: absolute;
  top: 8px;
  right: 8px;
  background: rgba(255, 255, 255, 0.25);
  color: #fff;
  padding: 2px 8px;
  border-radius: 4px;
  font-size: 12px;
}

.card-body {
  padding: 12px;
}

.card-title {
  font-size: 14px;
  font-weight: 500;
  color: #303133;
  line-height: 1.4;
  margin-bottom: 8px;
  overflow: hidden;
  text-overflow: ellipsis;
  display: -webkit-box;
  -webkit-line-clamp: 2;
  -webkit-box-orient: vertical;
  height: 40px;
}

.card-meta {
  font-size: 12px;
  color: #909399;
  display: flex;
  justify-content: space-between;
  margin-bottom: 8px;
}

.card-stats {
  font-size: 12px;
  color: #909399;
  display: flex;
  gap: 12px;
}

.card-stats span {
  display: flex;
  align-items: center;
  gap: 4px;
}

.pagination-wrapper {
  display: flex;
  justify-content: center;
  margin-top: 24px;
}

.resource-detail {
  padding: 8px 0;
}

.detail-header h3 {
  margin: 0 0 12px;
  font-size: 18px;
  color: #303133;
}

.detail-tags {
  display: flex;
  gap: 8px;
}

.detail-info {
  margin: 16px 0;
  padding: 12px 16px;
  background: #f5f7fa;
  border-radius: 6px;
}

.info-row {
  display: flex;
  gap: 24px;
  font-size: 13px;
  color: #606266;
  margin-bottom: 6px;
}

.info-row:last-child {
  margin-bottom: 0;
}

.info-row .label {
  color: #909399;
}

.detail-desc, .detail-preview {
  margin-top: 20px;
}

.desc-label {
  font-size: 14px;
  font-weight: 600;
  color: #303133;
  margin-bottom: 8px;
}

.desc-content {
  font-size: 14px;
  color: #606266;
  line-height: 1.6;
}

.link-preview {
  text-align: center;
  padding: 24px;
  background: #f5f7fa;
  border-radius: 6px;
}

.link-url {
  margin-top: 12px;
  font-size: 13px;
  color: #909399;
  word-break: break-all;
}

.preview-empty {
  text-align: center;
  padding: 40px;
  color: #909399;
}

.preview-empty p {
  margin-top: 12px;
  font-size: 14px;
}

.preview-pages {
  display: grid;
  grid-template-columns: repeat(3, 1fr);
  gap: 12px;
}

.preview-thumb {
  width: 100%;
  height: 180px;
  border: 1px solid #ebeef5;
  border-radius: 4px;
  cursor: pointer;
}
</style>
