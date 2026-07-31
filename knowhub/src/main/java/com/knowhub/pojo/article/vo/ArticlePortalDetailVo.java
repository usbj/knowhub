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

    /** 章节大纲（章节名+排序，不含正文；越级时仍下发大纲，不泄正文） */
    private List<ChapterOutlineVo> chapterList;

    /** 是否越级锁态（true=无权看完整内容，章节正文接口也会锁态拒发） */
    private Boolean locked;

    /** 锁态原因提示（如"需 L2 权限查看完整内容"，正常态为 null） */
    private String lockReason;

    public List<ChapterOutlineVo> getChapterList() {
        return chapterList;
    }

    public void setChapterList(List<ChapterOutlineVo> chapterList) {
        this.chapterList = chapterList;
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