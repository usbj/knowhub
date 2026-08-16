package com.knowhub.pojo.article.vo;

import java.util.List;

/**
 * 前台文章详情 VO（继承 ArticlePortalVo，追加章节大纲 + 锁态字段）。照搬 BlogPortalDetailVo 范式。
 * <p>
 * 供 GET /portal/article/{articleId} 出参：
 * - 正常（article.level<=userViewLevel）：locked=false、chapterList 下发完整章节大纲（点章节跳阅读页拉正文）、lockReason=null。
 * - 越级（article.level>userViewLevel，分级开关关时 userViewLevel 恒视 1）：locked=true、lockReason="需 L{N} 权限查看完整内容"、
 *   chapterList 仍下发（章节大纲只是章节名，不含正文），但正文走章节接口时也会锁态（不泄整章正文）；越级不计浏览量。
 * 文章正文不在 article 主表（正文在 chapter 表），详情只给大纲，每章正文按需走 /portal/article/{id}/chapter/{chapterId}。
 */
public class ArticlePortalDetailVo extends ArticlePortalVo {

    /** 文章内部可见性 PRIVATE未公开/SEMIPUBLIC半公开/PUBLIC全公开（与 level 正交；前台不按 visibility 过滤可见性，
     *  仅用于前端判断是否显「申请成为贡献者」按钮——PRIVATE 文章非作者不可贡献章节，申请无意义，按钮隐藏） */
    private String visibility;

    /** 章节大纲（章节名+排序，不含正文；越级时仍下发大纲，不泄正文） */
    private List<ChapterOutlineVo> chapterList;

    /** 是否越级锁态（true=无权看完整内容，章节正文接口也会锁态拒发） */
    private Boolean locked;

    /** 锁态原因提示（如"需 L2 权限查看完整内容"，正常态为 null） */
    private String lockReason;

    /** 当前用户是否已点赞（登录态回填，未登录为 null；对齐 ResourcePortalDetailVo.hasLiked） */
    private Boolean hasLiked;

    /** 当前用户是否已收藏（登录态回填，未登录为 null；对齐 ResourcePortalDetailVo.hasCollected） */
    private Boolean hasCollected;

    /** 评论区开关 1开/0关（见 comment 模块；详情接口 mapper 带出，前端据此渲染评论区开关态） */
    private Integer commentEnabled;

    /** 评论精选开关 0=新评论直接可见 / 1=新评论仅发表人+作者可见，作者同意展示后他人可见 */
    private Integer commentCurated;

    public List<ChapterOutlineVo> getChapterList() {
        return chapterList;
    }

    public void setChapterList(List<ChapterOutlineVo> chapterList) {
        this.chapterList = chapterList;
    }

    public String getVisibility() {
        return visibility;
    }

    public void setVisibility(String visibility) {
        this.visibility = visibility;
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