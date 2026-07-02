package com.knowhub.task;

import com.knowhub.service.FileService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 文件对象 GC 定时任务。
 * 扫描超时未确认的 PENDING 行与已软删行，DeleteObject 清 RustFS 对象 + 物理删元数据。
 * DeleteObject 失败保留行下次再扫（容错，见设计稿 §3.4）。
 *
 * 频率由 application.yml 的 storage.gc-interval-minutes 控制（默认 10 分钟），
 * 通过 @Scheduled 的 fixedDelayString 占位读取；改 yml 需重启。
 */
@Component
public class FileGcTask {

    private static final Logger log = LoggerFactory.getLogger(FileGcTask.class);

    @Autowired
    private FileService fileService;

    /**
     * GC 扫描。fixedDelay 用 SpEL 把 storage.gc-interval-minutes（分钟）转毫秒。
     * initialDelay 60s 避开应用启动高峰。
     */
    @Scheduled(fixedDelayString = "#{${storage.gc-interval-minutes:10} * 60 * 1000}", initialDelay = 60000)
    public void gc() {
        try {
            fileService.gc();
        } catch (Exception e) {
            log.error("[FileGcTask] GC 执行异常", e);
        }
    }
}
