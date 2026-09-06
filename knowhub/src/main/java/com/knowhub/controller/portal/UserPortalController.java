package com.knowhub.controller.portal;

import com.knowhub.pojo.user.vo.UserPortalVo;
import com.knowhub.service.user.impl.UserPortalService;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 用户公开主页接口（/portal/user/**，无 @PreAuthorize，走 /portal/** permitAll，游客可读）。
 * <p>
 * 供前台用户主页横幅展示用户公开信息（点击评论区用户名 / 作者名跳转目标）。
 * 仅输出 userId/nickName/avatar/username/sex/createTime，不含 phoneNumber 等敏感字段
 * （隐私保护，对游客隐藏手机号）。用户不存在或已停用时抛业务错误，前端展示「用户不存在」。
 *
 * @author knowhub
 */
@Tag(name = "用户主页", description = "前台用户公开主页信息查询")
@RestController
@RequestMapping("/portal/user")
public class UserPortalController {

    @Autowired
    UserPortalService userPortalService;

    @GetMapping("/{userId}")
    @Operation(summary = "按 userId 查用户公开主页信息（游客可读，隐藏手机号等敏感字段）")
    public Result<UserPortalVo> getPublicUser(@PathVariable Long userId) {
        UserPortalVo vo = userPortalService.getPublicUserById(userId);
        if (vo == null) {
            // 用户不存在或已停用/删除：抛业务错误，前端展示「用户不存在」
            throw new ServiceException(404, "用户不存在");
        }
        return Result.success(vo);
    }
}
