package com.ruoyi.web.controller.education;

import java.util.Date;
import java.util.List;
import java.util.Map;
import com.ruoyi.common.enums.BusinessStatus;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.enums.OperatorType;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.ServletUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.ip.IpUtils;
import com.ruoyi.framework.manager.AsyncManager;
import com.ruoyi.framework.manager.factory.AsyncFactory;
import com.ruoyi.system.domain.SysOperLog;

/** 写操作日志：在控制器里用运行时信息拼出包含具体对象身份的标题。 */
public final class EduOperLog {
    private EduOperLog() {}

    public static void record(String title, BusinessType type) {
        SysOperLog log = new SysOperLog();
        log.setTitle(title);
        log.setBusinessType(type.ordinal());
        log.setStatus(BusinessStatus.SUCCESS.ordinal());
        log.setOperName(SecurityUtils.getUsername());
        log.setOperIp(IpUtils.getIpAddr());
        log.setOperUrl(StringUtils.substring(ServletUtils.getRequest().getRequestURI(), 0, 255));
        log.setRequestMethod(ServletUtils.getRequest().getMethod());
        log.setMethod("com.ruoyi.web.controller.education.EduOperLog");
        log.setOperatorType(OperatorType.MANAGE.ordinal());
        log.setOperTime(new Date());
        AsyncManager.me().execute(AsyncFactory.recordOper(log));
    }

    /** 教师：'雷霆（teacher0）'，无姓名时退化为账号。 */
    public static String person(Map<String, Object> user) {
        String account = text(user.get("userName"));
        String name = text(user.get("nickName"));
        return name == null || name.isBlank() ? account : name + "（" + account + "）";
    }

    /** 学生：'2024123401 张三'，无姓名时退化为账号。 */
    public static String student(Map<String, Object> user) {
        String account = text(user.get("userName"));
        String name = text(user.get("nickName"));
        return name == null || name.isBlank() ? account : account + " " + name;
    }

    private static String text(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    /** '2024123401 张三、2024123402 李四（共 2 人）'，超过 3 人只列前 3 个。 */
    public static String students(List<Map<String, Object>> users) {
        StringBuilder sb = new StringBuilder();
        int shown = Math.min(users.size(), 3);
        for (int i = 0; i < shown; i++) {
            if (i > 0) sb.append("、");
            sb.append(student(users.get(i)));
        }
        if (users.size() > shown) sb.append(" 等 ").append(users.size()).append(" 人");
        sb.append("（共 ").append(users.size()).append(" 人）");
        return sb.toString();
    }
}
