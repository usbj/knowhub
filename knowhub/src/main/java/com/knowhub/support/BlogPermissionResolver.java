package com.knowhub.support;

import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 博客权限等级解析器：一次扫描当前用户 perms，返回 view 操作的最高等级。
 * <p>
 * 系统权限（全局、分等级、所有博客）的 perm_key 形如：
 *   knowhub:blog:view:l1 / l2 / l3
 * 用户最高拥有等级 N = max(角色里勾到的 lN)；能查看 level ≤ N 的博客。
 * <p>
 * <b>编辑不再走等级</b>（2026-07-11 收紧）：博客编辑/发布/撤回仅「作者本人 OR 超级管理员」可操作，
 * 不扫 edit:lN 等级键，故本 resolver 只解析 view 一个操作。编辑判定在 service 层
 * {@code canEditBlog(blog) = isAuthor OR currentUser().isAdmin()}，admin 走 rookie 框架短路
 * （{@link UserInfo#isAdmin()} + AdminBypassExpressionRoot）放行，不依赖本 resolver。
 * <p>
 * 博客模块无成员表（轻量权限模型：仅系统级 view 等级 + 作者归属），故只需 view 一个操作。
 * 作者对自己的博客全权（查看不看等级），由 service 层 canOp 在系统权限判定外补
 * author_id==userId 分支，此处 resolver 只管系统 view 等级。
 * <p>
 * admin 零特判：rookie 登录时 admin 角色已把 sys_menu 全部启用按钮 perm_key 物理塞入
 * UserInfo.permissions（见 UserDetailServiceImpl.selectAllPermKey），其中含 view:l1/l2/l3
 * 三条，扫完 Math.max 自然收敛到 3，无需 isAdmin 判断。
 * <p>
 * 注意：UserInfo.getPermissions() 返回 List<Permission>（不是 Set<String>），
 * Permission 只有 permKey 字段。此处直接遍历 List 取 permKey，不转 Set（省 HashSet 构造），
 * 规避 BlogServiceImpl 里 List<Permission>.contains(String) 永远 false 的隐坑。
 * <p>
 * 纯内存计算（正则+取 max），无 IO/锁/Redis 调用，每次请求 service 入口调一次，开销可忽略。
 * <p>
 * 与 {@link ArticlePermissionResolver} 区别：后者仍保留 view/edit 两操作（文章编辑走等级），
 * 本类已去 edit 分支（博客编辑不再分等级）。
 */
public final class BlogPermissionResolver {

    private BlogPermissionResolver() {
    }

    /** 匹配 knowhub:blog:view:l([1-3])，捕获等级数字（编辑等级键已废，不再匹配） */
    private static final Pattern P =
            Pattern.compile("^knowhub:blog:view:l([1-3])$");

    /**
     * 解析当前登录用户的博客查看权限等级。
     * 一次扫描 perms，返回 view 最高等级（admin 自然得 3，无权限者得 0）。
     *
     * @return BlogPermissionLevel(view)；未登录/无 perms 返回 empty(0)
     */
    public static BlogPermissionLevel resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserInfo u)) {
            return BlogPermissionLevel.empty();
        }
        if (u.getPermissions() == null || u.getPermissions().isEmpty()) {
            return BlogPermissionLevel.empty();
        }
        int view = 0;
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
            view = Math.max(view, lvl);
        }
        return new BlogPermissionLevel(view);
    }

    /**
     * 博客权限等级（view 操作最高等级，0=无该系统权限）。
     * record 不可变，service 层调一次 resolve() 拿到对象后多处取用。
     * <p>
     * 仅 view 一个字段——编辑权限不再分等级（作者+admin 判定在 service 层，不取此值）。
     */
    public record BlogPermissionLevel(int view) {
        public static BlogPermissionLevel empty() {
            return new BlogPermissionLevel(0);
        }

        /** 按操作名取等级（仅 view 有效，未知操作返回 0；编辑不取此值，留 0 兜底） */
        public int levelOf(String op) {
            return "view".equals(op) ? view : 0;
        }
    }
}
