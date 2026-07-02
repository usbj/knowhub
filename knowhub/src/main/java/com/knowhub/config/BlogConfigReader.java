package com.knowhub.config;

import com.rookie.common.pojo.entity.SysDictData;
import com.rookie.common.util.DictUtil;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 博客全局开关读取收口。
 * 全项目唯一"知道审核开关从哪来"的地方：BlogServiceImpl.publish 只调
 * {@link #isReviewEnabled()}，业务侧不直接使用 DictUtil。
 *
 * 当前实现走字典 sys_dict['blog_review_enabled']（见 doc/README.dev.md「全局开关落地约定」）。
 * 将来若切到系统设置表，仅需改本方法内部实现，签名与调用方零改动。
 */
@Component
public class BlogConfigReader {

    /** 字典键：博客审核开关 */
    public static final String DICT_KEY_REVIEW_ENABLED = "blog_review_enabled";

    /**
     * 读取审核开关是否开启。
     * 取字典 dict_data_value="true" 的数据项存在即视为开启；
     * 字典缺失或读取异常时默认关闭（直通发布），避免因配置缺失阻塞业务。
     */
    public boolean isReviewEnabled() {
        try {
            List<SysDictData> data = DictUtil.getDictData(DICT_KEY_REVIEW_ENABLED);
            if (data == null || data.isEmpty()) {
                return false;
            }
            return data.stream()
                    .anyMatch(d -> "true".equalsIgnoreCase(d.getDictDataValue()));
        } catch (Exception e) {
            // 字典缓存未加载等异常，降级为关闭，保证发布流程不中断
            return false;
        }
    }
}
