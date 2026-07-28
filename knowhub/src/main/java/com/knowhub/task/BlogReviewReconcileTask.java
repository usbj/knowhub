package com.knowhub.task;

import com.knowhub.config.BlogConfigReader;
import com.knowhub.service.blog.impl.BlogService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 博客审核对账定时任务。
 * <p>
 * 场景：管理员把审核开关（sys_config[knowhub.blog.review_enabled]）从开切到关后，
 * 仍处于 PENDING_REVIEW 的遗留文章无人收口（作者编辑/再发布/撤回均被状态机拒绝，
 * 审核员也未必手动批）。本任务在确认开关关闭且存在待审标记时，将这些遗留稿批量放行为已发布。
 * <p>
 * 两段省扫表：
 * <ol>
 *   <li>审核开关开启 → 直接 return，队列有人工审核意义，不收口。</li>
 *   <li>Redis 待审标记不存在 → 直接 return，没有待审稿，零扫表。
 *       标记由 publishBlog 在文章进入 PENDING_REVIEW 时 SET（不计数仅标记存在性），
 *       本任务消费后 DEL。假阳（稿已被审核员手动批但标记未清）仅导致多扫一次空表，可接受。</li>
 * </ol>
 * <p>
 * 频率由 application.yml 的 knowhub.blog.reconcile-interval-minutes 控制（默认 5 分钟），
 * 通过 @Scheduled 的 fixedDelayString 占位读取；改 yml 需重启。
 */
@Component
public class BlogReviewReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(BlogReviewReconcileTask.class);

    @Autowired
    private BlogService blogService;

    @Autowired
    private BlogConfigReader blogConfigReader;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${redis.base-key}")
    private String baseKey;

    /** 待审核存在标记 key（与 BlogServiceImpl.CACHE_PENDING_FLAG 同名，经 baseKey 前缀） */
    private static final String CACHE_PENDING_FLAG = "blog:review:pending-flag";

    /**
     * 对账扫描。fixedDelay 用 SpEL 把 knowhub.blog.reconcile-interval-minutes（分钟）转毫秒。
     * initialDelay 60s 避开应用启动高峰。
     */
    @Scheduled(fixedDelayString = "#{${knowhub.blog.reconcile-interval-minutes:5} * 60 * 1000}", initialDelay = 60000)
    public void reconcile() {
        try {
            // 1. 审核开关开启 → 队列有人工审核意义，不收口
            if (blogConfigReader.isReviewEnabled()) {
                return;
            }
            // 2. 无待审标记 → 没有待审稿，零扫表
            String flagKey = baseKey + CACHE_PENDING_FLAG;
            String flag = redisTemplate.opsForValue().get(flagKey);
            if (flag == null) {
                return;
            }
            // 3. 开关关且存在待审标记 → 批量放行遗留稿
            int released = blogService.reconcilePendingReview();
            // 4. 消费后清标记，下次无待审稿时直接 return 零扫表
            redisTemplate.delete(flagKey);
            if (released > 0) {
                log.info("[BlogReviewReconcileTask] 审核已关闭，自动放行遗留待审文章 {} 篇", released);
            }
        } catch (Exception e) {
            log.error("[BlogReviewReconcileTask] 对账执行异常", e);
        }
    }
}
