package com.knowhub.service.audit;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.json.JSONUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.enums.audit.PeriodType;
import com.knowhub.mapper.audit.AuditPeriodReportMapper;
import com.knowhub.mapper.audit.AuditSubjectMapper;
import com.knowhub.pojo.audit.entity.AuditPeriodReport;
import com.knowhub.pojo.audit.entity.AuditSubject;
import com.knowhub.pojo.audit.quarry.AuditPeriodReportQuarry;
import com.knowhub.pojo.audit.vo.AuditPeriodReportVo;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysNotice;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysNoticeMapper;
import com.rookie.system.mapper.SysNoticeUserRelMapper;
import com.rookie.system.pojo.SysNoticeUserRel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.IsoFields;
import java.time.temporal.TemporalAdjusters;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * 周期报表 Service 实现。
 *
 * 核心：聚合 + UPSERT + 通知。
 *
 * 周期解析：
 * - MONTH "yyyy-MM" → periodStart=月首, periodEnd=月末（YearMonth.atDay(1) / atEndOfMonth()）
 * - WEEK  "yyyy-'W'ww" → periodStart=该 ISO 周周一, periodEnd=该周周日（IsoFields.weekOfWeekBasedYear）
 *   ISO 周历：周一=首日，周日=末日；week 1 是含当年第一个周四的周。
 *
 * 聚合（PeriodReportMapper）：
 * - budget/income/expense 按期 [periodStart, periodEnd] 过滤 occur_date
 * - expense_by_category 按 category group 求 sum 拼 JSON（如 {"耗材":100.00,"差旅":200.00}）
 * - balance_end = subject.income_total − 历史已通过 EXPENSE 合计（occur_date <= periodEnd）
 * - loan_out_count 期内借出笔数；loan_unreturned 期末未归还（BORROWED/OVERDUE 且 borrow_date <= periodEnd）
 *
 * UPSERT：UNIQUE(subject_id, period_type, period_key)，存在则 update 覆盖（含软删行复位 deleted=0），否则 insert。
 *
 * 通知（generateForPreviousPeriod 定时任务用）：直走 SysNoticeMapper.addSysNotice + SysNoticeUserRelMapper
 * .insertSysNoticeUserRel，指定接收人=主体负责人(handler_id)。定时任务无登录态不走 addSysNoticeInfo
 * （其依赖 SecurityContextHolder），create_by/update_by 填 "system"。
 * 通知发送失败仅 warn 不阻断主流程（报表已落库，通知可后续补发；对齐 BlogServiceImpl.writeReviewLog 哲学）。
 */
@Service
public class AuditPeriodReportServiceImpl implements AuditPeriodReportService {

    private static final Logger log = LoggerFactory.getLogger(AuditPeriodReportServiceImpl.class);

    @Autowired
    private AuditPeriodReportMapper auditPeriodReportMapper;

    @Autowired
    private AuditSubjectMapper auditSubjectMapper;

    @Autowired
    private SysNoticeMapper sysNoticeMapper;

    @Autowired
    private SysNoticeUserRelMapper sysNoticeUserRelMapper;

    /** 系统操作者名（定时任务无登录态写审计列用） */
    private static final String SYSTEM_OPERATOR = "system";

    /** 通知类型：系统通知（notice_type 字典值，对齐 SysNoticeServiceImpl 约定用 'NOTICE'） */
    private static final String NOTICE_TYPE_SYSTEM = "NOTICE";

    /** 通知优先级：普通（level 字典值 'NORMAL'） */
    private static final String NOTICE_LEVEL_NORMAL = "NORMAL";

    /** 通知发布范围：指定成员（publish_scope 'USER'，配合 sys_notice_user_rel） */
    private static final String NOTICE_SCOPE_USER = "USER";

    /** 通知状态：已发布（status 'PUBLISHED'，定时任务直接发布） */
    private static final String NOTICE_STATUS_PUBLISHED = "PUBLISHED";

    /** 通知水平/置顶/需确认：默认 0/0（非置顶、不需确认） */
    private static final Integer NOTICE_NO_TOP = 0;

    private static final Integer NOTICE_NO_CONFIRM = 0;

    @Override
    public PageInfo<AuditPeriodReportVo> quarryAuditPeriodReport(AuditPeriodReportQuarry quarry) {
        PageUtil.startPage();
        List<AuditPeriodReport> list = auditPeriodReportMapper.quarryAuditPeriodReport(quarry);
        PageInfo<AuditPeriodReport> page = PageUtil.packagedPageInfo(list);
        PageInfo<AuditPeriodReportVo> voPage = PageUtil.copyPageInfo(page, AuditPeriodReportVo.class);
        // 回填 periodEnded 供前端禁用重算按钮
        Date now = new Date();
        for (AuditPeriodReportVo vo : voPage.getList()) {
            if (vo.getPeriodEnd() != null) {
                vo.setPeriodEnded(vo.getPeriodEnd().before(now));
            } else {
                vo.setPeriodEnded(false);
            }
        }
        return voPage;
    }

    @Override
    public AuditPeriodReportVo getAuditPeriodReportInfo(Long reportId) {
        AuditPeriodReport report = auditPeriodReportMapper.getAuditPeriodReportInfo(reportId);
        if (report == null) {
            throw new ServiceException(500, "报表不存在");
        }
        AuditPeriodReportVo vo = BeanUtil.toBean(report, AuditPeriodReportVo.class);
        // 回填 periodEnded
        if (report.getPeriodEnd() != null) {
            vo.setPeriodEnded(report.getPeriodEnd().before(new Date()));
        } else {
            vo.setPeriodEnded(false);
        }
        return vo;
    }

    @Override
    @Transactional
    public AuditPeriodReportVo generateReport(Long subjectId, String periodType, String periodKey, String operator) {
        PeriodType type = PeriodType.ofCode(periodType);
        if (type == null) {
            throw new ServiceException(500, "周期类型非法（MONTH 月度 / WEEK 周记）");
        }
        if (periodKey == null || periodKey.trim().isEmpty()) {
            throw new ServiceException(500, "周期键不能为空");
        }
        AuditSubject subject = auditSubjectMapper.getAuditSubjectInfo(subjectId);
        if (subject == null) {
            throw new ServiceException(500, "关联主体不存在");
        }

        // 解析周期起止
        PeriodRange range = parsePeriodRange(type, periodKey);

        // 聚合六大数据
        BigDecimal budget = auditPeriodReportMapper.sumFlowByType(subjectId, "BUDGET", range.start, range.end);
        BigDecimal income = auditPeriodReportMapper.sumFlowByType(subjectId, "INCOME", range.start, range.end);
        BigDecimal expense = auditPeriodReportMapper.sumFlowByType(subjectId, "EXPENSE", range.start, range.end);
        String expenseByCategory = buildExpenseByCategoryJson(subjectId, range.start, range.end);
        BigDecimal historicExpense = auditPeriodReportMapper.sumApprovedExpenseUntil(subjectId, range.end);
        BigDecimal historicIncome = auditPeriodReportMapper.sumIncomeUntil(subjectId, range.end);
        // balanceEnd 用截至 periodEnd 的实到 INCOME 合计 − 历史已通过 EXPENSE 合计（期末结余快照）。
        // 不用 subject.income_total 当前增量列，避免期后新增 INCOME 污染已结束期报表的结余快照。
        BigDecimal balanceEnd = nullToZero(historicIncome).subtract(nullToZero(historicExpense));
        Integer loanOutCount = auditPeriodReportMapper.countLoanOut(subjectId, range.start, range.end);
        Integer loanUnreturned = auditPeriodReportMapper.countLoanUnreturned(subjectId, range.end);

        Date now = new Date();
        AuditPeriodReport report = new AuditPeriodReport();
        report.setSubjectId(subjectId);
        report.setPeriodType(type.getCode());
        report.setPeriodKey(periodKey);
        report.setPeriodStart(range.start);
        report.setPeriodEnd(range.end);
        report.setBudgetAmount(nullToZero(budget));
        report.setIncomeAmount(nullToZero(income));
        report.setExpenseAmount(nullToZero(expense));
        report.setExpenseByCategory(expenseByCategory);
        report.setBalanceEnd(balanceEnd);
        report.setLoanOutCount(loanOutCount == null ? 0 : loanOutCount);
        report.setLoanUnreturned(loanUnreturned == null ? 0 : loanUnreturned);
        report.setGenerateTime(now);
        report.setCreateBy(operator);
        report.setUpdateBy(operator);

        // UPSERT：同键覆盖（含软删行复位 deleted=0）
        AuditPeriodReport exist = auditPeriodReportMapper.getBySubjectPeriod(subjectId, type.getCode(), periodKey);
        if (exist != null) {
            report.setReportId(exist.getReportId());
            report.setDeleted(0);
            auditPeriodReportMapper.updateAuditPeriodReport(report);
        } else {
            auditPeriodReportMapper.insertAuditPeriodReport(report);
        }

        AuditPeriodReportVo vo = BeanUtil.toBean(report, AuditPeriodReportVo.class);
        vo.setPeriodEnded(range.end.before(now));
        return vo;
    }

    @Override
    @Transactional
    public Boolean regenerateReport(Long subjectId, String periodType, String periodKey) {
        PeriodType type = PeriodType.ofCode(periodType);
        if (type == null) {
            throw new ServiceException(500, "周期类型非法（MONTH 月度 / WEEK 周记）");
        }
        PeriodRange range = parsePeriodRange(type, periodKey);
        // 重算校验：仅允许重算已结束期（period_end < now），当期拒绝
        if (!range.end.before(new Date())) {
            throw new ServiceException(500, "当前期未结束，不可重算（仅允许重算已结束的周期）");
        }
        UserInfo userInfo = currentUser();
        generateReport(subjectId, periodType, periodKey, userInfo.getUsername());
        return true;
    }

    @Override
    @Transactional
    public int generateForPreviousPeriod(String periodType) {
        PeriodType type = PeriodType.ofCode(periodType);
        if (type == null) {
            log.warn("[AuditPeriodReport] 非法周期类型 {}，跳过", periodType);
            return 0;
        }
        // 算上一期 periodKey（MONTH 上月 / WEEK 上周）
        LocalDate today = LocalDate.now();
        String periodKey;
        if (type == PeriodType.MONTH) {
            YearMonth lastMonth = YearMonth.from(today).minusMonths(1);
            periodKey = lastMonth.format(DateTimeFormatter.ofPattern("yyyy-MM"));
        } else {
            // WEEK：上周的 ISO 周键。today.minusWeeks(1) 落在上周某天，再取其 week-based-year + week。
            LocalDate lastWeekDay = today.minusWeeks(1);
            int weekBasedYear = lastWeekDay.get(IsoFields.WEEK_BASED_YEAR);
            int weekOfWeekBasedYear = lastWeekDay.get(IsoFields.WEEK_OF_WEEK_BASED_YEAR);
            periodKey = String.format("%d-W%02d", weekBasedYear, weekOfWeekBasedYear);
        }

        List<AuditSubject> subjects = auditSubjectMapper.listActiveSubjects();
        if (subjects == null || subjects.isEmpty()) {
            log.info("[AuditPeriodReport] 无 ACTIVE 主体，跳过 {} 生成", periodKey);
            return 0;
        }
        int count = 0;
        for (AuditSubject subject : subjects) {
            try {
                AuditPeriodReportVo vo = generateReport(subject.getSubjectId(), type.getCode(), periodKey, SYSTEM_OPERATOR);
                count++;
                // 通知负责人（handler_id），失败仅 warn 不阻断
                notifyReportReady(subject, type, periodKey, vo);
            } catch (Exception e) {
                // 单主体失败不影响其他主体生成
                log.error("[AuditPeriodReport] 主体 {} 生成 {} 报表失败", subject.getSubjectId(), periodKey, e);
            }
        }
        log.info("[AuditPeriodReport] {} {} 共生成 {} 份报表", type.getCode(), periodKey, count);
        return count;
    }

    // ============================ 私有辅助 ============================

    /**
     * 把 EXPENSE 分类聚合结果拼成 JSON 字符串存 expense_by_category。
     * Mapper.sumExpenseByCategory 返回 List&lt;Map{category,total}&gt;，转 {category: total} JSON。
     * 空聚合返回 "{}"（保证非空 JSON，前端解析不需判 null）。
     */
    private String buildExpenseByCategoryJson(Long subjectId, Date periodStart, Date periodEnd) {
        List<Map<String, Object>> rows = auditPeriodReportMapper.sumExpenseByCategory(subjectId, periodStart, periodEnd);
        if (rows == null || rows.isEmpty()) {
            return "{}";
        }
        Map<String, Object> categoryMap = new HashMap<>();
        for (Map<String, Object> row : rows) {
            // MySQL 列别名小写：category / total
            Object cat = row.get("category");
            Object total = row.get("total");
            if (cat == null) {
                continue;
            }
            categoryMap.put(cat.toString(), total);
        }
        return JSONUtil.toJsonStr(categoryMap);
    }

    /**
     * 解析周期键为起止日期。
     * MONTH "yyyy-MM" → 月首 ~ 月末
     * WEEK  "yyyy-'W'ww" → 该 ISO 周周一 ~ 周日
     *
     * @return PeriodRange（start 为含 00:00:00 的 Date，end 为当日末 23:59:59 的 Date，便于 occur_date DATE 比较与 period_end < now 判定）
     */
    private PeriodRange parsePeriodRange(PeriodType type, String periodKey) {
        ZoneId zone = ZoneId.systemDefault();
        if (type == PeriodType.MONTH) {
            YearMonth ym;
            try {
                ym = YearMonth.parse(periodKey, DateTimeFormatter.ofPattern("yyyy-MM"));
            } catch (Exception e) {
                throw new ServiceException(500, "月度周期键格式应为 yyyy-MM，如 2026-07");
            }
            LocalDate start = ym.atDay(1);
            LocalDate end = ym.atEndOfMonth();
            return new PeriodRange(toDate(start), toEndOfDay(end));
        } else {
            // WEEK "yyyy-Www"
            int dashIndex = periodKey.indexOf("-W");
            if (dashIndex <= 0 || dashIndex != periodKey.length() - 4) {
                throw new ServiceException(500, "周记周期键格式应为 yyyy-'W'ww，如 2026-W32");
            }
            try {
                int year = Integer.parseInt(periodKey.substring(0, dashIndex));
                int week = Integer.parseInt(periodKey.substring(dashIndex + 2));
                // 用该年 1 月 4 日（必落在 ISO week 1 内）回退到周一作为第 1 周起点，再加 (week-1) 周
                LocalDate jan4 = LocalDate.of(year, 1, 4);
                LocalDate week1Mon = jan4.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
                LocalDate start = week1Mon.plusWeeks(week - 1);
                LocalDate end = start.plusDays(6);
                return new PeriodRange(toDate(start), toEndOfDay(end));
            } catch (NumberFormatException e) {
                throw new ServiceException(500, "周记周期键格式应为 yyyy-'W'ww，如 2026-W32");
            }
        }
    }

    /** LocalDate → Date（00:00:00） */
    private Date toDate(LocalDate date) {
        return Date.from(date.atStartOfDay(ZoneId.systemDefault()).toInstant());
    }

    /** LocalDate → Date（当日 23:59:59），period_end < now 判定时确保当日结束才算"已结束" */
    private Date toEndOfDay(LocalDate date) {
        return Date.from(date.atTime(23, 59, 59).atZone(ZoneId.systemDefault()).toInstant());
    }

    /** BigDecimal null 兜底 0 */
    private BigDecimal nullToZero(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }

    /**
     * 发通知给主体负责人：报表已生成。
     * 走 SysNoticeMapper.addSysNotice + SysNoticeUserRelMapper.insertSysNoticeUserRel 指定接收人。
     * 定时任务无登录态不改用 addSysNoticeInfo（其依赖 SecurityContextHolder）；create_by/update_by 填 "system"。
     * 失败仅 warn 不阻断主流程（报表已落库，通知可后续补发）。
     */
    private void notifyReportReady(AuditSubject subject, PeriodType type, String periodKey, AuditPeriodReportVo vo) {
        try {
            if (subject.getHandlerId() == null) {
                log.warn("[AuditPeriodReport] 主体 {} 无负责人(handler_id)，跳过通知", subject.getSubjectId());
                return;
            }
            Date now = new Date();
            String title = String.format("审计报表已生成：%s %s（%s）",
                    type.getLabel(), periodKey, subject.getName() == null ? "" : subject.getName());
            String periodLabel = String.format("%s ~ %s", formatDate(vo.getPeriodStart()), formatDate(vo.getPeriodEnd()));
            String content = String.format(
                    "主体「%s」的%s报表（%s）已生成。\n" +
                            "预算合计：%s 元\n实到合计：%s 元\n已花合计：%s 元\n期末结余：%s 元\n" +
                            "本期借出 %d 笔，期末未归还 %d 笔。\n" +
                            "生成时间：%s。请前往 后台 > 审计管理 > 周期报表 查看。",
                    subject.getName() == null ? "" : subject.getName(),
                    type.getLabel(), periodLabel,
                    formatMoney(vo.getBudgetAmount()), formatMoney(vo.getIncomeAmount()),
                    formatMoney(vo.getExpenseAmount()), formatMoney(vo.getBalanceEnd()),
                    vo.getLoanOutCount() == null ? 0 : vo.getLoanOutCount(),
                    vo.getLoanUnreturned() == null ? 0 : vo.getLoanUnreturned(),
                    formatDate(now));

            SysNotice notice = new SysNotice();
            notice.setTitle(title);
            notice.setContent(content);
            notice.setNoticeType(NOTICE_TYPE_SYSTEM);
            notice.setLevel(NOTICE_LEVEL_NORMAL);
            notice.setPublishScope(NOTICE_SCOPE_USER);
            notice.setStatus(NOTICE_STATUS_PUBLISHED);
            notice.setIsTop(NOTICE_NO_TOP);
            notice.setNeedConfirm(NOTICE_NO_CONFIRM);
            notice.setPublishTime(now);
            notice.setCreateBy(SYSTEM_OPERATOR);
            notice.setUpdateBy(SYSTEM_OPERATOR);
            notice.setCreateTime(now);
            notice.setUpdateTime(now);
            notice.setRoutePath("/knowhub/audit/report/index");
            sysNoticeMapper.addSysNotice(notice);

            // 指定接收人=主体负责人
            SysNoticeUserRel rel = new SysNoticeUserRel();
            rel.setNoticeId(notice.getNoticeId());
            rel.setUserId(subject.getHandlerId());
            sysNoticeUserRelMapper.insertSysNoticeUserRel(Collections.singletonList(rel));
        } catch (Exception e) {
            log.warn("[AuditPeriodReport] 发送报表通知失败 subject={} period={}: {}",
                    subject.getSubjectId(), periodKey, e.getMessage());
        }
    }

    private String formatDate(Date d) {
        if (d == null) {
            return "";
        }
        return java.text.DateFormat.getDateInstance().format(d);
    }

    private String formatMoney(BigDecimal amount) {
        return (amount == null ? BigDecimal.ZERO : amount).toPlainString();
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }

    /** 周期起止范围内部载体 */
    private static class PeriodRange {
        final Date start;
        final Date end;

        PeriodRange(Date start, Date end) {
            this.start = start;
            this.end = end;
        }
    }
}