package com.ruoyi.education.group3.service;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

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
import com.ruoyi.common.exception.ServiceException;
import com.ruoyi.education.group3.mapper.Group3Mapper;

class Group3LearningAnalyticsServiceTest
{
    private static final Long USER_ID = 9002L;
    private static final Long BINDING_ID = 40002L;

    private Group3Mapper mapper;
    private Group3AccessService accessService;
    private Group3ResourceService resourceService;
    private Group3PrepareService prepareService;
    private Group3DiscussionService discussionService;
    private Group3LearningAnalyticsService service;

    @BeforeEach
    void setUp()
    {
        mapper = mock(Group3Mapper.class);
        accessService = mock(Group3AccessService.class);
        resourceService = mock(Group3ResourceService.class);
        prepareService = mock(Group3PrepareService.class);
        discussionService = mock(Group3DiscussionService.class);
        service = new Group3LearningAnalyticsService(mapper, accessService, resourceService,
                prepareService, discussionService, new ObjectMapper());
        when(accessService.requireTeacherBinding(BINDING_ID))
                .thenReturn(Map.of("bindingId", BINDING_ID, "teachingClassId", 7001L));

        SysUser user = new SysUser(USER_ID);
        SysRole role = new SysRole();
        role.setRoleKey("teacher");
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
    void courseProgressUsesActiveStudentsAsDenominatorAndCombinesTaskTypes()
    {
        when(accessService.activeStudentIds(BINDING_ID)).thenReturn(List.of(1L, 2L, 3L));
        when(resourceService.publishedResources(BINDING_ID)).thenReturn(List.of(
                row("resourceId", 1001L, "title", "视频任务", "taskEnabled", 1,
                        "opensAt", "2026-11-01T00:00:00.000Z"),
                row("resourceId", 1002L, "title", "普通资料", "taskEnabled", 0)));
        when(prepareService.teacherPreviewTasks(BINDING_ID)).thenReturn(List.of(
                row("assignmentId", 2001L, "title", "预习任务", "opensAt", "2026-11-02T00:00:00.000Z"),
                row("assignmentId", 2001L, "title", "预习任务", "opensAt", "2026-11-02T00:00:00.000Z")));
        when(discussionService.topics(BINDING_ID, null, null, null, null, null)).thenReturn(List.of(
                row("topicId", 3001L, "title", "计分讨论", "scoreEnabled", 1,
                        "opensAt", "2026-11-03T00:00:00.000Z"),
                row("topicId", 3002L, "title", "普通讨论", "scoreEnabled", 0)));
        when(mapper.selectResourceProgressStats(BINDING_ID)).thenReturn(List.of(
                row("resourceId", 1001L, "completedCount", 2, "learnerCount", 2)));
        when(mapper.selectAssignmentSubmissionStats(BINDING_ID)).thenReturn(List.of(
                row("assignmentId", 2001L, "submittedCount", 1, "targetCount", 3)));
        when(mapper.selectDiscussionParticipationStats(BINDING_ID)).thenReturn(List.of(
                row("topicId", 3001L, "userId", 1L, "postCount", 2)));

        Map<String, Object> result = service.courseProgress(BINDING_ID);
        @SuppressWarnings("unchecked")
        Map<String, Object> summary = (Map<String, Object>) result.get("summary");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> tasks = (List<Map<String, Object>>) result.get("tasks");

        assertEquals(3, summary.get("studentCount"));
        assertEquals(3, summary.get("taskCount"));
        assertEquals(1, summary.get("videoTaskCount"));
        assertEquals(1, summary.get("quizTaskCount"));
        assertEquals(1, summary.get("discussionTaskCount"));
        assertEquals(9, summary.get("totalOpportunities"));
        assertEquals(4, summary.get("completed"));
        assertEquals(44, summary.get("percent"));
        assertEquals(3, tasks.size());
    }

    @Test
    void submissionRecordsHideAnswersUntilPublished()
    {
        when(mapper.selectSubmissionAnalyticsCount(BINDING_ID)).thenReturn(2);
        String answer = "{\"choiceIndex\":1}";
        when(mapper.selectSubmissionAnalytics(BINDING_ID)).thenReturn(List.of(
                row("assignmentId", 2001L, "studentUserId", 11L, "questionKind", "SINGLE",
                        "answerJson", answer, "studentAnswerJson", answer,
                        "optionsJson", "[]", "explanation", "解析", "answerVisible", 1),
                row("assignmentId", 2001L, "studentUserId", 12L, "questionKind", "SINGLE",
                        "answerJson", answer, "studentAnswerJson", "{\"choiceIndex\":0}",
                        "optionsJson", "[]", "explanation", "解析", "answerVisible", 0)));

        Map<String, Object> result = service.submissionRecords(BINDING_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");

        assertEquals(2, result.get("total"));
        assertEquals(false, result.get("truncated"));
        assertEquals(2, records.size());
        assertEquals(true, records.get(0).get("answerVisible"));
        assertEquals(true, records.get(0).get("correct"));
        assertEquals("解析", records.get(0).get("explanation"));
        assertEquals(false, records.get(1).get("answerVisible"));
        assertNull(records.get(1).get("correct"));
        assertFalse(records.get(1).containsKey("correctAnswer"));
        assertFalse(records.get(1).containsKey("studentAnswer"));
        assertFalse(records.get(1).containsKey("explanation"));
    }

    @Test
    void discussionParticipationAggregatesPerStudentAndTopic()
    {
        when(mapper.selectDiscussionParticipationStats(BINDING_ID)).thenReturn(List.of(
                row("topicId", 3001L, "userId", 11L, "postCount", 3, "replyCount", 1,
                        "likeCount", 5, "lastPostAt", "2026-11-05T01:00:00.000Z"),
                row("topicId", 3002L, "userId", 11L, "postCount", 2, "replyCount", 2,
                        "likeCount", 1, "lastPostAt", "2026-11-04T01:00:00.000Z"),
                row("topicId", 3001L, "userId", 12L, "postCount", 1, "replyCount", 0,
                        "likeCount", 0, "lastPostAt", "2026-11-03T01:00:00.000Z")));

        Map<String, Object> result = service.discussionParticipation(BINDING_ID);
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> records = (List<Map<String, Object>>) result.get("records");
        @SuppressWarnings("unchecked")
        List<Map<String, Object>> breakdown = (List<Map<String, Object>>) result.get("topicBreakdown");

        assertEquals(2, result.get("total"));
        assertEquals(2, records.size());
        assertEquals(3, breakdown.size());
        assertEquals(11L, records.get(0).get("userId"));
        assertEquals(5L, records.get(0).get("postCount"));
        assertEquals(3L, records.get(0).get("replyCount"));
        assertEquals(6L, records.get(0).get("likeCount"));
        assertEquals(2, records.get(0).get("topicCount"));
        assertEquals("2026-11-05T01:00:00.000Z", records.get(0).get("lastPostAt"));
    }

    @Test
    void rejectsMissingBinding()
    {
        assertThrows(ServiceException.class, () -> service.courseProgress(null));
    }

    private Map<String, Object> row(Object... pairs)
    {
        Map<String, Object> row = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2)
        {
            row.put((String) pairs[i], pairs[i + 1]);
        }
        return row;
    }
}
