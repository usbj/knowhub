package com.knowhub.mapper;

import com.knowhub.pojo.entity.UserViewHistory;
import com.knowhub.pojo.quarry.ViewHistoryQuarry;
import com.knowhub.pojo.vo.ViewHistoryVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 统一浏览明细 Mapper（user_view_history 三模块共用事实表）。
 * 防刷语义：recordView 先查 (user_id,biz_type,biz_id) 命中行——命中则只累加 viewCount，
 * 未命中才 INSERT（service 据此对主表 view_count +1 记独立访客）。
 */
@Mapper
public interface UserViewHistoryMapper {

    /** 查命中行（UNIQUE(user_id,biz_type,biz_id)），命中返回该行，未命中返回 null */
    UserViewHistory getByUserBiz(@Param("userId") Long userId,
                                 @Param("bizType") String bizType,
                                 @Param("bizId") Long bizId);

    /** 新增一条浏览记录（首次访问该内容），view_count 默认 1 */
    Boolean insertViewHistory(UserViewHistory record);

    /** 命中行累加 view_count +1 并刷新 last_view_time（主表不动，刷不动） */
    Boolean incrementViewCount(@Param("viewId") Long viewId);

    /** 当前用户浏览历史分页（join blog/article/resource 带出标题封面），入参含 userId + 可选 bizType */
    List<ViewHistoryVo> listViewHistory(ViewHistoryQuarry quarry);

    /** 删单条（校验 user_id 归属） */
    Boolean deleteViewById(@Param("viewId") Long viewId, @Param("userId") Long userId);

    /** 清空当前用户全部历史 */
    Boolean clearByUser(@Param("userId") Long userId);

    /**
     * 主表 view_count +1（记独立访客，仅 recordView 首次 INSERT 时调用一次）。
     * 按 bizType 分发表名：BLOG→blog / ARTICLE→article / CHAPTER→chapter / RESOURCE→resource。
     * 主键列名各表不同（blog_id/article_id/chapter_id/resource_id），故用 ${} 拼表名+主键列。
     * bizType 由 service 层校验过枚举值，无注入风险。
     */
    Boolean incrementMainViewCount(@Param("bizType") String bizType, @Param("bizId") Long bizId);
}
