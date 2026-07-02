package com.knowhub.config;

import com.knowhub.enums.FileBusinessType;
import com.rookie.common.pojo.entity.SysDictData;
import com.rookie.common.util.DictUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 文件存储配置读取收口。
 * 业务侧唯一"知道白名单/上限/桶策略从哪来"的地方：FileServiceImpl 只调本类方法，
 * 不直接使用 DictUtil。与 BlogConfigReader 同构，换存储/换阈值时只改本类内部实现。
 *
 * 当前实现：
 * - 体积上限、类型白名单走字典（file_size_limit / file_type_whitelist，dict_data_label=业务类型 code，
 *   dict_data_value=上限MB数 或 逗号分隔的扩展名/MIME），可后台改，复用 DictUtil 缓存。
 * - 桶策略、连接参数走 StorageProperties（@ConfigurationProperties，改 yml 需重启）。
 */
@Component
public class StorageConfigReader {

    /** 字典键：各业务类型体积上限（label=业务类型 code，value=MB 数） */
    public static final String DICT_KEY_SIZE_LIMIT = "file_size_limit";

    /** 字典键：各业务类型允许的扩展名/MIME 白名单（label=业务类型 code，value=逗号分隔） */
    public static final String DICT_KEY_TYPE_WHITELIST = "file_type_whitelist";

    /** 默认体积上限（MB），字典缺失时回退 */
    private static final long DEFAULT_SIZE_LIMIT_MB = 10;

    @Autowired
    private StorageProperties storageProperties;

    /**
     * 取某业务类型的体积上限（字节）。
     * 字典 dict_data_value 存 MB 数；缺省回退 DEFAULT_SIZE_LIMIT_MB。
     */
    public long sizeLimitBytes(FileBusinessType type) {
        try {
            List<SysDictData> data = DictUtil.getDictData(DICT_KEY_SIZE_LIMIT);
            if (data != null) {
                for (SysDictData d : data) {
                    if (type.getCode().equalsIgnoreCase(d.getDictDataLabel())) {
                        long mb = Long.parseLong(d.getDictDataValue().trim());
                        return mb * 1024L * 1024L;
                    }
                }
            }
        } catch (Exception ignored) {
            // 字典缓存未加载等异常，降级为默认值
        }
        return DEFAULT_SIZE_LIMIT_MB * 1024L * 1024L;
    }

    /**
     * 取某业务类型的类型白名单（小写扩展名或 MIME 列表）。
     * 字典 dict_data_value 存逗号分隔的值；缺省回退到通配（空列表表示不限制）。
     */
    public List<String> typeWhitelist(FileBusinessType type) {
        try {
            List<SysDictData> data = DictUtil.getDictData(DICT_KEY_TYPE_WHITELIST);
            if (data != null) {
                for (SysDictData d : data) {
                    if (type.getCode().equalsIgnoreCase(d.getDictDataLabel())) {
                        String value = d.getDictDataValue();
                        if (value == null || value.trim().isEmpty()) {
                            return Collections.emptyList();
                        }
                        return Arrays.stream(value.split(","))
                                .map(String::trim)
                                .filter(s -> !s.isEmpty())
                                .map(String::toLowerCase)
                                .collect(Collectors.toList());
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return Collections.emptyList();
    }

    /** 校验 contentType 是否落在该业务类型白名单内（白名单空表示不限制） */
    public boolean isContentTypeAllowed(FileBusinessType type, String contentType) {
        if (contentType == null || contentType.isEmpty()) {
            return false;
        }
        List<String> whitelist = typeWhitelist(type);
        if (whitelist.isEmpty()) {
            return true;
        }
        String lower = contentType.toLowerCase();
        // 白名单项可能是 MIME（image/png）或扩展名（.png）；MIME 全匹配，扩展名按 contentType 末尾比对
        for (String item : whitelist) {
            if (lower.equals(item) || lower.startsWith(item + ";")) {
                return true;
            }
            if (item.startsWith(".") && lower.endsWith(item.substring(1))) {
                // 宽松匹配：用扩展名兜底（contentType 推断不出扩展名时也放过）
                return true;
            }
        }
        return false;
    }

    /** PUBLIC 对象是否直拼公开读 URL（false 则也走预签名 GET） */
    public boolean publicBucketReadable() {
        return storageProperties.isPublicBucketReadable();
    }

    public StorageProperties getProperties() {
        return storageProperties;
    }
}
