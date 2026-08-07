package com.knowhub.enums.history;

/**
 * 浏览明细业务类型，对应 user_view_history.biz_type 列（多模块共用事实表）。
 * String code 直接入库（不转数字），取值 BLOG/ARTICLE/CHAPTER/RESOURCE/PROJECT。
 * <p>
 * 2026-08-03 增 PROJECT：项目前台门户详情计浏览量，回写 project.view_count（见 UserViewHistoryMapper.xml
 * incrementMainViewCount 的 PROJECT 分支 + ProjectPortalServiceImpl.getDetail 调 recordView）。
 */
public enum ViewBizType {

    BLOG("BLOG", "博客"),
    ARTICLE("ARTICLE", "文章"),
    CHAPTER("CHAPTER", "章节"),
    RESOURCE("RESOURCE", "资源"),
    PROJECT("PROJECT", "项目");

    private final String code;

    private final String desc;

    ViewBizType(String code, String desc) {
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
