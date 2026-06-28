package com.rookie.system.service;

import com.github.pagehelper.PageInfo;
import com.rookie.system.pojo.quarry.ErrorLogQuarry;
import com.rookie.system.pojo.vo.SysErrorLogVo;

/**
 * 错误日志管理服务（供管理后台 Controller 调用：列表/详情/删除/清空）
 * 采集落库能力由 framework 的 com.rookie.framework.service.ErrorLogService 提供，由本接口实现类一并实现
 */
public interface SysErrorLogService {

    /** 分页查询错误日志 */
    PageInfo<SysErrorLogVo> quarryErrorLog(ErrorLogQuarry quarry);

    /** 查询错误日志详情 */
    SysErrorLogVo getErrorLogInfo(Long errorId);

    /** 批量删除错误日志 */
    Boolean deleteErrorLog(Long[] errorIds);

    /** 清空错误日志 */
    Boolean cleanErrorLog();
}
