package com.knowhub.service.audit;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.config.AuditConfigReader;
import com.knowhub.enums.audit.FlowStatus;
import com.knowhub.enums.audit.FlowType;
import com.knowhub.enums.audit.LoanItemType;
import com.knowhub.enums.audit.LoanStatus;
import com.knowhub.enums.common.ReviewAction;
import com.knowhub.enums.common.ReviewStatus;
import com.knowhub.mapper.audit.AuditFlowReviewLogMapper;
import com.knowhub.mapper.audit.AuditFundFlowMapper;
import com.knowhub.mapper.audit.AuditLoanMapper;
import com.knowhub.mapper.audit.AuditLoanReviewLogMapper;
import com.knowhub.mapper.audit.AuditSubjectMapper;
import com.knowhub.pojo.audit.entity.AuditFlowReviewLog;
import com.knowhub.pojo.audit.entity.AuditFundFlow;
import com.knowhub.pojo.audit.entity.AuditLoan;
import com.knowhub.pojo.audit.entity.AuditLoanReviewLog;
import com.knowhub.pojo.audit.entity.AuditSubject;
import com.knowhub.pojo.audit.quarry.AuditLoanQuarry;
import com.knowhub.pojo.audit.vo.AuditLoanReviewLogVo;
import com.knowhub.pojo.audit.vo.AuditLoanVo;
import com.knowhub.pojo.common.vo.ReviewVo;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

/**
 * 物品借出 Service 实现。
 *
 * 借出审批流（开关 AuditConfigReader.isLoanApprovalEnabled）：
 * - 开：addLoan → status=REQUEST + 记 SUBMIT/AUTHOR 流水；approveLoan → BORROWED + APPROVE/REVIEWER；
 *   rejectLoan → REJECTED + REJECT/REVIEWER（advice 必填）。
 * - 关：addLoan → status=BORROWED + 记 SUBMIT/AUTHOR + APPROVE/SYSTEM 双痕（免审直借留痕）。
 *
 * 归还 returnLoan：BORROWED/OVERDUE → RETURNED 回填 actual_return_date。
 *   传 wearLossAmount>0 时事务内先插一条 EXPENSE(category=WEAR 损耗) 走花销阈值审批
 *   （低阈值自动 APPROVED 记 SUBMIT+APPROVE 双痕 / 高阈值 PENDING 记 SUBMIT 一条），
 *   再把 flow_id 回写 loan.related_flow_id 指回；损耗流水的 handler_id 取当前操作人，
 *   occur_date 取归还日（actualReturnDate 或 now），subject_id 沿用借出关联主体。
 *
 * 审核员回避：借用人改为外部人员后无 sys_user 映射，自审回避已无意义（移除原 borrower_id 比对）；
 * 审核人本人是否借出创建人的回避走 createBy 比对，不在本模块约束。
 * 流水表只追加不改不删；写流水失败 catch 吞异常仅 warn（状态优先、历史容错，对齐 BlogServiceImpl 哲学）。
 * 审计列 createBy/updateBy 取 SecurityContextHolder 的 UserInfo username 快照；operatorId 用 userId 稳定锁定。
 *
 * 逾期对账 markOverdue 供 AuditLoanOverdueTask 调用，无登录态，update_by 记 "system"；
 * 仅置 status=OVERDUE，不写审批流水（逾期是系统判定非人工动作）。
 */
@Service
public class AuditLoanServiceImpl implements AuditLoanService {

    @Autowired
    AuditLoanMapper auditLoanMapper;

    @Autowired
    AuditLoanReviewLogMapper auditLoanReviewLogMapper;

    @Autowired
    AuditSubjectMapper auditSubjectMapper;

    @Autowired
    AuditFundFlowMapper auditFundFlowMapper;

    @Autowired
    AuditFlowReviewLogMapper auditFlowReviewLogMapper;

    @Autowired
    AuditConfigReader auditConfigReader;

    /** 损耗花销分类 code（对应字典 audit_expense_category 的 WEAR 项） */
    private static final String EXPENSE_CATEGORY_WEAR = "WEAR";

    @Override
    public PageInfo<AuditLoanVo> quarryAuditLoan(AuditLoanQuarry quarry) {
        PageUtil.startPage();
        List<AuditLoan> list = auditLoanMapper.quarryAuditLoan(quarry);
        PageInfo<AuditLoan> page = PageUtil.packagedPageInfo(list);
        PageInfo<AuditLoanVo> voPage = PageUtil.copyPageInfo(page, AuditLoanVo.class);
        // subjectName 由 Mapper join 带出经 BeanUtil 自动拷贝
        return voPage;
    }

    @Override
    public AuditLoanVo getAuditLoanInfo(Long loanId) {
        AuditLoan loan = auditLoanMapper.getAuditLoanInfo(loanId);
        if (loan == null) {
            throw new ServiceException(500, "借出记录不存在");
        }
        AuditLoanVo vo = BeanUtil.toBean(loan, AuditLoanVo.class);
        return vo;
    }

    @Override
    @Transactional
    public Boolean addLoan(AuditLoanVo vo) {
        validateLoanPayload(vo);
        AuditSubject subject = auditSubjectMapper.getAuditSubjectInfo(vo.getSubjectId());
        if (subject == null) {
            throw new ServiceException(500, "关联主体不存在");
        }
        LoanItemType itemType = LoanItemType.ofCode(vo.getItemType());
        if (itemType == null) {
            throw new ServiceException(500, "物品类型非法（ASSET 资产 / CONSUMABLE 耗材）");
        }
        UserInfo userInfo = currentUser();
        Date now = new Date();
        AuditLoan loan = BeanUtil.toBean(vo, AuditLoan.class);
        loan.setCreateBy(userInfo.getUsername());
        loan.setUpdateBy(userInfo.getUsername());
        loan.setCreateTime(now);
        loan.setUpdateTime(now);
        // 新增态默认置 NULL：related_flow_id（归还损耗才回填）、actual_return_date（归还才回填）
        loan.setRelatedFlowId(null);
        loan.setActualReturnDate(null);

        // 借出审批开关：开 → REQUEST 待审；关 → BORROWED 免审直借
        if (auditConfigReader.isLoanApprovalEnabled()) {
            loan.setStatus(LoanStatus.REQUEST.getCode());
            auditLoanMapper.insertAuditLoan(loan);
            writeLoanReviewLog(loan.getLoanId(), ReviewAction.SUBMIT, userInfo, null);
        } else {
            loan.setStatus(LoanStatus.BORROWED.getCode());
            auditLoanMapper.insertAuditLoan(loan);
            // 免审直借记 SUBMIT/AUTHOR + APPROVE/SYSTEM 双痕（对齐花销低阈值自动通过留痕范式）
            writeLoanReviewLog(loan.getLoanId(), ReviewAction.SUBMIT, userInfo, null);
            writeLoanReviewLog(loan.getLoanId(), ReviewAction.APPROVE, systemOperator(), "借出审批关闭，免审直借");
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean editAuditLoan(AuditLoanVo vo) {
        if (vo.getLoanId() == null) {
            throw new ServiceException(500, "借出ID不能为空");
        }
        AuditLoan exist = auditLoanMapper.getAuditLoanInfo(vo.getLoanId());
        if (exist == null) {
            throw new ServiceException(500, "借出记录不存在");
        }
        // 仅 REQUEST 态可编辑描述性字段（BORROWED 后物品已借出，改信息会失真；REJECTED/OVERDUE 无编辑意义）
        if (!LoanStatus.REQUEST.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅申请待审状态可编辑");
        }
        AuditLoan loan = BeanUtil.toBean(vo, AuditLoan.class);
        UserInfo userInfo = currentUser();
        loan.setUpdateBy(userInfo.getUsername());
        // 编辑不改状态机字段与回指字段（status/related_flow_id 由审批/归还接口改）
        loan.setStatus(null);
        loan.setRelatedFlowId(null);
        loan.setActualReturnDate(null);
        try {
            auditLoanMapper.updateAuditLoan(loan);
        } catch (Exception e) {
            throw new ServiceException(500, "借出修改失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteAuditLoan(Long loanId) {
        AuditLoan exist = auditLoanMapper.getAuditLoanInfo(loanId);
        if (exist == null) {
            throw new ServiceException(500, "借出记录不存在");
        }
        // 仅 REQUEST/REJECTED 可删（BORROWED 物品已借出不可删；OVERDUE/RETURNED 保留对账历史）
        String cur = exist.getStatus();
        if (LoanStatus.BORROWED.getCode().equals(cur)) {
            throw new ServiceException(500, "已借出记录不可删除，请先归还");
        }
        if (LoanStatus.OVERDUE.getCode().equals(cur)) {
            throw new ServiceException(500, "逾期记录不可删除，请先归还处理");
        }
        if (LoanStatus.RETURNED.getCode().equals(cur)) {
            throw new ServiceException(500, "已归还记录不可删除（保留台账）");
        }
        try {
            auditLoanMapper.deleteAuditLoan(loanId);
        } catch (Exception e) {
            throw new ServiceException(500, "借出删除失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean approveLoan(ReviewVo vo) {
        if (vo.getBlogId() == null) {
            // 复用 ReviewVo.blogId 承载 loanId（review 入参通用 VO，避免新建 AuditLoanReviewVo）
            throw new ServiceException(500, "借出ID不能为空");
        }
        Long loanId = vo.getBlogId();
        AuditLoan exist = auditLoanMapper.getAuditLoanInfo(loanId);
        if (exist == null) {
            throw new ServiceException(500, "借出记录不存在");
        }
        // 状态机前置校验：仅 REQUEST 可审核通过
        if (!LoanStatus.REQUEST.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅申请待审借出可审核通过");
        }
        UserInfo userInfo = currentUser();
        AuditLoan update = new AuditLoan();
        update.setLoanId(loanId);
        update.setStatus(LoanStatus.BORROWED.getCode());
        update.setUpdateBy(userInfo.getUsername());
        auditLoanMapper.updateAuditLoan(update);
        // 记 APPROVE/REVIEWER 流水；通过时 advice 可选
        writeLoanReviewLog(loanId, ReviewAction.APPROVE, userInfo, vo.getAdvice());
        return true;
    }

    @Override
    @Transactional
    public Boolean rejectLoan(ReviewVo vo) {
        if (vo.getBlogId() == null) {
            throw new ServiceException(500, "借出ID不能为空");
        }
        if (vo.getAdvice() == null || vo.getAdvice().isEmpty()) {
            throw new ServiceException(500, "驳回需填写审核意见");
        }
        Long loanId = vo.getBlogId();
        AuditLoan exist = auditLoanMapper.getAuditLoanInfo(loanId);
        if (exist == null) {
            throw new ServiceException(500, "借出记录不存在");
        }
        if (!LoanStatus.REQUEST.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅申请待审借出可审核驳回");
        }
        UserInfo userInfo = currentUser();
        AuditLoan update = new AuditLoan();
        update.setLoanId(loanId);
        update.setStatus(LoanStatus.REJECTED.getCode());
        update.setUpdateBy(userInfo.getUsername());
        auditLoanMapper.updateAuditLoan(update);
        // 记 REJECT/REVIEWER 流水，advice 必填
        writeLoanReviewLog(loanId, ReviewAction.REJECT, userInfo, vo.getAdvice());
        return true;
    }

    @Override
    @Transactional
    public Boolean returnLoan(AuditLoanVo vo) {
        if (vo.getLoanId() == null) {
            throw new ServiceException(500, "借出ID不能为空");
        }
        Long loanId = vo.getLoanId();
        AuditLoan exist = auditLoanMapper.getAuditLoanInfo(loanId);
        if (exist == null) {
            throw new ServiceException(500, "借出记录不存在");
        }
        // 归还前置校验：仅 BORROWED/OVERDUE 可归还（REQUEST 还没借出/REJECTED 驳回无需还/RETURNED 已还）
        String cur = exist.getStatus();
        if (!LoanStatus.BORROWED.getCode().equals(cur) && !LoanStatus.OVERDUE.getCode().equals(cur)) {
            throw new ServiceException(500, "仅已借出或逾期借出可归还");
        }
        UserInfo userInfo = currentUser();
        Date now = new Date();
        // 归还损耗扣费：金额>0 则事务内插一条 EXPENSE(category=WEAR) 走花销阈值审批，回指 related_flow_id
        Long wearFlowId = null;
        if (vo.getWearLossAmount() != null && vo.getWearLossAmount().compareTo(BigDecimal.ZERO) > 0) {
            wearFlowId = insertWearLossFlow(exist, vo.getWearLossAmount(), userInfo, now);
        }
        AuditLoan update = new AuditLoan();
        update.setLoanId(loanId);
        update.setStatus(LoanStatus.RETURNED.getCode());
        update.setActualReturnDate(now);
        if (wearFlowId != null) {
            update.setRelatedFlowId(wearFlowId);
        }
        update.setUpdateBy(userInfo.getUsername());
        auditLoanMapper.updateAuditLoan(update);
        return true;
    }

    @Override
    public List<AuditLoanReviewLogVo> listReviewLog(Long loanId) {
        // 直接复用 Mapper 的 VO 查询（join sys_user 带出 operatorNickname），Service 透传
        List<AuditLoanReviewLogVo> list = auditLoanMapper.listReviewLogByLoanId(loanId);
        return list == null ? new ArrayList<>() : list;
    }

    @Override
    @Transactional
    public int markOverdue(List<Long> loanIds) {
        if (loanIds == null || loanIds.isEmpty()) {
            return 0;
        }
        int count = 0;
        for (Long loanId : loanIds) {
            AuditLoan exist = auditLoanMapper.getAuditLoanInfo(loanId);
            if (exist == null) {
                continue;
            }
            // 仅 BORROWED 置 OVERDUE（已 OVERDUE 不重复置、RETURNED 已还跳过）
            if (!LoanStatus.BORROWED.getCode().equals(exist.getStatus())) {
                continue;
            }
            AuditLoan update = new AuditLoan();
            update.setLoanId(loanId);
            update.setStatus(LoanStatus.OVERDUE.getCode());
            update.setUpdateBy("system");
            auditLoanMapper.updateAuditLoan(update);
            count++;
        }
        return count;
    }

    // ============================ 私有辅助 ============================

    /**
     * 归还损耗：事务内插一条 EXPENSE(category=WEAR) 花销流水走阈值审批。
     * 低阈值自动 APPROVED 记 SUBMIT+APPROVE 双痕；高阈值 PENDING 记 SUBMIT 一条。
     * handler_id=操作人，occur_date=归还日，subject_id 沿用借出关联主体。
     *
     * @return 损耗流水 flow_id（回写 loan.related_flow_id）
     */
    private Long insertWearLossFlow(AuditLoan loan, BigDecimal wearAmount, UserInfo operator, Date returnDate) {
        AuditFundFlow flow = new AuditFundFlow();
        flow.setSubjectId(loan.getSubjectId());
        flow.setFlowType(FlowType.EXPENSE.getCode());
        flow.setAmount(wearAmount);
        flow.setOccurDate(returnDate);
        flow.setCategory(EXPENSE_CATEGORY_WEAR);
        flow.setHandlerId(operator.getUserId());
        flow.setVoucherObjectId(loan.getVoucherObjectId());
        flow.setNote("借出归还损耗: " + (loan.getItemName() == null ? "" : loan.getItemName())
                + (loan.getAssetNo() == null ? "" : "(" + loan.getAssetNo() + ")"));
        flow.setCreateBy(operator.getUsername());
        flow.setUpdateBy(operator.getUsername());
        // 复用花销阈值审批分流：低阈值自动 APPROVED，高阈值 PENDING
        boolean autoApproved = auditConfigReader.shouldAutoApprove(wearAmount);
        if (autoApproved) {
            flow.setStatus(FlowStatus.APPROVED.getCode());
            flow.setReviewStatus(ReviewStatus.APPROVED.getCode());
            auditFundFlowMapper.insertAuditFundFlow(flow);
            writeFlowReviewLog(flow.getFlowId(), ReviewAction.SUBMIT, operator, null);
            writeFlowReviewLog(flow.getFlowId(), ReviewAction.APPROVE, systemOperator(), "损耗低于阈值自动通过");
        } else {
            flow.setStatus(FlowStatus.PENDING.getCode());
            flow.setReviewStatus(ReviewStatus.PENDING.getCode());
            auditFundFlowMapper.insertAuditFundFlow(flow);
            writeFlowReviewLog(flow.getFlowId(), ReviewAction.SUBMIT, operator, null);
        }
        return flow.getFlowId();
    }

    /**
     * 校验借出入参：subjectId/itemName/itemType/borrowerName/borrowerPhone/borrowDate 必填；
     * itemType 须为合法枚举；quantity 须 ≥1。
     */
    private void validateLoanPayload(AuditLoanVo vo) {
        if (vo.getSubjectId() == null) {
            throw new ServiceException(500, "主体ID不能为空");
        }
        if (vo.getItemName() == null || vo.getItemName().trim().isEmpty()) {
            throw new ServiceException(500, "物品名称不能为空");
        }
        if (vo.getItemType() == null || LoanItemType.ofCode(vo.getItemType()) == null) {
            throw new ServiceException(500, "物品类型非法（ASSET 资产 / CONSUMABLE 耗材）");
        }
        if (vo.getQuantity() == null || vo.getQuantity() < 1) {
            throw new ServiceException(500, "数量须≥1");
        }
        if (vo.getBorrowerName() == null || vo.getBorrowerName().trim().isEmpty()) {
            throw new ServiceException(500, "借用人姓名不能为空");
        }
        if (vo.getBorrowerPhone() == null || vo.getBorrowerPhone().trim().isEmpty()) {
            throw new ServiceException(500, "借用人联系电话不能为空");
        }
        if (vo.getBorrowDate() == null) {
            throw new ServiceException(500, "借出时间不能为空");
        }
    }

    /**
     * 追加一条借出审核流水。role 由 ReviewAction 自带（AUTHOR/REVIEWER/SYSTEM）。
     * 流水表只追加不改不删，写失败不阻断主流程（catch 吞异常仅 warn）。
     */
    private void writeLoanReviewLog(Long loanId, ReviewAction action, UserInfo operator, String advice) {
        try {
            AuditLoanReviewLog log = new AuditLoanReviewLog(loanId, action.getCode(),
                    operator.getUserId(), operator.getUsername(), action.getRole(), advice);
            auditLoanReviewLogMapper.insertAuditLoanReviewLog(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditLoanServiceImpl.class)
                    .warn("写借出审核流水失败 loanId={} action={}: {}", loanId, action.getCode(), e.getMessage());
        }
    }

    /** 损耗花销流水的审核流水（落 audit_flow_review_log，与 AuditFundFlowServiceImpl.writeReviewLog 同构） */
    private void writeFlowReviewLog(Long flowId, ReviewAction action, UserInfo operator, String advice) {
        try {
            AuditFlowReviewLog log = new AuditFlowReviewLog(flowId, action.getCode(),
                    operator.getUserId(), operator.getUsername(), action.getRole(), advice);
            auditFlowReviewLogMapper.insertAuditFlowReviewLog(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditLoanServiceImpl.class)
                    .warn("写损耗花销审核流水失败 flowId={} action={}: {}", flowId, action.getCode(), e.getMessage());
        }
    }

    /** 构造一个 system 操作者 UserInfo，用于免审直借 / 损耗低阈值自动通过时写 APPROVE/SYSTEM 流水 */
    private UserInfo systemOperator() {
        UserInfo sys = new UserInfo();
        sys.setUserId(0L);
        sys.setUsername("system");
        return sys;
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}