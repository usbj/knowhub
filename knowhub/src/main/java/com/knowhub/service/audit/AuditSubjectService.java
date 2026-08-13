package com.knowhub.service.audit;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.audit.quarry.AuditSubjectQuarry;
import com.knowhub.pojo.audit.vo.AuditSubjectVo;

import java.math.BigDecimal;

/**
 * 花销主体 Service。
 * 接口只暴露 DTO/VO，不暴露实体（对齐 blog/resource 模块约定）。
 * 权限不分等级（审计模块内部使用），按钮权限由 Controller @PreAuthorize 兜底，Service 层不重复校验。
 */
public interface AuditSubjectService {

    /** 列表查询（分页） */
    PageInfo<AuditSubjectVo> quarryAuditSubject(AuditSubjectQuarry quarry);

    /** 详情（含 balance/monthExpense 聚合回填 + handlerNickname join 带出） */
    AuditSubjectVo getAuditSubjectInfo(Long subjectId);

    /** 新增主体 */
    Boolean addAuditSubject(AuditSubjectVo vo);

    /** 编辑主体（动态列） */
    Boolean editAuditSubject(AuditSubjectVo vo);

    /** 删除主体（软删 deleted=1） */
    Boolean deleteAuditSubject(Long subjectId);

    /**
     * 聚合查询：当前结余 = income_total − 历史已通过 EXPENSE 合计。
     * 供看板 balance 展示与 flow 写入时额度校验。
     */
    BigDecimal getBalance(Long subjectId);

    /**
     * 聚合查询：当月已花合计（status=APPROVED 的 EXPENSE，按 occur_date 落在指定年月）。
     * yearMonth 格式 "yyyy-MM"；供看板月度开销展示。
     */
    BigDecimal getMonthExpense(Long subjectId, String yearMonth);
}