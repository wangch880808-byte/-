package com.ruoyi.education.group3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.ruoyi.common.constant.HttpStatus;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.course.api.EduClassBindingRef;
import com.ruoyi.education.course.api.EduClassRef;
import com.ruoyi.education.course.api.EduCourseAccessService;
import com.ruoyi.education.exception.EduForbiddenException;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Requests;
import com.ruoyi.education.group3.support.Group3Time;
import com.ruoyi.education.service.IEduPortalService;
import com.ruoyi.education.service.IEduTermService;

class Group3DiscussionServiceTest
{
    private static final Long USER_ID = 9001L;
    private static final Long OTHER_USER_ID = 9002L;
    private static final Long CLASS_ID = 20001L;
    private static final Long COURSE_ID = 30001L;
    private static final Long BINDING_ID = 40001L;
    private static final Long TOPIC_ID = 50001L;

    private Group3Mapper mapper;
    private EduCourseAccessService access;
    private IEduPortalService portal;
    private Group3DiscussionService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(Group3Mapper.class);
        access = mock(EduCourseAccessService.class);
        portal = mock(IEduPortalService.class);
        Group3AccessService accessService = new Group3AccessService(mapper, access, portal, mock(IEduTermService.class));
        service = new Group3DiscussionService(mapper, accessService);
    }

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void studentOutsideClassCannotListTopics()
    {
        authenticate("student");
        stubStudentBinding();
        when(access.isActiveStudent(CLASS_ID, USER_ID)).thenReturn(false);

        assertThrows(EduForbiddenException.class,
                () -> service.topics(BINDING_ID, null, null, null, null, null));
    }

    @Test
    void studentCannotCreateTopic()
    {
        authenticate("student");

        assertThrows(EduForbiddenException.class,
                () -> service.createTopic(new Group3Requests.DiscussionTopic(BINDING_ID, null, null,
                        "标题", "正文", null, null, null, null, null, null, null, null, null,
                        null, null, null, null)));

    }

    @Test
    void teacherNeedsGradingPermissionToScoreDiscussion()
    {
        authenticate("teacher");
        Group3AccessService bindingAccess = mock(Group3AccessService.class);
        when(bindingAccess.requireTeacherBinding(BINDING_ID))
                .thenReturn(Map.of("canPublish", true, "canGrade", false));
        Group3DiscussionService discussions = new Group3DiscussionService(mapper, bindingAccess);

        assertThrows(EduForbiddenException.class, () -> discussions.action(
                new Group3Requests.DiscussionAction(BINDING_ID, TOPIC_ID, 7L, "score", 90)));
    }

    @Test
    void studentCannotPostBeforeTopicOpens()
    {
        authorizeStudent();
        Map<String, Object> topic = topic("PUBLISHED", Group3Time.nowUtc().plusHours(1), null, 1, 1);
        when(mapper.selectDiscussionTopic(BINDING_ID, TOPIC_ID, USER_ID)).thenReturn(topic);

        ServiceException error = assertThrows(ServiceException.class, () -> service.createPost(post()));

        assertEquals(HttpStatus.CONFLICT, error.getCode());
        verify(mapper, never()).insertDiscussionPost(org.mockito.ArgumentMatchers.anyMap());
    }

    @Test
    void studentCannotPostAfterTopicCloses()
    {
        authorizeStudent();
        Map<String, Object> topic = topic("PUBLISHED", Group3Time.nowUtc().minusHours(2),
                Group3Time.nowUtc().minusHours(1), 1, 1);
        when(mapper.selectDiscussionTopic(BINDING_ID, TOPIC_ID, USER_ID)).thenReturn(topic);

        ServiceException error = assertThrows(ServiceException.class, () -> service.createPost(post()));

        assertEquals(HttpStatus.CONFLICT, error.getCode());
    }

    @Test
    void replyIsRejectedWhenTopicDisallowsReply()
    {
        authorizeStudent();
        Map<String, Object> topic = topic("PUBLISHED", Group3Time.nowUtc().minusHours(1),
                Group3Time.nowUtc().plusHours(1), 0, 1);
        when(mapper.selectDiscussionTopic(BINDING_ID, TOPIC_ID, USER_ID)).thenReturn(topic);

        ServiceException error = assertThrows(ServiceException.class, () -> service.createPost(post()));

        assertEquals(HttpStatus.CONFLICT, error.getCode());
    }

    @Test
    void archivedTopicIsInvisibleToStudent()
    {
        authorizeStudent();
        when(mapper.selectDiscussionTopic(BINDING_ID, TOPIC_ID, USER_ID))
                .thenReturn(topic("ARCHIVED", Group3Time.nowUtc().minusHours(1),
                        Group3Time.nowUtc().plusHours(1), 1, 1));

        ServiceException error = assertThrows(ServiceException.class,
                () -> service.topicDetail(BINDING_ID, TOPIC_ID));

        assertEquals(HttpStatus.FORBIDDEN, error.getCode());
    }

    @Test
    void topicLikeTogglesWithoutDuplicatingRows()
    {
        authorizeStudent();
        when(mapper.selectDiscussionTopic(BINDING_ID, TOPIC_ID, USER_ID))
                .thenReturn(topic("PUBLISHED", Group3Time.nowUtc().minusHours(1),
                        Group3Time.nowUtc().plusHours(1), 1, 1));
        when(mapper.selectDiscussionLike(TOPIC_ID, 0L, USER_ID)).thenReturn(1, 0);

        Map<String, Object> first = service.action(new Group3Requests.DiscussionAction(
                BINDING_ID, TOPIC_ID, null, "like", null));
        Map<String, Object> second = service.action(new Group3Requests.DiscussionAction(
                BINDING_ID, TOPIC_ID, null, "like", null));

        assertFalse((Boolean) first.get("liked"));
        assertTrue((Boolean) second.get("liked"));
        verify(mapper, times(1)).deleteDiscussionLike(TOPIC_ID, 0L, USER_ID);
        verify(mapper, times(1)).insertDiscussionLike(TOPIC_ID, 0L, USER_ID);
    }

    @Test
    void anonymousAuthorIsMaskedForOtherStudents()
    {
        authorizeStudent();
        when(mapper.selectDiscussionTopic(BINDING_ID, TOPIC_ID, USER_ID))
                .thenReturn(topic("PUBLISHED", Group3Time.nowUtc().minusHours(1),
                        Group3Time.nowUtc().plusHours(1), 1, 1));
        Map<String, Object> anonymousPost = new LinkedHashMap<>();
        anonymousPost.put("postId", 60001L);
        anonymousPost.put("userId", OTHER_USER_ID);
        anonymousPost.put("authorName", "张三");
        anonymousPost.put("anonymous", 1);
        anonymousPost.put("content", "匿名发言");
        anonymousPost.put("status", "NORMAL");
        anonymousPost.put("likeCount", 0);
        anonymousPost.put("myLiked", 0);
        when(mapper.selectDiscussionPosts(eq(TOPIC_ID), eq(USER_ID)))
                .thenReturn(List.of(anonymousPost));

        Map<String, Object> detail = service.topicDetail(BINDING_ID, TOPIC_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> posts = (List<Map<String, Object>>) detail.get("posts");
        Map<String, Object> post = posts.get(0);

        assertEquals("匿名同学", post.get("authorName"));
        assertNull(post.get("userId"));
        assertTrue((Boolean) post.get("anonymous"));
    }

    private Group3Requests.DiscussionPost post()
    {
        return new Group3Requests.DiscussionPost(BINDING_ID, TOPIC_ID, null, "我的回复",
                false, null, null);
    }

    private Map<String, Object> topic(String status, LocalDateTime opensAt, LocalDateTime closesAt,
            int allowReply, int allowStudentTopic)
    {
        Map<String, Object> topic = new LinkedHashMap<>();
        topic.put("topicId", TOPIC_ID);
        topic.put("bindingId", BINDING_ID);
        topic.put("status", status);
        topic.put("opensAtRaw", opensAt);
        topic.put("closesAtRaw", closesAt);
        topic.put("allowReply", allowReply);
        topic.put("allowStudentTopic", allowStudentTopic);
        topic.put("allowAnonymous", 1);
        topic.put("allowAttachment", 0);
        topic.put("scoreEnabled", 1);
        topic.put("scoreMax", new java.math.BigDecimal("100.00"));
        topic.put("likeCount", 0);
        topic.put("viewCount", 0);
        return topic;
    }

    private void authorizeStudent()
    {
        authenticate("student");
        stubStudentBinding();
        when(access.isActiveStudent(CLASS_ID, USER_ID)).thenReturn(true);
        when(portal.courseDetail(COURSE_ID)).thenReturn(Map.of("id", COURSE_ID, "title", "课程A", "status", "ACTIVE"));
    }

    private void authenticate(String roleKey)
    {
        SysUser user = new SysUser(USER_ID);
        SysRole role = new SysRole();
        role.setRoleKey(roleKey);
        role.setStatus("0");
        user.setRoles(List.of(role));
        LoginUser loginUser = new LoginUser(USER_ID, null, user, java.util.Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    private void stubStudentBinding()
    {
        EduClassRef cls = new EduClassRef(CLASS_ID, "班级A", "C001", 5001L, "2026秋",
                100L, "ACTIVE", "TRIAL");
        when(access.classesOfStudent(USER_ID)).thenReturn(List.of(cls));
        when(access.activeBindings(CLASS_ID)).thenReturn(List.of(new EduClassBindingRef(BINDING_ID, CLASS_ID, COURSE_ID, null)));
    }
}
