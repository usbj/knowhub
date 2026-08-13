package com.knowhub.service.audit;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.audit.quarry.AuditLoanQuarry;
import com.knowhub.pojo.audit.vo.AuditLoanReviewLogVo;
import com.knowhub.pojo.audit.vo.AuditLoanVo;
import com.knowhub.pojo.common.vo.ReviewVo;

import java.util.List;

/**
 * 物品借出 Service。
 * 接口只暴露 DTO/VO，不暴露实体（对齐 blog/resource/flow 模块约定）。
 *
 * 借出审批开关（AuditConfigReader.isLoanApprovalEnabled）决定新增态：
 *   开 → 新增 REQUEST →(approve)→ BORROWED→(return)→ RETURNED；或 (reject)→ REJECTED
 *   关 → 新增直接 BORROWED →(return)→ RETURNED（免审直借）
 *
 * 归还 returnLoan：status→RETURNED 回填 actual_return_date；
 *   若传入 wearLossAmount>0 则事务内先插一条 EXPENSE(category=WEAR 损耗) 走花销审批
 *   （低阈值自动 APPROVED / 高阈值 PENDING 待审），并把该 flow_id 回写到 loan.related_flow_id 指回。
 *
 * 逾期由 AuditLoanOverdueTask 对账任务扫表置 OVERDUE，Service 暴露 markOverdue(loanIdList) 供任务调用。
 * 审核动作复用 ReviewAction(SUBMIT/APPROVE/REJECT) 与 ReviewStatus，审批流水落 audit_loan_review_log。
 * 权限不分等级（审计内部使用），Service 层不重复校验权限，由 Controller @PreAuthorize 兜底。
 */
public interface AuditLoanService {

    /** 列表查询（分页） */
    PageInfo<AuditLoanVo> quarryAuditLoan(AuditLoanQuarry quarry);

    /** 详情（含 subjectName join 带出） */
    AuditLoanVo getAuditLoanInfo(Long loanId);

    /**
     * 新增借出。按借出审批开关决定初始状态：
     *   开 → status=REQUEST（待审）；记 SUBMIT/AUTHOR 流水
     *   关 → status=BORROWED（免审直借）；记 SUBMIT/AUTHOR + APPROVE/SYSTEM 双痕
     */
    Boolean addLoan(AuditLoanVo vo);

    /** 编辑借出（仅 REQUEST 状态可编辑描述性字段，status 不在此改） */
    Boolean editAuditLoan(AuditLoanVo vo);

    /** 删除借出（软删；仅 REQUEST/REJECTED 可删，已借出/逾期不可删） */
    Boolean deleteAuditLoan(Long loanId);

    /** 审批通过（REQUEST → BORROWED，记 APPROVE 流水） */
    Boolean approveLoan(ReviewVo vo);

    /** 审批驳回（REQUEST → REJECTED，记 REJECT 流水，advice 必填） */
    Boolean rejectLoan(ReviewVo vo);

    /**
     * 归还借出（BORROWED/OVERDUE → RETURNED，回填 actual_return_date）。
     * 若 wearLossAmount &gt; 0：事务内先插一条 EXPENSE(category=WEAR) 花销流水走阈值审批，
     * 并回写 loan.related_flow_id 指向该损耗流水。记 REVOKE 不适用，归还本身不记审批流水
     * （归还不是审批动作，损耗流水自带审批流水留痕）。
     */
    Boolean returnLoan(AuditLoanVo vo);

    /** 审核历史（按 loanId 查审核流水时间线，详情折叠区用） */
    List<AuditLoanReviewLogVo> listReviewLog(Long loanId);

    /**
     * 逾期对账批量置 OVERDUE（供 AuditLoanOverdueTask 调用，无登录态走系统操作者写流水可选）。
     * 仅置 status=OVERDUE，不写审批流水（逾期是系统判定不是人工动作，记 update_by=system 即可）。
     *
     * @param loanIds 经任务扫描确认逾期的借出ID集合
     * @return 实际置为 OVERDUE 的条数（已 OVERDUE 的不重复置）
     */
    int markOverdue(List<Long> loanIds);
}