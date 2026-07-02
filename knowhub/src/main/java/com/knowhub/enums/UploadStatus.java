package com.knowhub.enums;

/**
 * 文件上传状态枚举（四态机，见设计稿 §3.2）。
 * PENDING：已签发上传令牌、前端尚未传完确认。
 * CONFIRMED：前端传完调 confirm，HeadObject 核对通过。
 * FAILED：confirm 时 HeadObject 不通过（对象不存在/类型不符/超限），或 PENDING 超 TTL 未确认。
 * GC：已软删，待定时任务 DeleteObject 清对象并物理删元数据。
 * 枚举值与字典 upload_status 的 dict_data_value 一致。
 */
public enum UploadStatus {

    PENDING("PENDING", "待确认"),

    CONFIRMED("CONFIRMED", "已确认"),

    FAILED("FAILED", "失败"),

    GC("GC", "待回收");

    private final String code;

    private final String label;

    UploadStatus(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static UploadStatus ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (UploadStatus s : values()) {
            if (s.code.equals(code)) {
                return s;
            }
        }
        return null;
    }
}
