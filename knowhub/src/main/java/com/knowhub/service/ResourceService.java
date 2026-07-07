package com.knowhub.service;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.quarry.ResourceQuarry;
import com.knowhub.pojo.vo.ResourceReviewLogVo;
import com.knowhub.pojo.vo.ResourceReviewVo;
import com.knowhub.pojo.vo.ResourceVo;

import java.util.List;

/**
 * 资源 Service。
 * 接口只暴露 DTO，不暴露实体。
 *
 * 审核流程复用博客那套范式（状态机+回避+流水表+对账任务）：
 * - publish：DRAFT/REJECTED/REVOKED → 开关开 PENDING_REVIEW+PENDING / 关 PUBLISHED+NONE，写流水
 * - revoke：PUBLISHED → REVOKED，写流水
 * - review：仅 PENDING_REVIEW 可审，作者不能审自己(回避)，通过→PUBLISHED/驳回→REJECTED，写流水
 * - edit：PUBLISHED 禁止编辑（须先撤回），合法前置 DRAFT/REJECTED/REVOKED
 *
 * 互动计数（点赞/收藏/评分）不冗余主表，走事实表聚合回填；下载数仅 FILE 下载 +1 主表。
 */
public interface ResourceService {

    /** 列表查询（分页，回填互动计数与作者昵称/分类名） */
    PageInfo<ResourceVo> quarryResource(ResourceQuarry quarry);

    /** 详情（含互动计数、当前用户态、FILE 下载链接、审核快照走流水表） */
    ResourceVo getResourceInfo(Long resourceId);

    /** 新增（草稿；FILE 类资源落库后回填 file_object.biz_ref_id） */
    Boolean addResourceInfo(ResourceVo vo);

    /** 编辑（校验归属 + 状态机前置；PUBLISHED 禁止编辑须先撤回；FILE 类可换文件） */
    Boolean editResourceInfo(ResourceVo vo);

    /** 批量软删（FILE 类级联软删关联 file_object 行，对象本体由 FileGcTask 异步清） */
    Boolean deleteResourceInfo(Long[] resourceIds);

    /** 发布（经审核开关决定 PUBLISHED 或 PENDING_REVIEW；前置状态校验+写流水+Redis标记） */
    Boolean publishResource(Long resourceId);

    /** 撤回（→ REVOKED；前置状态校验+写流水） */
    Boolean revokeResource(Long resourceId);

    /** 审核（通过 → PUBLISHED；驳回 → REJECTED；前置状态校验+审核员回避+写流水） */
    Boolean reviewResource(ResourceReviewVo vo);

    /** 审核历史（按资源ID查审核流水时间线，前台详情/后台记录共用） */
    List<ResourceReviewLogVo> listReviewLog(Long resourceId);

    /**
     * 对账收口：审核开关关闭后，将所有遗留的待审核资源批量转为已发布。
     * 由定时任务在确认开关关闭且 Redis 待审标记存在时调用。逐条放行 + 写 PUBLISH/SYSTEM 流水，
     * 单条失败跳过不阻塞其它资源（状态优先、历史容错）。
     *
     * @return 实际放行条数
     */
    int reconcilePendingReview();

    /** 点赞 / 取消点赞（toggle，事实表 insert/delete，不冗余主表计数） */
    Boolean toggleLike(Long resourceId, Boolean liked);

    /** 收藏 / 取消收藏（toggle，事实表 insert/delete，不冗余主表计数） */
    Boolean toggleCollect(Long resourceId, Boolean collected);

    /** 评分（upsert 事实表，改分后重算主表 rating_avg/rating_count 由聚合读时算，此处只写事实表） */
    Boolean rateResource(Long resourceId, Integer score);

    /** FILE 下载（校验 PUBLISHED + FILE 类型 + 下载量 +1，返回下载链接） */
    String downloadResource(Long resourceId);
}
