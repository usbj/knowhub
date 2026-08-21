package com.knowhub.task;

import com.knowhub.service.storage.impl.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * 文件对象 GC 定时任务。
 * 扫描超时未确认的 PENDING 行与已软删行，DeleteObject 清 RustFS 对象 + 物理删元数据。
 * DeleteObject 失败保留行下次再扫（容错，见设计稿 §3.4）。
 * <p>
 * 触发由 rookie sys_job 调度器（CronTrigger + 独立线程池）驱动，cron 见 sys_job 表对应行
 * （初始 cron 0 * /10 * * * ?，对应原 10 分钟间隔）。后台「系统监控→定时任务」可改 cron / 启停 /
 * 立即执行，改 cron 即时生效无需重启。原 @Scheduled fixedDelay + storage.gc-interval-minutes yml 项
 * 已不再驱动调度（yml 项保留未删，仅作历史）。
 */
@Component
public class FileGcTask {

    private static final Logger log = LoggerFactory.getLogger(FileGcTask.class);

    @Autowired
    private FileService fileService;

    /**
     * GC 扫描。由 sys_job 调度器按 cron 触发（无参方法，符合 rookie findTaskMethod 要求）。
     */
    public void gc() {
        try {
            fileService.gc();
        } catch (Exception e) {
            log.error("[FileGcTask] GC 执行异常", e);
        }
    }
}
