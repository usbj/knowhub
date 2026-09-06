package com.knowhub.pojo.user.vo;

import java.util.Date;

/**
 * 用户公开主页 VO（/portal/user/{userId} 出参，游客可读，隐藏敏感字段）。
 * <p>
 * 与 {@link AuthoringUserVo} 区别：本 VO 面向用户主页公开展示，**不含 phoneNumber**
 * （手机号属隐私，不对游客暴露），仅输出主页横幅渲染所需的最小公开集：
 * userId/nickName/avatar/username/sex/createTime。
 * <p>
 * status/delete/password 等敏感字段一律不输出（SQL select 列限定 + resultMap 双重保险）。
 *
 * @author knowhub
 */
public class UserPortalVo {

    /** 用户 ID */
    private Long userId;

    /** 昵称（主页横幅主显示） */
    private String nickName;

    /** 用户名（登录名，主页横幅副显示） */
    private String username;

    /** 头像 URL（主页横幅头像） */
    private String avatar;

    /** 性别（sys_user_sex 字典：'0'男/'1'女/'3'未知，主页横幅可选展示） */
    private String sex;

    /** 创建时间（注册时间，主页横幅展示「加入于 xxx」） */
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

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }
}
