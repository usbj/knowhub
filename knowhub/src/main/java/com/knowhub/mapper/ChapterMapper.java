package com.knowhub.mapper;

import com.knowhub.pojo.entity.Chapter;
import com.knowhub.pojo.quarry.ChapterQuarry;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 章节表 Mapper（≈博客，正文走主表不分表）。
 * 列表查询不带 content 大字段（避免拖列表），详情接口单独查 content。
 * 列表带 author_nickname(join sys_user on author_id) + article_title/article_visibility/
 * article_level/article_author_id(join article on article_id) 供前端展示与权限判定。
 * 权限过滤透传 userViewLevel/userId/articleAuthorId（章节可见性=文章可见性）。
 */
@Mapper
public interface ChapterMapper {

    /**
     * 列表查询（PageHelper 在 Service 层 startPage 拦截）。不带 content 大字段。
     * 权限过滤（章节可见性=文章可见性）：
     *   能看文章(level<=userViewLevel OR article_author_id=userId) 即可看其 PUBLISHED 章节；
     *   非 PUBLISHED 章节(DRAFT/PENDING_AUTHOR_REVIEW/REJECTED/REVOKED) 仅章节作者 OR 文章作者可见：
     *     chapter.author_id=userId OR article_author_id=userId
     * 按 sort_order asc, chapter_id asc 排序（章节文档顺序）。
     */
    List<Chapter> quarryChapter(ChapterQuarry quarry);

    /** 详情：按主键取未删除章节（带 content 大字段 + join article/sys_user 带出展示与权限字段） */
    Chapter getChapterInfoById(Long chapterId);

    /** 新增章节，回填主键 */
    Boolean addChapter(Chapter chapter);

    /** 编辑章节（动态列） */
    Boolean editChapterInfo(Chapter chapter);

    /** 软删章节 */
    Boolean softDeleteChapter(Long chapterId);

    /** 软删某文章下所有章节（删文章时事务内级联调用） */
    Boolean softDeleteByArticleId(Long articleId);
}
