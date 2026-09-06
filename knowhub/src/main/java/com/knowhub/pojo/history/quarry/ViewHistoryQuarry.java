package com.knowhub.pojo.history.quarry;

/**
 * 浏览历史分页查询条件，作为 Mapper parameterType 与列表接口入参。
 * userId 由 service 层从登录态强制回填（前端不可传）；bizType 可选过滤。
 * pageNum/pageSize 由 PageUtil 从请求读取（与 BlogQuarry 范式一致，此处不声明）。
 */
public class ViewHistoryQuarry {

    /** 当前用户ID（service 层回填，非前端入参） */
    private Long userId;

    /** 业务类型可选过滤 BLOG/ARTICLE/CHAPTER/RESOURCE */
    private String bizType;

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getBizType() {
        return bizType;
    }

    public void setBizType(String bizType) {
        this.bizType = bizType;
    }
}
