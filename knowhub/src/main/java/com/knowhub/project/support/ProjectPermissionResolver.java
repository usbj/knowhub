package com.knowhub.project.support;

import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 项目权限等级解析器：一次扫描当前用户 perms，返回 view/download/edit 三操作各自最高等级。
 * <p>
 * 系统权限（全局、分等级、所有项目）的 perm_key 形如：
 *   knowhub:project:view:l1 / l2 / l3
 *   knowhub:project:download:l1 / l2 / l3
 *   knowhub:project:edit:l1 / l2 / l3
 * 用户最高拥有等级 N = max(角色里勾到的 lN)；能对 level ≤ N 的项目执行对应操作。
 * <p>
 * admin 零特判：rookie 登录时 admin 角色已把 sys_menu 全部启用按钮 perm_key 物理塞入
 * UserInfo.permissions（见 UserDetailServiceImpl.selectAllPermKey），其中含 view:l1/l2/l3
 * 三条，扫完 Math.max 自然收敛到 3/3/3，无需 isAdmin 判断（UserInfo 本身也没暴露 isAdmin）。
 * <p>
 * 同时持有 l1+l2 都能获取：循环里 Math.max 累积，两条 perm 都在 List 里，最终取最高不覆盖。
 * <p>
 * 注意：UserInfo.getPermissions() 返回 List<Permission>（不是 Set<String>），
 * Permission 只有 permKey 字段。此处直接遍历 List 取 permKey，不转 Set（省 HashSet 构造），
 * 规避现有 BlogServiceImpl/ResourceServiceImpl 里 List<Permission>.contains(String) 永远 false 的隐坑。
 * <p>
 * 纯内存计算（正则+取 max），无 IO/锁/Redis 调用，每次请求 service 入口调一次，开销 0.1–0.6ms
 * （n=100–200 perms），相对后续 DB IO 可忽略，不需要缓存。
 */
public final class ProjectPermissionResolver {

    private ProjectPermissionResolver() {
    }

    /** 匹配 knowhub:project:(view|download|edit):l([1-3])，捕获操作名与等级数字 */
    private static final Pattern P =
            Pattern.compile("^knowhub:project:(view|download|edit):l([1-3])$");

    /**
     * 解析当前登录用户的项目权限等级。
     * 一次扫描 perms，返回三操作各自最高等级（admin 自然得 3/3/3，无权限者得 0/0/0）。
     *
     * @return ProjectPermissionLevel(view, download, edit)；未登录/无 perms 返回 empty(0,0,0)
     */
    public static ProjectPermissionLevel resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserInfo u)) {
            return ProjectPermissionLevel.empty();
        }
        if (u.getPermissions() == null || u.getPermissions().isEmpty()) {
            return ProjectPermissionLevel.empty();
        }
        int view = 0, download = 0, edit = 0;
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
                case "download" -> download = Math.max(download, lvl);
                case "edit" -> edit = Math.max(edit, lvl);
            }
        }
        return new ProjectPermissionLevel(view, download, edit);
    }

    /**
     * 项目权限等级（view/download/edit 三操作各自最高等级，0=无该系统权限）。
     * record 不可变，service 层调一次 resolve() 拿到对象后多处取用。
     */
    public record ProjectPermissionLevel(int view, int download, int edit) {
        public static ProjectPermissionLevel empty() {
            return new ProjectPermissionLevel(0, 0, 0);
        }

        /** 按操作名取等级（view/download/edit），未知操作返回 0 */
        public int levelOf(String op) {
            return switch (op) {
                case "view" -> view;
                case "download" -> download;
                case "edit" -> edit;
                default -> 0;
            };
        }
    }
}
