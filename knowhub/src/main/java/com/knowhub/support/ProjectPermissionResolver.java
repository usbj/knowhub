package com.knowhub.support;

import com.rookie.framework.security.pojo.Permission;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 项目权限等级解析器：一次扫描当前用户 perms，返回最高等级（单键，不分 view/edit/download 操作维度）。
 * <p>
 * 系统权限（全局、分等级、所有项目）的 perm_key 形如：
 *   knowhub:project:l1 / l2 / l3
 * 用户最高拥有等级 N = max(角色里勾到的 lN）；能查看/下载 level ≤ N 的项目，能创作 level ≤ N 的项目。
 * <p>
 * <b>编辑不走系统等级分支</b>（2026-08-18 权限大修去 edit:lN 分级）：项目编辑 = LEADER OR 作者 OR
 * 成员 can_edit=1 OR admin，非成员不能靠系统等级编辑，必须被邀请成成员才能编辑。故本 resolver 不再
 * 解析 edit 等级，编辑判定在 service 层 canOp(project,"edit") 不取本值。view/download 仍走系统等级
 * （搜索/阅读锁/下载闸用 resolve().level() 与项目 level 比较）。
 * <p>
 * 项目模块有成员表（project_member，can_view/can_download/can_edit 标志位 + memberRole），
 * 成员权限与系统等级短路 OR 叠加（"成为成员就按成员权限算不论等级"）。作者=创建者默认 LEADER 全权。
 * <p>
 * admin 零特判：rookie 登录时 admin 角色已把 sys_menu 全部启用按钮 perm_key 物理塞入
 * UserInfo.permissions（见 UserDetailServiceImpl.selectAllPermKey），其中含 knowhub:project:l1/l2/l3
 * 三条，扫完 Math.max 自然收敛到 3，无需 isAdmin 判断（UserInfo 本身也没暴露 isAdmin）。
 * <p>
 * 同时持有 l1+l2 都能获取：循环里 Math.max 累积，两条 perm 都在 List 里，最终取最高不覆盖。
 * <p>
 * 注意：UserInfo.getPermissions() 返回 List<Permission>（不是 Set<String>），
 * Permission 只有 permKey 字段。此处直接遍历 List 取 permKey，不转 Set（省 HashSet 构造），
 * 规避现有 BlogServiceImpl/ResourceServiceImpl 里 List<Permission>.contains(String) 永远 false 的隐坑。
 * <p>
 * 纯内存计算（正则+取 max），无 IO/锁/Redis 调用，每次请求 service 入口调一次，开销 0.1–0.6ms
 * （n=100–200 perms），相对后续 DB IO 可忽略，不需要缓存。
 * <p>
 * 2026-08-18 权限大修：perm_key 从 knowhub:project:view:lN + download:lN + edit:lN 合并为单键
 * knowhub:project:lN，本 resolver 正则改为 ^knowhub:project:l([1-3])$，record 从
 * ProjectPermissionLevel(view,download,edit) 改为单 level。与 {@link BlogPermissionResolver} /
 * {@link ArticlePermissionResolver} / {@link ResourcePermissionResolver} 同构。
 */
public final class ProjectPermissionResolver {

    private ProjectPermissionResolver() {
    }

    /** 匹配 knowhub:project:l([1-3])，捕获等级数字（单键，无 view/edit/download 操作维度） */
    private static final Pattern P =
            Pattern.compile("^knowhub:project:l([1-3])$");

    /**
     * 解析当前登录用户的项目权限等级。
     * 一次扫描 perms，返回最高等级（admin 自然得 3，无权限者得 0）。
     *
     * @return ProjectPermissionLevel(level)；未登录/无 perms 返回 empty(0)
     */
    public static ProjectPermissionLevel resolve() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !(auth.getPrincipal() instanceof UserInfo u)) {
            return ProjectPermissionLevel.empty();
        }
        if (u.getPermissions() == null || u.getPermissions().isEmpty()) {
            return ProjectPermissionLevel.empty();
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
        return new ProjectPermissionLevel(level);
    }

    /**
     * 项目权限等级（最高等级，0=无该系统权限）。
     * record 不可变，service 层调一次 resolve() 拿到对象后多处取用。
     * <p>
     * 单 level 字段——编辑/成员管理不取此值（LEADER/作者/成员can_edit/admin 判定在 service 层），
     * view/download/创作闸取此值与项目 level 比较。
     */
    public record ProjectPermissionLevel(int level) {
        public static ProjectPermissionLevel empty() {
            return new ProjectPermissionLevel(0);
        }
    }
}
