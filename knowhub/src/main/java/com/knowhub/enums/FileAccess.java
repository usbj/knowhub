package com.knowhub.enums;

/**
 * 文件访问语义枚举。
 * PUBLIC：公开读（博客配图、Markdown 内插图），取用走 GET /file/public/{id} 302 重定向。
 * PRIVATE：私有（项目源码包、私有资源），取用走 GET /file/download/{id} 鉴权后下发预签名。
 * 枚举值与字典 file_access 的 dict_data_value 一致。
 */
public enum FileAccess {

    PUBLIC("PUBLIC", "公开"),

    PRIVATE("PRIVATE", "私有");

    private final String code;

    private final String label;

    FileAccess(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public static FileAccess ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (FileAccess a : values()) {
            if (a.code.equals(code)) {
                return a;
            }
        }
        return null;
    }
}
