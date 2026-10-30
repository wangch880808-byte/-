package com.ruoyi;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * ruoyi-admin 启动级上下文测试。
 *
 * <p>默认不执行；需显式提供可用的 MySQL（含若依基础表）和 Redis：</p>
 *
 * <pre>
 * -Dnotice.mysql.url=jdbc:mysql://127.0.0.1:3399/ruoyi_hardening?useSSL=false&amp;allowPublicKeyRetrieval=true&amp;serverTimezone=UTC
 * -Dnotice.mysql.user=root
 * -Dnotice.mysql.password=
 * </pre>
 *
 * <p>该测试只验证 Spring 容器可启动，不验证真实 HTTP、登录、角色拦截或审计落库。</p>
 */
@SpringBootTest(classes = RuoYiApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.MOCK,
        properties = {
                "spring.devtools.restart.enabled=false",
                "spring.main.banner-mode=off",
                "logging.level.com.ruoyi=info"
        })
@EnabledIfSystemProperty(named = "notice.mysql.url", matches = ".+")
class RuoYiApplicationContextTest
{
    /**
     * 将外部测试属性映射到 application*.yml 中的环境占位符。
     *
     * @param registry Spring 动态属性注册器
     */
    @DynamicPropertySource
    static void testProperties(DynamicPropertyRegistry registry)
    {
        registry.add("RUOYI_DB_URL", () -> System.getProperty("notice.mysql.url"));
        registry.add("RUOYI_DB_USERNAME",
                () -> System.getProperty("notice.mysql.user", "root"));
        registry.add("RUOYI_DB_PASSWORD",
                () -> System.getProperty("notice.mysql.password", ""));
        registry.add("RUOYI_TOKEN_SECRET",
                () -> System.getProperty("ruoyi.token.secret",
                        "ruoyi-hardening-context-test-secret-20261030-abcdef"));
        registry.add("RUOYI_REDIS_HOST",
                () -> System.getProperty("ruoyi.redis.host", "127.0.0.1"));
        registry.add("RUOYI_REDIS_PORT",
                () -> System.getProperty("ruoyi.redis.port", "6379"));
        registry.add("RUOYI_REDIS_DATABASE",
                () -> System.getProperty("ruoyi.redis.database", "15"));
        registry.add("RUOYI_REDIS_PASSWORD",
                () -> System.getProperty("ruoyi.redis.password", ""));
        registry.add("ruoyi.profile",
                () -> System.getProperty("ruoyi.profile", "G:/AI临时文件/ruoyi-upload"));
    }

    /**
     * 容器加载成功即通过；不发起 HTTP 请求。
     */
    @Test
    void contextLoads()
    {
    }
}
