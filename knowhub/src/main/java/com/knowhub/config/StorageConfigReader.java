package com.knowhub.config;

import com.knowhub.enums.FileAccessMode;
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

    /** 字典键：文件访问模式（value=transfer/direct，见 FileAccessMode 枚举） */
    public static final String DICT_KEY_ACCESS_MODE = "file_access_mode";

    /** 字典键：直链模式对外暴露的 OSS 地址 base（value=nginx 公网反代域名或 OSS 公网 endpoint） */
    public static final String DICT_KEY_DIRECT_BASE_URL = "file_direct_base_url";

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

    /**
     * 文件访问模式：TRANSFER（中转）/ DIRECT（直链）。
     * <p>
     * 当前实现走字典 sys_dict['file_access_mode']，运维后台改、复用 DictUtil 缓存、运行时生效；
     * 字典缺失或读取异常时默认 TRANSFER（中转模式更通用，不依赖 OSS 公网可达 / CORS）。
     * <p>
     * 将来迁系统设置表时，仅需改本方法内部实现（直接读设置值而非遍历字典列表），签名与调用方零改动。
     */
    public FileAccessMode accessMode() {
        try {
            List<SysDictData> data = DictUtil.getDictData(DICT_KEY_ACCESS_MODE);
            if (data != null && !data.isEmpty()) {
                // 取首条数据项的 value 作为模式 code
                for (SysDictData d : data) {
                    FileAccessMode mode = FileAccessMode.ofCode(d.getDictDataValue());
                    if (mode != null) {
                        return mode;
                    }
                }
            }
        } catch (Exception ignored) {
            // 字典缓存未加载等异常，降级为中转模式
        }
        return FileAccessMode.TRANSFER;
    }

    /**
     * 直链模式对外暴露的 OSS 地址 base（如 nginx 公网反代域名 https://your-domain.com/rustfs，
     * 或 OSS 公网 endpoint）。直链模式下后端用它拼出给前端的直链 URL。
     * <p>
     * 当前实现走字典 sys_dict['file_direct_base_url']（单值，取首条数据项 value）；
     * 字典缺失或为空时回退到 storageProperties.endpoint（即与后端连 OSS 的内网地址相同——
     * 仅适用于用户与后端同网络段的场景，公网用户需配置此字典项指向可达的 nginx/OSS 公网地址）。
     * <p>
     * 将来迁系统设置表时，仅需改本方法内部实现，签名与调用方零改动。
     */
    public String directBaseUrl() {
        try {
            List<SysDictData> data = DictUtil.getDictData(DICT_KEY_DIRECT_BASE_URL);
            if (data != null && !data.isEmpty()) {
                for (SysDictData d : data) {
                    String v = d.getDictDataValue();
                    if (v != null && !v.trim().isEmpty()) {
                        return v.trim().replaceAll("/+$", "");
                    }
                }
            }
        } catch (Exception ignored) {
            // 字典缓存未加载等异常，降级为 yml endpoint
        }
        String endpoint = storageProperties.getEndpoint();
        return endpoint == null ? "" : endpoint.replaceAll("/+$", "");
    }

    public StorageProperties getProperties() {
        return storageProperties;
    }
}
