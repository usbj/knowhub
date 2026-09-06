package com.knowhub.config;

import com.rookie.common.util.SysConfigUtil;
import org.springframework.stereotype.Component;

/**
 * 前台门户配置读取器（照 BlogConfigReader 范式，业务侧不直接用 SysConfigUtil）。
 * SysConfigUtil 只读 Redis 永久缓存，编辑设置项时由 SysConfigServiceImpl 重写缓存。
 */
@Component
public class PortalConfigReader {

    /**
     * 系统设置键：前台分级推荐开关（BOOLEAN）。
     * false（默认）= 前台推荐/详情一律 level=1 二元闸（L2/L3 永不下发门户）；
     * true = 前台按登录用户实际 view 等级分级下发（level<=userViewLevel 阶梯闸）。
     * 默认关 = 公开门户语义不变、无越级风险；详见 doc/blog-portal-and-view-history-plan.md 决策#8。
     */
    public static final String CONFIG_KEY_HIERARCHICAL_ENABLED = "knowhub.portal.hierarchical.enabled";

    public boolean isHierarchicalEnabled() {
        try {
            return Boolean.TRUE.equals(SysConfigUtil.getBoolean(CONFIG_KEY_HIERARCHICAL_ENABLED, false));
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * 系统设置键：越级阅读锁预览长度（INT，字符数）。
     * 越级用户查看高等级作品时，正文前 N 个字符可见作预览，其后锁遮罩。
     * 默认 200 字符；0 = 不预览（回退到整篇锁 null 正文语义）。可由后台系统设置调。
     */
    public static final String CONFIG_KEY_LOCK_PREVIEW_LENGTH = "knowhub.portal.lock.preview.length";

    /** 越级阅读锁预览字符数（默认 200；配置缺失或异常回退 200）。 */
    public int getLockPreviewLength() {
        try {
            String raw = SysConfigUtil.getString(CONFIG_KEY_LOCK_PREVIEW_LENGTH, null);
            if (raw == null || raw.isBlank()) {
                return 200;
            }
            int len = Integer.parseInt(raw.trim());
            return len < 0 ? 0 : len;
        } catch (Exception e) {
            return 200;
        }
    }
}
