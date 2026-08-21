package com.knowhub.enums.resource;

/**
 * 资源等级，对应字典 resource_level 与 resource.level 列（tinyint 1/2/3）。
 * 与权限等级对标：拥有 knowhub:resource:lN（及更高）权限者可查看/下载 level ≤ N 的资源。
 *   L1=1 公开（默认） / L2=2 内部 / L3=3 机密（最高）。
 * 等级获取走 {@link com.knowhub.support.ResourcePermissionResolver}（一次扫描 List<Permission>
 * 取最高等级，admin 登录时全 perm_key 已塞入 perms，自然得 L3 全权）。
 * 与博客等级 BlogLevel、文章等级 ArticleLevel、项目等级 ProjectLevel 同义，资源模块独立定义以解耦。
 * <p>
 * 2026-08-18 权限大修：资源模块从零引入 level 分级（原先无等级概念，PUBLISHED 即全公开）。
 * 配套搜索范围 level<=userViewLevel+1（L1 搜 L1+L2 带 lock）、详情锁态（越级 description 可见、
 * FILE 锁下载、LINK 锁跳转）、创作闸（userView>=targetLevel 才能建）。
 */
public enum ResourceLevel {

    L1(1, "公开"),
    L2(2, "内部"),
    L3(3, "机密");

    private final int code;

    private final String desc;

    ResourceLevel(int code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public int getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
