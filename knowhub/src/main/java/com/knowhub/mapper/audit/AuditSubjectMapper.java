package com.knowhub.mapper.audit;

import com.knowhub.pojo.audit.entity.AuditSubject;
import com.knowhub.pojo.audit.quarry.AuditSubjectQuarry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.util.List;

/**
 * 花销主体 Mapper。
 * 列表查询带 handler_nickname（join sys_user on handler_id）。
 * balance/monthExpense 非表字段不进 resultMap，由 Service 层聚合回填（聚合查询走 sumApprovedExpense/sumMonthExpense）。
 */
@Mapper
public interface AuditSubjectMapper {

    /** 列表查询（PageHelper 在 Service 层 startPage 拦截）；带 handler_nickname */
    List<AuditSubject> quarryAuditSubject(AuditSubjectQuarry quarry);

    /** 详情：按主键取未删除主体（带 handler_nickname） */
    AuditSubject getAuditSubjectInfo(Long subjectId);

    /** 新增主体，回填主键 */
    Boolean insertAuditSubject(AuditSubject subject);

    /** 编辑主体（动态列） */
    Boolean updateAuditSubject(AuditSubject subject);

    /** 软删主体（deleted=1，回填 updateBy） */
    Boolean deleteAuditSubject(@Param("subjectId") Long subjectId, @Param("updateBy") String updateBy);

    /**
     * 取所有未删除且 ACTIVE 的主体（供报表定时任务遍历生成，不含软删/CLOSED）。
     * 不带分页全量返回；审计模块内部主体数量有限，无需分页。
     */
    List<AuditSubject> listActiveSubjects();

    /**
     * 原子累加预算累计列（BUDGET 写入时事务内调用）。
     * budget_total = budget_total + amount；amount 为正即追加预算，为负即下调。
     */
    Boolean addBudgetTotal(@Param("subjectId") Long subjectId, @Param("amount") BigDecimal amount);

    /**
     * 原子累加实到累计列（INCOME 写入时事务内调用）。
     * income_total = income_total + amount；amount 为正即到账，为负即冲红。
     */
    Boolean addIncomeTotal(@Param("subjectId") Long subjectId, @Param("amount") BigDecimal amount);

    /**
     * 聚合历史已通过 EXPENSE 合计（算 balance 用：income_total − 此合计 = 结余）。
     * 只统计 status=APPROVED 且 deleted=0 的 EXPENSE 流水。
     */
    BigDecimal sumApprovedExpense(@Param("subjectId") Long subjectId);

    /**
     * 聚合当月已花合计（看板月度开销用）。
     * yearMonth 格式 "yyyy-MM"；统计 status=APPROVED 且 occur_date 在该月且 deleted=0 的 EXPENSE 流水。
     */
    BigDecimal sumMonthExpense(@Param("subjectId") Long subjectId, @Param("yearMonth") String yearMonth);
}