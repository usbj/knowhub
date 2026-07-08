package com.knowhub.enums;

/**
 * 项目成员角色，对应字典 project_member_role 与 project_member.member_role 列。
 * LEADER 负责人 / MENTOR 导师 / MEMBER 参与者。
 * LEADER 判定时强制全权（不看 can_view/can_download/can_edit 标志位），每项目仅一个；
 * MENTOR/MEMBER 默认标志位由 service 层按角色给（MENTOR→看+下、MEMBER→只看），可后续微调。
 */
public enum ProjectMemberRole {

    LEADER("LEADER", "负责人"),
    MENTOR("MENTOR", "导师"),
    MEMBER("MEMBER", "参与者");

    private final String code;

    private final String desc;

    ProjectMemberRole(String code, String desc) {
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
