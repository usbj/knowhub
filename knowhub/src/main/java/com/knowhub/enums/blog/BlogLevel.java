package com.knowhub.enums.blog;

/**
 * 博客等级，对应字典 blog_level 与 blog.level 列（tinyint 1/2/3）。
 * 与权限等级对标：拥有 view:lN（及更高）权限者可查看 level ≤ N 的博客。
 *   L1=1 公开（默认） / L2=2 内部 / L3=3 机密（最高）。
 * 等级获取走 BlogPermissionResolver（一次扫描 List<Permission> 取最高等级，
 * admin 登录时全 perm_key 已塞入 perms，自然得 L3 全权）。
 * 与文章等级 ArticleLevel、项目等级 ProjectLevel 同义，博客模块独立定义以解耦。
 */
public enum BlogLevel {

    L1(1, "公开"),
    L2(2, "内部"),
    L3(3, "机密");

    private final int code;

    private final String desc;

    BlogLevel(int code, String desc) {
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
