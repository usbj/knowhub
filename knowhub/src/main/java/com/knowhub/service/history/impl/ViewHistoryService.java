package com.knowhub.service.history.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.history.vo.ViewHistoryVo;

/**
 * 统一浏览历史 Service（user_view_history 三模块共用事实表）。
 * 接口只暴露 DTO，不暴露实体。
 */
public interface ViewHistoryService {

    /**
     * 记录一次浏览（详情接口在确认登录态后、返回前调）。
     * 事务内：查 (user_id,biz_type,biz_id) 命中行——命中累加 viewCount（主表不动）；
     * 未命中 INSERT 一行并对主表 view_count +1（记独立访客，刷不动）。
     * 未登录态不调本方法（直接返回不计数，见第二条链路决策#3）。
     *
     * @param userId  登录用户ID（由各详情 ServiceImpl 从 currentUser 传入）
     * @param bizType 业务类型（ViewBizType 枚举 code）
     * @param bizId   业务ID
     */
    void recordView(Long userId, String bizType, Long bizId);

    /** 当前用户浏览历史分页（userId 由 service 内部从 currentUser 取） */
    PageInfo<ViewHistoryVo> listHistory(String bizType);

    /** 删单条（userId 由 service 内部取，校验归属） */
    Boolean deleteHistory(Long viewId);

    /** 清空当前用户全部历史 */
    Boolean clearHistory();
}
