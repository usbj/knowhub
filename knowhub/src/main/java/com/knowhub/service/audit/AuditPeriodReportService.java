package com.knowhub.service.audit;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.audit.quarry.AuditPeriodReportQuarry;
import com.knowhub.pojo.audit.vo.AuditPeriodReportVo;

/**
 * 周期报表 Service。
 * 接口只暴露 DTO/VO，不暴露实体（对齐 blog/resource/flow/loan 模块约定）。
 *
 * 生成/重算合一 generateReport(subjectId, periodType, periodKey, operator)：
 *   1. 按 periodKey 解析 periodStart/periodEnd（MONTH "yyyy-MM" 月首~月末；WEEK "yyyy-'W'ww" 周一~周日）
 *   2. 聚合 flow（BUDGET/INCOME/EXPENSE 合计 + EXPENSE 分类汇总 JSON）+ loan（笔数/未归还）
 *   3. balanceEnd = subject.income_total − 历史已通过 EXPENSE 合计（截至 periodEnd）
 *   4. UPSERT：UNIQUE(subject_id,period_type,period_key) 存在则 update 覆盖，否则 insert
 *
 * 定时任务入口 generateForPreviousPeriod(periodType)：遍历所有 ACTIVE 主体生成上一期（MONTH 上月/WEEK 上周），
 * 生成后通知负责人（走 SysNoticeMapper insert + SysNoticeUserRelMapper 指定接收人，无登录态不走 addSysNoticeInfo）。
 *
 * 手动重算 regenerateReport(periodType, periodKey)：仅允许重算已结束期（period_end &lt; now），
 * 当期拒绝；调用 generateReport 用 operator=当前登录人。
 *
 * 权限不分等级（审计内部使用），Service 层不重复校验权限，由 Controller @PreAuthorize 兜底。
 */
public interface AuditPeriodReportService {

    /** 列表查询（分页） */
    PageInfo<AuditPeriodReportVo> quarryAuditPeriodReport(AuditPeriodReportQuarry quarry);

    /** 详情（含 subjectName/handlerNickname join 带出，periodEnded 回填供前端禁用重算按钮） */
    AuditPeriodReportVo getAuditPeriodReportInfo(Long reportId);

    /**
     * 生成/重算报表（UPSERT 同键覆盖）。
     * 供 regenerate 接口与定时任务共用：operator 为操作人 username（定时任务传 "system"）。
     *
     * @param subjectId  关联主体
     * @param periodType MONTH / WEEK
     * @param periodKey  MONTH "yyyy-MM" / WEEK "yyyy-'W'ww"
     * @param operator   操作人 username 快照（写入 create_by/update_by）
     * @return 生成的 report（含 reportId，用于后续通知等可选逻辑）
     */
    AuditPeriodReportVo generateReport(Long subjectId, String periodType, String periodKey, String operator);

    /**
     * 重算报表（手动入口，仅允许已结束期）。
     * 校验 period_end &lt; now，当期拒绝；通过则委托 generateReport 用当前登录人。
     *
     * @param subjectId  关联主体
     * @param periodType MONTH / WEEK
     * @param periodKey  周期键
     */
    Boolean regenerateReport(Long subjectId, String periodType, String periodKey);

    /**
     * 定时任务入口：遍历所有 ACTIVE 主体生成上一期报表并通知负责人。
     *
     * @param periodType MONTH 生成上月 / WEEK 生成上周
     * @return 实际生成（含覆盖）的报表条数
     */
    int generateForPreviousPeriod(String periodType);
}