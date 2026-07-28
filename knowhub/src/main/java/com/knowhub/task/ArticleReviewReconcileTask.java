package com.knowhub.task;

import com.knowhub.config.ArticleConfigReader;
import com.knowhub.service.article.impl.ArticleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 文章审核对账定时任务。
 * <p>
 * 场景：管理员把审核开关（sys_config[knowhub.article.review_enabled]）从开切到关后，
 * 仍处于 PENDING_REVIEW 的遗留文章无人收口（作者编辑/再发布/撤回均被状态机拒绝，
 * 审核员也未必手动批）。本任务在确认开关关闭且存在待审标记时，将这些遗留文章批量放行为已发布。
 * <p>
 * 两段省扫表（与博客/资源/项目对账任务同构）：
 * <ol>
 *   <li>审核开关开启 → 直接 return，队列有人工审核意义，不收口。</li>
 *   <li>Redis 待审标记不存在 → 直接 return，没有待审文章，零扫表。
 *       标记由 publishArticle 在文章进入 PENDING_REVIEW 时 SET（不计数仅标记存在性），
 *       本任务消费后 DEL。假阳（文章已被审核员手动批但标记未清）仅导致多扫一次空表，可接受。</li>
 * </ol>
 * <p>
 * 频率由 application.yml 的 knowhub.article.reconcile-interval-minutes 控制（默认 5 分钟），
 * 通过 @Scheduled 的 fixedDelayString 占位读取；改 yml 需重启。
 * 对账间隔走 yml（@Scheduled 注解在 Bean 创建时解析，只能读 yml/环境变量，读不了 sys_config
 * Redis 缓存）；审核开关走 sys_config（运行时业务侧读取，可后台改即时生效）。
 * <p>
 * 章节作者审核是 visibility=SEMIPUBLIC 固有机制，不走系统审核开关，故章节无需对账任务
 * （章节不存在"开关关后遗留待审"场景——半公开文章的作者审是常驻机制，无开关可关）。
 */
@Component
public class ArticleReviewReconcileTask {

    private static final Logger log = LoggerFactory.getLogger(ArticleReviewReconcileTask.class);

    @Autowired
    private ArticleService articleService;

    @Autowired
    private ArticleConfigReader articleConfigReader;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @Value("${redis.base-key}")
    private String baseKey;

    /** 待审核存在标记 key（与 ArticleServiceImpl.CACHE_PENDING_FLAG 同名，经 baseKey 前缀） */
    private static final String CACHE_PENDING_FLAG = "article:review:pending-flag";

    /**
     * 对账扫描。fixedDelay 用 SpEL 把 knowhub.article.reconcile-interval-minutes（分钟）转毫秒。
     * initialDelay 60s 避开应用启动高峰。
     */
    @Scheduled(fixedDelayString = "#{${knowhub.article.reconcile-interval-minutes:5} * 60 * 1000}", initialDelay = 60000)
    public void reconcile() {
        try {
            // 1. 审核开关开启 → 队列有人工审核意义，不收口
            if (articleConfigReader.isReviewEnabled()) {
                return;
            }
            // 2. 无待审标记 → 没有待审文章，零扫表
            String flagKey = baseKey + CACHE_PENDING_FLAG;
            String flag = redisTemplate.opsForValue().get(flagKey);
            if (flag == null) {
                return;
            }
            // 3. 开关关且存在待审标记 → 批量放行遗留待审文章
            int released = articleService.reconcilePendingReview();
            // 4. 消费后清标记，下次无待审文章时直接 return 零扫表
            redisTemplate.delete(flagKey);
            if (released > 0) {
                log.info("[ArticleReviewReconcileTask] 审核已关闭，自动放行遗留待审文章 {} 篇", released);
            }
        } catch (Exception e) {
            log.error("[ArticleReviewReconcileTask] 对账执行异常", e);
        }
    }
}
