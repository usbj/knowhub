package com.knowhub.pojo.article.vo;

/**
 * 章节正文 VO（GET /portal/article/{articleId}/chapter/{chapterId} 出参）。
 * <p>
 * - 达权（文章 level<=userViewLevel 且 chapter status=PUBLISHED）：locked=false、content 下发整章 markdown、previewContent=null。
 * - 越级（文章 level>userViewLevel）：locked=true、content 置空、previewContent 下发前 N 字符作预览（默认 200，0=不预览）、
 *   lockReason="需 L{N} 权限查看完整内容"，越级不计章节浏览量（与文章详情越级锁态同构，防 L2/L3 内部机密泄公网）。
 */
public class ChapterContentVo {

    private Long chapterId;

    private Long articleId;

    private String chapterName;

    private Integer sortOrder;

    /** 章节正文 markdown（越级锁态时置空，不下发完整正文；达权时下发整章） */
    private String content;

    /**
     * 越级预览正文（越级锁态时下发前 N 字符，N 由 PortalConfigReader.getLockPreviewLength 控制，默认 200）。
     * 达权时为 null。前端 locked=true 时渲染 previewContent + 锁遮罩。
     */
    private String previewContent;

    /** 是否越级锁态 */
    private Boolean locked;

    /** 锁态原因提示（正常态为 null） */
    private String lockReason;

    public Long getChapterId() {
        return chapterId;
    }

    public void setChapterId(Long chapterId) {
        this.chapterId = chapterId;
    }

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public String getChapterName() {
        return chapterName;
    }

    public void setChapterName(String chapterName) {
        this.chapterName = chapterName;
    }

    public Integer getSortOrder() {
        return sortOrder;
    }

    public void setSortOrder(Integer sortOrder) {
        this.sortOrder = sortOrder;
    }

    public String getContent() {
        return content;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public String getPreviewContent() {
        return previewContent;
    }

    public void setPreviewContent(String previewContent) {
        this.previewContent = previewContent;
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
}