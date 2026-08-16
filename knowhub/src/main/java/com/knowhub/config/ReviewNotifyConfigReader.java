package com.knowhub.config;

import com.rookie.common.util.SysConfigUtil;
import org.springframework.stereotype.Component;

/**
 * 审核通知角色读取收口。
 * <p>
 * 全项目唯一"知道提审通知配置从哪来"的地方。作品进入 PENDING_REVIEW 时（四模块共用），
 * ReviewNotifyService 只调本类的 {@link #isNotifyEnabled()} + {@link #getNotifyRoleKey()}，
 * 业务侧不直接使用 SysConfigUtil。
 *
 * <p>当前实现走系统设置 sys_config 两项：
 * <ul>
 *   <li>{@code knowhub.review.notify_enabled}（BOOLEAN，默认 false）：总开关。关 → 不通知任何人；
 *       开 → 按 notify_role_key 找到拥有该角色的有效用户 list 后逐一发站内通知。</li>
 *   <li>{@code knowhub.review.notify_role_key}（STRING，默认 "admin"）：被通知角色的 role_key。
 *       持有该 role_key 且 sys_role/sys_user 双方有效（status=1 / delete=0）的用户都会收到提审通知。</li>
 * </ul>
 * SysConfigUtil 只读 Redis 永久缓存，编辑设置项时由 SysConfigServiceImpl 重写缓存，
 * 运行时生效无需重启。结构与 {@link BlogConfigReader} 同构。
 *
 * <p>降级策略：配置缺失、停用、类型不符或读取异常时——总开关默认关（不发，避免配置缺失即打扰），
 * role_key 默认 "admin"（最常见的审核角色兜底）。配置项 status=0 停用时 isEnabled 自动降级。
 */
@Component
public class ReviewNotifyConfigReader {

    /** 系统设置键：提审通知总开关（BOOLEAN，true 开 / false 关） */
    public static final String CONFIG_KEY_NOTIFY_ENABLED = "knowhub.review.notify_enabled";

    /** 系统设置键：提审通知角色 role_key（STRING，持有该 role_key 的有效用户都收通知） */
    public static final String CONFIG_KEY_NOTIFY_ROLE_KEY = "knowhub.review.notify_role_key";

    /** role_key 兜底默认值（配置缺失/异常时用；与 sys_role 内置 role_key='admin' 对齐） */
    private static final String DEFAULT_ROLE_KEY = "admin";

    /**
     * 提审通知总开关是否开启。
     * 缺失/停用/异常默认关，避免配置未配置时即打扰用户。
     */
    public boolean isNotifyEnabled() {
        try {
            return Boolean.TRUE.equals(SysConfigUtil.getBoolean(CONFIG_KEY_NOTIFY_ENABLED, false));
        } catch (Exception e) {
            // 缓存未加载等异常，降级为关闭
            return false;
        }
    }

    /**
     * 提审通知角色 role_key（非空）。缺失/异常回落 "admin"。
     * 调用方拿到 role_key 后，自行经 mapper join sys_user_role/sys_role/sys_user
     * 解析成有效用户 userId 列表后 for-loop 发通知。
     */
    public String getNotifyRoleKey() {
        try {
            String rk = SysConfigUtil.getString(CONFIG_KEY_NOTIFY_ROLE_KEY, DEFAULT_ROLE_KEY);
            return (rk == null || rk.trim().isEmpty()) ? DEFAULT_ROLE_KEY : rk.trim();
        } catch (Exception e) {
            return DEFAULT_ROLE_KEY;
        }
    }
}