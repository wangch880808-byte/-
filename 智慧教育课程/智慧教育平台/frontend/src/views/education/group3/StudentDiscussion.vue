<template>
  <div class="g3-student-page g3-discussion-page">
    <Group3ContextBar
      v-if="!contextBindingId"
      :list="list"
      :binding-id="bindingId"
      :loading="loading"
      :error="error"
      @select="handleSelect"
      @refresh="refresh"
    />

    <el-empty v-if="!bindingId" description="暂无在修课程，请联系任课教师确认教学班成员关系" />

    <template v-else>
      <div class="g3-page-head">
        <div class="g3-title-block">
          <h2>课堂讨论</h2>
          <p>{{ current ? `${current.courseTitle} · ${current.className}` : '—' }}</p>
          <span class="g3-head-note">
            参与教师发布的课堂讨论；已截止、已归档或尚未开放的话题仅可浏览。匿名帖不显示真实姓名。
          </span>
        </div>
        <div class="g3-head-filter">
          <el-button :loading="listLoading" @click="loadTopics">刷新</el-button>
        </div>
      </div>

      <div class="g3-stats">
        <div class="g3-stat"><small>话题总数</small><strong>{{ stats.total }}</strong></div>
        <div class="g3-stat"><small>进行中</small><strong>{{ stats.open }}</strong></div>
        <div class="g3-stat"><small>已截止</small><strong>{{ stats.closed }}</strong></div>
        <div class="g3-stat"><small>置顶 / 精华</small><strong>{{ stats.marked }}</strong></div>
      </div>

      <el-alert v-if="listError" :title="listError" type="error" show-icon :closable="false" class="g3-gap">
        <el-button size="small" @click="loadTopics">重试</el-button>
      </el-alert>

      <div class="g3-discussion-filter">
        <el-radio-group v-model="scope" size="small" @change="loadTopics">
          <el-radio-button value="all">全部话题</el-radio-button>
          <el-radio-button value="mine">我的</el-radio-button>
        </el-radio-group>
        <el-input
          v-model="keyword"
          class="g3-search"
          clearable
          placeholder="搜索标题或正文"
          @keyup.enter="loadTopics"
          @clear="loadTopics"
        >
          <template #append>
            <el-button @click="loadTopics">搜索</el-button>
          </template>
        </el-input>
        <el-select v-model="folderId" clearable placeholder="全部文件夹" style="width: 160px" @change="loadTopics">
          <el-option
            v-for="item in folders"
            :key="item.folderId"
            :label="`${item.name}（${Number(item.topicCount || 0)}）`"
            :value="String(item.folderId)"
          />
        </el-select>
        <el-select v-model="sort" style="width: 130px" @change="loadTopics">
          <el-option label="最新发布" value="created" />
          <el-option label="最热讨论" value="hot" />
          <el-option label="回复最多" value="replies" />
        </el-select>
      </div>

      <div class="g3-layout">
        <div v-loading="listLoading" class="g3-tl-main">
          <el-card shadow="never" class="g3-topic-list">
            <div
              v-for="item in topics"
              :key="item.topicId"
              class="g3-topic-item"
              :class="{ active: String(item.topicId) === String(activeTopicId) }"
              role="button"
              tabindex="0"
              @click="openTopic(item.topicId)"
              @keyup.enter="openTopic(item.topicId)"
            >
              <div class="g3-topic-head">
                <span class="g3-topic-title">{{ item.title || '讨论话题' }}</span>
                <el-tag v-if="item.pinned" size="small" type="danger" effect="plain">置顶</el-tag>
                <el-tag v-if="item.featured" size="small" type="warning" effect="plain">精华</el-tag>
              </div>
              <div class="g3-topic-meta">
                <el-tag size="small" type="info" effect="plain">{{ item.category || '讨论' }}</el-tag>
                <span>{{ item.authorName || '教师' }}</span>
                <span>{{ formatTime(item.opensAt || item.createdAt) }}</span>
              </div>
              <div class="g3-topic-foot">
                <span class="g3-topic-state" :class="stateTone(item.state)">{{ stateLabel(item.state) }}</span>
                <span>{{ Number(item.replyCount || 0) }} 回复</span>
                <span>{{ Number(item.likeCount || 0) }} 赞</span>
                <span>{{ Number(item.viewCount || 0) }} 浏览</span>
              </div>
            </div>
            <el-empty v-if="!listLoading && !topics.length" description="还没有讨论话题" :image-size="80" />
          </el-card>
        </div>

        <div class="g3-tl-detail">
          <el-card v-if="topic" shadow="never" class="g3-topic-detail">
            <template #header>
              <div class="g3-detail-head">
                <span class="g3-kind">讨论话题</span>
                <el-tag size="small" :type="stateTagType(topic.state)">{{ stateLabel(topic.state) }}</el-tag>
                <el-tag v-if="topic.pinned" size="small" type="danger" effect="plain">置顶</el-tag>
                <el-tag v-if="topic.featured" size="small" type="warning" effect="plain">精华</el-tag>
                <el-button
                  class="g3-like"
                  size="small"
                  :type="topic.myLiked ? 'primary' : ''"
                  :loading="likeLoading"
                  @click="toggleTopicLike"
                >
                  {{ topic.myLiked ? '已点赞' : '点赞' }} · {{ Number(topic.likeCount || 0) }}
                </el-button>
              </div>
            </template>

            <h3 class="g3-detail-title">{{ topic.title }}</h3>
            <p class="g3-topic-body">{{ topic.content || '教师暂未填写正文' }}</p>

            <div v-if="ideologyPoints.length" class="g3-ideology-block">
              <div class="g3-ideology-head">思政融入</div>
              <div v-for="point in ideologyPoints" :key="point.categoryId" class="g3-ideology-row">
                <el-tag size="small" type="warning" effect="plain">{{ point.categoryName }}</el-tag>
                <span v-if="point.note">{{ point.note }}</span>
              </div>
            </div>

            <div class="g3-topic-tags">
              <el-tag size="small" type="info" effect="plain">{{ topic.category || '未分类' }}</el-tag>
              <el-tag v-if="topic.knowledgePoint" size="small" effect="plain">{{ topic.knowledgePoint }}</el-tag>
              <el-tag v-if="topic.folderName" size="small" effect="plain">{{ topic.folderName }}</el-tag>
              <el-tag v-if="topic.scoreEnabled" size="small" type="success" effect="plain">
                评分满分 {{ Number(topic.scoreMax || 0) }}
              </el-tag>
            </div>

            <el-descriptions :column="2" size="small" border class="g3-topic-desc">
              <el-descriptions-item label="发布人">{{ topic.authorName || '教师' }}</el-descriptions-item>
              <el-descriptions-item label="开放时间">{{ formatTime(topic.opensAt) }}</el-descriptions-item>
              <el-descriptions-item label="截止时间">{{ topic.closesAt ? formatTime(topic.closesAt) : '长期开放' }}</el-descriptions-item>
              <el-descriptions-item label="互动设置">
                {{ topic.allowAnonymous ? '可匿名' : '实名' }} · {{ topic.allowReply ? '可回复' : '仅浏览' }}
              </el-descriptions-item>
            </el-descriptions>

            <el-alert v-if="readonly" :title="readonlyHint" type="info" show-icon :closable="false" class="g3-gap" />

            <el-divider content-position="left">讨论区（{{ totalPosts }} 条）</el-divider>

            <div v-loading="topicLoading" class="g3-post-list">
              <el-alert v-if="topicError" :title="topicError" type="error" show-icon :closable="false">
                <el-button size="small" @click="loadTopic(activeTopicId)">重试</el-button>
              </el-alert>

              <el-empty
                v-else-if="!topicLoading && !posts.length"
                description="还没有人发言，来说说你的看法"
                :image-size="80"
              />

              <div v-for="post in posts" :key="post.postId" class="g3-post">
                <div class="g3-post-head">
                  <strong>{{ authorOf(post) }}</strong>
                  <span class="g3-hint">{{ formatTime(post.createdAt) }}</span>
                  <el-tag v-if="post.myScore !== null && post.myScore !== undefined" size="small" type="success">
                    得分 {{ post.myScore }}
                  </el-tag>
                </div>
                <p class="g3-post-body">{{ post.content }}</p>
                <div v-if="post.attachmentUrl" class="g3-post-attach">
                  <el-link type="primary" :href="post.attachmentUrl" target="_blank" rel="noopener noreferrer">
                    {{ post.attachmentName || '附件' }}
                  </el-link>
                </div>
                <div class="g3-post-actions">
                  <el-button link type="primary" :disabled="!canReply" @click="startReply(post)">回复</el-button>
                  <el-button link :type="post.myLiked ? 'primary' : ''" @click="togglePostLike(post)">
                    {{ post.myLiked ? '已赞' : '点赞' }} {{ Number(post.likeCount || 0) }}
                  </el-button>
                </div>

                <div v-if="post.replies && post.replies.length" class="g3-replies">
                  <div v-for="reply in post.replies" :key="reply.postId" class="g3-reply">
                    <div class="g3-post-head">
                      <strong>{{ authorOf(reply) }}</strong>
                      <span class="g3-hint">{{ formatTime(reply.createdAt) }}</span>
                    </div>
                    <p class="g3-post-body">{{ reply.content }}</p>
                  </div>
                </div>

                <div v-if="replyTo && String(replyTo.postId) === String(post.postId)" class="g3-reply-box">
                  <el-input
                    v-model="replyContent"
                    type="textarea"
                    :rows="2"
                    maxlength="1000"
                    show-word-limit
                    :placeholder="`回复 ${authorOf(post)}`"
                    :disabled="!canReply"
                  />
                  <div class="g3-composer-actions">
                    <el-checkbox v-if="topic.allowAnonymous" v-model="replyAnonymous" :disabled="!canReply">匿名回复</el-checkbox>
                    <el-button size="small" @click="cancelReply">取消</el-button>
                    <el-button size="small" type="primary" :loading="posting" :disabled="!canReply" @click="submitReply(post)">
                      回复
                    </el-button>
                  </div>
                </div>
              </div>
            </div>

            <div class="g3-composer">
              <el-input
                v-model="postContent"
                type="textarea"
                :rows="3"
                maxlength="2000"
                show-word-limit
                :placeholder="canPost ? '写下你的观点' : postDisabledHint"
                :disabled="!canPost"
              />
              <div class="g3-composer-actions">
                <span v-if="!canPost" class="g3-hint">{{ postDisabledHint }}</span>
                <el-checkbox v-if="topic.allowAnonymous" v-model="postAnonymous" :disabled="!canPost">匿名发布</el-checkbox>
                <el-button type="primary" size="small" :loading="posting" :disabled="!canPost" @click="submitPost">
                  发布
                </el-button>
              </div>
            </div>
          </el-card>

          <el-card v-else shadow="never">
            <el-empty description="选择左侧话题查看详情" :image-size="90" />
          </el-card>
        </div>
      </div>
    </template>
  </div>
</template>

<script setup name="StudentDiscussion">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { ElMessage } from 'element-plus'
import {
  createDiscussionPost,
  discussionAction,
  getDiscussion,
  getStageIdeologyPoints,
  listDiscussionFolders,
  listDiscussions
} from '@/api/education/group3'
import Group3ContextBar from './Group3ContextBar.vue'
import { useGroup3Binding } from './useGroup3Binding.js'

const props = defineProps({ contextBindingId: { type: String, default: '' } })
const route = useRoute()
const { list, bindingId, current, loading, error, load, select } = useGroup3Binding(
  () => props.contextBindingId || String(route.query.bindingId || '')
)

const STATE_LABELS = {
  DRAFT: '待发布',
  UPCOMING: '未开放',
  OPEN: '进行中',
  CLOSED: '已截止',
  ARCHIVED: '已归档'
}

const topics = ref([])
const folders = ref([])
const listLoading = ref(false)
const listError = ref('')
const keyword = ref('')
const scope = ref('all')
const folderId = ref('')
const sort = ref('created')
const activeTopicId = ref(route.query.topicId ? String(route.query.topicId) : '')
const topic = ref(null)
const topicLoading = ref(false)
const topicError = ref('')
const postContent = ref('')
const postAnonymous = ref(false)
const replyTo = ref(null)
const replyContent = ref('')
const replyAnonymous = ref(false)
const posting = ref(false)
const likeLoading = ref(false)
const ideologyPoints = ref([])

let listToken = 0
let topicToken = 0

function listRows(data) {
  if (Array.isArray(data)) return data
  return data && Array.isArray(data.rows) ? data.rows : []
}

const stats = computed(() => ({
  total: topics.value.length,
  open: topics.value.filter(item => String(item.state || '').toUpperCase() === 'OPEN').length,
  closed: topics.value.filter(item => String(item.state || '').toUpperCase() === 'CLOSED').length,
  marked: topics.value.filter(item => item.pinned || item.featured).length
}))

function postStamp(post) {
  const value = new Date((post && post.createdAt) || 0).getTime()
  return Number.isFinite(value) ? value : 0
}

const posts = computed(() => ((topic.value && topic.value.posts) || []).slice()
  .sort((a, b) => postStamp(a) - postStamp(b))
  .map(post => Object.assign({}, post, {
    replies: (post.replies || []).slice().sort((a, b) => postStamp(a) - postStamp(b))
  })))

const totalPosts = computed(() => posts.value.reduce(
  (total, post) => total + 1 + ((post.replies && post.replies.length) || 0),
  0
))

const readonly = computed(() => {
  const state = String((topic.value && topic.value.state) || '').toUpperCase()
  return state === 'UPCOMING' || state === 'CLOSED' || state === 'ARCHIVED' || state === 'DRAFT'
})

const readonlyHint = computed(() => {
  const state = String((topic.value && topic.value.state) || '').toUpperCase()
  if (state === 'ARCHIVED') return '该讨论已归档，仅可浏览。'
  if (state === 'CLOSED') return '该讨论已截止，仅可浏览。'
  return '该讨论尚未开放，暂不可发言。'
})

const canReply = computed(() => !readonly.value && (!topic.value || topic.value.allowReply !== false))
const canPost = computed(() => canReply.value && !!topic.value && topic.value.allowStudentTopic !== false)
const postDisabledHint = computed(() => {
  if (readonly.value) return readonlyHint.value
  if (!canReply.value) return '教师已关闭该话题的回复，仅可浏览。'
  return '教师未开放学生发起新发言，可在帖子下回复。'
})

function pad(value) {
  return String(value).padStart(2, '0')
}

function formatTime(value) {
  if (!value) return '不限'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return String(value)
  return `${pad(date.getMonth() + 1)}-${pad(date.getDate())} ${pad(date.getHours())}:${pad(date.getMinutes())}`
}

function stateLabel(state) {
  return STATE_LABELS[String(state || '').toUpperCase()] || state || '未知'
}

function stateTagType(state) {
  const value = String(state || '').toUpperCase()
  if (value === 'OPEN') return 'success'
  if (value === 'UPCOMING' || value === 'DRAFT') return 'warning'
  return 'info'
}

function stateTone(state) {
  return `is-${String(state || '').toLowerCase() || 'unknown'}`
}

function authorOf(post) {
  return post && post.anonymous ? '匿名同学' : ((post && post.authorName) || '同学')
}

function listParams() {
  return {
    keyword: keyword.value.trim() || undefined,
    folderId: folderId.value || undefined,
    sort: sort.value,
    scope: scope.value
  }
}

async function refreshTopicList() {
  if (!bindingId.value) return
  try {
    const res = await listDiscussions(bindingId.value, listParams())
    topics.value = listRows(res.data)
  } catch (_) {
    /* 计数刷新失败不打断当前阅读 */
  }
}

async function loadTopics() {
  if (!bindingId.value) return
  const token = ++listToken
  listLoading.value = true
  listError.value = ''
  try {
    const [res, folderRes] = await Promise.all([
      listDiscussions(bindingId.value, listParams()),
      listDiscussionFolders(bindingId.value).catch(() => null)
    ])
    if (token !== listToken) return
    topics.value = listRows(res.data)
    if (folderRes) folders.value = listRows(folderRes.data)
    const wanted = activeTopicId.value
    const hit = topics.value.find(item => String(item.topicId) === String(wanted))
    if (hit) {
      activeTopicId.value = String(hit.topicId)
      await loadTopic(hit.topicId)
    } else if (topics.value.length) {
      activeTopicId.value = String(topics.value[0].topicId)
      await loadTopic(topics.value[0].topicId)
    } else {
      activeTopicId.value = ''
      topic.value = null
    }
  } catch (err) {
    if (token !== listToken) return
    listError.value = err && err.message ? err.message : '讨论列表加载失败'
  } finally {
    if (token === listToken) listLoading.value = false
  }
}

async function loadTopic(topicId) {
  if (!topicId || !bindingId.value) return
  const token = ++topicToken
  topicLoading.value = true
  topicError.value = ''
  try {
    const res = await getDiscussion(topicId, bindingId.value)
    if (token !== topicToken) return
    topic.value = res.data || null
    replyTo.value = null
    replyContent.value = ''
    replyAnonymous.value = false
    postAnonymous.value = false
    loadIdeologyPoints(topicId)
  } catch (err) {
    if (token !== topicToken) return
    topicError.value = err && err.message ? err.message : '讨论详情加载失败'
  } finally {
    if (token === topicToken) topicLoading.value = false
  }
}

async function loadIdeologyPoints(topicId) {
  ideologyPoints.value = []
  if (!topicId || !bindingId.value) return
  try {
    const res = await getStageIdeologyPoints({
      bindingId: bindingId.value,
      stageKind: 'DISCUSSION',
      stageId: topicId
    })
    ideologyPoints.value = Array.isArray(res.data) ? res.data : []
  } catch (err) {
    ideologyPoints.value = []
  }
}

function openTopic(topicId) {
  const value = String(topicId)
  activeTopicId.value = value
  loadTopic(topicId)
}

async function toggleTopicLike() {
  if (!topic.value || likeLoading.value) return
  likeLoading.value = true
  try {
    await discussionAction({
      bindingId: Number(bindingId.value),
      topicId: Number(topic.value.topicId),
      postId: null,
      action: 'like',
      value: !topic.value.myLiked
    })
    await loadTopic(topic.value.topicId)
    await refreshTopicList()
  } catch (err) {
    ElMessage.error(err && err.message ? err.message : '点赞失败')
  } finally {
    likeLoading.value = false
  }
}

async function togglePostLike(post) {
  if (!topic.value) return
  try {
    await discussionAction({
      bindingId: Number(bindingId.value),
      topicId: Number(topic.value.topicId),
      postId: Number(post.postId),
      action: 'like',
      value: !post.myLiked
    })
    await loadTopic(topic.value.topicId)
    await refreshTopicList()
  } catch (err) {
    ElMessage.error(err && err.message ? err.message : '点赞失败')
  }
}

function startReply(post) {
  if (!canReply.value) return
  replyTo.value = post
  replyContent.value = ''
  replyAnonymous.value = false
}

function cancelReply() {
  replyTo.value = null
  replyContent.value = ''
  replyAnonymous.value = false
}

async function submitPost() {
  if (!topic.value || !canPost.value) return
  const content = postContent.value.trim()
  if (!content) {
    ElMessage.warning('请输入讨论内容')
    return
  }
  posting.value = true
  try {
    await createDiscussionPost(topic.value.topicId, {
      bindingId: Number(bindingId.value),
      topicId: Number(topic.value.topicId),
      parentPostId: null,
      content,
      anonymous: Boolean(topic.value.allowAnonymous && postAnonymous.value),
      attachmentName: null,
      attachmentUrl: null
    })
    postContent.value = ''
    postAnonymous.value = false
    ElMessage.success('发布成功')
    await loadTopic(topic.value.topicId)
    await refreshTopicList()
  } catch (err) {
    ElMessage.error(err && err.message ? err.message : '发布失败')
  } finally {
    posting.value = false
  }
}

async function submitReply(post) {
  if (!topic.value || !canReply.value) return
  const content = replyContent.value.trim()
  if (!content) {
    ElMessage.warning('请输入回复内容')
    return
  }
  posting.value = true
  try {
    await createDiscussionPost(topic.value.topicId, {
      bindingId: Number(bindingId.value),
      topicId: Number(topic.value.topicId),
      parentPostId: Number(post.postId),
      content,
      anonymous: Boolean(topic.value.allowAnonymous && replyAnonymous.value),
      attachmentName: null,
      attachmentUrl: null
    })
    cancelReply()
    ElMessage.success('回复成功')
    await loadTopic(topic.value.topicId)
    await refreshTopicList()
  } catch (err) {
    ElMessage.error(err && err.message ? err.message : '回复失败')
  } finally {
    posting.value = false
  }
}

async function handleSelect(value) {
  select(value)
  await loadTopics()
}

async function refresh() {
  await load()
  await loadTopics()
}

watch(bindingId, value => {
  if (value) loadTopics()
})

onMounted(async () => {
  await load()
  if (bindingId.value) await loadTopics()
})
</script>

<style scoped>
.g3-discussion-page {
  padding-bottom: 24px;
}
.g3-page-head { display: flex; align-items: flex-start; justify-content: space-between; gap: 16px; flex-wrap: wrap; }
.g3-title-block h2 { margin: 0; font-size: 20px; color: #303133; }
.g3-title-block p { margin: 4px 0; color: #606266; }
.g3-head-note { font-size: 12px; color: #909399; }
.g3-head-filter { display: flex; align-items: center; gap: 8px; font-size: 13px; color: #606266; }
.g3-stats { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: 12px; margin: 14px 0; }
.g3-stat { background: #f5f7fa; border-radius: 6px; padding: 10px 12px; display: flex; flex-direction: column; gap: 2px; }
.g3-stat small { font-size: 12px; color: #909399; }
.g3-stat strong { font-size: 20px; color: #303133; }
.g3-gap { margin-bottom: 12px; }
.g3-discussion-filter {
  display: flex;
  align-items: center;
  gap: 10px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}
.g3-search { width: 280px; }
.g3-layout { display: grid; grid-template-columns: minmax(280px, 5fr) minmax(340px, 7fr); gap: 16px; align-items: start; }
.g3-tl-main { min-width: 0; }
.g3-tl-detail { min-width: 0; }
.g3-topic-list { min-height: 120px; }
.g3-topic-item {
  padding: 10px 12px;
  border: 1px solid #ebeef5;
  border-left: 3px solid #dcdfe6;
  border-radius: 6px;
  cursor: pointer;
  margin-bottom: 8px;
  transition: border-color 0.2s, background 0.2s;
}
.g3-topic-item:last-child { margin-bottom: 0; }
.g3-topic-item:hover { background: #fafafa; }
.g3-topic-item.active { border-color: #409eff; border-left-color: #409eff; background: #f5f9ff; }
.g3-topic-head { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; }
.g3-topic-title { font-size: 14px; font-weight: 600; color: #303133; }
.g3-topic-meta { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; margin-top: 6px; font-size: 12px; color: #909399; }
.g3-topic-foot { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 6px; font-size: 12px; color: #909399; }
.g3-topic-state { color: #409eff; }
.g3-topic-state.is-open { color: #67c23a; }
.g3-topic-state.is-closed,
.g3-topic-state.is-archived { color: #909399; }
.g3-topic-state.is-upcoming { color: #e6a23c; }
.g3-detail-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.g3-kind { font-size: 14px; font-weight: 600; color: #8b5cf6; }
.g3-like { margin-left: auto; }
.g3-detail-title { margin: 0 0 8px; font-size: 16px; color: #303133; }
.g3-topic-body { margin: 0 0 10px; font-size: 13px; line-height: 1.8; color: #606266; white-space: pre-wrap; word-break: break-word; }
.g3-topic-tags { display: flex; align-items: center; gap: 6px; flex-wrap: wrap; margin-bottom: 10px; }
.g3-topic-desc { margin-bottom: 12px; }
.g3-post-list { min-height: 100px; }
.g3-post { padding: 10px 0; border-bottom: 1px dashed #ebeef5; }
.g3-post:last-child { border-bottom: none; }
.g3-post-head { display: flex; align-items: center; gap: 8px; flex-wrap: wrap; }
.g3-post-head strong { font-size: 13px; color: #303133; }
.g3-hint { font-size: 12px; color: #909399; }
.g3-post-body { margin: 6px 0 0; font-size: 13px; line-height: 1.7; color: #606266; white-space: pre-wrap; word-break: break-word; }
.g3-post-attach { margin-top: 4px; font-size: 13px; }
.g3-post-actions { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; margin-top: 4px; }
.g3-replies { margin: 8px 0 0 14px; padding-left: 10px; border-left: 2px solid #ebeef5; }
.g3-reply { padding: 4px 0; }
.g3-reply-box { margin-top: 8px; padding: 8px; background: #fafafa; border-radius: 6px; }
.g3-composer { margin-top: 12px; padding: 10px; border: 1px solid #ebeef5; border-radius: 6px; background: #fafafa; }
.g3-composer-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex-wrap: wrap; margin-top: 8px; }
@media (max-width: 1100px) {
  .g3-layout { grid-template-columns: minmax(0, 1fr); }
}
@media (max-width: 768px) {
  .g3-stats { grid-template-columns: repeat(2, minmax(0, 1fr)); }
  .g3-search { width: 100%; }
  .g3-discussion-filter { align-items: stretch; }
  .g3-like { margin-left: 0; }
}
.g3-ideology-block {
  margin: 10px 0;
  padding: 10px 14px;
  background: #fffaf0;
  border: 1px solid #f5dab1;
  border-radius: 6px;
}
.g3-ideology-head { font-size: 13px; font-weight: 600; color: #b45309; margin-bottom: 6px; }
.g3-ideology-row { display: flex; align-items: baseline; gap: 8px; font-size: 13px; line-height: 1.7; color: #4b5563; padding: 2px 0; }
</style>
