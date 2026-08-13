package com.knowhub.mapper.audit;

import com.knowhub.pojo.audit.entity.AuditPeriodReport;
import com.knowhub.pojo.audit.quarry.AuditPeriodReportQuarry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.Date;
import java.util.List;
import java.util.Map;

/**
 * 周期报表 Mapper。
 * 列表/详情查询带 subject_name(join audit_subject) + handler_nickname(join sys_user via subject)。
 * 聚合查询（供 Service.generateReport 内部组合数据，不入接口）：
 * - sumFlowByType       按期聚合某 flow_type 的 APPROVED 合计（BUDGET/INCOME 恒 APPROVED；EXPENSE 仅 APPROVED）
 * - sumExpenseByCategory 按期聚合 EXPENSE 各分类合计（键 category，值 amount，Service 拼 JSON 存 expense_by_category）
 * - sumApprovedExpenseUntil 聚合截至 periodEnd 的历史已通过 EXPENSE 合计（算 balanceEnd 用）
 * - countLoanOut        本期借出笔数（borrow_date 落在期内）
 * - countLoanUnreturned 本期末未归还笔数（status IN BORROWED/OVERDUE 且 borrow_date <= periodEnd）
 *
 * 生成/重算用 insertOrUpdate：UNIQUE(subject_id,period_type,period_key) 存在则 update 覆盖，否则 insert。
 *
 * 通知落库走 addSysNotice/SysNoticeUserRelMapper.insertSysNoticeUserRel 直插（定时任务无登录态，
 * 不走 SysNoticeServiceImpl.addSysNoticeInfo 依赖 SecurityContext）。此 Mapper 仅承报表数据读写，
 * 不耦合 SysNotice；通知发送由 Service 持 SysNoticeMapper/SysNoticeUserRelMapper 注入完成。
 */
@Mapper
public interface AuditPeriodReportMapper {

    /** 列表查询（PageHelper 在 Service 层 startPage 拦截）；带 subject_name/handler_nickname */
    List<AuditPeriodReport> quarryAuditPeriodReport(AuditPeriodReportQuarry quarry);

    /** 详情：按主键取未删除报表（带 subject_name/handler_nickname） */
    AuditPeriodReport getAuditPeriodReportInfo(Long reportId);

    /**
     * 按 (subjectId, periodType, periodKey) 取已存在的报表（重算前查同键覆盖用）。
     * 不论 deleted 状态（软删后同键重算也覆盖恢复）。
     */
    AuditPeriodReport getBySubjectPeriod(@Param("subjectId") Long subjectId,
                                          @Param("periodType") String periodType,
                                          @Param("periodKey") String periodKey);

    /** 新增报表，回填主键 */
    Boolean insertAuditPeriodReport(AuditPeriodReport report);

    /** 按主键更新报表（重算覆盖同键用，动态列） */
    Boolean updateAuditPeriodReport(AuditPeriodReport report);

    /** 软删报表（deleted=1） */
    Boolean deleteAuditPeriodReport(@Param("reportId") Long reportId);

    // ============================ 聚合查询（供 generateReport 内部组合，不入接口） ============================

    /**
     * 按期聚合某 flow_type 的流水合计。
     * BUDGET/INCOME 全计入（status 恒 APPROVED）；EXPENSE 仅计 status=APPROVED。
     * 落库 deleted=0 全计；按 occur_date 落在 [periodStart, periodEnd] 内过滤。
     *
     * @param flowType BUDGET / INCOME / EXPENSE
     */
    BigDecimal sumFlowByType(@Param("subjectId") Long subjectId,
                             @Param("flowType") String flowType,
                             @Param("periodStart") Date periodStart,
                             @Param("periodEnd") Date periodEnd);

    /**
     * 按期聚合 EXPENSE 各分类合计（仅 status=APPROVED）。
     * 返回每行一个 Map{category, total}，Service 拼 JSON 存 expense_by_category。
     */
    List<Map<String, Object>> sumExpenseByCategory(@Param("subjectId") Long subjectId,
                                                   @Param("periodStart") Date periodStart,
                                                   @Param("periodEnd") Date periodEnd);

    /**
     * 聚合截至截止日的历史已通过 EXPENSE 合计（算 balanceEnd 用：截至 periodEnd 的实到 − 此合计 = 期末结余）。
     * 只统计 occur_date <= periodEnd 且 status=APPROVED 且 deleted=0 的 EXPENSE。
     */
    BigDecimal sumApprovedExpenseUntil(@Param("subjectId") Long subjectId,
                                        @Param("periodEnd") Date periodEnd);

    /**
     * 聚合截至截止日的历史 INCOME 合计（算 balanceEnd 用，截至 periodEnd 实到钱）。
     * INCOME 恒 APPROVED，按 occur_date <= periodEnd 累加全部未删 INCOME。
     * 用历史聚合而非 subject.income_total 当前增量列，避免期后新增 INCOME 污染已结束期报表的结余快照。
     */
    BigDecimal sumIncomeUntil(@Param("subjectId") Long subjectId,
                              @Param("periodEnd") Date periodEnd);

    /** 本期借出笔数（borrow_date 落在 [periodStart, periodEnd] 内，未删） */
    Integer countLoanOut(@Param("subjectId") Long subjectId,
                         @Param("periodStart") Date periodStart,
                         @Param("periodEnd") Date periodEnd);

    /**
     * 本期末未归还笔数（status IN BORROWED/OVERDUE 且 borrow_date <= periodEnd，未删）。
     * 逾期态也算未归还（OVERDUE 是 BORROWED 演化态，物品仍在外）。
     */
    Integer countLoanUnreturned(@Param("subjectId") Long subjectId,
                                @Param("periodEnd") Date periodEnd);
}