package com.knowhub.pojo.user.quarry;

/**
 * 前台选人搜索条件（GET /authoring/user/search 入参）。
 * <p>
 * 三字段皆可空：任一非空即按该字段 LIKE；三字段同时非空时 AND 复合过滤（照后台 SysUserListQuery 语义，
 * 但本接口仅 novice like 模式，不分 searchType）。 三字段皆空 → 全量走"最近注册兜底"分页。<br>
 * 与后台 {@code SysUserListQuery} 区别：不暴露 status/deptId/roleId/beginEndTime（不在前台用户选人范围），
 * 也只匹配未删/已启用的有效用户。
 *
 * @author knowhub
 */
public class AuthoringUserSearchQuarry {

    /** 昵称 LIKE */
    private String nickName;

    /** 用户名 LIKE */
    private String username;

    /** 手机号 LIKE */
    private String phoneNumber;

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
}