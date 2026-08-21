package com.knowhub.task;

import com.knowhub.config.ResourceConfigReader;
import com.knowhub.service.resource.impl.ResourceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * 资源审核对账定时任务。
 * <p>
 * 场景：管理员把审核开关（sys_config[knowhub.resource.review_enabled]）从开切到关后，
 * 仍处于 PENDING_REVIEW 的遗留资源无人收口（作者编辑/再发布/撤回均被状态机拒绝，
 * 审核员也未必手动批）。本任务在确认开关关闭且存在待审标记时，将这些遗留资源批量放行为已发布。
 * <p>
 * 两段省扫表（与博客对账任务同构）：
 * <ol>
 *   <li>审核开关开启 → 直接 return，队列有人工审核意义，不收口。</li>
 *   <li>Redis 待审标记不存在 → 直接 return，没有待审资源，零扫表。
 *       标记由 publishResource 在资源进入 PENDING_REVIEW 时 SET（不计数仅标记存在性），
 *       本任务消费后 DEL。假阳（资源已被审核员手动批但标记未清）仅导致多扫一次空表，可接受。</li>
 * </ol>
 * <p>
 * 触发由 rookie sys_job 调度器（CronTrigger + 独立线程池）驱动，cron 见 sys_job 表对应行
 * （初始 cron 0 * / 5 * * * ?，对应原 5 分钟间隔）。后台「系统监控→定时任务」可改 cron / 启停 /
 * 立即执行，改 cron 即时生效无需重启。原 @Scheduled fixedDelay + knowhub.resource.reconcile-interval-minutes
 * yml 项已不再驱动调度（yml 项保留未删，仅作历史）；审核开关仍走 sys_config[knowhub.resource.review_enabled]
 * （运行时业务侧读取，可后台改即时生效）。
 */
@Component
public class ResourceReviewReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(ResourceReviewReconcileTask.class);

    @Autowired
    private ResourceService resourceService;

    @Autowired
    private ResourceConfigReader resourceConfigReader;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${redis.base-key}")
    private String baseKey;

    /** 待审核存在标记 key（与 ResourceServiceImpl.CACHE_PENDING_FLAG 同名，经 baseKey 前缀） */
    private static final String CACHE_PENDING_FLAG = "resource:review:pending-flag";

    /**
     * 对账扫描。由 sys_job 调度器按 cron 触发（无参方法，符合 rookie findTaskMethod 要求）。
     */
    public void reconcile() {
        try {
            // 1. 审核开关开启 → 队列有人工审核意义，不收口
            if (resourceConfigReader.isReviewEnabled()) {
                return;
            }
            // 2. 无待审标记 → 没有待审资源，零扫表
            String flagKey = baseKey + CACHE_PENDING_FLAG;
            String flag = redisTemplate.opsForValue().get(flagKey);
            if (flag == null) {
                return;
            }
            // 3. 开关关且存在待审标记 → 批量放行遗留资源
            int released = resourceService.reconcilePendingReview();
            // 4. 消费后清标记，下次无待审资源时直接 return 零扫表
            redisTemplate.delete(flagKey);
            if (released > 0) {
                log.info("[ResourceReviewReconcileTask] 审核已关闭，自动放行遗留待审资源 {} 个", released);
            }
        } catch (Exception e) {
            log.error("[ResourceReviewReconcileTask] 对账执行异常", e);
        }
    }
}