package com.knowhub.pojo.blog.vo;

/**
 * 前台博客详情 VO（继承 BlogPortalVo，追加 content + 锁态字段）。
 * <p>
 * 供 GET /portal/blog/{blogId} 出参：
 * - 正常（blog.level<=userViewLevel）：locked=false、content 下发完整正文、lockReason=null。
 * - 越级（blog.level>userViewLevel，分级开关关时 userViewLevel 恒视 1）：locked=true、content 置空（不下发只字正文）、
 *   lockReason="需 L{N} 权限查看完整正文"；其余元数据字段正常带出。
 * 详见 doc/blog-portal-and-view-history-plan.md 决策#9。
 */
public class BlogPortalDetailVo extends BlogPortalVo {

    /** 正文（越级锁态时置空，不下发只字正文） */
    private String content;

    /** 是否越级锁态（true=无权看完整正文，只给元数据） */
    private Boolean locked;

    /** 锁态原因提示（如"需 L2 权限查看完整正文"，正常态为 null） */
    private String lockReason;

    /** 博客等级（1/2/3，service 判越级锁态用；meta 查询带出，前台可据此提示内容等级） */
    private Integer level;

    /** 当前用户是否已点赞（登录态回填，未登录为 null；对齐 ResourcePortalDetailVo.hasLiked） */
    private Boolean hasLiked;

    /** 当前用户是否已收藏（登录态回填，未登录为 null；对齐 ResourcePortalDetailVo.hasCollected） */
    private Boolean hasCollected;

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public Boolean getLocked() {
        return locked;
    }

    public void setLocked(Boolean locked) {
        this.locked = locked;
    }

    public String getLockReason() {
        return lockReason;
    }

    public void setLockReason(String lockReason) {
        this.lockReason = lockReason;
    }

    public Integer getLevel() {
        return level;
    }

    public void setLevel(Integer level) {
        this.level = level;
    }

    public Boolean getHasLiked() {
        return hasLiked;
    }

    public void setHasLiked(Boolean hasLiked) {
        this.hasLiked = hasLiked;
    }

    public Boolean getHasCollected() {
        return hasCollected;
    }

    public void setHasCollected(Boolean hasCollected) {
        this.hasCollected = hasCollected;
    }
}
