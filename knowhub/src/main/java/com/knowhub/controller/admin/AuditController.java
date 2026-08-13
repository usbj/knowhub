package com.knowhub.controller.admin;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.audit.quarry.AuditFundFlowQuarry;
import com.knowhub.pojo.audit.quarry.AuditLoanQuarry;
import com.knowhub.pojo.audit.quarry.AuditPeriodReportQuarry;
import com.knowhub.pojo.audit.quarry.AuditSubjectQuarry;
import com.knowhub.pojo.audit.vo.AuditFlowReviewLogVo;
import com.knowhub.pojo.audit.vo.AuditFundFlowVo;
import com.knowhub.pojo.audit.vo.AuditLoanReviewLogVo;
import com.knowhub.pojo.audit.vo.AuditLoanVo;
import com.knowhub.pojo.audit.vo.AuditPeriodReportVo;
import com.knowhub.pojo.audit.vo.AuditSubjectVo;
import com.knowhub.pojo.common.vo.ReviewVo;
import com.knowhub.service.audit.AuditFundFlowService;
import com.knowhub.service.audit.AuditLoanService;
import com.knowhub.service.audit.AuditPeriodReportService;
import com.knowhub.service.audit.AuditSubjectService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 审计模块接口（纯后台 admin，前台不接入）。
 * 路由为 /audit，权限键三段式 knowhub:audit:{模块}:{动作}，与 sys_menu 中 knowhub:audit:* 行一致
 * （菜单 menu_id 162-195，见 sql/knowhub-audit.sql）。
 *
 * 四组接口：
 *   /audit/subject   主体 CRUD + balance 看板数据
 *   /audit/flow      资金流水 CRUD + 提交审批/通过/驳回/撤回/审核历史（三流合一，阈值审批）
 *   /audit/loan      物品借出 CRUD + 审批通过/驳回/归还（损耗落花销流水走阈值审批）/审核历史
 *   /audit/report    周期报表查询/详情/重算（仅已结束期可重算；月度/周记定时任务生成）
 * 权限不分等级（审计内部使用），无 :l1-3；admin 登录时全 perm_key 已塞入，@PreAuthorize 兜底。
 */
@Tag(name = "审计管理", description = "花销记录与事务管理（主体/资金流水/借出/报表）后台接口")
@RestController
@RequestMapping("/audit")
public class AuditController {

    @Autowired
    AuditSubjectService auditSubjectService;

    @Autowired
    AuditFundFlowService auditFundFlowService;

    @Autowired
    AuditLoanService auditLoanService;

    @Autowired
    AuditPeriodReportService auditPeriodReportService;

    // ============================ 花销主体 /audit/subject ============================

    @GetMapping("/subject/list")
    @Operation(summary = "获取花销主体列表")
    @PreAuthorize("hasAuthority('knowhub:audit:subject:quarry')")
    public Result<PageInfo<AuditSubjectVo>> quarryAuditSubject(AuditSubjectQuarry quarry) {
        PageInfo<AuditSubjectVo> pageInfo = auditSubjectService.quarryAuditSubject(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/subject/{subjectId}")
    @Operation(summary = "获取花销主体详情")
    @PreAuthorize("hasAuthority('knowhub:audit:subject:info')")
    public Result<AuditSubjectVo> getAuditSubjectInfo(@PathVariable Long subjectId) {
        AuditSubjectVo vo = auditSubjectService.getAuditSubjectInfo(subjectId);
        return Result.success(vo);
    }

    @PostMapping("/subject")
    @Operation(summary = "新增花销主体")
    @Log(title = "花销主体", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:audit:subject:add')")
    public Result<Boolean> addAuditSubject(@RequestBody AuditSubjectVo vo) {
        Boolean b = auditSubjectService.addAuditSubject(vo);
        return Result.success(b);
    }

    @PutMapping("/subject")
    @Operation(summary = "编辑花销主体")
    @Log(title = "花销主体", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:subject:edit')")
    public Result<Boolean> editAuditSubject(@RequestBody AuditSubjectVo vo) {
        Boolean b = auditSubjectService.editAuditSubject(vo);
        return Result.success(b);
    }

    @DeleteMapping("/subject/{subjectId}")
    @Operation(summary = "删除花销主体")
    @Log(title = "花销主体", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:audit:subject:delete')")
    public Result<Boolean> deleteAuditSubject(@PathVariable Long subjectId) {
        Boolean b = auditSubjectService.deleteAuditSubject(subjectId);
        return Result.success(b);
    }

    @GetMapping("/subject/balance/{subjectId}")
    @Operation(summary = "查主体结余与当月开销（看板数据）")
    @PreAuthorize("hasAuthority('knowhub:audit:subject:info')")
    public Result<AuditSubjectVo> getSubjectBalance(@PathVariable Long subjectId) {
        // 复用详情接口的聚合回填（balance + monthExpense）；前端看板可直接用 detail，
        // 此接口为轻量看板入口单独暴露（仅看板字段，不含主体详情全字段则前端少接收）。
        AuditSubjectVo vo = auditSubjectService.getAuditSubjectInfo(subjectId);
        return Result.success(vo);
    }

    // ============================ 资金流水 /audit/flow ============================

    @GetMapping("/flow/list")
    @Operation(summary = "获取资金流水列表")
    @PreAuthorize("hasAuthority('knowhub:audit:flow:quarry')")
    public Result<PageInfo<AuditFundFlowVo>> quarryAuditFundFlow(AuditFundFlowQuarry quarry) {
        PageInfo<AuditFundFlowVo> pageInfo = auditFundFlowService.quarryAuditFundFlow(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/flow/{flowId}")
    @Operation(summary = "获取资金流水详情")
    @PreAuthorize("hasAuthority('knowhub:audit:flow:info')")
    public Result<AuditFundFlowVo> getAuditFundFlowInfo(@PathVariable Long flowId) {
        AuditFundFlowVo vo = auditFundFlowService.getAuditFundFlowInfo(flowId);
        return Result.success(vo);
    }

    @PostMapping("/flow")
    @Operation(summary = "新增资金流水（预算/收账/花销三流合一入口）")
    @Log(title = "资金流水", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:audit:flow:add')")
    public Result<Boolean> addFundFlow(@RequestBody AuditFundFlowVo vo) {
        Boolean b = auditFundFlowService.addFundFlow(vo);
        return Result.success(b);
    }

    @PutMapping("/flow")
    @Operation(summary = "编辑资金流水（仅草稿/待审核可编辑）")
    @Log(title = "资金流水", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:flow:edit')")
    public Result<Boolean> editAuditFundFlow(@RequestBody AuditFundFlowVo vo) {
        Boolean b = auditFundFlowService.editAuditFundFlow(vo);
        return Result.success(b);
    }

    @DeleteMapping("/flow/{flowId}")
    @Operation(summary = "删除资金流水（仅草稿/已驳回/已撤回可删）")
    @Log(title = "资金流水", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:audit:flow:delete')")
    public Result<Boolean> deleteAuditFundFlow(@PathVariable Long flowId) {
        Boolean b = auditFundFlowService.deleteAuditFundFlow(flowId);
        return Result.success(b);
    }

    @PostMapping("/flow/submit/{flowId}")
    @Operation(summary = "提交花销审批（草稿→待审核）")
    @Log(title = "资金流水", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:flow:review')")
    public Result<Boolean> submitExpenseFlow(@PathVariable Long flowId) {
        Boolean b = auditFundFlowService.submitExpenseFlow(flowId);
        return Result.success(b);
    }

    @PostMapping("/flow/approve")
    @Operation(summary = "审核通过花销（待审核→已通过）")
    @Log(title = "资金流水", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:flow:approve')")
    public Result<Boolean> approveFlow(@RequestBody ReviewVo vo) {
        Boolean b = auditFundFlowService.approveFlow(vo);
        return Result.success(b);
    }

    @PostMapping("/flow/reject")
    @Operation(summary = "审核驳回花销（待审核→已驳回，advice必填）")
    @Log(title = "资金流水", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:flow:reject')")
    public Result<Boolean> rejectFlow(@RequestBody ReviewVo vo) {
        Boolean b = auditFundFlowService.rejectFlow(vo);
        return Result.success(b);
    }

    @PutMapping("/flow/revoke/{flowId}")
    @Operation(summary = "撤回已通过花销（已通过→已撤回）")
    @Log(title = "资金流水", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:flow:revoke')")
    public Result<Boolean> revokeFlow(@PathVariable Long flowId) {
        Boolean b = auditFundFlowService.revokeFlow(flowId);
        return Result.success(b);
    }

    @GetMapping("/flow/review-log/{flowId}")
    @Operation(summary = "获取流水审核历史")
    @PreAuthorize("hasAuthority('knowhub:audit:flow:reviewLog')")
    public Result<List<AuditFlowReviewLogVo>> listFlowReviewLog(@PathVariable Long flowId) {
        List<AuditFlowReviewLogVo> list = auditFundFlowService.listReviewLog(flowId);
        return Result.success(list);
    }

    // ============================ 借出 /audit/loan ============================

    @GetMapping("/loan/list")
    @Operation(summary = "获取物品借出列表")
    @PreAuthorize("hasAuthority('knowhub:audit:loan:quarry')")
    public Result<PageInfo<AuditLoanVo>> quarryAuditLoan(AuditLoanQuarry quarry) {
        PageInfo<AuditLoanVo> pageInfo = auditLoanService.quarryAuditLoan(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/loan/{loanId}")
    @Operation(summary = "获取借出详情")
    @PreAuthorize("hasAuthority('knowhub:audit:loan:info')")
    public Result<AuditLoanVo> getAuditLoanInfo(@PathVariable Long loanId) {
        AuditLoanVo vo = auditLoanService.getAuditLoanInfo(loanId);
        return Result.success(vo);
    }

    @PostMapping("/loan")
    @Operation(summary = "新增物品借出（按借出审批开关决定 REQUEST 或 BORROWED）")
    @Log(title = "物品借出", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:audit:loan:add')")
    public Result<Boolean> addLoan(@RequestBody AuditLoanVo vo) {
        Boolean b = auditLoanService.addLoan(vo);
        return Result.success(b);
    }

    @PutMapping("/loan")
    @Operation(summary = "编辑借出（仅申请待审状态可编）")
    @Log(title = "物品借出", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:loan:edit')")
    public Result<Boolean> editAuditLoan(@RequestBody AuditLoanVo vo) {
        Boolean b = auditLoanService.editAuditLoan(vo);
        return Result.success(b);
    }

    @DeleteMapping("/loan/{loanId}")
    @Operation(summary = "删除借出（仅申请待审/已驳回可删）")
    @Log(title = "物品借出", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:audit:loan:delete')")
    public Result<Boolean> deleteAuditLoan(@PathVariable Long loanId) {
        Boolean b = auditLoanService.deleteAuditLoan(loanId);
        return Result.success(b);
    }

    @PostMapping("/loan/approve")
    @Operation(summary = "审核通过借出（申请待审→已借出）")
    @Log(title = "物品借出", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:loan:approve')")
    public Result<Boolean> approveLoan(@RequestBody ReviewVo vo) {
        Boolean b = auditLoanService.approveLoan(vo);
        return Result.success(b);
    }

    @PostMapping("/loan/reject")
    @Operation(summary = "审核驳回借出（申请待审→已驳回，advice必填）")
    @Log(title = "物品借出", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:loan:reject')")
    public Result<Boolean> rejectLoan(@RequestBody ReviewVo vo) {
        Boolean b = auditLoanService.rejectLoan(vo);
        return Result.success(b);
    }

    @PostMapping("/loan/return")
    @Operation(summary = "归还借出（已借出/逾期→已归还；wearLossAmount>0时事务内插损耗花销流水走阈值审批）")
    @Log(title = "物品借出", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:loan:return')")
    public Result<Boolean> returnLoan(@RequestBody AuditLoanVo vo) {
        Boolean b = auditLoanService.returnLoan(vo);
        return Result.success(b);
    }

    @GetMapping("/loan/review-log/{loanId}")
    @Operation(summary = "获取借出审核历史")
    @PreAuthorize("hasAuthority('knowhub:audit:loan:reviewLog')")
    public Result<List<AuditLoanReviewLogVo>> listLoanReviewLog(@PathVariable Long loanId) {
        List<AuditLoanReviewLogVo> list = auditLoanService.listReviewLog(loanId);
        return Result.success(list);
    }

    // ============================ 报表 /audit/report ============================

    @GetMapping("/report/list")
    @Operation(summary = "获取周期报表列表（含 periodEnded: 前端据此禁当期重算按钮）")
    @PreAuthorize("hasAuthority('knowhub:audit:report:quarry')")
    public Result<PageInfo<AuditPeriodReportVo>> quarryAuditPeriodReport(AuditPeriodReportQuarry quarry) {
        PageInfo<AuditPeriodReportVo> pageInfo = auditPeriodReportService.quarryAuditPeriodReport(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/report/{reportId}")
    @Operation(summary = "获取周期报表详情")
    @PreAuthorize("hasAuthority('knowhub:audit:report:info')")
    public Result<AuditPeriodReportVo> getAuditPeriodReportInfo(@PathVariable Long reportId) {
        AuditPeriodReportVo vo = auditPeriodReportService.getAuditPeriodReportInfo(reportId);
        return Result.success(vo);
    }

    @PostMapping("/report/regenerate")
    @Operation(summary = "重算周期报表（仅已结束期 period_end<now 可重算；当期拒绝）")
    @Log(title = "周期报表", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:audit:report:regenerate')")
    public Result<Boolean> regenerateReport(@RequestBody AuditPeriodReportVo vo) {
        Boolean b = auditPeriodReportService.regenerateReport(vo.getSubjectId(), vo.getPeriodType(), vo.getPeriodKey());
        return Result.success(b);
    }
}