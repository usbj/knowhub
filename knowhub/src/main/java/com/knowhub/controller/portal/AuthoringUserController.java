package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.user.quarry.AuthoringUserSearchQuarry;
import com.knowhub.pojo.user.vo.AuthoringUserVo;
import com.knowhub.service.user.impl.AuthoringUserService;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 前台选人接口（/authoring/user/**，走 /authoring/** authenticated 兜底）。
 * <p>
 * 供前台项目/资源成员添加子弹窗选人用。不依赖后台 {@code knowhub:system:user:quarry} 系统 admin 接口
 * （后台接口含 role/email 等敏感字段，且无条件按钮权限普通创作者未必有）；
 * 本接口独立权限键 {@code knowhub:authoring:user-search}（默认分配给"实验室成员"角色，admin 自带）。
 * 后续"团队类型是否需特定权限才能创建"细化门槛再调整该权限落位。
 * <p>
 * 输出最小渲染集 userId/nickName/username/avatar/phoneNumber，不输出敏感字段。
 *
 * @author knowhub
 */
@Tag(name = "前台选人", description = "前台项目/资源成员添加子弹窗选人查询")
@RestController
@RequestMapping("/authoring/user")
public class AuthoringUserController {

    @Autowired
    private AuthoringUserService authoringUserService;

    @GetMapping("/search")
    @Operation(summary = "前台模糊查询活动用户（昵称/用户名/手机号 LIKE，分页）")
    @PreAuthorize("hasAuthority('knowhub:authoring:user-search')")
    public Result<PageInfo<AuthoringUserVo>> search(AuthoringUserSearchQuarry quarry) {
        PageInfo<AuthoringUserVo> page = authoringUserService.search(quarry);
        return Result.success(page);
    }
}