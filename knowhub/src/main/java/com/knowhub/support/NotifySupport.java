package com.knowhub.support;

import com.rookie.common.pojo.entity.SysNotice;
import com.rookie.system.mapper.SysNoticeMapper;
import com.rookie.system.mapper.SysNoticeUserRelMapper;
import com.rookie.system.pojo.SysNoticeUserRel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.Date;

/**
 * knowhub 通知薄封装：给指定用户发一条站内私发通知（notice_type=NOTIFY，publish_scope=USER）。
 * <p>
 * 为何绕开 rookie {@code SysNoticeServiceImpl.addSysNoticeInfo}：那套依赖 SecurityContextHolder 取
 * createBy/updateBy（定时任务/无登录上下文场景不可用），而 knowhub 评论 service 已能拿到当前登录
 * 用户 username，直接以其为 operator 填 createBy/updateBy 更准。
 * <p>
 * 为何 addSysNotice 后再补一发 editSysNoticeInfo：rookie {@code SysNoticeMapper.addSysNotice} 的
 * INSERT &lt;trim&gt; 没有 status 列（{@code rookie-system/.../mapper/system/SysNoticeMapper.xml:41-72}），
 * 落库走 DB DEFAULT status='DRAFT'，而 {@code getNoticesForUser} 要求 status='PUBLISHED' 才列——
 * 直接 addSysNotice 写出的通知对前台不可见（审计 notifyReportReady 有同款 latent bug）。
 * 本次不动 rookie XML（由用户指示「rookie后续再改」），在 knowhub 侧复用 editSysNoticeInfo 的
 * status &lt;if&gt;（XML L82）补翻 PUBLISHED 纠正可见性。editSysNoticeInfo 的其它 &lt;if&gt; 因
 * null/空 自动跳过，本次只翻 status，不污染已落库的 title/content/route_path。
 * <p>
 * 全程 try/catch 吞异常仅 warn——通知失败不阻断调用方主流程（对齐 AuditPeriodReportServiceImpl
 * .notifyReportReady 哲学：被通知内容已落库，通知可后续补发）。
 */
@Component
public class NotifySupport {

    private static final Logger log = LoggerFactory.getLogger(NotifySupport.class);

    /** 通知类型：站内通知（复用 sys_notice_type 字典已有 NOTIFY 值，不新增字典行） */
    private static final String NOTICE_TYPE_NOTIFY = "NOTIFY";

    /** 通知级别：普通（sys_notice_level 字典值） */
    private static final String NOTICE_LEVEL_NORMAL = "NORMAL";

    /** 发布范围：指定成员（sys_notice_scope 字典值 USER，配合 sys_notice_user_rel 指定接收人） */
    private static final String NOTICE_SCOPE_USER = "USER";

    /** 通知状态：已发布（addSysNotice 补翻，对齐 getNoticesForUser 可见性要求） */
    private static final String NOTICE_STATUS_PUBLISHED = "PUBLISHED";

    /** 非置顶、不需确认为默认 0（Integer，对齐实体字段类型） */
    private static final Integer NOTICE_NO_TOP = 0;
    private static final Integer NOTICE_NO_CONFIRM = 0;

    @Autowired
    SysNoticeMapper sysNoticeMapper;

    @Autowired
    SysNoticeUserRelMapper sysNoticeUserRelMapper;

    /**
     * 给指定用户发一条站内通知。
     * <ul>
     *   <li>{@code targetUserId}：接收人 userId；null 直接跳过。</li>
     *   <li>{@code title} / {@code content}：通知标题与正文（调用方负责截断至合理长度）。</li>
     *   <li>{@code routePath}：前台跳转路由（评论回复通知拼 {@code /blog/{id}} 等作品详情路由）；不可空。</li>
     *   <li>{@code operator}：操作人名（填 createBy/updateBy）；评论 service 传当前登录 username。</li>
     * </ul>
     * 失败仅 warn 不向上抛——调用方主流程（如评论落库）不受影响。
     */
    public void notifyUser(Long targetUserId, String title, String content, String routePath, String operator) {
        try {
            if (targetUserId == null) {
                return;
            }
            Date now = new Date();
            SysNotice notice = new SysNotice();
            notice.setTitle(title);
            notice.setContent(content);
            notice.setNoticeType(NOTICE_TYPE_NOTIFY);
            notice.setLevel(NOTICE_LEVEL_NORMAL);
            notice.setPublishScope(NOTICE_SCOPE_USER);
            notice.setIsTop(NOTICE_NO_TOP);
            notice.setNeedConfirm(NOTICE_NO_CONFIRM);
            notice.setPublishTime(now);
            notice.setCreateBy(operator);
            notice.setUpdateBy(operator);
            notice.setRoutePath(routePath);
            // ① 落库（status 走 DB DEFAULT='DRAFT'，XML insert 无 status 列）
            sysNoticeMapper.addSysNotice(notice);
            // ② 补翻 PUBLISHED——rookie addSysNotice SQL 缺 status 列，复用 editSysNoticeInfo 的 status <if> 纠正可见性
            notice.setStatus(NOTICE_STATUS_PUBLISHED);
            sysNoticeMapper.editSysNoticeInfo(notice);
            // ③ 指定接收人（publish_scope=USER 配 sys_notice_user_rel）
            SysNoticeUserRel rel = new SysNoticeUserRel();
            rel.setNoticeId(notice.getNoticeId());
            rel.setUserId(targetUserId);
            sysNoticeUserRelMapper.insertSysNoticeUserRel(Collections.singletonList(rel));
        } catch (Exception e) {
            log.warn("[NotifySupport] 发通知失败 target={} title={}: {}", targetUserId, title, e.getMessage());
        }
    }
}