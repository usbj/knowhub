package com.rookie.system.mapper;

import com.rookie.common.pojo.entity.SysOperLog;
import com.rookie.system.pojo.quarry.OperLogQuarry;
import com.rookie.system.pojo.vo.SysOperLogVo;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface SysOperLogMapper {

    /** 分页查询操作日志（第一段：操作日志表单独分页，返回 VO 便于承接 errorLogId） */
    List<SysOperLogVo> quarryOperLog(OperLogQuarry quarry);

    /** 新增操作日志，回写主键到 operLog.operId */
    Boolean addOperLog(SysOperLog operLog);

    /** 按主键查询详情 */
    SysOperLogVo getOperLogInfoById(Long operId);

    /** 批量删除（物理删除，日志清空场景） */
    Boolean deleteOperLogByIds(@Param("operIds") Long[] operIds);

    /** 清空全部操作日志（物理删除） */
    Boolean cleanOperLog();
}
