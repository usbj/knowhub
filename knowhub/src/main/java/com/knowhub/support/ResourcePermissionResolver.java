package com.knowhub.support;

import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 资源权限等级解析器：一次扫描当前用户 perms，返回最高等级（单键，不分 view/edit/download 操作维度）。
 * <p>
 * 系统权限（全局、分等级、所有资源）的 perm_key 形如：
 *   knowhub:resource:l1 / l2 / l3
 * 用户最高拥有等级 N = max(角色里勾到的 lN)；能查看/下载 level ≤ N 的资源，能创作 level ≤ N 的资源。
 * <p>
 * <b>编辑/删除不分等级</b>：资源编辑/发布/撤回/删除仅「作者本人 OR 超级管理员」可操作，不扫 edit:lN 等级键。
 * 编辑判定在 service 层 {@code canEditResource(resource) = isAuthor OR currentUser().isAdmin()}，
 * admin 走 rookie 框架短路（{@link UserInfo#isAdmin()} + AdminBypassExpressionRoot）放行，不依赖本 resolver。
 * <p>
 * <b>下载走等级</b>：下载需 {@code resolve().level() >= resource.level OR isAuthor OR isAdmin}，
 * 等级不够抛"无权下载该资源（等级不足）"。
 * <p>
 * admin 零特判：rookie 登录时 admin 角色已把 sys_menu 全部启用按钮 perm_key 物理塞入
 * UserInfo.permissions（见 UserDetailServiceImpl.selectAllPermKey），其中含 knowhub:resource:l1/l2/l3
 * 三条，扫完 Math.max 自然收敛到 3，无需 isAdmin 判断。
 * <p>
 * 注意：UserInfo.getPermissions() 返回 List<Permission>（不是 Set<String>），
 * Permission 只有 permKey 字段。此处直接遍历 List 取 permKey，不转 Set（省 HashSet 构造），
 * 规避 ResourceServiceImpl 旧 checkOwnerOrAdmin 里 List<Permission>.contains(String) 永远 false 的隐坑。
 * <p>
 * 纯内存计算（正则+取 max），无 IO/锁/Redis 调用，每次请求 service 入口调一次，开销可忽略。
 * <p>
 * 2026-08-18 权限大修：资源模块从零引入分级，本 resolver 与 {@link BlogPermissionResolver} 同构
 * （单等级 record，正则 ^knowhub:resource:l([1-3])$）。
 */
public final class ResourcePermissionResolver {

    private ResourcePermissionResolver() {
    }

    /** 匹配 knowhub:resource:l([1-3])，捕获等级数字（单键，无 view/edit/download 操作维度） */
    private static final Pattern P =
            Pattern.compile("^knowhub:resource:l([1-3])$");

    /**
     * 解析当前登录用户的资源权限等级。
     * 一次扫描 perms，返回最高等级（admin 自然得 3，无权限者得 0）。
     *
     * @return ResourcePermissionLevel(level)；未登录/无 perms 返回 empty(0)
     */
    public static ResourcePermissionLevel resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserInfo u)) {
            return ResourcePermissionLevel.empty();
        }
        if (u.getPermissions() == null || u.getPermissions().isEmpty()) {
            return ResourcePermissionLevel.empty();
        }
        int level = 0;
        // 直接遍历 List<Permission> 取 permKey，不转 Set（省构造，规避 contains(String) 隐坑）
        for (Permission perm : u.getPermissions()) {
            String key = perm.getPermKey();
            if (key == null) {
                continue;
            }
            Matcher m = P.matcher(key);
            if (!m.matches()) {
                continue;
            }
            int lvl = Integer.parseInt(m.group(1));
            level = Math.max(level, lvl);
        }
        return new ResourcePermissionLevel(level);
    }

    /**
     * 资源权限等级（最高等级，0=无该系统权限）。
     * record 不可变，service 层调一次 resolve() 拿到对象后多处取用。
     * <p>
     * 单 level 字段——编辑/删除不取此值（作者+admin 判定在 service 层），
     * view/download/创作闸取此值与资源 level 比较。
     */
    public record ResourcePermissionLevel(int level) {
        public static ResourcePermissionLevel empty() {
            return new ResourcePermissionLevel(0);
        }
    }
}
