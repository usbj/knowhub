package com.knowhub.mapper.audit;

import com.knowhub.pojo.audit.entity.AuditLoan;
import com.knowhub.pojo.audit.quarry.AuditLoanQuarry;
import com.knowhub.pojo.audit.vo.AuditLoanReviewLogVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 物品借出 Mapper。
 * 列表/详情查询带 subject_name(join audit_subject on subject_id)。
 * 借用人改为外部人员内联字段（borrower_name/phone/org/remark），不再 join sys_user。
 * 审核历史 listReviewLogByLoanId 直接返回 VO（join sys_user 带出 operatorNickname），Service 透传。
 * 逾期对账 listOverdueLoans 供 AuditLoanOverdueTask 扫表置 OVERDUE。
 */
@Mapper
public interface AuditLoanMapper {

    /** 列表查询（PageHelper 在 Service 层 startPage 拦截）；带 subject_name */
    List<AuditLoan> quarryAuditLoan(AuditLoanQuarry quarry);

    /** 详情：按主键取未删除借出（带 subject_name） */
    AuditLoan getAuditLoanInfo(Long loanId);

    /** 新增借出，回填主键 */
    Boolean insertAuditLoan(AuditLoan loan);

    /** 编辑借出（动态列，用于审批改 status / 归还回填 actualReturnDate 等） */
    Boolean updateAuditLoan(AuditLoan loan);

    /** 软删借出（deleted=1） */
    Boolean deleteAuditLoan(@Param("loanId") Long loanId);

    /**
     * 按借出ID查审核历史（按动作时间升序还原轨迹），直接返回 VO（join sys_user 带出 operatorNickname）。
     */
    List<AuditLoanReviewLogVo> listReviewLogByLoanId(Long loanId);

    /**
     * 逾期对账扫描：取所有 status=BORROWED 且 expected_return_date 早于 now 且未删的借出。
     * 供 AuditLoanOverdueTask 批量置 OVERDUE。expected_return_date 为空视为无限期不逾期。
     *
     * @param now 当前时间（由调用方传，避免 DB 时钟与服务时钟差异）
     */
    List<AuditLoan> listOverdueLoans(@Param("now") Date now);
}