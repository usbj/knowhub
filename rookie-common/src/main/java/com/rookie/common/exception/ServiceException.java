package com.rookie.common.exception;

import com.rookie.common.enums.ResultEnum;

public class ServiceException extends RuntimeException{

    private Integer code;

    private String message;

    private String errorMsg;

    public ServiceException() {
    }

    public ServiceException(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public ServiceException(Integer code, String message, String errorMsg) {
        this.code = code;
        this.message = message;
        this.errorMsg = errorMsg;
    }

    public ServiceException(ResultEnum resultEnum){
        this.code = resultEnum.getCode();
        this.message = resultEnum.getMsg();
    }

    public Integer getCode() {
        return code;
    }

    public void setCode(Integer code) {
        this.code = code;
    }

    @Override
    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }

    public String getErrorMsg() {
        return errorMsg;
    }

    public void setErrorMsg(String errorMsg) {
        this.errorMsg = errorMsg;
    }
}
