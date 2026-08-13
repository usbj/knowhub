package com.knowhub.service.audit;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.audit.quarry.AuditFundFlowQuarry;
import com.knowhub.pojo.audit.vo.AuditFlowReviewLogVo;
import com.knowhub.pojo.audit.vo.AuditFundFlowVo;
import com.knowhub.pojo.common.vo.ReviewVo;

import java.util.List;

/**
 * 资金流水 Service。
 * 接口只暴露 DTO/VO，不暴露实体（对齐 blog/resource 模块约定）。
 * 三流合一写入：BUDGET/INCOME 免审(approve)直接累加主体累计列；EXPENSE 走阈值审批。
 * 审核动作复用 ReviewAction(SUBMIT/APPROVE/REJECT/REVOKE) 与 ReviewStatus(NONE/PENDING/APPROVED/REJECTED)。
 * 权限不分等级（审计内部使用），按钮权限由 Controller @PreAuthorize 兜底，Service 层不重复校验。
 */
public interface AuditFundFlowService {

    /** 列表查询（分页） */
    PageInfo<AuditFundFlowVo> quarryAuditFundFlow(AuditFundFlowQuarry quarry);

    /** 详情（含 subjectName/handlerNickname join 带出） */
    AuditFundFlowVo getAuditFundFlowInfo(Long flowId);

    /**
     * 新增资金流水（三流合一入口，按 flowType 分流）：
     *   BUDGET  → status=APPROVED + 事务内 addBudgetTotal + 记 SUBMIT 流水
     *   INCOME  → status=APPROVED + 事务内 addIncomeTotal + 记 SUBMIT 流水
     *   EXPENSE → 读阈值判断：低于自动 APPROVED + 记 SUBMIT+APPROVE 两条流水；
     *             高于 PENDING + 记 SUBMIT 一条流水
     */
    Boolean addFundFlow(AuditFundFlowVo vo);

    /** 编辑流水（动态列，仅 DRAFT/PENDING 可编辑，需经办人/admin） */
    Boolean editAuditFundFlow(AuditFundFlowVo vo);

    /** 删除流水（软删 deleted=1） */
    Boolean deleteAuditFundFlow(Long flowId);

    /**
     * 提交花销审批（DRAFT → PENDING，记 SUBMIT 流水）。
     * 仅 EXPENSE 且当前 DRAFT 可提交；高于阈值且未提交时用此入口进待审。
     */
    Boolean submitExpenseFlow(Long flowId);

    /**
     * 审批通过（PENDING → APPROVED，记 APPROVE 流水）。
     * 仅 EXPENSE 且当前 PENDING 可审核；审核员回避：经办人不能审自己提交的流水。
     */
    Boolean approveFlow(ReviewVo vo);

    /**
     * 审批驳回（PENDING → REJECTED，记 REJECT 流水，advice 必填）。
     * 仅 EXPENSE 且当前 PENDING 可审核；审核员回避：经办人不能审自己提交的流水。
     */
    Boolean rejectFlow(ReviewVo vo);

    /**
     * 撤回已通过花销（APPROVED → REVOKED，记 REVOKE 流水）。
     * 仅 EXPENSE 且当前 APPROVED 可撤回；经办人或 admin 可操作。
     * 撤回不退减已累加的主体列（EXPENSE APPROVED 本就不累加），仅置状态。
     */
    Boolean revokeFlow(Long flowId);

    /** 审核历史（按 flowId 查审核流水时间线，详情折叠区用） */
    List<AuditFlowReviewLogVo> listReviewLog(Long flowId);
}