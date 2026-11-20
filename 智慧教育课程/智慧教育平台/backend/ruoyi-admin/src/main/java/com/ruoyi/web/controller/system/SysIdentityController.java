package com.ruoyi.web.controller.system;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.utils.SecurityUtils;

/**
 * 第一组公共身份示例：身份全部取自服务端认证上下文，不接收调用方传入的 userId/role。
 * 此接口只返回本人身份，不表示具有任何课程或教学班的访问权限。
 */
@RestController
@RequestMapping("/system/identity")
public class SysIdentityController
{
    @GetMapping("/current")
    public AjaxResult current()
    {
        Map<String, Object> identity = new LinkedHashMap<>();
        identity.put("userId", String.valueOf(SecurityUtils.getUserId()));
        identity.put("deptId", SecurityUtils.getDeptId() == null ? null : String.valueOf(SecurityUtils.getDeptId()));
        identity.put("roles", SecurityUtils.getRoles());
        return AjaxResult.success(identity);
    }
}
