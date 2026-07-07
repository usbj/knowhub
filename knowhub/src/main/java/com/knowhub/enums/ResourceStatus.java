package com.knowhub.enums;

/**
 * 资源状态，对应字典 resource_status 与 resource.status 列。
 * 值与 {@link BlogStatus} 相同但语义属资源，独立枚举避免资源后续加状态时改到博客枚举。
 * DRAFT 草稿 / PUBLISHED 已发布 / REVOKED 已撤回 / PENDING_REVIEW 待审核 / REJECTED 已驳回
 */
public enum ResourceStatus {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    REVOKED("REVOKED", "已撤回"),
    PENDING_REVIEW("PENDING_REVIEW", "待审核"),
    REJECTED("REJECTED", "已驳回");

    private final String code;

    private final String desc;

    ResourceStatus(String code, String desc) {
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
