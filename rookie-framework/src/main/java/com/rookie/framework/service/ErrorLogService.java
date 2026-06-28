package com.rookie.framework.service;

import com.rookie.common.pojo.entity.SysErrorLog;

/**
 * 错误日志服务接口（依赖倒置：framework 定义，rookie-system 实现）
 * 统一错误采集入口：各来源(请求触发/定时任务/异步任务/事件监听/启动初始化)调用方填充 SysErrorLog 后交由本接口落库
 * 调用方负责在各自线程内填充来源相关字段；本接口实现只管落库
 */
public interface ErrorLogService {

    /**
     * 异步保存错误日志
     * 走 logExecutor 线程池异步落库，不阻塞当前流程
     *
     * @param errorLog 错误日志（调用方已填充来源/异常/简述等字段）
     */
    void saveAsync(SysErrorLog errorLog);
}
