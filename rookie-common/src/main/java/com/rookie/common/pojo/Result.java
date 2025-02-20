package com.rookie.common.pojo;

import com.rookie.common.enums.ResultEnum;

public class Result<T> {

    private int code;

    private String msg;

    private T data;

    public Result() {
    }

    public Result(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
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

    public T getData() {
        return data;
    }

    public void setData(T data) {
        this.data = data;
    }

    public static<T> Result<T> error(int code, String msg, T data){
        return new Result<>(code,msg,data);
    }
    
    public static<T> Result<T> error(int code,String msg) {
        return error(code,msg,null);
    }

    public static<T> Result<T> error(ResultEnum result) {
        return error(result.getCode(),result.getMsg());
    }

    public static<T> Result<T> error() {
        return error(ResultEnum.COMMON_ERROR);
    }

    public static<T> Result<T> success(T data) {
        return new Result<>(200,"请求成功",data);
    }

}
