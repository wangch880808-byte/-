package com.ruoyi.education.group3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ruoyi.common.core.domain.entity.SysRole;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.education.group3.mapper.Group3Mapper;
import com.ruoyi.education.group3.support.Group3Time;

class Group3StudentDashboardServiceTest
{
    private static final Long USER_ID = 9001L;
    private static final Long BINDING_ID = 40001L;

    private Group3Mapper mapper;
    private Group3ResourceService resourceService;
    private Group3PrepareService prepareService;
    private Group3DiscussionService discussionService;
    private Group3StudentDashboardService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(Group3Mapper.class);
        Group3AccessService accessService = mock(Group3AccessService.class);
        resourceService = mock(Group3ResourceService.class);
        prepareService = mock(Group3PrepareService.class);
        discussionService = mock(Group3DiscussionService.class);
        service = new Group3StudentDashboardService(mapper, accessService, resourceService,
                prepareService, discussionService, new ObjectMapper());
        when(accessService.requireStudentBinding(BINDING_ID))
                .thenReturn(Map.of("bindingId", BINDING_ID));

        SysUser user = new SysUser(USER_ID);
        SysRole role = new SysRole();
        role.setRoleKey("student");
        role.setStatus("0");
        user.setRoles(List.of(role));
        LoginUser loginUser = new LoginUser(USER_ID, null, user, Set.of());
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginUser, null, List.of()));
    }

    @AfterEach
    void clearSecurityContext()
    {
        SecurityContextHolder.clearContext();
    }

    @Test
    void dashboardAggregatesVideoQuizAndDiscussionTasks()
    {
        LocalDateTime now = Group3Time.nowUtc();
        Map<String, Object> video = row(
                "resourceId", 1001L,
                "title", "视频任务",
                "taskEnabled", 1,
                "taskRequiredSeconds", 60,
                "opensAt", now.minusHours(1).toInstant(java.time.ZoneOffset.UTC).toString(),
                "closesAt", now.plusDays(1).toInstant(java.time.ZoneOffset.UTC).toString());
        Map<String, Object> progress = row(
                "resourceId", 1001L,
                "watchedSeconds", 60,
                "completed", 1);
        Map<String, Object> quizFirst = row(
                "assignmentId", 2001L,
                "title", "预习任务",
                "opensAt", now.minusHours(2).toInstant(java.time.ZoneOffset.UTC).toString(),
                "closesAt", now.plusDays(2).toInstant(java.time.ZoneOffset.UTC).toString(),
                "state", "OPEN",
                "submissionId", 3001L);
        Map<String, Object> quizSecond = row(
                "assignmentId", 2001L,
                "title", "预习任务",
                "opensAt", quizFirst.get("opensAt"),
                "closesAt", quizFirst.get("closesAt"),
                "state", "OPEN");
        Map<String, Object> topic = row(
                "topicId", 5001L,
                "title", "讨论任务",
                "scoreEnabled", true,
                "opensAt", now.minusHours(3).toInstant(java.time.ZoneOffset.UTC).toString(),
                "closesAt", now.plusDays(1).toInstant(java.time.ZoneOffset.UTC).toString(),
                "state", "OPEN");

        when(resourceService.studentMaterials(BINDING_ID)).thenReturn(List.of(video));
        when(resourceService.studentPrepMaterials(BINDING_ID)).thenReturn(List.of());
        when(prepareService.studentPreviewTasks(BINDING_ID)).thenReturn(List.of(quizFirst, quizSecond));
        when(discussionService.topics(BINDING_ID, null, null, null, null, null))
                .thenReturn(List.of(topic));
        when(mapper.selectResourceProgress(BINDING_ID, USER_ID)).thenReturn(List.of(progress));
        when(mapper.selectStudentDiscussionParticipation(BINDING_ID, USER_ID)).thenReturn(List.of());

        Map<String, Object> result = service.dashboard(BINDING_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> items = (List<Map<String, Object>>) result.get("items");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> todos = (List<Map<String, Object>>) result.get("todos");

        assertEquals(3, summary.get("total"));
        assertEquals(2, summary.get("completed"));
        assertEquals(67, summary.get("percent"));
        assertEquals(3, items.size());
        assertEquals(1, todos.size());
        assertEquals("DUE_SOON", todos.get(0).get("tone"));
        assertEquals(5001L, todos.get(0).get("refId"));
    }

    @Test
    void answerReviewSeparatesWrongQuestionsAndAggregatesKnowledgePoints()
    {
        Map<String, Object> single = reviewRow(1L, "SINGLE",
                "{\"choiceIndex\":1}", "{\"choiceIndex\":1}", "代数", "2026-11-18T10:00:00.000Z");
        Map<String, Object> multiple = reviewRow(2L, "MULTIPLE",
                "{\"choiceIndices\":[0,2]}", "{\"choiceIndices\":[0,1]}", "函数,集合",
                "2026-11-18T10:01:00.000Z");
        Map<String, Object> shortText = reviewRow(3L, "SHORT_TEXT",
                "{\"referenceAnswer\":\"参考答案\"}", "我的回答", "",
                "2026-11-18T10:02:00.000Z");
        when(mapper.selectStudentAnswerReview(BINDING_ID, USER_ID))
                .thenReturn(List.of(single, multiple, shortText));

        Map<String, Object> result = service.answerReview(BINDING_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> reviews = (List<Map<String, Object>>) result.get("reviews");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> wrong = (List<Map<String, Object>>) result.get("wrongQuestions");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> points = (List<Map<String, Object>>) result.get("knowledgePoints");

        assertEquals(3, reviews.size());
        assertEquals(Boolean.TRUE, reviews.get(0).get("correct"));
        assertEquals(Boolean.FALSE, reviews.get(1).get("correct"));
        assertNull(reviews.get(2).get("correct"));
        assertFalse((Boolean) reviews.get(2).get("graded"));
        assertEquals(1, wrong.size());
        assertEquals(2L, wrong.get(0).get("assignmentItemId"));
        assertEquals(2, points.size());
        assertEquals("函数", points.get(0).get("name"));
        assertTrue(points.stream().anyMatch(point -> "集合".equals(point.get("name"))));
    }

    private Map<String, Object> reviewRow(
            Long itemId, String kind, String answer, String studentAnswer,
            String knowledgePoints, String submittedAt)
    {
        return row(
                "assignmentId", 7001L,
                "assignmentTitle", "预习任务",
                "assignmentItemId", itemId,
                "prompt", "题目 " + itemId,
                "questionKind", kind,
                "answerJson", answer,
                "studentAnswerJson", studentAnswer,
                "knowledgePoints", knowledgePoints,
                "submittedAt", submittedAt,
                "answerVisibleAt", submittedAt);
    }

    private Map<String, Object> row(Object... values)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < values.length; i += 2)
        {
            row.put((String) values[i], values[i + 1]);
        }
        return row;
    }
}
