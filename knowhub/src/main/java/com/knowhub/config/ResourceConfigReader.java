package com.knowhub.config;

import com.rookie.common.util.SysConfigUtil;
import org.springframework.stereotype.Component;

/**
 * 资源全局开关读取收口。
 * 全项目唯一"知道资源审核开关从哪来"的地方：ResourceServiceImpl.publish 只调
 * {@link #isReviewEnabled()}，业务侧不直接使用 SysConfigUtil。
 *
 * 当前实现走系统设置 sys_config['knowhub.resource.review_enabled']（BOOLEAN 类型，
 * 见 sql/knowhub-resource.sql）。SysConfigUtil 只读 Redis 永久缓存，
 * 编辑设置项时由 SysConfigServiceImpl 重写缓存，运行时生效无需重启。
 * 将来若再换存储，仅需改本方法内部实现，签名与调用方零改动。
 * 结构与 {@link BlogConfigReader} 同构。
 */
@Component
public class ResourceConfigReader {

    /** 系统设置键：资源审核开关（BOOLEAN，true 开启审核 / false 直通发布） */
    public static final String CONFIG_KEY_REVIEW_ENABLED = "knowhub.resource.review_enabled";

    /**
     * 读取审核开关是否开启。
     * 取系统设置 configValue="true" 即视为开启；
     * 设置项缺失、停用、值类型不符或读取异常时默认关闭（直通发布），避免因配置缺失阻塞业务。
     *
     * @return true 需审核发布；false 直通发布
     */
    public boolean isReviewEnabled() {
        try {
            // SysConfigUtil.getBoolean 在缓存未命中/停用/类型不符时回落到默认值 false，保证发布流程不中断
            return Boolean.TRUE.equals(SysConfigUtil.getBoolean(CONFIG_KEY_REVIEW_ENABLED, false));
        } catch (Exception e) {
            // 缓存未加载等异常，降级为关闭，保证发布流程不中断
            return false;
        }
    }
}