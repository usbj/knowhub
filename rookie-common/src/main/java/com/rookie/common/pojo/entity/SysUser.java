package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

public class SysUser extends BaseEntity {

    private Long userId;

    private String username;

    private String password;

    private String nickName;

    private String sex;

    private String phoneNumber;

    private String avatar;

    private Integer status;

    private Integer delete;

    public SysUser() {
    }

    public SysUser(Date createTime, Date updateTime, String createBy, String updateBy, Long userId, String username, String password, String nickName, String sex, String phoneNumber, String avatar, Integer status, Integer delete) {
        super(createTime, updateTime, createBy, updateBy);
        this.userId = userId;
        this.username = username;
        this.password = password;
        this.nickName = nickName;
        this.sex = sex;
        this.phoneNumber = phoneNumber;
        this.avatar = avatar;
        this.status = status;
        this.delete = delete;
    }


    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nickName) {
        this.nickName = nickName;
    }

    public String getSex() {
        return sex;
    }

    public void setSex(String sex) {
        this.sex = sex;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public String getAvatar() {
        return avatar;
    }

    public void setAvatar(String avatar) {
        this.avatar = avatar;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelete() {
        return delete;
    }

    public void setDelete(Integer delete) {
        this.delete = delete;
    }

    @Override
    public String toString() {
        return "UserEntity{" +
                "userId=" + userId +
                ", username='" + username + '\'' +
                ", password='" + password + '\'' +
                ", nickName='" + nickName + '\'' +
                ", sex='" + sex + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", avatar='" + avatar + '\'' +
                ", status=" + status +
                ", createTime=" + getCreateTime() +
                ", updateTime=" + getUpdateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                ", updateBy='" + getUpdateBy() + '\'' +
                ", delete=" + delete +
                '}';
    }
}
