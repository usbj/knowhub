package com.knowhub.enums;

/**
 * 项目状态，对应字典 project_status 与 project.status 列。
 * 值同 resource_status 语义并新增 ARCHIVED 归档：DRAFT 草稿 / PUBLISHED 已发布 /
 * REVOKED 已撤回 / PENDING_REVIEW 待审核 / REJECTED 已驳回 / ARCHIVED 已归档。
 * ARCHIVED 为项目完结归档状态，仍可查看，仅状态标记（归档是状态变更，不算删除）。
 */
public enum ProjectStatus {

    DRAFT("DRAFT", "草稿"),
    PUBLISHED("PUBLISHED", "已发布"),
    REVOKED("REVOKED", "已撤回"),
    PENDING_REVIEW("PENDING_REVIEW", "待审核"),
    REJECTED("REJECTED", "已驳回"),
    ARCHIVED("ARCHIVED", "已归档");

    private final String code;

    private final String desc;

    ProjectStatus(String code, String desc) {
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
