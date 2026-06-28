package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.system.pojo.quarry.OperLogQuarry;
import com.rookie.system.pojo.vo.SysOperLogVo;

/**
 * 操作日志管理服务（供管理后台 Controller 调用：列表/详情/删除/清空）
 * 采集落库能力由 framework 的 com.rookie.framework.service.OperLogService 提供，由本接口实现类一并实现
 */
public interface SysOperLogService {

    /** 分页查询操作日志（含失败行的 errorLogId 关联） */
    PageInfo<SysOperLogVo> quarryOperLog(OperLogQuarry quarry);

    /** 查询操作日志详情 */
    SysOperLogVo getOperLogInfo(Long operId);

    /** 批量删除操作日志 */
    Boolean deleteOperLog(Long[] operIds);

    /** 清空操作日志 */
    Boolean cleanOperLog();
}
