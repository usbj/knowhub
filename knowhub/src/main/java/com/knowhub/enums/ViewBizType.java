package com.knowhub.enums;

/**
 * 浏览明细业务类型，对应 user_view_history.biz_type 列（三模块共用事实表）。
 * String code 直接入库（不转数字），取值 BLOG/ARTICLE/CHAPTER/RESOURCE。
 */
public enum ViewBizType {

    BLOG("BLOG", "博客"),
    ARTICLE("ARTICLE", "文章"),
    CHAPTER("CHAPTER", "章节"),
    RESOURCE("RESOURCE", "资源");

    private final String code;

    private final String desc;

    ViewBizType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
