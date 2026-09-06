package com.knowhub.pojo.project.vo;

/**
 * 前台项目详情 VO（门户详情出参，照博客 {@code BlogPortalDetailVo}）。
 * <p>
 * 继承列表 VO 字段，加 description（详细介绍正文，越级锁态降级时置 null）+ 当前用户对该项目的
 * 下载权限态 {@code canDownload}（供前端控制文件树下载按钮显隐）。
 * 越级锁态：level>userViewLevel 时 locked=true，description=null，下 lockReason。
 *
 * @author knowhub
 */
public class ProjectPortalDetailVo extends ProjectPortalVo {

    /** 详细介绍（锁态时 null；查自 project.description mediumtext） */
    private String description;

    /** 越级锁态：true=不下发正文，前端按 lockReason 提示 */
    private Boolean locked;

    /** 锁态原因文案（如"需 L2 权限查看完整内容"） */
    private String lockReason;

    /** 当前用户对该项目的下载权限（系统 download:lN 够 OR 成员 can_download=1 OR LEADER；未登录=false） */
    private Boolean canDownload;

    /** 当前用户是否已收藏（登录态回填，未登录为 null；项目无点赞链路，仅收藏，对齐 ResourcePortalDetailVo.hasCollected） */
    private Boolean hasCollected;

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

    public Boolean getCanDownload() {
        return canDownload;
    }

    public void setCanDownload(Boolean canDownload) {
        this.canDownload = canDownload;
    }

    public Boolean getHasCollected() {
        return hasCollected;
    }

    public void setHasCollected(Boolean hasCollected) {
        this.hasCollected = hasCollected;
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
}