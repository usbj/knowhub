package com.knowhub.enums;

/**
 * 文件业务类型枚举。
 * 各业务模块上传文件时携带 businessType，后端据此选 objectKey 前缀、套用类型/大小白名单、
 * 决定默认 access。枚举值与字典 file_business_type 的 dict_data_value 一致，
 * 供前端下拉与后端校验共用（见 doc/storage/file-storage-module-design.md §1.2）。
 *
 * 默认 access：BLOG_COVER/BLOG_BODY 为 PUBLIC（博客配图公开读），其余为 PRIVATE。
 */
public enum FileBusinessType {

    /** 博客封面图：access=PUBLIC，类型图片，上限 5MB */
    BLOG_COVER("BLOG_COVER", "博客封面图", FileAccess.PUBLIC),

    /** 博客正文配图：access=PUBLIC，类型图片，上限 10MB */
    BLOG_BODY("BLOG_BODY", "博客正文配图", FileAccess.PUBLIC),

    /** 项目源码压缩包：access=PRIVATE，类型压缩包，上限 500MB */
    PROJECT_SRC("PROJECT_SRC", "项目源码压缩包", FileAccess.PRIVATE),

    /** 项目可执行包/安装包：access=PRIVATE，类型安装包，上限 500MB */
    PROJECT_PKG("PROJECT_PKG", "项目可执行包/安装包", FileAccess.PRIVATE),

    /** 项目大 Office 文档：access=PRIVATE，类型 docx/pptx/xlsx/pdf，上限 100MB */
    PROJECT_DOC("PROJECT_DOC", "项目大 Office 文档", FileAccess.PRIVATE),

    /** 资源模块文件资源：access=PRIVATE（资源模块定），通用，上限 100MB */
    RESOURCE_FILE("RESOURCE_FILE", "资源模块文件资源", FileAccess.PRIVATE),

    /** 插件市场插件包：access=PRIVATE，类型 .jar，上限 50MB */
    PLUGIN_JAR("PLUGIN_JAR", "插件市场插件包", FileAccess.PRIVATE),

    /** 文章封面图：access=PUBLIC，类型图片，上限 5MB（对齐 BLOG_COVER，biz_ref_id=article_id） */
    ARTICLE_COVER("ARTICLE_COVER", "文章封面图", FileAccess.PUBLIC);

    private final String code;

    private final String label;

    private final FileAccess defaultAccess;

    FileBusinessType(String code, String label, FileAccess defaultAccess) {
        this.code = code;
        this.label = label;
        this.defaultAccess = defaultAccess;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    public FileAccess getDefaultAccess() {
        return defaultAccess;
    }

    /** 按 code 安全解析枚举，未命中返回 null */
    public static FileBusinessType ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (FileBusinessType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}
