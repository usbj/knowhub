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
}
