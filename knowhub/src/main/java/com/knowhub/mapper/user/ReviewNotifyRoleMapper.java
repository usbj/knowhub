package com.knowhub.mapper.user;

import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 提审通知角色→用户解析 Mapper（只读 sys_user_role + sys_role + sys_user）。
 * <p>
 * 与 {@link AuthoringUserMapper} 同套路：不依赖 rookie-system 的 SysRoleMapper/SysUserRoleMapper，
 * 只读 sys_* 表不算违规。仅输出 user_id（不输出敏感字段）。
 *
 * @author knowhub
 */
@Mapper
public interface ReviewNotifyRoleMapper {

    /**
     * 按角色 role_key 查所有拥有该角色且 sys_role/sys_user 双方有效（status=1, delete=0）的用户 userId。
     * join sys_user_role + sys_role + sys_user 三表过滤，剔除停用/已删账号与角色。
     */
    List<Long> listActiveUserIdsByRoleKey(String roleKey);
}