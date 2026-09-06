package com.knowhub.pojo.article.quarry;

/**
 * 文章贡献者申请列表查询参数（分页由 PageUtil.startPage() 从请求读 pageNum/pageSize，无需在此声明）。
 * 业务过滤：
 * - listReceived：service 注入 articleAuthorId=当前用户 userId（我作为哪些文章的作者，收到哪些申请）；
 *                 可选 status 过滤。
 * - listMine：service 注入 userId=当前用户 userId（我申请过哪些文章的贡献），可选 status 过滤。
 * 照 ChapterQuarry 口径，纯 POJO + 显式 getter/setter。
 */
public class ArticleContributorQuarry {

    /** 列出收到的申请时=当前文章作者 userId */
    private Long articleAuthorId;

    /** 列出我的申请时=当前用户 userId */
    private Long userId;

    /** 可选状态过滤（PENDING/APPROVED/REJECTED） */
    private String status;

    public ArticleContributorQuarry() {
    }

    public Long getArticleAuthorId() {
        return articleAuthorId;
    }

    public void setArticleAuthorId(Long articleAuthorId) {
        this.articleAuthorId = articleAuthorId;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public String toString() {
        return "ArticleContributorQuarry{" +
                "articleAuthorId=" + articleAuthorId +
                ", userId=" + userId +
                ", status='" + status + '\'' +
                '}';
    }
}