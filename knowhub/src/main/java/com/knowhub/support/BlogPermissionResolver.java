package com.knowhub.support;

import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 博客权限等级解析器：一次扫描当前用户 perms，返回 view/edit 两操作各自最高等级。
 * <p>
 * 系统权限（全局、分等级、所有博客）的 perm_key 形如：
 *   knowhub:blog:view:l1 / l2 / l3
 *   knowhub:blog:edit:l1 / l2 / l3
 * 用户最高拥有等级 N = max(角色里勾到的 lN)；能对 level ≤ N 的博客执行对应操作。
 * <p>
 * 博客模块无成员表（轻量权限模型：仅系统级 + 作者归属），故只需 view/edit 两操作。
 * 作者对自己的博客全权（不看等级），由 service 层 canOp 在系统权限判定外补
 * author_id==userId 分支，此处 resolver 只管系统等级。
 * <p>
 * admin 零特判：rookie 登录时 admin 角色已把 sys_menu 全部启用按钮 perm_key 物理塞入
 * UserInfo.permissions（见 UserDetailServiceImpl.selectAllPermKey），其中含 view:l1/l2/l3
 * 三条，扫完 Math.max 自然收敛到 3/3，无需 isAdmin 判断。
 * <p>
 * 注意：UserInfo.getPermissions() 返回 List<Permission>（不是 Set<String>），
 * Permission 只有 permKey 字段。此处直接遍历 List 取 permKey，不转 Set（省 HashSet 构造），
 * 规避 BlogServiceImpl 里 List<Permission>.contains(String) 永远 false 的隐坑。
 * <p>
 * 纯内存计算（正则+取 max），无 IO/锁/Redis 调用，每次请求 service 入口调一次，开销可忽略。
 * <p>
 * 结构与 {@link ArticlePermissionResolver} 同构（去 download，博客无下载）。
 */
public final class BlogPermissionResolver {

    private BlogPermissionResolver() {
    }

    /** 匹配 knowhub:blog:(view|edit):l([1-3])，捕获操作名与等级数字 */
    private static final Pattern P =
            Pattern.compile("^knowhub:blog:(view|edit):l([1-3])$");

    /**
     * 解析当前登录用户的博客权限等级。
     * 一次扫描 perms，返回两操作各自最高等级（admin 自然得 3/3，无权限者得 0/0）。
     *
     * @return BlogPermissionLevel(view, edit)；未登录/无 perms 返回 empty(0,0)
     */
    public static BlogPermissionLevel resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserInfo u)) {
            return BlogPermissionLevel.empty();
        }
        if (u.getPermissions() == null || u.getPermissions().isEmpty()) {
            return BlogPermissionLevel.empty();
        }
        int view = 0, edit = 0;
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
            int lvl = Integer.parseInt(m.group(2));
            switch (m.group(1)) {
                case "view" -> view = Math.max(view, lvl);
                case "edit" -> edit = Math.max(edit, lvl);
            }
        }
        return new BlogPermissionLevel(view, edit);
    }

    /**
     * 博客权限等级（view/edit 两操作各自最高等级，0=无该系统权限）。
     * record 不可变，service 层调一次 resolve() 拿到对象后多处取用。
     */
    public record BlogPermissionLevel(int view, int edit) {
        public static BlogPermissionLevel empty() {
            return new BlogPermissionLevel(0, 0);
        }

        /** 按操作名取等级（view/edit），未知操作返回 0 */
        public int levelOf(String op) {
            return switch (op) {
                case "view" -> view;
                case "edit" -> edit;
                default -> 0;
            };
        }
    }
}
