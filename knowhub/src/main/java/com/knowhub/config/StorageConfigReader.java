package com.knowhub.config;

import com.knowhub.enums.storage.FileAccessMode;
import com.knowhub.enums.storage.FileBusinessType;
import com.rookie.common.util.SysConfigUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 文件存储配置读取收口。
 * 业务侧唯一"知道白名单/上限/桶策略从哪来"的地方：FileServiceImpl 只调本类方法，
 * 不直接使用 SysConfigUtil。与 BlogConfigReader 同构，换存储/换阈值时只改本类内部实现。
 *
 * 当前实现（已从字典迁到系统设置 sys_config）：
 * - 体积上限、类型白名单走系统设置 JSON 设置项（knowhub.file.size_limit /
 *   knowhub.file.type_whitelist），值是「业务类型 code → 上限 MB 数 / 逗号分隔白名单」的对象，
 *   可后台改，复用 SysConfigUtil 缓存。
 * - 文件访问模式、直链 OSS 地址 base 走系统设置 STRING 设置项
 *   （knowhub.file.access_mode / knowhub.file.direct_base_url）。
 * - 本地模式根目录走 StorageProperties.localBasePath（yml storage.local.base-path，改需重启），
 *   切本地模式前需确保该目录已配且历史 OSS 数据已下载到本地。
 * - 桶策略、连接参数走 StorageProperties（@ConfigurationProperties，改 yml 需重启）。
 *
 * SysConfigUtil 只读 Redis 永久缓存，编辑设置项时由 SysConfigServiceImpl 重写缓存，
 * 运行时生效无需重启；设置项缺失/停用/类型不符时回落默认值，不阻塞业务。
 */
@Component
public class StorageConfigReader {

    /** 系统设置键：文件访问模式（STRING，transfer 中转 / direct 直链，见 FileAccessMode 枚举） */
    public static final String CONFIG_KEY_ACCESS_MODE = "knowhub.file.access_mode";

    /** 系统设置键：直链模式对外暴露的 OSS 地址 base（STRING，nginx 公网反代域名或 OSS 公网 endpoint） */
    public static final String CONFIG_KEY_DIRECT_BASE_URL = "knowhub.file.direct_base_url";

    /** 系统设置键：各业务类型体积上限（JSON 对象，key=业务类型 code，value=MB 数） */
    public static final String CONFIG_KEY_SIZE_LIMIT = "knowhub.file.size_limit";

    /** 系统设置键：各业务类型允许的扩展名/MIME 白名单（JSON 对象，key=业务类型 code，value=逗号分隔） */
    public static final String CONFIG_KEY_TYPE_WHITELIST = "knowhub.file.type_whitelist";

    /** 默认体积上限（MB），设置项缺失或业务类型未配置时回退 */
    private static final long DEFAULT_SIZE_LIMIT_MB = 10;

    @Autowired
    private StorageProperties storageProperties;

    /**
     * 取某业务类型的体积上限（字节）。
     * 系统设置 knowhub.file.size_limit 是 JSON 对象，按 type.code 取对应 MB 数；
     * 业务类型未配置、值无法解析为数字或设置项缺失时回退 DEFAULT_SIZE_LIMIT_MB。
     *
     * @param type 文件业务类型
     * @return 体积上限字节数
     */
    public long sizeLimitBytes(FileBusinessType type) {
        try {
            // 反序列化为 Map，Hutool 把 JSON 数值解析为 Integer/Long/BigDecimal 等 Number 子类，统一用 longValue() 取值防 ClassCastException
            Map<String, Object> map = SysConfigUtil.getObject(CONFIG_KEY_SIZE_LIMIT, Map.class, Collections.emptyMap());
            if (map != null) {
                Object value = map.get(type.getCode());
                if (value instanceof Number) {
                    long mb = ((Number) value).longValue();
                    return mb * 1024L * 1024L;
                }
                // 兼容 value 为字符串数字的情况（理论上 JSON 数值不会是字符串，兜底防御）
                if (value instanceof String) {
                    String s = ((String) value).trim();
                    if (!s.isEmpty()) {
                        return Long.parseLong(s) * 1024L * 1024L;
                    }
                }
            }
        } catch (Exception ignored) {
            // 设置项缓存未加载 / JSON 解析失败等异常，降级为默认值
        }
        return DEFAULT_SIZE_LIMIT_MB * 1024L * 1024L;
    }

    /**
     * 取某业务类型的类型白名单（小写扩展名或 MIME 列表）。
     * 系统设置 knowhub.file.type_whitelist 是 JSON 对象，按 type.code 取逗号分隔的值；
     * 业务类型未配置或值为空时回退到空列表（空列表表示不限制），设置项缺失亦回落空列表。
     *
     * @param type 文件业务类型
     * @return 小写扩展名/MIME 列表，空列表表示不限制
     */
    public List<String> typeWhitelist(FileBusinessType type) {
        try {
            Map<String, Object> map = SysConfigUtil.getObject(CONFIG_KEY_TYPE_WHITELIST, Map.class, Collections.emptyMap());
            if (map != null) {
                Object value = map.get(type.getCode());
                if (value == null) {
                    return Collections.emptyList();
                }
                String s = value.toString();
                if (s.trim().isEmpty()) {
                    return Collections.emptyList();
                }
                return Arrays.stream(s.split(","))
                        .map(String::trim)
                        .filter(x -> !x.isEmpty())
                        .map(String::toLowerCase)
                        .collect(Collectors.toList());
            }
        } catch (Exception ignored) {
            // 设置项缓存未加载 / JSON 解析失败等异常，降级为空列表（不限制）
        }
        return Collections.emptyList();
    }

    /**
     * 校验 contentType 是否落在该业务类型白名单内（白名单空表示不限制）。
     * <p>
     * 仅按 contentType 匹配 MIME 项；扩展名项（.docx 等）因浏览器对 office 类文件给出的
     * contentType 形如 application/vnd.openxmlformats-officedocument.wordprocessingml.document，
     * 与扩展名末尾不一致，无法用 contentType 兜底，请改用
     * {@link #isContentTypeAllowed(FileBusinessType, String, String)} 传文件名比对。
     */
    public boolean isContentTypeAllowed(FileBusinessType type, String contentType) {
        return isContentTypeAllowed(type, contentType, null);
    }

    /**
     * 校验 contentType / 文件名是否落在该业务类型白名单内（白名单空表示不限制）。
     * <p>
     * 与前端 upload-whitelist.ts 同口径：
     * - MIME 项（含 /）：contentType 全匹配或以 {item}; 开头；
     * - 扩展名项（带不带前导点都兜底）：按 originalName 末尾扩展名比对（case-insensitive），
     *   避开浏览器对 office 等文件 contentType 与扩展名不一致导致误拦的问题。
     * originalName 为 null/空时退化为仅按 contentType 匹配 MIME 项（旧调用点兼容口径）。
     */
    public boolean isContentTypeAllowed(FileBusinessType type, String contentType, String originalName) {
        if ((contentType == null || contentType.isEmpty()) && (originalName == null || originalName.isEmpty())) {
            return false;
        }
        List<String> whitelist = typeWhitelist(type);
        if (whitelist.isEmpty()) {
            return true;
        }
        String lower = contentType == null ? "" : contentType.toLowerCase();
        String lowerName = originalName == null ? "" : originalName.toLowerCase();
        for (String item : whitelist) {
            if (item.contains("/")) {
                // MIME 项：按 contentType 匹配（含分号参数情形）
                if (lower.equals(item) || lower.startsWith(item + ";")) {
                    return true;
                }
            } else {
                // 扩展名项（带不带前导点都兜底）：按文件名末尾比对
                String ext = item.startsWith(".") ? item : "." + item;
                if (lowerName.endsWith(ext)) {
                    return true;
                }
            }
        }
        return false;
    }

    /** PUBLIC 对象是否直拼公开读 URL（false 则也走预签名 GET） */
    public boolean publicBucketReadable() {
        return storageProperties.isPublicBucketReadable();
    }

    /**
     * 文件访问模式：TRANSFER（中转）/ DIRECT（直链）/ LOCAL（本地存储）。
     * <p>
     * 当前实现走系统设置 knowhub.file.access_mode（STRING，transfer/direct/local），
     * 运维后台改、复用 SysConfigUtil 缓存、运行时生效；
     * 设置项缺失、停用或读取异常时默认 TRANSFER（中转模式更通用，不依赖 OSS 公网可达 / CORS）。
     * <p>
     * 将来若再换存储，仅需改本方法内部实现，签名与调用方零改动。
     *
     * @return 文件访问模式枚举
     */
    public FileAccessMode accessMode() {
        try {
            String code = SysConfigUtil.getString(CONFIG_KEY_ACCESS_MODE, FileAccessMode.TRANSFER.getCode());
            FileAccessMode mode = FileAccessMode.ofCode(code);
            if (mode != null) {
                return mode;
            }
        } catch (Exception ignored) {
            // 设置项缓存未加载等异常，降级为中转模式
        }
        return FileAccessMode.TRANSFER;
    }

    /**
     * 直链模式对外暴露的 OSS 地址 base（如 nginx 公网反代域名 https://your-domain.com/rustfs，
     * 或 OSS 公网 endpoint）。直链模式下后端用它拼出给前端的直链 URL。
     * <p>
     * 当前实现走系统设置 knowhub.file.direct_base_url（STRING 单值）；
     * 设置项缺失或为空时回退到 storageProperties.endpoint（即与后端连 OSS 的内网地址相同——
     * 仅适用于用户与后端同网络段的场景，公网用户需配置此设置项指向可达的 nginx/OSS 公网地址）。
     * <p>
     * 将来若再换存储，仅需改本方法内部实现，签名与调用方零改动。
     *
     * @return 去掉末尾斜杠的 OSS 地址 base
     */
    public String directBaseUrl() {
        try {
            String v = SysConfigUtil.getString(CONFIG_KEY_DIRECT_BASE_URL, null);
            if (v != null && !v.trim().isEmpty()) {
                return v.trim().replaceAll("/+$", "");
            }
        } catch (Exception ignored) {
            // 设置项缓存未加载等异常，降级为 yml endpoint
        }
        String endpoint = storageProperties.getEndpoint();
        return endpoint == null ? "" : endpoint.replaceAll("/+$", "");
    }

    public StorageProperties getProperties() {
        return storageProperties;
    }

    /**
     * 本地存储模式根目录（access_mode=local 时文件落盘于此）。
     * <p>
     * 走 StorageProperties.localBasePath（yml storage.local.base-path，默认 ./knowhub-upload），
     * 改需重启。本地后端 LocalStorageBackend 直接 @Value 读同一 key，本方法供打包下载落盘等场景按统一入口取。
     *
     * @return 本地存储根目录路径
     */
    public String localBasePath() {
        String path = storageProperties.getLocalBasePath();
        return path != null && !path.isEmpty() ? path : "./knowhub-upload";
    }
}
