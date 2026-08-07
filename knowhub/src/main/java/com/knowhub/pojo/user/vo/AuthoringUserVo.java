package com.knowhub.pojo.user.vo;

import java.util.Date;

/**
 * 前台选人接口轻量 VO（/authoring/user/search 出参，仅选人用，不暴露其他 sys_user 敏感字段）。
 * <p>
 * 字段最小集：userId/nickName/username/avatar/phoneNumber；与后台 {@code SysUserVo} 区别在于只读返回用户选人所需
 * 渲染信息，不输出 role/status/email/sex/loginIp 等敏感或无关字段。 createTime 用于按注册时间兜底排序展示。
 *
 * @author knowhub
 */
public class AuthoringUserVo {

    /** 用户 ID */
    private Long userId;

    /** 昵称（搜索主要字段，列表主显示） */
    private String nickName;

    /** 用户名（登录名，搜索次要字段） */
    private String username;

    /** 头像 URL（前端列表行渲染头像） */
    private String avatar;

    /** 手机号（搜索字段，列表行展示便于认人） */
    private String phoneNumber;

    /** 创建时间（注册时间，列表兜底排序展示，不展示前端时忽略） */
    private Date createTime;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

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

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    @Override
    public String toString() {
        return "AuthoringUserVo{" +
                "userId=" + userId +
                ", nickName='" + nickName + '\'' +
                ", username='" + username + '\'' +
                '}';
    }
}