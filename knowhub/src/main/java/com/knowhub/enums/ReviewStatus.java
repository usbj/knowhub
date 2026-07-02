package com.knowhub.enums;

/**
 * 博客审核状态，对应字典 review_status 与 blog.review_status 列。
 * NONE 无 / PENDING 待审 / APPROVED 通过 / REJECTED 驳回
 */
public enum ReviewStatus {

    NONE("NONE", "无"),
    PENDING("PENDING", "待审"),
    APPROVED("APPROVED", "通过"),
    REJECTED("REJECTED", "驳回");

    private final String code;

    private final String desc;

    ReviewStatus(String code, String desc) {
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
