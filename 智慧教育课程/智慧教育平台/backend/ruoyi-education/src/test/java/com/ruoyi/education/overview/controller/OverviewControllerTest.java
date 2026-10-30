package com.ruoyi.education.overview.controller;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.lang.reflect.Method;
import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializerProvider;
import com.fasterxml.jackson.databind.module.SimpleModule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import com.ruoyi.education.overview.dto.OverviewItem;
import com.ruoyi.education.overview.dto.OverviewResponse;
import com.ruoyi.education.overview.dto.OverviewStatus;
import com.ruoyi.education.overview.dto.OverviewWindow;
import com.ruoyi.education.overview.exception.OverviewAccessDeniedException;
import com.ruoyi.education.overview.security.OverviewIdentityProvider;
import com.ruoyi.education.overview.service.OverviewQueryService;
import com.ruoyi.education.overview.support.OverviewWindowFactory;
import com.ruoyi.education.overview.web.OverviewExceptionHandler;

/**
 * 总览四端点的 MockMvc 契约测试。
 *
 * <p>使用 standalone MockMvc 和 Mockito 服务替身，只验证 HTTP 绑定、三态输出、
 * 403/400 映射和缺适配器语义；不代表真实登录、权限拦截或生产联调。
 * 方法级 {@code @PreAuthorize} 在 standalone 模式下不执行，因此另用反射断言
 * 注解权限串，真实拦截待第 1 组配置菜单后联调。</p>
 */
class OverviewControllerTest
{
    private static final Instant AS_OF = Instant.parse("2026-10-30T06:00:00Z");

    private static final OverviewWindow WINDOW = OverviewWindow.of(
            Instant.parse("2026-10-30T00:00:00Z"),
            Instant.parse("2026-10-31T00:00:00Z"));

    private OverviewQueryService queryService;

    private OverviewIdentityProvider identityProvider;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp()
    {
        queryService = mock(OverviewQueryService.class);
        identityProvider = mock(OverviewIdentityProvider.class);
        OverviewWindowFactory windowFactory = new OverviewWindowFactory(
                Clock.fixed(AS_OF, ZoneOffset.UTC));
        OverviewController controller = new OverviewController(queryService, windowFactory,
                identityProvider);
        ObjectMapper objectMapper = new ObjectMapper();
        SimpleModule instantModule = new SimpleModule();
        instantModule.addSerializer(Instant.class, new JsonSerializer<Instant>()
        {
            @Override
            public void serialize(Instant value, JsonGenerator generator,
                    SerializerProvider serializers) throws IOException
            {
                generator.writeString(value.toString());
            }
        });
        objectMapper.registerModule(instantModule);
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new OverviewExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }

    @Test
    void teacherOverviewReturnsOk() throws Exception
    {
        when(identityProvider.currentUserId()).thenReturn(7L);
        when(queryService.teacherOverview(eq(7L), isNull(), any(OverviewWindow.class)))
                .thenReturn(response("teacher.class.count", OverviewStatus.OK, "1"));

        mockMvc.perform(get("/education/teacher/overview")
                        .param("from", "2026-10-30T00:00:00Z")
                        .param("to", "2026-10-31T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].metricCode").value("teacher.class.count"))
                .andExpect(jsonPath("$.data.items[0].status").value("OK"));

        verify(queryService).teacherOverview(eq(7L), isNull(), any(OverviewWindow.class));
    }

    @Test
    void studentTodayTodosReturnsThreeStates() throws Exception
    {
        when(identityProvider.currentUserId()).thenReturn(9L);
        OverviewResponse response = OverviewResponse.of(AS_OF, WINDOW, List.of(
                item("student.class.count", OverviewStatus.OK, "1"),
                OverviewItem.noData("student.assignment.due", "COUNT", "assignment", null),
                OverviewItem.unavailable("student.notice.unread", "COUNT", "notice", null)));
        when(queryService.studentTodayTodos(eq(9L), any(OverviewWindow.class)))
                .thenReturn(response);

        mockMvc.perform(get("/education/student/today-todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].status").value("OK"))
                .andExpect(jsonPath("$.data.items[1].status").value("NO_DATA"))
                .andExpect(jsonPath("$.data.items[2].status").value("UNAVAILABLE"));
    }

    @Test
    void adminOverviewReturnsOk() throws Exception
    {
        when(identityProvider.currentOrganizationId()).thenReturn(1L);
        when(queryService.adminOverview(eq(1L), any(OverviewWindow.class)))
                .thenReturn(response("admin.class.count", OverviewStatus.OK, "3"));

        mockMvc.perform(get("/education/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].metricCode").value("admin.class.count"));
    }

    @Test
    void adminTodosReturnsNoData() throws Exception
    {
        when(identityProvider.currentOrganizationId()).thenReturn(1L);
        when(queryService.adminTodos(eq(1L), any(OverviewWindow.class)))
                .thenReturn(OverviewResponse.of(AS_OF, WINDOW, List.of(
                        OverviewItem.noData("admin.todo.pending", "COUNT",
                                "pending_product_confirmation", null))));

        mockMvc.perform(get("/education/admin/todos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].status").value("NO_DATA"))
                .andExpect(jsonPath("$.data.items[0].source")
                        .value("pending_product_confirmation"));
    }

    @Test
    void missingAdapterReturnsUnavailableItemNotZero() throws Exception
    {
        when(identityProvider.currentUserId()).thenReturn(7L);
        when(queryService.teacherOverview(eq(7L), isNull(), any(OverviewWindow.class)))
                .thenReturn(OverviewResponse.of(AS_OF, WINDOW, List.of(
                        OverviewItem.unavailable("teacher.notice.unread", "COUNT",
                                "notice", null))));

        mockMvc.perform(get("/education/teacher/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(200))
                .andExpect(jsonPath("$.data.items[0].status").value("UNAVAILABLE"))
                .andExpect(jsonPath("$.data.items[0].value").doesNotExist());
    }

    @Test
    void unauthorizedMapsToBusinessCode403() throws Exception
    {
        when(identityProvider.currentUserId()).thenReturn(7L);
        when(queryService.teacherOverview(eq(7L), isNull(), any(OverviewWindow.class)))
                .thenThrow(new OverviewAccessDeniedException("无权查看该教学班总览"));

        mockMvc.perform(get("/education/teacher/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void missingAdminOrganizationMapsTo403() throws Exception
    {
        when(identityProvider.currentOrganizationId()).thenReturn(null);
        when(queryService.adminOverview(isNull(), any(OverviewWindow.class)))
                .thenThrow(new OverviewAccessDeniedException("管理员组织范围缺失"));

        mockMvc.perform(get("/education/admin/overview"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(403));
    }

    @Test
    void invalidWindowMapsTo400() throws Exception
    {
        mockMvc.perform(get("/education/teacher/overview")
                        .param("from", "not-a-time")
                        .param("to", "2026-10-31T00:00:00Z"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void invalidClassIdTypeMapsTo400() throws Exception
    {
        mockMvc.perform(get("/education/teacher/overview").param("classId", "abc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(400));
    }

    @Test
    void usesPlannedPermissionAnnotations() throws Exception
    {
        assertPermission("teacherOverview", new Class<?>[] { String.class, String.class,
                Long.class }, "@ss.hasPermi('education:overview:teacher')");
        assertPermission("studentTodayTodos", new Class<?>[] { String.class, String.class },
                "@ss.hasPermi('education:overview:student')");
        assertPermission("adminOverview", new Class<?>[] { String.class, String.class },
                "@ss.hasPermi('education:overview:admin')");
        assertPermission("adminTodos", new Class<?>[] { String.class, String.class },
                "@ss.hasPermi('education:overview:admin')");
    }

    private void assertPermission(String methodName, Class<?>[] parameterTypes,
            String expected) throws Exception
    {
        Method method = OverviewController.class.getMethod(methodName, parameterTypes);
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertEquals(expected, annotation.value());
    }

    private OverviewResponse response(String metricCode, OverviewStatus status, String value)
    {
        return OverviewResponse.of(AS_OF, WINDOW, List.of(item(metricCode, status, value)));
    }

    private OverviewItem item(String metricCode, OverviewStatus status, String value)
    {
        if (status == OverviewStatus.OK)
        {
            return OverviewItem.ok(metricCode, new BigDecimal(value), "COUNT",
                    "course", 1L, "/teacher/classes");
        }
        return OverviewItem.noData(metricCode, "COUNT", "course", null);
    }
}
