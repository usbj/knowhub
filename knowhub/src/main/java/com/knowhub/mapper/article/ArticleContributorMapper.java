package com.knowhub.mapper.article;

import com.knowhub.pojo.article.entity.ArticleContributor;
import com.knowhub.pojo.article.quarry.ArticleContributorQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 文章贡献者申请/授权 Mapper。
 * - addContributor：提交一条申请（PENDING）
 * - getActiveByArticleAndUser：查 (articleId,userId) 当前的 ACTIVE 行（deleted=0），用于去重与重申检测
 * - isApproved：canEditArticle 第三放行分支判定（approved 贡献者可提交章节）
 * - getById：审批回填用
 * - listReceived / listMine：「我的协作」页两个 tab 的列表（service 注入 authorId/userId 过滤）
 * - updateStatus：accept/reject 改 status + 审计 + advice（拒绝时填 advice）
 * - softDeleteById：重申时把旧 REJECTED 行软删（避开 uk_article_contributor 冲突）
 */
@Mapper
public interface ArticleContributorMapper {

    /** 提交一条申请（apply_time/create_time 由 DB 默认 CURRENT_TIMESTAMP 填充） */
    Boolean addContributor(ArticleContributor c);

    /** 查 (articleId, userId) 的当前 ACTIVE 行（deleted=0），null 表示无须重申前处理 */
    ArticleContributor getActiveByArticleAndUser(Long articleId, Long userId);

    /** approved 贡献者放行判定：是否存在 status=APPROVED 且 deleted=0 的行 */
    Boolean isApproved(Long articleId, Long userId);

    /** 按主键查（含 deleted 字段，用于审批回填与防越权校验） */
    ArticleContributor getById(Long contributorId);

    /** 作者侧：列出该作者所有文章收到的申请（service 注入 articleAuthorId，按 apply_time 倒序） */
    List<ArticleContributor> listReceived(ArticleContributorQuarry quarry);

    /** 申请人侧：列出自已发过的申请（service 注入 userId，按 apply_time 倒序） */
    List<ArticleContributor> listMine(ArticleContributorQuarry quarry);

    /** accept/reject 改 status + 审计 + advice + handle_by + handle_time */
    Boolean updateStatus(ArticleContributor c);

    /** 软删（重申时把旧 REJECTED 行 deleted=1，避 uk_article_contributor 冲突） */
    Boolean softDeleteById(Long contributorId);
}