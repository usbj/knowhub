package com.knowhub.mapper;

import com.knowhub.pojo.entity.ChapterReviewLog;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 章节作者审核流水 Mapper（仅 SEMIPUBLIC 文章场景触发）。
 * 照搬 ArticleReviewLogMapper 结构（被审对象换 chapter_id）。
 * 仅 insert（追加）+ 按 chapter_id 查历史（升序，供章节审核时间线展示）。
 * 流水表不改不删，故无 update/delete 方法。
 * action 仅 SUBMIT/APPROVE/REJECT 三值；role 取 AUTHOR(章节提交者)/REVIEWER(文章作者审)。
 */
@Mapper
public interface ChapterReviewLogMapper {

    /** 追加一条章节审核流水（动作时间由 DB 默认 CURRENT_TIMESTAMP 填充） */
    Boolean insertReviewLog(ChapterReviewLog log);

    /** 按章节ID查审核历史（按动作时间升序，还原轨迹） */
    List<ChapterReviewLog> listByChapterId(Long chapterId);
}
