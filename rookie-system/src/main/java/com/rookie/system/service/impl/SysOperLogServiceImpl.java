package com.rookie.system.service.impl;

import com.github.pagehelper.PageInfo;
import com.rookie.common.pojo.entity.SysOperLog;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.service.OperLogService;
import com.rookie.system.mapper.SysErrorLogMapper;
import com.rookie.system.mapper.SysOperLogMapper;
import com.rookie.system.pojo.quarry.OperLogQuarry;
import com.rookie.system.pojo.vo.SysErrorLogVo;
import com.rookie.system.pojo.vo.SysOperLogVo;
import com.rookie.system.service.SysOperLogService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 操作日志服务实现
 * 同时实现两个接口：
 *   - SysOperLogService：管理后台的列表/详情/删除/清空
 *   - OperLogService(framework)：切面 LogAspect 的采集落库入口（saveAndGetId 同步拿主键 / saveAsync 异步落库）
 */
@Service
public class SysOperLogServiceImpl implements SysOperLogService, OperLogService {

    @Autowired
    private SysOperLogMapper sysOperLogMapper;

    @Autowired
    private SysErrorLogMapper sysErrorLogMapper;

    // ============ 采集落库（供 LogAspect 调用） ============

    /**
     * 同步保存并返回主键：失败路径用，切面需拿 oper_id 塞进 request 供错误日志关联
     */
    @Override
    public Long saveAndGetId(SysOperLog operLog) {
        sysOperLogMapper.addOperLog(operLog);
        return operLog.getOperId();
    }

    /**
     * 异步保存：成功路径用，走 logExecutor 线程池，不阻塞业务请求
     */
    @Async("logExecutor")
    @Override
    public void saveAsync(SysOperLog operLog) {
        sysOperLogMapper.addOperLog(operLog);
    }

    // ============ 管理后台（供 Controller 调用） ============

    /**
     * 分页查询操作日志（两段式，与表体量解耦）：
     * 1. 操作日志表单独分页查（PageHelper 劫持，count 单表准确）
     * 2. 用当页 oper_id 批量查关联错误日志，走 idx_error_log_oper，最多当页条数
     * 3. 内存按 operId 拼装 errorLogId 到 VO
     */
    @Override
    public PageInfo<SysOperLogVo> quarryOperLog(OperLogQuarry quarry) {
        PageUtil.startPage();
        List<SysOperLogVo> list = sysOperLogMapper.quarryOperLog(quarry);
        PageInfo<SysOperLogVo> pageInfo = PageUtil.packagedPageInfo(list);

        // 第二段：拼装失败操作的 errorLogId（调错误日志 mapper，返回 SysErrorLogVo）
        Set<Long> operIds = list.stream().map(SysOperLogVo::getOperId).collect(Collectors.toSet());
        if (!operIds.isEmpty()) {
            List<SysErrorLogVo> refs = sysErrorLogMapper.getErrorLogByOperLogIds(List.copyOf(operIds));
            Map<Long, Long> operIdToErrorId = refs.stream()
                    .collect(Collectors.toMap(SysErrorLogVo::getOperLogId, SysErrorLogVo::getErrorId, (a, b) -> a));
            for (SysOperLogVo vo : list) {
                vo.setErrorLogId(operIdToErrorId.get(vo.getOperId()));
            }
        }
        return pageInfo;
    }

    @Override
    public SysOperLogVo getOperLogInfo(Long operId) {
        return sysOperLogMapper.getOperLogInfoById(operId);
    }

    @Override
    public Boolean deleteOperLog(Long[] operIds) {
        return sysOperLogMapper.deleteOperLogByIds(operIds);
    }

    @Override
    public Boolean cleanOperLog() {
        return sysOperLogMapper.cleanOperLog();
    }
}
