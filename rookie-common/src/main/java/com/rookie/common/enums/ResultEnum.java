package com.rookie.common.enums;

public enum ResultEnum {

    COMMON_ERROR(500,"请求失败"),
    LOGIN_ERROR(401,"用户名或密码错误");


    private int code;

    private String msg;

    ResultEnum() {
    }

    ResultEnum(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public int getCode() {
        return code;
    }

    public void setCode(int code) {
        this.code = code;
    }

    public String getMsg() {
        return msg;
    }

    public void setMsg(String msg) {
        this.msg = msg;
    }
}
