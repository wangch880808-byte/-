package com.ruoyi.web.controller.system;

import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import jakarta.validation.Validator;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import com.ruoyi.common.annotation.Log;
import com.ruoyi.common.config.RuoYiConfig;
import com.ruoyi.common.core.controller.BaseController;
import com.ruoyi.common.core.domain.AjaxResult;
import com.ruoyi.common.core.domain.entity.SysUser;
import com.ruoyi.common.core.domain.model.LoginUser;
import com.ruoyi.common.enums.BusinessType;
import com.ruoyi.common.utils.DateUtils;
import com.ruoyi.common.utils.SecurityUtils;
import com.ruoyi.common.utils.StringUtils;
import com.ruoyi.common.utils.file.FileUploadUtils;
import com.ruoyi.common.utils.file.FileUtils;
import com.ruoyi.common.utils.file.MimeTypeUtils;
import com.ruoyi.framework.web.service.TokenService;
import com.ruoyi.system.service.ISysUserService;

/**
 * 个人信息 业务处理
 * 
 * @author ruoyi
 */
@RestController
@RequestMapping("/system/user/profile")
public class SysProfileController extends BaseController
{
    @Autowired
    private ISysUserService userService;

    @Autowired
    private TokenService tokenService;

    @Autowired
    private Validator validator;

    /**
     * 个人信息
     */
    @GetMapping
    public AjaxResult profile()
    {
        LoginUser loginUser = getLoginUser();
        SysUser user = loginUser.getUser();
        AjaxResult ajax = AjaxResult.success(user);
        ajax.put("roleGroup", userService.selectUserRoleGroup(loginUser.getUsername()));
        ajax.put("postGroup", userService.selectUserPostGroup(loginUser.getUsername()));
        return ajax;
    }

    /**
     * 修改用户
     */
    @Log(title = "个人信息", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping
    public AjaxResult updateProfile(@RequestBody SysUser user)
    {
        LoginUser loginUser = getLoginUser();
        SysUser currentUser = loginUser.getUser();
        user.setUserId(loginUser.getUserId());
        user.setUserName(currentUser.getUserName());
        if (user.getNickName() != null)
        {
            user.setNickName(user.getNickName().trim());
            if (user.getNickName().isEmpty())
            {
                return error("昵称不能为空");
            }
        }
        if (user.getSex() != null && !Set.of("0", "1", "2").contains(user.getSex()))
        {
            return error("性别选项无效");
        }
        var violations = validator.validate(user);
        if (!violations.isEmpty())
        {
            return error(violations.iterator().next().getMessage());
        }
        if (StringUtils.isNotEmpty(user.getPhonenumber()) && !userService.checkPhoneUnique(user))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，手机号码已存在");
        }
        if (StringUtils.isNotEmpty(user.getEmail()) && !userService.checkEmailUnique(user))
        {
            return error("修改用户'" + loginUser.getUsername() + "'失败，邮箱账号已存在");
        }
        if (userService.updateUserProfile(user) > 0)
        {
            if (user.getNickName() != null) currentUser.setNickName(user.getNickName());
            if (user.getEmail() != null) currentUser.setEmail(user.getEmail());
            if (user.getPhonenumber() != null) currentUser.setPhonenumber(user.getPhonenumber());
            if (user.getSex() != null) currentUser.setSex(user.getSex());
            // 更新当前会话中的个人资料
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("修改个人信息异常，请联系管理员");
    }

    /**
     * 重置密码
     */
    @Log(title = "修改登录密码", businessType = BusinessType.UPDATE, isSaveRequestData = false)
    @PutMapping("/updatePwd")
    public AjaxResult updatePwd(@RequestBody Map<String, String> params)
    {
        String oldPassword = params.get("oldPassword");
        String newPassword = params.get("newPassword");
        if (StringUtils.isEmpty(oldPassword))
        {
            return error("请输入当前密码");
        }
        if (newPassword == null || newPassword.isBlank() || newPassword.length() < 6 || newPassword.length() > 20)
        {
            return error("新密码长度必须介于 6 和 20 之间");
        }
        LoginUser loginUser = getLoginUser();
        Long userId = loginUser.getUserId();
        SysUser user = userService.selectUserById(userId);
        if (user == null || !"0".equals(user.getStatus()) || !"0".equals(user.getDelFlag()))
        {
            return error("当前账号不可用");
        }
        String password = user.getPassword();
        if (!SecurityUtils.matchesPassword(oldPassword, password))
        {
            return error("修改密码失败，旧密码错误");
        }
        if (SecurityUtils.matchesPassword(newPassword, password))
        {
            return error("新密码不能与旧密码相同");
        }
        newPassword = SecurityUtils.encryptPassword(newPassword);
        if (userService.resetUserPwdIfCurrent(userId, password, newPassword) > 0)
        {
            // 更新缓存用户密码&密码最后更新时间
            loginUser.getUser().setPwdUpdateDate(DateUtils.getNowDate());
            loginUser.getUser().setPassword(newPassword);
            tokenService.setLoginUser(loginUser);
            return success();
        }
        return error("密码状态已变化，请重新核对当前密码后重试");
    }

    /**
     * 头像上传
     */
    @Log(title = "用户头像", businessType = BusinessType.UPDATE)
    @PostMapping("/avatar")
    public AjaxResult avatar(@RequestParam("avatarfile") MultipartFile file) throws Exception
    {
        if (file.isEmpty() || file.getSize() > 5 * 1024 * 1024L || !isAvatarImage(file))
        {
            return error("请选择不超过 5 MB 且尺寸不超过 4096 像素的有效图片");
        }
        LoginUser loginUser = getLoginUser();
        String avatar = FileUploadUtils.upload(RuoYiConfig.getAvatarPath(), file, MimeTypeUtils.IMAGE_EXTENSION, true);
        if (!userService.updateUserAvatar(loginUser.getUserId(), avatar))
        {
            FileUtils.deleteFile(RuoYiConfig.getProfile() + FileUtils.stripPrefix(avatar));
            return error("上传图片异常，请联系管理员");
        }

        String oldAvatar = loginUser.getUser().getAvatar();
        if (oldAvatar != null && oldAvatar.matches("/profile/avatar/[0-9/]+/[a-zA-Z0-9_-]+\\.[a-zA-Z]+"))
        {
            FileUtils.deleteFile(RuoYiConfig.getProfile() + FileUtils.stripPrefix(oldAvatar));
        }
        loginUser.getUser().setAvatar(avatar);
        tokenService.setLoginUser(loginUser);
        AjaxResult ajax = AjaxResult.success();
        ajax.put("imgUrl", avatar);
        return ajax;
    }

    private boolean isAvatarImage(MultipartFile file)
    {
        try (InputStream input = file.getInputStream(); ImageInputStream image = ImageIO.createImageInputStream(input))
        {
            if (image == null) return false;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(image);
            if (!readers.hasNext()) return false;
            ImageReader reader = readers.next();
            try
            {
                reader.setInput(image, true, true);
                String format = reader.getFormatName();
                String extension = FileUploadUtils.getExtension(file);
                boolean matchesExtension = format.equalsIgnoreCase(extension)
                        || ("JPEG".equalsIgnoreCase(format) && "jpg".equalsIgnoreCase(extension));
                return matchesExtension && reader.getWidth(0) <= 4096 && reader.getHeight(0) <= 4096;
            }
            finally
            {
                reader.dispose();
            }
        }
        catch (IOException | RuntimeException e)
        {
            return false;
        }
    }
}
