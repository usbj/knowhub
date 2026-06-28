package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysErrorLog;
import com.rookie.system.pojo.quarry.ErrorLogQuarry;
import com.rookie.system.pojo.vo.SysErrorLogVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysErrorLogMapper {

    /** 分页查询错误日志 */
    List<SysErrorLogVo> quarryErrorLog(ErrorLogQuarry quarry);

    /**
     * 第二段：按操作日志主键批量查关联的错误日志（oper_log_id → error_id）
     * 用于操作日志列表拼装 errorLogId，1:1 最多返回入参数量条
     */
    List<SysErrorLogVo> getErrorLogByOperLogIds(@Param("operLogIds") List<Long> operLogIds);

    /** 新增错误日志，回写主键到 errorLog.errorId */
    Boolean addErrorLog(SysErrorLog errorLog);

    /** 按主键查询详情 */
    SysErrorLogVo getErrorLogInfoById(Long errorId);

    /** 批量删除（物理删除，日志清空场景） */
    Boolean deleteErrorLogByIds(@Param("errorIds") Long[] errorIds);

    /** 清空全部错误日志（物理删除） */
    Boolean cleanErrorLog();
}
