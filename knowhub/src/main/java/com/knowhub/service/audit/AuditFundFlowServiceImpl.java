package com.knowhub.service.audit;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.config.AuditConfigReader;
import com.knowhub.enums.audit.FlowStatus;
import com.knowhub.enums.audit.FlowType;
import com.knowhub.enums.common.ReviewAction;
import com.knowhub.enums.common.ReviewStatus;
import com.knowhub.mapper.audit.AuditFlowReviewLogMapper;
import com.knowhub.mapper.audit.AuditFundFlowMapper;
import com.knowhub.mapper.audit.AuditSubjectMapper;
import com.knowhub.pojo.audit.entity.AuditFlowReviewLog;
import com.knowhub.pojo.audit.entity.AuditFundFlow;
import com.knowhub.pojo.audit.entity.AuditSubject;
import com.knowhub.pojo.audit.quarry.AuditFundFlowQuarry;
import com.knowhub.pojo.audit.vo.AuditFlowReviewLogVo;
import com.knowhub.pojo.audit.vo.AuditFundFlowVo;
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
import java.util.stream.Collectors;

/**
 * 资金流水 Service 实现。
 *
 * 三流合一写入（核心逻辑）：
 * - BUDGET：status=APPROVED + reviewStatus=NONE，事务内 addBudgetTotal(subjectId, amount) 累加主体 budget_total；记 SUBMIT/AUTHOR 流水一条（留痕）。
 * - INCOME：status=APPROVED + reviewStatus=NONE，事务内 addIncomeTotal(subjectId, amount) 累加主体 income_total；记 SUBMIT/AUTHOR 流水一条。
 * - EXPENSE：按阈值分流（本批次阈值逻辑先用硬编码 + TODO，批次2 AuditConfigReader 接入后替换）：
 *     · amount &lt;= 阈值 → status=APPROVED + reviewStatus=APPROVED，记 SUBMIT/AUTHOR + APPROVE/SYSTEM 两条流水（自动通过留双痕）；
 *     · amount &gt; 阈值 → status=PENDING + reviewStatus=PENDING，记 SUBMIT/AUTHOR 一条流水，待审核员 approve。
 *   EXPENSE 不直接动主体累计列（balance 查时聚合算 income_total − 历史APPROVED EXPENSE 合计）。
 *
 * 审核动作复用 ReviewAction(SUBMIT/APPROVE/REJECT/REVOKE) + ReviewStatus(NONE/PENDING/APPROVED/REJECTED)：
 * - submitExpenseFlow：DRAFT → PENDING，记 SUBMIT/AUTHOR（仅 EXPENSE，已低于阈值自动通过的不走此入口）。
 * - approveFlow：PENDING → APPROVED，审核员回避（handler_id == 操作人 userId 拒绝），记 APPROVE/REVIEWER。
 * - rejectFlow：PENDING → REJECTED，advice 必填，回避同 approve，记 REJECT/REVIEWER。
 * - revokeFlow：APPROVED → REVOKED，经办人或 admin 可操作，记 REVOKE/AUTHOR。撤回不退减累计列（EXPENSE APPROVED 本就不累加）。
 *
 * 每个审核动作都在 @Transactional 内完成"写流水 + 更新 flow.status/reviewStatus"原子提交，避免半量落库。
 * 流水表只追加不改不删；写流水失败 catch 吞异常仅 warn（状态优先、历史容错，对齐 BlogServiceImpl.writeReviewLog 哲学）。
 * 审计列 createBy/updateBy 取 SecurityContextHolder 的 UserInfo username 快照；operatorId 用 userId 稳定锁定。
 *
 * 权限不分等级（审计内部使用）；经办人归属判定用 handler_id 比对（userId 稳定，比 username 准），
 * admin 走 UserInfo.isAdmin() 短路（rookie 框架放行）。
 */
@Service
public class AuditFundFlowServiceImpl implements AuditFundFlowService {

    @Autowired
    AuditFundFlowMapper auditFundFlowMapper;

    @Autowired
    AuditFlowReviewLogMapper auditFlowReviewLogMapper;

    @Autowired
    AuditSubjectMapper auditSubjectMapper;

    @Autowired
    AuditConfigReader auditConfigReader;

    @Override
    public PageInfo<AuditFundFlowVo> quarryAuditFundFlow(AuditFundFlowQuarry quarry) {
        PageUtil.startPage();
        List<AuditFundFlow> list = auditFundFlowMapper.quarryAuditFundFlow(quarry);
        PageInfo<AuditFundFlow> page = PageUtil.packagedPageInfo(list);
        PageInfo<AuditFundFlowVo> voPage = PageUtil.copyPageInfo(page, AuditFundFlowVo.class);
        // subjectName/handlerNickname 由 Mapper join 带出经 BeanUtil 自动拷贝；categoryLabel 字典翻译由前端做（批次不涉及字典翻译工具）
        return voPage;
    }

    @Override
    public AuditFundFlowVo getAuditFundFlowInfo(Long flowId) {
        AuditFundFlow flow = auditFundFlowMapper.getAuditFundFlowInfo(flowId);
        if (flow == null) {
            throw new ServiceException(500, "流水不存在");
        }
        AuditFundFlowVo vo = BeanUtil.toBean(flow, AuditFundFlowVo.class);
        // categoryLabel 字典翻译留前端做（批次不接字典翻译工具），此处仅透传 code
        return vo;
    }

    @Override
    @Transactional
    public Boolean addFundFlow(AuditFundFlowVo vo) {
        validateFundFlowPayload(vo);
        AuditSubject subject = auditSubjectMapper.getAuditSubjectInfo(vo.getSubjectId());
        if (subject == null) {
            throw new ServiceException(500, "关联主体不存在");
        }
        FlowType type = FlowType.ofCode(vo.getFlowType());
        if (type == null) {
            throw new ServiceException(500, "流水类型非法（BUDGET/INCOME/EXPENSE）");
        }
        UserInfo userInfo = currentUser();
        Date now = new Date();
        AuditFundFlow flow = BeanUtil.toBean(vo, AuditFundFlow.class);
        flow.setCreateBy(userInfo.getUsername());
        flow.setUpdateBy(userInfo.getUsername());
        flow.setCreateTime(now);
        flow.setUpdateTime(now);

        switch (type) {
            case BUDGET: {
                // 预算注入：免审，status 恒 APPROVED；事务内累加主体 budget_total；记 SUBMIT/AUTHOR 留痕
                flow.setStatus(FlowStatus.APPROVED.getCode());
                flow.setReviewStatus(ReviewStatus.NONE.getCode());
                auditFundFlowMapper.insertAuditFundFlow(flow);
                auditSubjectMapper.addBudgetTotal(flow.getSubjectId(), flow.getAmount());
                writeReviewLog(flow.getFlowId(), ReviewAction.SUBMIT, userInfo, "BUDGET 预算注入，免审直入");
                break;
            }
            case INCOME: {
                // 收账到账：免审，status 恒 APPROVED；事务内累加主体 income_total；记 SUBMIT/AUTHOR 留痕
                flow.setStatus(FlowStatus.APPROVED.getCode());
                flow.setReviewStatus(ReviewStatus.NONE.getCode());
                auditFundFlowMapper.insertAuditFundFlow(flow);
                auditSubjectMapper.addIncomeTotal(flow.getSubjectId(), flow.getAmount());
                writeReviewLog(flow.getFlowId(), ReviewAction.SUBMIT, userInfo, "INCOME 收账到账，免审直入");
                break;
            }
            case EXPENSE: {
                // 花销：按阈值分流。低于等于阈值自动 APPROVED(记 SUBMIT+APPROVE 双痕)；高于 PENDING(记 SUBMIT 一条)
                // 开关未开 → 一律自动通过（免审但仍记双痕留痕）；开关开 → amount≤阈值 才自动通过。
                boolean autoApproved = auditConfigReader.shouldAutoApprove(flow.getAmount());
                if (autoApproved) {
                    flow.setStatus(FlowStatus.APPROVED.getCode());
                    flow.setReviewStatus(ReviewStatus.APPROVED.getCode());
                    auditFundFlowMapper.insertAuditFundFlow(flow);
                    // 自动通过记 SUBMIT/AUTHOR + APPROVE/SYSTEM 两条流水（低阈值直通留痕）
                    writeReviewLog(flow.getFlowId(), ReviewAction.SUBMIT, userInfo, null);
                    writeReviewLog(flow.getFlowId(), ReviewAction.APPROVE, systemOperator(), "低于阈值自动通过");
                } else {
                    flow.setStatus(FlowStatus.PENDING.getCode());
                    flow.setReviewStatus(ReviewStatus.PENDING.getCode());
                    auditFundFlowMapper.insertAuditFundFlow(flow);
                    // 高于阈值记 SUBMIT/AUTHOR 一条，待审核员 approve
                    writeReviewLog(flow.getFlowId(), ReviewAction.SUBMIT, userInfo, null);
                }
                break;
            }
            default:
                throw new ServiceException(500, "不支持的流水类型: " + vo.getFlowType());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean editAuditFundFlow(AuditFundFlowVo vo) {
        if (vo.getFlowId() == null) {
            throw new ServiceException(500, "流水ID不能为空");
        }
        AuditFundFlow exist = auditFundFlowMapper.getAuditFundFlowInfo(vo.getFlowId());
        if (exist == null) {
            throw new ServiceException(500, "流水不存在");
        }
        // 状态机前置校验：仅 DRAFT/PENDING 可编辑（APPROVED/REJECTED/REVOKED 不可改）
        String cur = exist.getStatus();
        if (FlowStatus.APPROVED.getCode().equals(cur)) {
            throw new ServiceException(500, "已通过流水不可编辑，如需调整请先撤回");
        }
        if (FlowStatus.REJECTED.getCode().equals(cur)) {
            throw new ServiceException(500, "已驳回流水不可编辑");
        }
        if (FlowStatus.REVOKED.getCode().equals(cur)) {
            throw new ServiceException(500, "已撤回流水不可编辑");
        }
        // 经办人归属：仅经办人或 admin 可编辑（handler_id 比对，userId 稳定）
        if (!isHandler(exist, currentUser()) && !currentUser().isAdmin()) {
            throw new ServiceException(500, "无权编辑该流水（仅经办人或管理员）");
        }
        // BUDGET/INCOME 不允许编辑金额（会破坏已累加的累计列恒等式）；如需调整应新增冲红流水
        if (FlowType.BUDGET.getCode().equals(exist.getFlowType())
                || FlowType.INCOME.getCode().equals(exist.getFlowType())) {
            throw new ServiceException(500, "预算/收账流水不允许编辑，如需调整请新增冲红流水");
        }
        AuditFundFlow flow = BeanUtil.toBean(vo, AuditFundFlow.class);
        UserInfo userInfo = currentUser();
        flow.setUpdateBy(userInfo.getUsername());
        // 编辑不改状态机字段（status/reviewStatus 由审批接口改），仅改描述性字段
        flow.setStatus(null);
        flow.setReviewStatus(null);
        try {
            auditFundFlowMapper.updateAuditFundFlow(flow);
        } catch (Exception e) {
            throw new ServiceException(500, "流水修改失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteAuditFundFlow(Long flowId) {
        AuditFundFlow exist = auditFundFlowMapper.getAuditFundFlowInfo(flowId);
        if (exist == null) {
            throw new ServiceException(500, "流水不存在");
        }
        // 软删前置校验：仅 DRAFT/REJECTED/REVOKED 可删（APPROVED/PENDING 不删避免丢账与污染审核）
        String cur = exist.getStatus();
        if (FlowStatus.APPROVED.getCode().equals(cur) || FlowStatus.PENDING.getCode().equals(cur)) {
            throw new ServiceException(500, "已通过或待审核流水不可删除");
        }
        try {
            auditFundFlowMapper.deleteAuditFundFlow(flowId);
        } catch (Exception e) {
            throw new ServiceException(500, "流水删除失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean submitExpenseFlow(Long flowId) {
        AuditFundFlow exist = auditFundFlowMapper.getAuditFundFlowInfo(flowId);
        if (exist == null) {
            throw new ServiceException(500, "流水不存在");
        }
        // 仅 EXPENSE 走提交审批入口（BUDGET/INCOME 免审不需提交）
        if (!FlowType.EXPENSE.getCode().equals(exist.getFlowType())) {
            throw new ServiceException(500, "仅花销流水可提交审批");
        }
        // 状态机前置校验：仅 DRAFT 可提交（PENDING 已在审；APPROVED/REJECTED/REVOKED 无提交意义）
        if (!FlowStatus.DRAFT.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅草稿状态流水可提交审批");
        }
        UserInfo userInfo = currentUser();
        AuditFundFlow update = new AuditFundFlow();
        update.setFlowId(flowId);
        update.setStatus(FlowStatus.PENDING.getCode());
        update.setReviewStatus(ReviewStatus.PENDING.getCode());
        update.setUpdateBy(userInfo.getUsername());
        auditFundFlowMapper.updateAuditFundFlow(update);
        // 记 SUBMIT/AUTHOR 流水
        writeReviewLog(flowId, ReviewAction.SUBMIT, userInfo, null);
        return true;
    }

    @Override
    @Transactional
    public Boolean approveFlow(ReviewVo vo) {
        if (vo.getBlogId() == null) {
            // 复用 ReviewVo.blogId 承载 flowId（review 入参通用 VO，避免新建 AuditReviewVo）
            throw new ServiceException(500, "流水ID不能为空");
        }
        Long flowId = vo.getBlogId();
        AuditFundFlow exist = auditFundFlowMapper.getAuditFundFlowInfo(flowId);
        if (exist == null) {
            throw new ServiceException(500, "流水不存在");
        }
        // 状态机前置校验：仅 PENDING 可审核，防止对草稿/已通过等误调审核接口改状态
        if (!FlowStatus.PENDING.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅待审核流水可审核");
        }
        UserInfo userInfo = currentUser();
        // 审核员回避：经办人不能审自己提交的花销（用 handler_id 比对，比 username 更准）
        if (exist.getHandlerId() != null && exist.getHandlerId().equals(userInfo.getUserId())) {
            throw new ServiceException(500, "不能审核自己提交的花销");
        }
        AuditFundFlow update = new AuditFundFlow();
        update.setFlowId(flowId);
        update.setStatus(FlowStatus.APPROVED.getCode());
        update.setReviewStatus(ReviewStatus.APPROVED.getCode());
        update.setUpdateBy(userInfo.getUsername());
        auditFundFlowMapper.updateAuditFundFlow(update);
        // 记 APPROVE/REVIEWER 流水；通过时 advice 可选
        writeReviewLog(flowId, ReviewAction.APPROVE, userInfo, vo.getAdvice());
        return true;
    }

    @Override
    @Transactional
    public Boolean rejectFlow(ReviewVo vo) {
        if (vo.getBlogId() == null) {
            throw new ServiceException(500, "流水ID不能为空");
        }
        if (vo.getAdvice() == null || vo.getAdvice().isEmpty()) {
            throw new ServiceException(500, "驳回需填写审核意见");
        }
        Long flowId = vo.getBlogId();
        AuditFundFlow exist = auditFundFlowMapper.getAuditFundFlowInfo(flowId);
        if (exist == null) {
            throw new ServiceException(500, "流水不存在");
        }
        if (!FlowStatus.PENDING.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅待审核流水可审核");
        }
        UserInfo userInfo = currentUser();
        // 审核员回避：经办人不能审自己提交的花销
        if (exist.getHandlerId() != null && exist.getHandlerId().equals(userInfo.getUserId())) {
            throw new ServiceException(500, "不能审核自己提交的花销");
        }
        AuditFundFlow update = new AuditFundFlow();
        update.setFlowId(flowId);
        update.setStatus(FlowStatus.REJECTED.getCode());
        update.setReviewStatus(ReviewStatus.REJECTED.getCode());
        update.setUpdateBy(userInfo.getUsername());
        auditFundFlowMapper.updateAuditFundFlow(update);
        // 记 REJECT/REVIEWER 流水，advice 必填
        writeReviewLog(flowId, ReviewAction.REJECT, userInfo, vo.getAdvice());
        return true;
    }

    @Override
    @Transactional
    public Boolean revokeFlow(Long flowId) {
        AuditFundFlow exist = auditFundFlowMapper.getAuditFundFlowInfo(flowId);
        if (exist == null) {
            throw new ServiceException(500, "流水不存在");
        }
        // 状态机前置校验：仅 APPROVED 可撤回（DRAFT/PENDING 无撤回意义，REJECTED/REVOKED 已终态）
        if (!FlowStatus.APPROVED.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅已通过流水可撤回");
        }
        // 经办人或 admin 可撤回（handler_id 比对 + admin 短路）
        if (!isHandler(exist, currentUser()) && !currentUser().isAdmin()) {
            throw new ServiceException(500, "无权撤回该流水（仅经办人或管理员）");
        }
        UserInfo userInfo = currentUser();
        AuditFundFlow update = new AuditFundFlow();
        update.setFlowId(flowId);
        update.setStatus(FlowStatus.REVOKED.getCode());
        update.setReviewStatus(ReviewStatus.NONE.getCode());
        update.setUpdateBy(userInfo.getUsername());
        auditFundFlowMapper.updateAuditFundFlow(update);
        // 记 REVOKE/AUTHOR 流水；撤回不退减主体累计列（EXPENSE APPROVED 本就不累加 income_total）
        writeReviewLog(flowId, ReviewAction.REVOKE, userInfo, null);
        return true;
    }

    @Override
    public List<AuditFlowReviewLogVo> listReviewLog(Long flowId) {
        // 直接复用 Mapper 的 VO 查询（join sys_user 带出 operatorNickname），Service 透传
        List<AuditFlowReviewLogVo> list = auditFundFlowMapper.listReviewLogByFlowId(flowId);
        return list == null ? new ArrayList<>() : list;
    }

    // ============================ 私有辅助 ============================

    /**
     * 校验流水入参：subjectId/flowType/amount/occurDate/handlerId 必填；
     * amount 须为正；flowType 须为合法枚举。
     */
    private void validateFundFlowPayload(AuditFundFlowVo vo) {
        if (vo.getSubjectId() == null) {
            throw new ServiceException(500, "主体ID不能为空");
        }
        if (vo.getFlowType() == null || FlowType.ofCode(vo.getFlowType()) == null) {
            throw new ServiceException(500, "流水类型非法（BUDGET/INCOME/EXPENSE）");
        }
        if (vo.getAmount() == null || vo.getAmount().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ServiceException(500, "金额须为正数");
        }
        if (vo.getOccurDate() == null) {
            throw new ServiceException(500, "发生日期不能为空");
        }
        if (vo.getHandlerId() == null) {
            throw new ServiceException(500, "经办人不能为空");
        }
    }

    /** 当前用户是否该流水经办人（handler_id 比对，userId 稳定锁定，username 可改不影响） */
    private boolean isHandler(AuditFundFlow flow, UserInfo user) {
        return flow.getHandlerId() != null && flow.getHandlerId().equals(user.getUserId());
    }

    /**
     * 追加一条审核流水。role 由 ReviewAction 自带（AUTHOR/REVIEWER/SYSTEM），
     * operator_id 用 userId 稳定锁定，operator 存 username 快照便于直读。
     * 流水表只追加不改不删，写失败不阻断主流程（catch 吞异常仅 warn，保证审核状态变更已落库）。
     * 对齐 BlogServiceImpl.writeReviewLog 的"状态优先、历史容错"哲学。
     */
    private void writeReviewLog(Long flowId, ReviewAction action, UserInfo operator, String advice) {
        try {
            AuditFlowReviewLog log = new AuditFlowReviewLog(flowId, action.getCode(),
                    operator.getUserId(), operator.getUsername(), action.getRole(), advice);
            auditFlowReviewLogMapper.insertAuditFlowReviewLog(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(AuditFundFlowServiceImpl.class)
                    .warn("写审核流水失败 flowId={} action={}: {}", flowId, action.getCode(), e.getMessage());
        }
    }

    /** 构造一个 system 操作者 UserInfo，用于低阈值自动通过时写 APPROVE/SYSTEM 流水（operator_id=0, operator=system） */
    private UserInfo systemOperator() {
        UserInfo sys = new UserInfo();
        sys.setUserId(0L);
        sys.setUsername("system");
        return sys;
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /** 保留以备后续业务用（当前未直接使用，避免编译警告）：流转 VO 列表便捷方法 */
    @SuppressWarnings("unused")
    private List<AuditFundFlowVo> toVoList(List<AuditFundFlow> list) {
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        return list.stream().map(f -> BeanUtil.toBean(f, AuditFundFlowVo.class)).collect(Collectors.toList());
    }
}