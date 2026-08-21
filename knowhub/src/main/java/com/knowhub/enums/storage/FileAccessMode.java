package com.knowhub.enums.storage;

/**
 * 文件访问模式枚举（双模式，见 doc/knowhub-api.md「文件访问模式」）。
 * <p>
 * 控制后端签发给前端的链接形态——前端永远只认后端给的链接，地址完全由后端决定，
 * 迁移 OSS / 切换部署拓扑时只改后端配置，前端代码与 nginx 相对路径都不用动。
 * <p>
 * TRANSFER（中转模式）：后端给的链接是同源后端中转接口（/file/public/{id}、/file/proxy/{id}、
 *   /file/proxy-upload/{objectId}），由后端用 s3Client 拉取/写入 OSS 字节流并回写/转发。
 *   适用于：① OSS 在内网、用户浏览器不可达；② 不愿配 OSS CORS；③ 后端扛文件流量可接受。
 * <p>
 * DIRECT（直链模式）：后端给的链接是直链地址（{directBaseUrl}/{bucket}/{objectKey}，
 *   PUBLIC 回显走公开读直链不带签名；上传/PRIVATE 下载走预签名绝对 URL），前端直连该地址。
 *   该地址通常是 nginx 代理的公网域名（nginx 反代到内网 OSS），故外网用户可达且无需 OSS CORS
 *   （nginx 同域反代不跨域）。适用于：希望后端不经文件字节流、OSS 经 nginx 公网反代暴露。
 * <p>
 * LOCAL（本地存储模式）：后端不连 OSS，文件直接落本地磁盘（storage.local.base-path 根目录，
 *   相对路径即 objectKey，与 S3 目录结构对齐）。上传走后端中转接口 /file/local-upload/{id}（@RequestBody
 *   byte[] 收字节写盘）；访问走中转接口形态——PUBLIC 回显 /file/public/{id}、PRIVATE 下载 /file/proxy/{id}，
 *   后端用本地后端读盘回写。无预签名概念。适用于：单机部署 / 不部署 OSS / 开发演示。
 *   ⚠️ 切换到 LOCAL 只影响新上传，历史 OSS 数据不自动迁移——切前建议先用「文件管理→打包下载」
 *   把历史 OSS 数据下载到本地目录再切换。
 * <p>
 * 枚举值与 sys_config 的 knowhub.file.access_mode 配置项值一致（transfer/direct/local）；
 */
public enum FileAccessMode {

    /** 中转模式：链接指向后端中转接口，后端代理读写 OSS 字节流 */
    TRANSFER("transfer", "中转模式"),

    /** 直链模式：链接指向 nginx 代理/直连 OSS 地址，前端直连 */
    DIRECT("direct", "直链模式"),

    /** 本地存储模式：文件落本地磁盘，访问走后端中转读盘（无预签名） */
    LOCAL("local", "本地存储模式");

    private final String code;

    private final String label;

    FileAccessMode(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /** 按 code 安全解析枚举，未命中返回 null */
    public static FileAccessMode ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (FileAccessMode m : values()) {
            if (m.code.equalsIgnoreCase(code)) {
                return m;
            }
        }
        return null;
    }
}
