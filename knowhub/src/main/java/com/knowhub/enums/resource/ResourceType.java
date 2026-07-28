package com.knowhub.enums.resource;

/**
 * 资源类型，对应字典 resource_type 与 resource.resource_type 列。
 * FILE 文件类资源（走文件存储模块上传，关联 file_object.object_id）
 * LINK 链接类资源（只存外部 link_url，不涉及文件上传）
 * 程序/文档等细分是 FILE 的子分类，走 resource_category 分类树区分，不在本枚举展开。
 */
public enum ResourceType {

    FILE("FILE", "文件"),
    LINK("LINK", "链接");

    private final String code;

    private final String desc;

    ResourceType(String code, String desc) {
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
