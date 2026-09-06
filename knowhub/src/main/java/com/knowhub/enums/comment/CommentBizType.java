package com.knowhub.enums.comment;

/**
 * 评论业务类型，对应 comment.biz_type 列。
 * BLOG/ARTICLE/PROJECT/RESOURCE 四类作品共用评论表。
 * 不复用 {@link com.knowhub.enums.history.ViewBizType}（后者含 CHAPTER 不含 PROJECT，语义不同）。
 * String code 直接入库（不进字典），取值固定四项。
 */
public enum CommentBizType {

    BLOG("BLOG", "博客"),
    ARTICLE("ARTICLE", "文章"),
    PROJECT("PROJECT", "项目"),
    RESOURCE("RESOURCE", "资源");

    private final String code;

    private final String desc;

    CommentBizType(String code, String desc) {
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