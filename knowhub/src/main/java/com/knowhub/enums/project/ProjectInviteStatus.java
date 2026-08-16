package com.knowhub.enums.project;

/**
 * 项目成员邀请状态，对应 project_invite.status 列。
 * 项目邀请流程：负责人发起邀请（PENDING）→ 受邀人同意（ACCEPTED，写入 project_member）或拒绝（REJECTED）。
 * 三态枚举不入字典（前端 inline 映射文案，照 ArticleContributorStatus 口径）：
 *   PENDING  待回应         受邀人尚未处理
 *   ACCEPTED 已加入         受邀人同意，project_member 已写入 MEMBER 行
 *   REJECTED 已拒绝         受邀人婉拒，记录留档不写成员表
 */
public enum ProjectInviteStatus {

    PENDING("PENDING", "待回应"),
    ACCEPTED("ACCEPTED", "已加入"),
    REJECTED("REJECTED", "已拒绝");

    private final String code;

    private final String desc;

    ProjectInviteStatus(String code, String desc) {
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