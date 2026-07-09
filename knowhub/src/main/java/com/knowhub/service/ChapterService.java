package com.knowhub.service;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.quarry.ChapterQuarry;
import com.knowhub.pojo.vo.ChapterReviewLogVo;
import com.knowhub.pojo.vo.ChapterReviewVo;
import com.knowhub.pojo.vo.ChapterVo;

import java.util.List;

/**
 * 章节管理 Service（章节 ≈ 博客，正文走主表不分表）。
 * 章节可见性 = 文章可见性（章节不分等级）。章节提交状态机由文章 visibility + 提交者是否作者决定：
 * - 作者本人提交（任意 visibility）   DRAFT → PUBLISHED 免审
 * - 非作者提交 PRIVATE 文章             拒绝提交（无章节编辑权）
 * - 非作者提交 SEMIPUBLIC 文章          DRAFT → PENDING_AUTHOR_REVIEW → 作者审 → PUBLISHED/REJECTED
 * - 非作者提交 PUBLIC 文章              DRAFT → PUBLISHED 免审
 * 章节作者审核（仅 SEMIPUBLIC）走 chapter_review_log，不受系统审核开关影响（visibility 固有机制）。
 * 章节编辑权限：章节作者 OR 文章作者 OR 系统编辑权限够（edit:lN≥文章level）。
 * PUBLISHED 禁编须先 revoke 再改（对齐博客/文章 PUBLISHED 禁编范式）。
 */
public interface ChapterService {

    /** 列表查询（按 articleId 过滤；权限过滤：能看文章即看 PUBLISHED 章节，非 PUBLISHED 仅章节作者/文章作者可见） */
    PageInfo<ChapterVo> quarryChapter(ChapterQuarry quarry);

    /** 详情（含 content 大字段；二次权限校验章节可见性，回填 canEdit/canReview） */
    ChapterVo getChapterInfo(Long chapterId);

    /** 新增/提交章节（按 visibility + 提交者是否作者决定状态机分支，作者提交免审，半公开非作者提交进待作者审） */
    Boolean submitChapter(ChapterVo vo);

    /** 编辑章节（状态机前置 PUBLISHED 禁编 + canEdit 校验） */
    Boolean editChapterInfo(ChapterVo vo);

    /** 删除章节（章节作者或文章作者或 delete 权限） */
    Boolean deleteChapterInfo(Long[] chapterIds);

    /** 发布/提交章节（submitChapter 的显式语义别名，前端"提交"按钮调；同 submitChapter 逻辑） */
    Boolean publishChapter(Long chapterId);

    /** 撤回章节（仅 PUBLISHED 可撤回 → REVOKED，写流水 REVOKE） */
    Boolean revokeChapter(Long chapterId);

    /** 章节作者审核（仅 PENDING_AUTHOR_REVIEW 可审，审核人必须是文章作者，写流水 APPROVE/REJECT） */
    Boolean reviewChapter(ChapterReviewVo vo);

    /** 章节审核历史流水（按时间升序） */
    List<ChapterReviewLogVo> listReviewLog(Long chapterId);
}
