package com.knowhub.pojo.resource.vo;

/**
 * 前台资源详情 VO（继承 ResourcePortalVo，追加 description 详细说明 + 当前用户互动态 + FILE 下载链接）。
 * <p>
 * 供 GET /portal/resource/{resourceId} 出参：
 * - description：详细说明（支持 Markdown），仅详情接口下发，列表/搜索 SQL 不查此大字段。
 * - hasLiked/hasCollected/myScore：登录态回填当前用户互动态；未登录则不回填（前端隐藏互动按钮或跳登录）。
 * <p>
 * 详情不下发 FILE 下载链接：fileService.getDownloadUrl 对 PRIVATE 文件走 checkOwnerOrAdmin→currentUser() 强转
 * principal 为 UserInfo，而 /portal/resource/** 是 permitAll，匿名访问时 principal 是 "anonymousUser"（String），
 * 强转即 CCE。FILE 真实下载链接由用户点"下载资源"按钮现取：走 /authoring/resource/{id}/download
 * （isAuthenticated 兜底，principal 是 UserInfo，checkOwnerOrAdmin 安全），且带 download_count +1 业务语义。
 * <p>
 * 2026-08-18 权限大修：资源引入 level 分级，详情越级锁态 = locked=true（继承父类）+ lockReason + description 仍下发
 * （可见，对齐项目详情"越级 description 可见只锁下载"语义）+ FILE 锁下载（本就不在详情预填，保持）+ LINK 置空 linkUrl
 * （锁跳转）。非 PUBLISHED 资源前台根本不下发（详情查询返回 null，controller 404）。
 */
public class ResourcePortalDetailVo extends ResourcePortalVo {

    /** 详细说明（支持 Markdown，详情接口下发；越级时仍下发——对齐"越级 description 可见只锁下载/跳转"语义） */
    private String description;

    /** 越级锁原因（越级时 service 置"需 L{N} 权限..."；达权为 null） */
    private String lockReason;

    /** 当前用户是否已点赞（登录态回填，未登录为 null/false） */
    private Boolean hasLiked;

    /** 当前用户是否已收藏（登录态回填，未登录为 null/false） */
    private Boolean hasCollected;

    /** 当前用户评分（1-5；未评分为 null/0；登录态回填） */
    private Integer myScore;

    /** 评论区开关 1开/0关（见 comment 模块；详情接口 mapper 带出，前端据此渲染评论区开关态） */
    private Integer commentEnabled;

    /** 评论精选开关 0=新评论直接可见 / 1=新评论仅发表人+作者可见，作者同意展示后他人可见 */
    private Integer commentCurated;

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getLockReason() {
        return lockReason;
    }

    public void setLockReason(String lockReason) {
        this.lockReason = lockReason;
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

    public Integer getMyScore() {
        return myScore;
    }

    public void setMyScore(Integer myScore) {
        this.myScore = myScore;
    }

    public Integer getCommentEnabled() {
        return commentEnabled;
    }

    public void setCommentEnabled(Integer commentEnabled) {
        this.commentEnabled = commentEnabled;
    }

    public Integer getCommentCurated() {
        return commentCurated;
    }

    public void setCommentCurated(Integer commentCurated) {
        this.commentCurated = commentCurated;
    }

    @Override
    public String toString() {
        return "ResourcePortalDetailVo{" +
                "description=" + (description == null ? "null" : "[len=" + description.length() + "]") +
                ", hasLiked=" + hasLiked +
                ", hasCollected=" + hasCollected +
                ", myScore=" + myScore +
                "} " + super.toString();
    }
}