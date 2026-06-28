package com.rookie.system.service.impl;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.entity.SysErrorLog;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.service.ErrorLogService;
import com.rookie.system.mapper.SysErrorLogMapper;
import com.rookie.system.pojo.quarry.ErrorLogQuarry;
import com.rookie.system.pojo.vo.SysErrorLogVo;
import com.rookie.system.service.SysErrorLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 错误日志服务实现
 * 同时实现两个接口：
 *   - SysErrorLogService：管理后台的列表/详情/删除/清空
 *   - ErrorLogService(framework)：统一错误采集落库入口，供 GlobalExceptionHandler / 定时任务 / 异步任务等调用
 */
@Service
public class SysErrorLogServiceImpl implements SysErrorLogService, ErrorLogService {

    @Autowired
    private SysErrorLogMapper sysErrorLogMapper;

    /**
     * 异步保存错误日志：走 logExecutor 线程池，不阻塞当前流程
     * 调用方在各自线程内已填充来源/异常/简述等字段，本方法只管落库
     */
    @Async("logExecutor")
    @Override
    public void saveAsync(SysErrorLog errorLog) {
        sysErrorLogMapper.addErrorLog(errorLog);
    }

    // ============ 管理后台（供 Controller 调用） ============

    @Override
    public PageInfo<SysErrorLogVo> quarryErrorLog(ErrorLogQuarry quarry) {
        PageUtil.startPage();
        List<SysErrorLogVo> list = sysErrorLogMapper.quarryErrorLog(quarry);
        return PageUtil.packagedPageInfo(list);
    }

    @Override
    public SysErrorLogVo getErrorLogInfo(Long errorId) {
        return sysErrorLogMapper.getErrorLogInfoById(errorId);
    }

    @Override
    public Boolean deleteErrorLog(Long[] errorIds) {
        return sysErrorLogMapper.deleteErrorLogByIds(errorIds);
    }

    @Override
    public Boolean cleanErrorLog() {
        return sysErrorLogMapper.cleanErrorLog();
    }
}
