package com.knowhub.service.user.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.user.quarry.AuthoringUserSearchQuarry;
import com.knowhub.pojo.user.vo.AuthoringUserVo;

/**
 * 前台选人 Service（/authoring/user/search）。
 * <p>
 * 轻量接口供前台项目成员添加子弹窗选人用，不依赖后台 {@code knowhub:system:user:quarry} 系统 admin 接口口径
 * （后台接口走 SysUserVo 含 role/email 等敏感字段，前台选人无需）。只输出 userId/nickName/username/avatar/
 * phoneNumber/createTime 最小渲染集。权限键 {@code knowhub:authoring:user-search}（默认分配给"实验室成员"角色）。
 *
 * @author knowhub
 */
public interface AuthoringUserService {

    /** 分页模糊查询活跃用户（delete=0 AND status=1），按 create_time desc 排序兜底 */
    PageInfo<AuthoringUserVo> search(AuthoringUserSearchQuarry quarry);
}