package com.rookie.system.pojo.quarry;


import java.util.Date;

public class UserQuarry {

    private String username;

    private  String nickName;

    private String phoneNumber;

    private Integer status;

    private Date beginTime;

    private Date endTime;

    public UserQuarry() {
    }

    public UserQuarry(String username, String nickName, String phoneNumber, Integer status, Date beginTime, Date endTime) {
        this.username = username;
        this.nickName = nickName;
        this.phoneNumber = phoneNumber;
        this.status = status;
        this.beginTime = beginTime;
        this.endTime = endTime;
    }

    public String getUsername() {
        return username;
    }

    public void setUsername(String username) {
        this.username = username;
    }

    public String getNickName() {
        return nickName;
    }

    public void setNickName(String nick_name) {
        this.nickName = nick_name;
    }

    public String getPhoneNumber() {
        return phoneNumber;
    }

    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Date getBeginTime() {
        return beginTime;
    }

    public void setBeginTime(Date beginTime) {
        this.beginTime = beginTime;
    }

    public Date getEndTime() {
        return endTime;
    }

    public void setEndTime(Date endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "UserQuarry{" +
                "username='" + username + '\'' +
                ", nick_name='" + nickName + '\'' +
                ", phoneNumber='" + phoneNumber + '\'' +
                ", status=" + status +
                ", beginTime=" + beginTime +
                ", endTime=" + endTime +
                '}';
    }
}
