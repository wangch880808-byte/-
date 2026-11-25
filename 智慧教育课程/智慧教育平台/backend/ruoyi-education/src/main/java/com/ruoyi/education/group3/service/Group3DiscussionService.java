package com.ruoyi.education.group3.service;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Requests;
import com.ruoyi.education.group3.support.Group3Security;
import com.ruoyi.education.group3.support.Group3Time;

/**
 * 讨论区业务：话题即课堂时间轴上的 DISCUSSION 环节。
 *
 * <p>讨论域独立于正式成绩：教师评分只写入 {@code edu_discussion_score}，
 * 不回写作业、成绩或学习事件。附件仅保存名称与地址，不提供上传接口。</p>
 */
@Service
public class Group3DiscussionService
{
    private static final String ANONYMOUS_NAME = "匿名同学";
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_ARCHIVED = "ARCHIVED";
    private static final String POST_NORMAL = "NORMAL";
    private static final String POST_REMOVED = "REMOVED";

    private final Group3Mapper mapper;
    private final Group3AccessService accessService;

    public Group3DiscussionService(Group3Mapper mapper, Group3AccessService accessService)
    {
        this.mapper = mapper;
        this.accessService = accessService;
    }

    /**
     * 话题列表：教师可查看全部未删除话题，学生仅查看已发布话题。
     */
    public List<Map<String, Object>> topics(
            Long bindingId, String view, String keyword, Long folderId, String sort, String scope)
    {
        if (bindingId == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        boolean teacher = Group3Security.isTeacher();
        Map<String, Object> binding = teacher
                ? accessService.requireTeacherBinding(bindingId)
                : accessService.requireStudentBinding(bindingId);
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Long userId = Group3Security.currentUserId();
        String normalizedSort = lower(sort);
        String normalizedScope = upper(scope);
        String normalizedView = upper(view);

        List<Map<String, Object>> rows = teacher
                ? mapper.selectDiscussionTopics(bindingId, courseSpaceId, userId, keyword,
                        folderId, normalizedSort, normalizedScope, normalizedView)
                : mapper.selectStudentDiscussionTopics(bindingId, userId, keyword,
                        folderId, normalizedSort, normalizedScope);
        if (rows == null)
        {
            rows = List.of();
        }
        List<Map<String, Object>> result = new ArrayList<>();
        String serverTime = Group3Time.nowIso();
        for (Map<String, Object> row : rows)
        {
            // 学生端只允许看到已发布话题，SQL 已过滤，这里再做一次防御性校验。
            if (!teacher && !STATUS_PUBLISHED.equals(text(row.get("status"))))
            {
                continue;
            }
            Map<String, Object> topic = topicMap(row);
            topic.put("serverTime", serverTime);
            result.add(topic);
        }
        return result;
    }

    /**
     * 话题详情：返回话题字段与楼中楼回复树。
     */
    @Transactional
    public Map<String, Object> topicDetail(Long bindingId, Long topicId)
    {
        if (bindingId == null || topicId == null)
        {
            throw new ServiceException("教学班和话题不能为空", HttpStatus.BAD_REQUEST);
        }
        boolean teacher = Group3Security.isTeacher();
        Map<String, Object> binding = teacher
                ? accessService.requireTeacherBinding(bindingId)
                : accessService.requireStudentBinding(bindingId);
        Long userId = Group3Security.currentUserId();
        Map<String, Object> row = requireTopic(bindingId, topicId, userId);
        if (!teacher && !STATUS_PUBLISHED.equals(text(row.get("status"))))
        {
            throw new ServiceException("话题未发布或已归档", HttpStatus.FORBIDDEN);
        }

        Map<String, Object> result = topicMap(row);
        mapper.updateDiscussionTopicCounters(bindingId, topicId, 0, 1);
        result.put("viewCount", Math.max(toInt(row.get("viewCount")), 0) + 1);
        List<Map<String, Object>> postRows = mapper.selectDiscussionPosts(topicId, userId);
        result.put("posts", postTree(postRows == null ? List.of() : postRows, teacher, userId));
        result.put("serverTime", Group3Time.nowIso());
        return result;
    }

    /**
     * 教师创建话题（DISCUSSION 环节），默认保存为草稿。
     */
    @Transactional
    public Long createTopic(Group3Requests.DiscussionTopic request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        if (!Boolean.TRUE.equals(binding.get("canPublish")))
        {
            throw new ServiceException("当前教师账号无发布权限", HttpStatus.FORBIDDEN);
        }
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        Long teachingClassId = Group3AccessService.asLong(binding.get("teachingClassId"));
        if (request.lessonId() != null
                && mapper.selectLesson(request.bindingId(), request.lessonId()) == null)
        {
            throw new ServiceException("课次不属于当前教学班", HttpStatus.BAD_REQUEST);
        }
        if (request.folderId() != null && !folderExists(request.bindingId(), request.folderId()))
        {
            throw new ServiceException("讨论文件夹不存在", HttpStatus.BAD_REQUEST);
        }

        String title = requiredText(request.title(), "话题标题", 200);
        String content = requiredText(request.content(), "话题正文", 20000);
        String category = optionalText(request.category(), "话题分类", 40);
        String knowledgePoint = optionalText(request.knowledgePoint(), "知识点", 200);
        String publishScope = optionalText(request.publishScope(), "发布范围", 16);
        if (publishScope == null)
        {
            publishScope = "CLASS";
        }
        publishScope = publishScope.toUpperCase();

        LocalDateTime opensAt = Group3Time.parseUtc(request.opensAt(), "开放时间");
        LocalDateTime closesAt = Group3Time.parseUtc(request.closesAt(), "截止时间");
        if (opensAt == null)
        {
            opensAt = Group3Time.nowUtc();
        }
        if (closesAt != null && !closesAt.isAfter(opensAt))
        {
            throw new ServiceException("截止时间必须晚于开放时间", HttpStatus.BAD_REQUEST);
        }
        BigDecimal scoreMax = request.scoreMax() == null ? new BigDecimal("100.00") : request.scoreMax();
        if (scoreMax.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("评分上限不能小于 0", HttpStatus.BAD_REQUEST);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", request.bindingId());
        row.put("teachingClassId", teachingClassId);
        row.put("courseSpaceId", courseSpaceId);
        row.put("lessonId", request.lessonId());
        row.put("folderId", request.folderId());
        row.put("title", title);
        row.put("content", content);
        row.put("category", category);
        row.put("knowledgePoint", knowledgePoint);
        row.put("publishScope", publishScope);
        row.put("allowAnonymous", Boolean.TRUE.equals(request.allowAnonymous()));
        row.put("allowAttachment", Boolean.TRUE.equals(request.allowAttachment()));
        row.put("allowReply", request.allowReply() == null || request.allowReply());
        row.put("allowStudentTopic", Boolean.TRUE.equals(request.allowStudentTopic()));
        row.put("scoreEnabled", Boolean.TRUE.equals(request.scoreEnabled()));
        row.put("scoreMax", scoreMax);
        row.put("pinned", Boolean.TRUE.equals(request.pinned()));
        row.put("featured", Boolean.TRUE.equals(request.featured()));
        row.put("opensAt", opensAt);
        row.put("closesAt", closesAt);
        row.put("userId", Group3Security.currentUserId());
        mapper.insertDiscussionTopic(row);
        return Group3AccessService.asLong(row.get("id"));
    }

    /**
     * 教师或学生发布回复；parentPostId 为空表示顶层发言。
     */
    @Transactional
    public Long createPost(Group3Requests.DiscussionPost request)
    {
        if (request == null || request.bindingId() == null || request.topicId() == null)
        {
            throw new ServiceException("教学班和话题不能为空", HttpStatus.BAD_REQUEST);
        }
        boolean teacher = Group3Security.isTeacher();
        Map<String, Object> binding = teacher
                ? accessService.requireTeacherBinding(request.bindingId())
                : accessService.requireStudentBinding(request.bindingId());
        Long userId = Group3Security.currentUserId();
        Map<String, Object> topic = requireTopic(request.bindingId(), request.topicId(), userId);
        String status = text(topic.get("status"));
        if (!STATUS_PUBLISHED.equals(status))
        {
            throw new ServiceException("话题未发布或已归档，不能回复", HttpStatus.CONFLICT);
        }
        LocalDateTime now = Group3Time.nowUtc();
        LocalDateTime opensAt = topicTime(topic, "opensAt");
        LocalDateTime closesAt = topicTime(topic, "closesAt");
        if (opensAt != null && now.isBefore(opensAt))
        {
            throw new ServiceException("话题尚未开放", HttpStatus.CONFLICT);
        }
        if (closesAt != null && !now.isBefore(closesAt))
        {
            throw new ServiceException("话题已截止", HttpStatus.CONFLICT);
        }
        if (!flag(topic.get("allowReply")))
        {
            throw new ServiceException("该话题不允许回复", HttpStatus.CONFLICT);
        }
        if (!teacher && request.parentPostId() == null && !flag(topic.get("allowStudentTopic")))
        {
            throw new ServiceException("该话题不允许学生发起新发言", HttpStatus.FORBIDDEN);
        }
        if (request.parentPostId() != null)
        {
            Map<String, Object> parent = mapper.selectDiscussionPost(
                    request.topicId(), request.parentPostId());
            if (parent == null || POST_REMOVED.equals(text(parent.get("status"))))
            {
                throw new ServiceException("被回复的发言不存在或已移除", HttpStatus.NOT_FOUND);
            }
        }

        boolean anonymous = Boolean.TRUE.equals(request.anonymous());
        if (anonymous && !flag(topic.get("allowAnonymous")))
        {
            throw new ServiceException("该话题不允许匿名回复", HttpStatus.CONFLICT);
        }
        String content = requiredText(request.content(), "回复内容", 10000);
        String attachmentName = optionalText(request.attachmentName(), "附件名称", 255);
        String attachmentUrl = optionalText(request.attachmentUrl(), "附件地址", 1024);
        if ((attachmentName == null) != (attachmentUrl == null))
        {
            throw new ServiceException("附件名称和附件地址需同时提供", HttpStatus.BAD_REQUEST);
        }
        if ((attachmentName != null || attachmentUrl != null) && !flag(topic.get("allowAttachment")))
        {
            throw new ServiceException("该话题不允许添加附件", HttpStatus.CONFLICT);
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("topicId", request.topicId());
        row.put("bindingId", request.bindingId());
        row.put("parentPostId", request.parentPostId());
        row.put("userId", userId);
        row.put("content", content);
        row.put("anonymous", anonymous);
        row.put("attachmentName", attachmentName);
        row.put("attachmentUrl", attachmentUrl);
        mapper.insertDiscussionPost(row);
        mapper.updateDiscussionTopicCounters(request.bindingId(), request.topicId(), 1, 0);
        return Group3AccessService.asLong(row.get("id"));
    }

    /**
     * 讨论操作：置顶、精华、归档、删除回复、点赞、移动文件夹、教师评分与设置。
     */
    @Transactional
    public Map<String, Object> action(Group3Requests.DiscussionAction request)
    {
        if (request == null || request.bindingId() == null || request.action() == null
                || request.action().isBlank())
        {
            throw new ServiceException("教学班和操作类型不能为空", HttpStatus.BAD_REQUEST);
        }
        String action = request.action().trim().toLowerCase();
        Long bindingId = request.bindingId();
        Long topicId = request.topicId();
        Long postId = request.postId();
        Long userId = Group3Security.currentUserId();

        // 点赞对教师和学生开放，其余管理动作仅教师可用。
        boolean teacherOnly = Set.of("pin", "unpin", "feature", "unfeature", "archive", "restore",
                "publish", "remove-post", "removepost", "move", "folder", "score", "settings")
                .contains(action);
        if (teacherOnly)
        {
            Map<String, Object> binding = accessService.requireTeacherBinding(bindingId);
            if ("score".equals(action) && !Boolean.TRUE.equals(binding.get("canGrade")))
            {
                throw new EduForbiddenException("当前账号没有该教学班的评分权限");
            }
        }
        else
        {
            accessService.requireAnyBinding(bindingId);
        }

        switch (action)
        {
            case "pin":
                return updateFlags(bindingId, topicId, true, null);
            case "unpin":
                return updateFlags(bindingId, topicId, false, null);
            case "feature":
                return updateFlags(bindingId, topicId, null, true);
            case "unfeature":
                return updateFlags(bindingId, topicId, null, false);
            case "publish":
                return updateStatus(bindingId, topicId, STATUS_PUBLISHED);
            case "archive":
                return updateStatus(bindingId, topicId, STATUS_ARCHIVED);
            case "restore":
                return updateStatus(bindingId, topicId, STATUS_PUBLISHED);
            case "remove-post":
            case "removepost":
                return removePost(bindingId, topicId, postId);
            case "like":
                return toggleLike(bindingId, topicId, postId, userId);
            case "move":
            case "folder":
                return moveTopic(bindingId, topicId, request.value());
            case "score":
                return gradePost(bindingId, topicId, postId, request.value(), userId);
            case "settings":
                // settings 复用 UpdateStage 的话题设置能力，避免出现第二套字段语义。
                if (!(request.value() instanceof Map<?, ?> raw))
                {
                    throw new ServiceException("settings 需要通过 /stages/update 或传入设置对象",
                            HttpStatus.BAD_REQUEST);
                }
                Map<String, Object> settings = new LinkedHashMap<>();
                raw.forEach((key, value) -> settings.put(String.valueOf(key), value));
                return updateTopicSettings(bindingId, topicId, settings);
            default:
                throw new ServiceException("不支持的讨论操作", HttpStatus.BAD_REQUEST);
        }
    }

    /** 讨论文件夹列表：教师与学生均可读取，用于筛选话题。 */
    public List<Map<String, Object>> folders(Long bindingId)
    {
        if (bindingId == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        accessService.requireAnyBinding(bindingId);
        return mapper.selectDiscussionFolders(bindingId);
    }

    /** 教师创建讨论文件夹。 */
    @Transactional
    public Map<String, Object> createFolder(Group3Requests.DiscussionFolder request)
    {
        if (request == null || request.bindingId() == null)
        {
            throw new ServiceException("教学班不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        String name = requiredText(request.name(), "文件夹名称", 60);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", request.bindingId());
        row.put("courseSpaceId", Group3AccessService.asLong(binding.get("courseSpaceId")));
        row.put("name", name);
        row.put("sortOrder", mapper.selectNextDiscussionFolderSort(request.bindingId()));
        row.put("userId", Group3Security.currentUserId());
        mapper.insertDiscussionFolder(row);
        Map<String, Object> result = new LinkedHashMap<>();
        Long folderId = Group3AccessService.asLong(row.get("id"));
        result.put("id", folderId);
        result.put("folderId", folderId);
        result.put("name", name);
        result.put("sortOrder", row.get("sortOrder"));
        return result;
    }

    /**
     * 时间轴环节更新：DISCUSSION 更新话题设置，其他 kind 更新对应资料、作业或备课。
     */
    @Transactional
    public Map<String, Object> updateStage(Group3Requests.UpdateStage request)
    {
        if (request == null || request.bindingId() == null || request.id() == null
                || request.kind() == null || request.kind().isBlank())
        {
            throw new ServiceException("教学班、环节类型和环节 ID 不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> binding = accessService.requireTeacherBinding(request.bindingId());
        String kind = request.kind().trim().toUpperCase();
        switch (kind)
        {
            case "DISCUSSION":
                Map<String, Object> settings = new LinkedHashMap<>();
                settings.put("title", request.title());
                // 讨论正文在时间轴契约中对应 instructions。
                settings.put("content", request.instructions());
                settings.put("opensAt", request.opensAt());
                settings.put("closesAt", request.closesAt());
                return updateTopicSettings(request.bindingId(), request.id(), settings);
            case "MATERIAL":
                return updateMaterialStage(binding, request);
            case "QUIZ":
                return updateQuizStage(request);
            case "PLAN":
                return updatePlanStage(request);
            default:
                throw new ServiceException("kind 必须是 DISCUSSION / MATERIAL / QUIZ / PLAN",
                        HttpStatus.BAD_REQUEST);
        }
    }

    /** 时间轴删除：DISCUSSION 仅做话题归档，保留回复与评分数据。 */
    @Transactional
    public Map<String, Object> removeStage(Group3Requests.RemoveStage request)
    {
        if (request == null || request.bindingId() == null || request.id() == null
                || request.kind() == null || request.kind().isBlank())
        {
            throw new ServiceException("教学班、环节类型和环节 ID 不能为空", HttpStatus.BAD_REQUEST);
        }
        accessService.requireTeacherBinding(request.bindingId());
        if (!"DISCUSSION".equalsIgnoreCase(request.kind()))
        {
            throw new ServiceException("讨论服务仅处理 DISCUSSION 环节", HttpStatus.BAD_REQUEST);
        }
        int changed = mapper.updateDiscussionTopicStatus(
                request.bindingId(), request.id(), STATUS_ARCHIVED);
        if (changed == 0)
        {
            throw new ServiceException("话题不存在或不属于当前教学班", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "DISCUSSION");
        result.put("id", request.id());
        result.put("status", STATUS_ARCHIVED);
        return result;
    }

    private Map<String, Object> updateFlags(
            Long bindingId, Long topicId, Boolean pinned, Boolean featured)
    {
        requireTopic(bindingId, topicId, Group3Security.currentUserId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", bindingId);
        row.put("topicId", topicId);
        row.put("pinned", pinned);
        row.put("featured", featured);
        mapper.updateDiscussionTopicFlags(row);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("pinned", pinned == null ? null : pinned);
        result.put("featured", featured == null ? null : featured);
        return result;
    }

    /** 恢复已删除的讨论环节：与原「恢复」操作一致，回到已发布状态。 */
    public Map<String, Object> restoreArchivedStage(Long bindingId, Long topicId)
    {
        return updateStatus(bindingId, topicId, STATUS_PUBLISHED);
    }

    private Map<String, Object> updateStatus(Long bindingId, Long topicId, String status)
    {
        requireTopic(bindingId, topicId, Group3Security.currentUserId());
        int changed = mapper.updateDiscussionTopicStatus(bindingId, topicId, status);
        if (changed == 0)
        {
            throw new ServiceException("话题不存在或不属于当前教学班", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("status", status);
        return result;
    }

    private Map<String, Object> removePost(Long bindingId, Long topicId, Long postId)
    {
        if (postId == null)
        {
            throw new ServiceException("回复 ID 不能为空", HttpStatus.BAD_REQUEST);
        }
        requireTopic(bindingId, topicId, Group3Security.currentUserId());
        Map<String, Object> post = mapper.selectDiscussionPost(topicId, postId);
        if (post == null)
        {
            throw new ServiceException("回复不存在或不属于该话题", HttpStatus.NOT_FOUND);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("postId", postId);
        result.put("status", POST_REMOVED);
        if (POST_REMOVED.equals(text(post.get("status"))))
        {
            return result;
        }
        int changed = mapper.updateDiscussionPostStatus(topicId, postId, POST_REMOVED);
        if (changed > 0)
        {
            mapper.updateDiscussionTopicCounters(bindingId, topicId, -1, 0);
        }
        return result;
    }

    /**
     * 点赞切换：同一用户重复点赞不会产生重复记录，第二次调用取消点赞。
     */
    private Map<String, Object> toggleLike(
            Long bindingId, Long topicId, Long postId, Long userId)
    {
        if (topicId == null)
        {
            throw new ServiceException("话题不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> topic = requireTopic(bindingId, topicId, userId);
        long targetPostId = postId == null ? 0L : postId;
        Map<String, Object> post = null;
        if (targetPostId > 0)
        {
            post = mapper.selectDiscussionPost(topicId, targetPostId);
            if (post == null)
            {
                throw new ServiceException("回复不存在或不属于该话题", HttpStatus.NOT_FOUND);
            }
        }
        boolean liked = mapper.selectDiscussionLike(topicId, targetPostId, userId) == 0;
        if (liked)
        {
            mapper.insertDiscussionLike(topicId, targetPostId, userId);
        }
        else
        {
            mapper.deleteDiscussionLike(topicId, targetPostId, userId);
        }
        if (targetPostId > 0)
        {
            mapper.updateDiscussionPostLikeCount(targetPostId, liked ? 1 : -1);
        }
        else
        {
            mapper.updateDiscussionTopicLikeCount(topicId, liked ? 1 : -1);
        }
        int base = post != null ? toInt(post.get("likeCount")) : toInt(topic.get("likeCount"));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("postId", targetPostId);
        result.put("liked", liked);
        result.put("likeCount", Math.max(base + (liked ? 1 : -1), 0));
        return result;
    }

    private Map<String, Object> moveTopic(Long bindingId, Long topicId, Object value)
    {
        if (topicId == null)
        {
            throw new ServiceException("话题不能为空", HttpStatus.BAD_REQUEST);
        }
        requireTopic(bindingId, topicId, Group3Security.currentUserId());
        Long folderId = toLong(value);
        if (folderId != null && !folderExists(bindingId, folderId))
        {
            throw new ServiceException("讨论文件夹不存在", HttpStatus.BAD_REQUEST);
        }
        mapper.updateDiscussionTopicFolder(bindingId, topicId, folderId);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("folderId", folderId);
        return result;
    }

    /**
     * 教师给某条回复的作者评分；只写讨论评分表，不进入正式成绩。
     */
    private Map<String, Object> gradePost(
            Long bindingId, Long topicId, Long postId, Object value, Long gradedBy)
    {
        if (topicId == null || postId == null)
        {
            throw new ServiceException("话题和回复不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> topic = requireTopic(bindingId, topicId, gradedBy);
        if (!flag(topic.get("scoreEnabled")))
        {
            throw new ServiceException("该话题未开启评分", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> post = mapper.selectDiscussionPost(topicId, postId);
        if (post == null || POST_REMOVED.equals(text(post.get("status"))))
        {
            throw new ServiceException("回复不存在或不属于该话题", HttpStatus.NOT_FOUND);
        }
        BigDecimal score = readScore(value);
        if (score == null)
        {
            throw new ServiceException("评分不能为空", HttpStatus.BAD_REQUEST);
        }
        if (score.compareTo(BigDecimal.ZERO) < 0)
        {
            throw new ServiceException("评分不能小于 0", HttpStatus.BAD_REQUEST);
        }
        BigDecimal scoreMax = decimal(topic.get("scoreMax"));
        if (scoreMax != null && score.compareTo(scoreMax) > 0)
        {
            throw new ServiceException("评分不能超过话题评分上限", HttpStatus.BAD_REQUEST);
        }
        Long studentUserId = Group3AccessService.asLong(post.get("userId"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("topicId", topicId);
        row.put("userId", studentUserId);
        row.put("score", score);
        row.put("comment", readComment(value));
        row.put("gradedByUserId", gradedBy);
        mapper.upsertDiscussionScore(row);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("topicId", topicId);
        result.put("postId", postId);
        result.put("userId", studentUserId);
        result.put("score", score);
        return result;
    }

    /**
     * 话题设置更新：供 /stages/update 与 action=settings 复用。
     */
    private Map<String, Object> updateTopicSettings(
            Long bindingId, Long topicId, Map<String, Object> settings)
    {
        if (topicId == null)
        {
            throw new ServiceException("话题不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> topic = requireTopic(bindingId, topicId, Group3Security.currentUserId());
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("bindingId", bindingId);
        row.put("topicId", topicId);
        boolean changed = false;

        Object titleValue = settings.get("title");
        if (titleValue != null)
        {
            row.put("title", requiredText(titleValue.toString(), "话题标题", 200));
            changed = true;
        }
        Object contentValue = settings.get("content");
        if (contentValue != null)
        {
            row.put("content", requiredText(contentValue.toString(), "话题正文", 20000));
            changed = true;
        }
        LocalDateTime opensAt = Group3Time.parseUtc(
                optionalText(settings.get("opensAt") == null ? null : settings.get("opensAt").toString(),
                        "开放时间", 64), "开放时间");
        LocalDateTime closesAt = Group3Time.parseUtc(
                optionalText(settings.get("closesAt") == null ? null : settings.get("closesAt").toString(),
                        "截止时间", 64), "截止时间");
        if (opensAt != null || closesAt != null)
        {
            LocalDateTime effectiveOpens = opensAt != null
                    ? opensAt : topicTime(topic, "opensAt");
            LocalDateTime effectiveCloses = closesAt != null
                    ? closesAt : topicTime(topic, "closesAt");
            if (effectiveOpens == null)
            {
                effectiveOpens = Group3Time.nowUtc();
            }
            if (effectiveCloses != null && !effectiveCloses.isAfter(effectiveOpens))
            {
                throw new ServiceException("截止时间必须晚于开放时间", HttpStatus.BAD_REQUEST);
            }
            if (opensAt != null)
            {
                row.put("opensAt", opensAt);
                changed = true;
            }
            if (closesAt != null)
            {
                row.put("closesAt", closesAt);
                changed = true;
            }
        }
        if (changed)
        {
            mapper.updateDiscussionTopicSettings(row);
        }

        boolean scoreChanged = settings.containsKey("scoreEnabled")
                || settings.containsKey("scoreMax");
        if (scoreChanged)
        {
            Boolean enabled = boolValue(settings.get("scoreEnabled"));
            BigDecimal scoreMax = settings.get("scoreMax") == null
                    ? decimal(topic.get("scoreMax"))
                    : decimal(settings.get("scoreMax"));
            if (scoreMax == null)
            {
                scoreMax = BigDecimal.ZERO;
            }
            if (scoreMax.compareTo(BigDecimal.ZERO) < 0)
            {
                throw new ServiceException("评分上限不能小于 0", HttpStatus.BAD_REQUEST);
            }
            Map<String, Object> scoreRow = new LinkedHashMap<>();
            scoreRow.put("bindingId", bindingId);
            scoreRow.put("topicId", topicId);
            scoreRow.put("scoreEnabled", enabled == null
                    ? flag(topic.get("scoreEnabled")) : enabled);
            scoreRow.put("scoreMax", scoreMax);
            mapper.updateDiscussionTopicScoreConfig(scoreRow);
            changed = true;
        }
        if (!changed)
        {
            throw new ServiceException("没有可更新的话题设置", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "DISCUSSION");
        result.put("id", topicId);
        return result;
    }

    private Map<String, Object> updateMaterialStage(
            Map<String, Object> binding, Group3Requests.UpdateStage request)
    {
        Long courseSpaceId = Group3AccessService.asLong(binding.get("courseSpaceId"));
        LocalDateTime opensAt = Group3Time.parseUtc(request.opensAt(), "开放时间");
        LocalDateTime closesAt = Group3Time.parseUtc(request.closesAt(), "截止时间");
        if (opensAt == null && closesAt == null && request.title() == null)
        {
            throw new ServiceException("没有可更新的资料内容", HttpStatus.BAD_REQUEST);
        }
        if (opensAt != null && closesAt != null && !closesAt.isAfter(opensAt))
        {
            throw new ServiceException("截止时间必须晚于开放时间", HttpStatus.BAD_REQUEST);
        }
        if (request.title() != null)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resourceId", request.id());
            row.put("courseSpaceId", courseSpaceId);
            row.put("title", requiredText(request.title(), "资料名称", 200));
            mapper.updateResource(row);
        }
        if (opensAt != null || closesAt != null)
        {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("resourceId", request.id());
            row.put("bindingId", request.bindingId());
            row.put("opensAt", opensAt);
            row.put("closesAt", closesAt);
            mapper.updateActiveReleaseTimes(row);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "MATERIAL");
        result.put("id", request.id());
        return result;
    }

    private Map<String, Object> updateQuizStage(Group3Requests.UpdateStage request)
    {
        Map<String, Object> existing = mapper.selectAssignmentStage(request.bindingId(), request.id());
        if (existing == null)
        {
            throw new ServiceException("作业/测验不存在或不属于当前教学班", HttpStatus.NOT_FOUND);
        }
        if ("ARCHIVED".equalsIgnoreCase(String.valueOf(existing.get("status"))))
        {
            throw new ServiceException("已撤回的预习任务不能修改", HttpStatus.CONFLICT);
        }
        LocalDateTime opensAt = Group3Time.parseUtc(request.opensAt(), "开放时间");
        LocalDateTime closesAt = Group3Time.parseUtc(request.closesAt(), "截止时间");
        LocalDateTime answerOpensAt = Group3Time.parseUtc(request.answerOpensAt(), "解析公布时间");
        if (opensAt == null && closesAt == null && answerOpensAt == null
                && request.title() == null && request.instructions() == null)
        {
            throw new ServiceException("没有可更新的测验内容", HttpStatus.BAD_REQUEST);
        }
        LocalDateTime effectiveOpens = opensAt != null
                ? opensAt : localDateTime(existing.get("opensAtRaw"));
        LocalDateTime effectiveCloses = closesAt != null
                ? closesAt : localDateTime(existing.get("closesAtRaw"));
        LocalDateTime effectiveAnswer = answerOpensAt != null
                ? answerOpensAt : localDateTime(existing.get("answerOpensAtRaw"));
        if (effectiveOpens != null && effectiveCloses != null
                && !effectiveCloses.isAfter(effectiveOpens))
        {
            throw new ServiceException("截止时间必须晚于开放时间", HttpStatus.BAD_REQUEST);
        }
        if (effectiveCloses != null && effectiveAnswer != null
                && effectiveAnswer.isBefore(effectiveCloses))
        {
            throw new ServiceException("解析公布时间不能早于截止时间", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("assignmentId", request.id());
        row.put("bindingId", request.bindingId());
        row.put("title", request.title() == null ? null
                : requiredText(request.title(), "测验名称", 200));
        row.put("instructions", request.instructions());
        row.put("opensAt", opensAt);
        row.put("closesAt", closesAt);
        row.put("answerOpensAt", answerOpensAt);
        mapper.updateAssignmentStage(row);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "QUIZ");
        result.put("id", request.id());
        return result;
    }

    private Map<String, Object> updatePlanStage(Group3Requests.UpdateStage request)
    {
        Map<String, Object> existing = mapper.selectLessonPlanStage(request.bindingId(), request.id());
        if (existing == null)
        {
            throw new ServiceException("备课方案不存在或不属于当前教学班", HttpStatus.NOT_FOUND);
        }
        if (request.title() == null && request.privateNotes() == null
                && request.durationSeconds() == null)
        {
            throw new ServiceException("没有可更新的备课内容", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("planId", request.id());
        row.put("bindingId", request.bindingId());
        row.put("title", request.title() == null ? null
                : requiredText(request.title(), "备课方案名称", 160));
        row.put("privateNotes", request.privateNotes());
        if (row.get("title") != null || row.get("privateNotes") != null)
        {
            mapper.updateLessonPlanStage(row);
        }
        if (request.durationSeconds() != null)
        {
            if (request.durationSeconds() < 60)
            {
                throw new ServiceException("活动时长不能少于 60 秒", HttpStatus.BAD_REQUEST);
            }
            Map<String, Object> duration = new LinkedHashMap<>();
            duration.put("planId", request.id());
            duration.put("bindingId", request.bindingId());
            duration.put("durationSeconds", request.durationSeconds());
            // 时长落在活动模板上，edu_lesson_plan 本身没有时长字段。
            mapper.updatePlanTemplateDuration(duration);
        }
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("kind", "PLAN");
        result.put("id", request.id());
        return result;
    }

    private Map<String, Object> requireTopic(Long bindingId, Long topicId, Long userId)
    {
        if (topicId == null)
        {
            throw new ServiceException("话题不能为空", HttpStatus.BAD_REQUEST);
        }
        Map<String, Object> topic = mapper.selectDiscussionTopic(bindingId, topicId, userId);
        if (topic == null)
        {
            throw new ServiceException("话题不存在或不属于当前教学班", HttpStatus.NOT_FOUND);
        }
        return topic;
    }

    private boolean folderExists(Long bindingId, Long folderId)
    {
        return mapper.selectDiscussionFolders(bindingId).stream()
                .anyMatch(folder -> folderId.equals(Group3AccessService.asLong(folder.get("folderId")))
                        || folderId.equals(Group3AccessService.asLong(folder.get("id"))));
    }

    /**
     * 列表/详情统一的字段映射；时间已在 SQL 中格式化为 ISO-8601 UTC。
     */
    private Map<String, Object> topicMap(Map<String, Object> row)
    {
        Map<String, Object> topic = new LinkedHashMap<>();
        topic.put("topicId", row.get("topicId"));
        topic.put("bindingId", row.get("bindingId"));
        topic.put("lessonId", row.get("lessonId"));
        topic.put("folderId", row.get("folderId"));
        topic.put("folderName", row.get("folderName"));
        topic.put("title", row.get("title"));
        topic.put("content", row.get("content"));
        topic.put("category", row.get("category"));
        topic.put("knowledgePoint", row.get("knowledgePoint"));
        topic.put("publishScope", row.get("publishScope"));
        topic.put("status", row.get("status"));
        topic.put("state", state(row));
        topic.put("allowAnonymous", flag(row.get("allowAnonymous")));
        topic.put("allowAttachment", flag(row.get("allowAttachment")));
        topic.put("allowReply", flag(row.get("allowReply")));
        topic.put("allowStudentTopic", flag(row.get("allowStudentTopic")));
        topic.put("scoreEnabled", flag(row.get("scoreEnabled")));
        topic.put("scoreMax", row.get("scoreMax"));
        topic.put("pinned", flag(row.get("pinned")));
        topic.put("featured", flag(row.get("featured")));
        topic.put("opensAt", row.get("opensAt"));
        topic.put("closesAt", row.get("closesAt"));
        topic.put("createdAt", row.get("createdAt"));
        topic.put("updatedAt", row.get("updatedAt"));
        topic.put("authorName", row.get("authorName"));
        topic.put("replyCount", row.get("replyCount"));
        topic.put("likeCount", row.get("likeCount"));
        topic.put("viewCount", row.get("viewCount"));
        topic.put("myLiked", flag(row.get("myLiked")));
        topic.put("myScore", row.get("myScore"));
        return topic;
    }

    /**
     * 帖子树：按时间升序，parentPostId 非空挂到父帖 replies。
     * 匿名帖仅作者本人和教师可见真实作者，其他学生显示「匿名同学」。
     */
    private List<Map<String, Object>> postTree(
            List<Map<String, Object>> rows, boolean teacher, Long currentUserId)
    {
        Map<Long, Map<String, Object>> byId = new LinkedHashMap<>();
        for (Map<String, Object> row : rows)
        {
            Long postId = Group3AccessService.asLong(row.get("postId"));
            Long authorId = Group3AccessService.asLong(row.get("userId"));
            boolean anonymous = flag(row.get("anonymous"));
            boolean masked = anonymous && !teacher && !Objects.equals(authorId, currentUserId);
            Map<String, Object> post = new LinkedHashMap<>();
            post.put("postId", postId);
            post.put("parentPostId", row.get("parentPostId"));
            post.put("userId", masked ? null : authorId);
            post.put("authorName", masked ? ANONYMOUS_NAME : row.get("authorName"));
            post.put("anonymous", anonymous);
            post.put("content", row.get("content"));
            post.put("attachmentName", row.get("attachmentName"));
            post.put("attachmentUrl", row.get("attachmentUrl"));
            post.put("status", row.get("status"));
            post.put("likeCount", row.get("likeCount"));
            post.put("myLiked", flag(row.get("myLiked")));
            post.put("createdAt", row.get("createdAt"));
            post.put("replies", new ArrayList<Map<String, Object>>());
            byId.put(postId, post);
        }
        List<Map<String, Object>> roots = new ArrayList<>();
        for (Map<String, Object> post : byId.values())
        {
            Long parentId = Group3AccessService.asLong(post.get("parentPostId"));
            Map<String, Object> parent = parentId == null ? null : byId.get(parentId);
            if (parent == null)
            {
                roots.add(post);
            }
            else
            {
                @SuppressWarnings("unchecked")
                List<Map<String, Object>> replies = (List<Map<String, Object>>) parent.get("replies");
                replies.add(post);
            }
        }
        return roots;
    }

    private String state(Map<String, Object> row)
    {
        String status = text(row.get("status"));
        if (STATUS_ARCHIVED.equals(status))
        {
            return STATUS_ARCHIVED;
        }
        if (STATUS_DRAFT.equals(status))
        {
            return STATUS_DRAFT;
        }
        LocalDateTime now = Group3Time.nowUtc();
        LocalDateTime opensAt = topicTime(row, "opensAt");
        LocalDateTime closesAt = topicTime(row, "closesAt");
        if (opensAt != null && now.isBefore(opensAt))
        {
            return "UPCOMING";
        }
        if (closesAt != null && !now.isBefore(closesAt))
        {
            return "CLOSED";
        }
        return "OPEN";
    }

    private BigDecimal readScore(Object value)
    {
        Object raw = value instanceof Map<?, ?> map ? map.get("score") : value;
        return decimal(raw);
    }

    private String readComment(Object value)
    {
        if (value instanceof Map<?, ?> map)
        {
            Object comment = map.get("comment");
            return comment == null ? null : comment.toString();
        }
        return null;
    }

    private BigDecimal decimal(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof BigDecimal decimal)
        {
            return decimal;
        }
        if (value instanceof Number number)
        {
            return BigDecimal.valueOf(number.doubleValue());
        }
        try
        {
            return new BigDecimal(value.toString().trim());
        }
        catch (NumberFormatException error)
        {
            throw new ServiceException("数值格式不正确", HttpStatus.BAD_REQUEST);
        }
    }

    private Boolean boolValue(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Boolean bool)
        {
            return bool;
        }
        return Boolean.parseBoolean(value.toString());
    }

    private boolean flag(Object value)
    {
        if (value instanceof Boolean bool)
        {
            return bool;
        }
        if (value instanceof Number number)
        {
            return number.intValue() != 0;
        }
        if (value == null)
        {
            return false;
        }
        String text = value.toString();
        return "1".equals(text) || "true".equalsIgnoreCase(text);
    }

    private int toInt(Object value)
    {
        if (value instanceof Number number)
        {
            return number.intValue();
        }
        if (value == null)
        {
            return 0;
        }
        try
        {
            return Integer.parseInt(value.toString());
        }
        catch (NumberFormatException ignored)
        {
            return 0;
        }
    }

    private Long toLong(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Number number)
        {
            return number.longValue();
        }
        String text = value.toString().trim();
        if (text.isEmpty())
        {
            return null;
        }
        try
        {
            return Long.valueOf(text);
        }
        catch (NumberFormatException error)
        {
            throw new ServiceException("ID 格式不正确", HttpStatus.BAD_REQUEST);
        }
    }

    private LocalDateTime topicTime(Map<String, Object> row, String field)
    {
        LocalDateTime value = localDateTime(row.get(field + "Raw"));
        return value != null ? value : localDateTime(row.get(field));
    }

    private LocalDateTime localDateTime(Object value)
    {
        if (value == null)
        {
            return null;
        }
        if (value instanceof Timestamp timestamp)
        {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof LocalDateTime localDateTime)
        {
            return localDateTime;
        }
        try
        {
            return Group3Time.parseUtc(value.toString(), "时间");
        }
        catch (ServiceException error)
        {
            return null;
        }
    }

    private String requiredText(String value, String label, int maxLength)
    {
        String text = value == null ? "" : value.trim();
        if (text.isBlank())
        {
            throw new ServiceException(label + "不能为空", HttpStatus.BAD_REQUEST);
        }
        if (text.length() > maxLength)
        {
            throw new ServiceException(label + "不能超过 " + maxLength + " 个字符", HttpStatus.BAD_REQUEST);
        }
        return text;
    }

    private String optionalText(String value, String label, int maxLength)
    {
        if (value == null || value.isBlank())
        {
            return null;
        }
        String text = value.trim();
        if (text.length() > maxLength)
        {
            throw new ServiceException(label + "不能超过 " + maxLength + " 个字符", HttpStatus.BAD_REQUEST);
        }
        return text;
    }

    private String text(Object value)
    {
        return value == null ? null : value.toString();
    }

    private String upper(String value)
    {
        return value == null || value.isBlank() ? null : value.trim().toUpperCase();
    }

    private String lower(String value)
    {
        return value == null || value.isBlank() ? null : value.trim().toLowerCase();
    }
}
