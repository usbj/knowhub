package com.rookie.framework.service;

import com.rookie.common.pojo.entity.SysOperLog;

/**
 * 操作日志服务接口（依赖倒置：framework 定义，rookie-system 实现）
 * 切面 LogAspect 调用本接口，由 system 侧实现类完成落库，避免 framework→system 反向依赖
 */
public interface OperLogService {

    /**
     * 同步保存并返回主键
     * 用于失败路径：切面需拿到 oper_id 塞进 request attribute，供 GlobalExceptionHandler 写错误日志时关联
     *
     * @param operLog 操作日志（调用方在请求线程内已填充完整）
     * @return 日志主键 oper_id
     */
    Long saveAndGetId(SysOperLog operLog);

    /**
     * 异步保存
     * 用于成功路径：无需主键关联，走 logExecutor 线程池异步落库，不阻塞业务请求
     *
     * @param operLog 操作日志（调用方在请求线程内已填充完整）
     */
    void saveAsync(SysOperLog operLog);
}
