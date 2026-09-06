package com.knowhub.service.audit;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.enums.audit.SubjectScope;
import com.knowhub.mapper.audit.AuditSubjectMapper;
import com.knowhub.pojo.audit.entity.AuditSubject;
import com.knowhub.pojo.audit.quarry.AuditSubjectQuarry;
import com.knowhub.pojo.audit.vo.AuditSubjectVo;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.List;

/**
 * 花销主体 Service 实现。
 *
 * 资金池模型（设计核心）：
 * - budget_total/income_total 在 flow 写入时由 AuditFundFlowService 事务内原子累加，本 Service 不直接改这两列。
 * - balance 不存列，查时算 = income_total − 历史已通过 EXPENSE 合计（getBalance 聚合）。
 * - monthExpense 当月已花合计（getMonthExpense 按 yearMonth 聚合）。
 * 详情接口同时回填 balance/monthExpense，看板直接用。
 *
 * 审计列：add/edit 取 SecurityContextHolder 的 UserInfo 填 createBy/updateBy（username 快照）。
 * 软删走 deleted=1，回填 updateBy。
 * 权限不分等级（审计内部使用），按钮权限由 Controller @PreAuthorize 兜底，Service 层不重复校验。
 */
@Service
public class AuditSubjectServiceImpl implements AuditSubjectService {

    @Autowired
    AuditSubjectMapper auditSubjectMapper;

    @Override
    public PageInfo<AuditSubjectVo> quarryAuditSubject(AuditSubjectQuarry quarry) {
        PageUtil.startPage();
        List<AuditSubject> list = auditSubjectMapper.quarryAuditSubject(quarry);
        PageInfo<AuditSubject> page = PageUtil.packagedPageInfo(list);
        PageInfo<AuditSubjectVo> voPage = PageUtil.copyPageInfo(page, AuditSubjectVo.class);
        // 列表回填 balance + monthExpense（看板列常显），按 subjectId 批量聚合避免 N+1。
        // 列表通常量小，此处逐条聚合可接受；若量大可后续改批量查询（对齐 resource 列表聚合范式）。
        for (AuditSubjectVo vo : voPage.getList()) {
            if (vo.getSubjectId() != null) {
                vo.setBalance(getBalance(vo.getSubjectId()));
                vo.setMonthExpense(getMonthExpense(vo.getSubjectId(), currentYearMonth()));
            }
        }
        return voPage;
    }

    @Override
    public AuditSubjectVo getAuditSubjectInfo(Long subjectId) {
        AuditSubject subject = auditSubjectMapper.getAuditSubjectInfo(subjectId);
        if (subject == null) {
            throw new ServiceException(500, "主体不存在");
        }
        AuditSubjectVo vo = BeanUtil.toBean(subject, AuditSubjectVo.class);
        // 聚合回填 balance + monthExpense（非表字段，详情接口带出供看板/编辑回显）
        vo.setBalance(getBalance(subjectId));
        vo.setMonthExpense(getMonthExpense(subjectId, currentYearMonth()));
        return vo;
    }

    @Override
    @Transactional
    public Boolean addAuditSubject(AuditSubjectVo vo) {
        if (vo.getName() == null || vo.getName().isEmpty()) {
            throw new ServiceException(500, "主体名不能为空");
        }
        if (vo.getScope() == null || SubjectScope.ofCode(vo.getScope()) == null) {
            throw new ServiceException(500, "主体范围非法（LAB/PROJECT）");
        }
        if (vo.getHandlerId() == null) {
            throw new ServiceException(500, "负责人不能为空");
        }
        AuditSubject subject = BeanUtil.toBean(vo, AuditSubject.class);
        UserInfo userInfo = currentUser();
        subject.setCreateBy(userInfo.getUsername());
        subject.setUpdateBy(userInfo.getUsername());
        subject.setCreateTime(new Date());
        subject.setUpdateTime(new Date());
        // 新建主体 budget/income 默认 0（DB 已有 DEFAULT 0.00，此处显式置零避免前端误传累计值）
        if (subject.getBudgetTotal() == null) {
            subject.setBudgetTotal(BigDecimal.ZERO);
        }
        if (subject.getIncomeTotal() == null) {
            subject.setIncomeTotal(BigDecimal.ZERO);
        }
        if (subject.getStatus() == null || subject.getStatus().isEmpty()) {
            subject.setStatus("ACTIVE");
        }
        try {
            auditSubjectMapper.insertAuditSubject(subject);
        } catch (Exception e) {
            throw new ServiceException(500, "主体添加失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean editAuditSubject(AuditSubjectVo vo) {
        if (vo.getSubjectId() == null) {
            throw new ServiceException(500, "主体ID不能为空");
        }
        AuditSubject exist = auditSubjectMapper.getAuditSubjectInfo(vo.getSubjectId());
        if (exist == null) {
            throw new ServiceException(500, "主体不存在");
        }
        if (vo.getScope() != null && SubjectScope.ofCode(vo.getScope()) == null) {
            throw new ServiceException(500, "主体范围非法（LAB/PROJECT）");
        }
        AuditSubject subject = BeanUtil.toBean(vo, AuditSubject.class);
        UserInfo userInfo = currentUser();
        subject.setUpdateBy(userInfo.getUsername());
        // 编辑不允许直接改 budget_total/income_total（由写入 flow 事务内原子累加）；置空避免覆盖累计列。
        subject.setBudgetTotal(null);
        subject.setIncomeTotal(null);
        try {
            auditSubjectMapper.updateAuditSubject(subject);
        } catch (Exception e) {
            throw new ServiceException(500, "主体修改失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteAuditSubject(Long subjectId) {
        AuditSubject exist = auditSubjectMapper.getAuditSubjectInfo(subjectId);
        if (exist == null) {
            throw new ServiceException(500, "主体不存在");
        }
        // 软删前置校验：CLOSED 主体或无关联流水才允许删（避免误删活跃资金池）。
        // 本批次暂不强制查关联流水（flow 表已独立软删）；后续可加 sumApprovedExpense>0 拒删校验。
        UserInfo userInfo = currentUser();
        try {
            auditSubjectMapper.deleteAuditSubject(subjectId, userInfo.getUsername());
        } catch (Exception e) {
            throw new ServiceException(500, "主体删除失败", e.getMessage());
        }
        return true;
    }

    @Override
    public BigDecimal getBalance(Long subjectId) {
        AuditSubject subject = auditSubjectMapper.getAuditSubjectInfo(subjectId);
        if (subject == null) {
            return BigDecimal.ZERO;
        }
        BigDecimal incomeTotal = subject.getIncomeTotal() == null ? BigDecimal.ZERO : subject.getIncomeTotal();
        BigDecimal approvedExpense = auditSubjectMapper.sumApprovedExpense(subjectId);
        if (approvedExpense == null) {
            approvedExpense = BigDecimal.ZERO;
        }
        // balance = income_total − 历史已通过 EXPENSE 合计
        return incomeTotal.subtract(approvedExpense);
    }

    @Override
    public BigDecimal getMonthExpense(Long subjectId, String yearMonth) {
        if (yearMonth == null || yearMonth.isEmpty()) {
            yearMonth = currentYearMonth();
        }
        BigDecimal sum = auditSubjectMapper.sumMonthExpense(subjectId, yearMonth);
        return sum == null ? BigDecimal.ZERO : sum;
    }

    // ============================ 私有辅助 ============================

    /** 当前年月字符串 "yyyy-MM"，用于看板月度开销默认查询 */
    private String currentYearMonth() {
        return LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM"));
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}