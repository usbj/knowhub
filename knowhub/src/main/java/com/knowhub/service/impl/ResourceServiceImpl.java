package com.knowhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.config.ResourceConfigReader;
import com.knowhub.enums.ResourceStatus;
import com.knowhub.enums.ResourceType;
import com.knowhub.enums.ReviewAction;
import com.knowhub.enums.ReviewStatus;
import com.knowhub.mapper.ResourceCollectMapper;
import com.knowhub.mapper.ResourceLikeMapper;
import com.knowhub.mapper.ResourceMapper;
import com.knowhub.mapper.ResourceRatingMapper;
import com.knowhub.mapper.ResourceReviewLogMapper;
import com.knowhub.pojo.entity.Resource;
import com.knowhub.pojo.entity.ResourceCollect;
import com.knowhub.pojo.entity.ResourceLike;
import com.knowhub.pojo.entity.ResourceRating;
import com.knowhub.pojo.entity.ResourceReviewLog;
import com.knowhub.pojo.quarry.ResourceQuarry;
import com.knowhub.pojo.vo.BindVo;
import com.knowhub.pojo.vo.DownloadVo;
import com.knowhub.pojo.vo.ResourceReviewLogVo;
import com.knowhub.pojo.vo.ResourceReviewVo;
import com.knowhub.pojo.vo.ResourceVo;
import com.knowhub.service.FileService;
import com.knowhub.service.ResourceService;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资源 Service 实现。
 *
 * 审核流程复用博客那套范式（状态机+回避+流水表+对账任务），代码模式与 BlogServiceImpl 同构：
 * - 主表只存 status/review_status/publish_time，审核员/审核时间/审核意见全在 resource_review_log
 *   流水表（不冗余主表快照，比博客主表更干净）
 * - publish 经审核开关决定 PENDING_REVIEW 或 PUBLISHED，进 PENDING_REVIEW 时 SET Redis 标记
 * - review 校验状态+回避（author_id 比对），写流水 APPROVE/REJECT
 * - reconcilePendingReview 逐条放行遗留待审资源，对账任务在开关关闭+标记存在时调用
 *
 * 互动计数（点赞/收藏/评分）不冗余主表，走事实表聚合回填：
 * - 列表批量聚合：countLikesByResourceIds / countCollectsByResourceIds / ratingStatsByResourceIds
 * - 详情单条聚合：复用批量聚合（传单元素列表）
 * - hasLiked/hasCollected/myScore：详情接口回填当前用户态
 * - 下载数 download_count 冗余主表（仅 FILE 下载 +1，原子自增）
 *
 * FILE 类资源文件链路复用文件模块：
 * - 新增/编辑时若 fileObjectId 有值则调 fileService.bindBizRef 回填 file_object.biz_ref_id
 * - 删除时级联软删关联 file_object 行（FileGcTask 异步清对象本体）
 * - 下载时调 fileService.getDownloadUrl 取预签名/中转链接
 */
@Service
public class ResourceServiceImpl implements ResourceService {

    @Autowired
    ResourceMapper resourceMapper;

    @Autowired
    ResourceReviewLogMapper resourceReviewLogMapper;

    @Autowired
    ResourceLikeMapper resourceLikeMapper;

    @Autowired
    ResourceCollectMapper resourceCollectMapper;

    @Autowired
    ResourceRatingMapper resourceRatingMapper;

    @Autowired
    ResourceConfigReader resourceConfigReader;

    @Autowired
    FileService fileService;

    @Autowired
    StringRedisTemplate redisTemplate;

    @Value("${redis.base-key}")
    private String baseKey;

    /** 待审核存在标记 key（经 baseKey 前缀）：作者提交进 PENDING_REVIEW 时 SET，对账任务消费后 DEL */
    private static final String CACHE_PENDING_FLAG = "resource:review:pending-flag";

    /** "其他"分类 ID 约定值（前端硬编码同值，-1 表示未分类/其他） */
    private static final long CATEGORY_OTHER = -1L;

    @Override
    public PageInfo<ResourceVo> quarryResource(ResourceQuarry quarry) {
        PageUtil.startPage();
        List<Resource> list = resourceMapper.quarryResource(quarry);
        PageInfo<Resource> page = PageUtil.packagedPageInfo(list);
        PageInfo<ResourceVo> voPage = PageUtil.copyPageInfo(page, ResourceVo.class);
        // 列表回填互动计数 + 作者昵称 + 分类名（批量聚合，避免 N+1）
        fillListExtra(voPage.getList());
        return voPage;
    }

    @Override
    public ResourceVo getResourceInfo(Long resourceId) {
        Resource resource = resourceMapper.getResourceInfoById(resourceId);
        if (resource == null) {
            throw new ServiceException(500, "资源不存在");
        }
        ResourceVo vo = BeanUtil.toBean(resource, ResourceVo.class);
        // 详情回填：互动计数（单条聚合）+ 作者昵称 + 分类名 + 当前用户态 + FILE 下载链接
        fillListExtra(new ArrayList<>(List.of(vo)));
        fillCurrentUserInteract(vo);
        fillDownloadUrl(vo, resource);
        return vo;
    }

    @Override
    @Transactional
    public Boolean addResourceInfo(ResourceVo vo) {
        validateResourcePayload(vo);
        Resource resource = BeanUtil.toBean(vo, Resource.class);
        UserInfo userInfo = currentUser();
        resource.setAuthorId(userInfo.getUserId());
        resource.setCreateBy(userInfo.getUsername());
        resource.setUpdateBy(userInfo.getUsername());
        resource.setCreateTime(new Date());
        resource.setUpdateTime(new Date());
        // 新建即草稿；审核相关字段初始化
        resource.setStatus(ResourceStatus.DRAFT.getCode());
        resource.setReviewStatus(ReviewStatus.NONE.getCode());
        resource.setDownloadCount(0L);
        // resourceCategoryId 缺省置 -1（其他）
        if (resource.getResourceCategoryId() == null) {
            resource.setResourceCategoryId(CATEGORY_OTHER);
        }
        try {
            resourceMapper.addResource(resource);
        } catch (Exception e) {
            throw new ServiceException(500, "资源添加失败", e.getMessage());
        }
        // FILE 类资源：回填 file_object.biz_ref_id 为资源 id（绑定业务关联）
        bindFileBizRef(resource.getFileObjectId(), resource.getResourceId());
        return true;
    }

    @Override
    @Transactional
    public Boolean editResourceInfo(ResourceVo vo) {
        if (vo.getResourceId() == null) {
            throw new ServiceException(500, "资源ID不能为空");
        }
        Resource exist = resourceMapper.getResourceInfoById(vo.getResourceId());
        if (exist == null) {
            throw new ServiceException(500, "资源不存在");
        }
        // 状态机前置校验：仅 DRAFT/REJECTED/REVOKED 可编辑（与博客同构）
        // - PUBLISHED 禁止原地编辑：内容改了状态仍 PUBLISHED 等于绕过审核，须先撤回再编辑
        // - PENDING_REVIEW 禁止编辑：审核员审的是提交快照，作者此时改动会污染审核依据
        String cur = exist.getStatus();
        if (ResourceStatus.PUBLISHED.getCode().equals(cur)) {
            throw new ServiceException(500, "已发布资源请先撤回再编辑");
        }
        if (ResourceStatus.PENDING_REVIEW.getCode().equals(cur)) {
            throw new ServiceException(500, "审核中资源不能编辑，如需修改请先驳回或撤回后操作");
        }
        checkOwnerOrAdmin(exist);
        validateResourcePayload(vo);

        Resource resource = BeanUtil.toBean(vo, Resource.class);
        UserInfo userInfo = currentUser();
        resource.setUpdateBy(userInfo.getUsername());
        // resourceCategoryId 缺省置 -1（其他）
        if (resource.getResourceCategoryId() == null) {
            resource.setResourceCategoryId(CATEGORY_OTHER);
        }
        try {
            resourceMapper.editResourceInfo(resource);
        } catch (Exception e) {
            throw new ServiceException(500, "资源修改失败", e.getMessage());
        }
        // FILE 类资源：若 fileObjectId 变化则回填新文件的业务关联（旧文件由 GC 兜底清）
        bindFileBizRef(resource.getFileObjectId(), resource.getResourceId());
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteResourceInfo(Long[] resourceIds) {
        try {
            for (Long id : resourceIds) {
                Resource resource = resourceMapper.getResourceInfoById(id);
                if (resource == null) {
                    continue;
                }
                // FILE 类资源：级联软删关联 file_object 行（对象本体由 FileGcTask 异步清）
                if (resource.getFileObjectId() != null) {
                    try {
                        fileService.deleteFileObjects(new Long[]{resource.getFileObjectId()});
                    } catch (Exception e) {
                        // 文件软删失败不阻断资源删除（资源已软删，文件残行由 GC 兜底）
                        org.slf4j.LoggerFactory.getLogger(ResourceServiceImpl.class)
                                .warn("级联软删文件失败 resourceId={} fileObjectId={}: {}", id, resource.getFileObjectId(), e.getMessage());
                    }
                }
                resourceMapper.softDeleteResource(id);
            }
        } catch (Exception e) {
            throw new ServiceException(500, "资源删除失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean publishResource(Long resourceId) {
        Resource exist = resourceMapper.getResourceInfoById(resourceId);
        if (exist == null) {
            throw new ServiceException(500, "资源不存在");
        }
        // 状态机前置校验：仅 DRAFT/REJECTED/REVOKED 可发布（与博客同构）
        String cur = exist.getStatus();
        if (ResourceStatus.PUBLISHED.getCode().equals(cur)) {
            throw new ServiceException(500, "资源已发布，无需重复发布");
        }
        if (ResourceStatus.PENDING_REVIEW.getCode().equals(cur)) {
            throw new ServiceException(500, "资源审核中，请勿重复提交");
        }
        checkOwnerOrAdmin(exist);
        UserInfo userInfo = currentUser();
        Date now = new Date();
        // 经审核开关决定目标状态：开关关→直接发布；开→待审核
        boolean reviewEnabled = resourceConfigReader.isReviewEnabled();
        Resource update = new Resource();
        update.setResourceId(resourceId);
        update.setUpdateBy(userInfo.getUsername());
        ReviewAction action;
        if (reviewEnabled) {
            update.setStatus(ResourceStatus.PENDING_REVIEW.getCode());
            update.setReviewStatus(ReviewStatus.PENDING.getCode());
            action = ReviewAction.SUBMIT;
            // 标记存在待审核资源，供对账定时任务快速判断是否需要扫表收口（不计数仅标记存在性）
            redisTemplate.opsForValue().set(baseKey + CACHE_PENDING_FLAG, "1");
        } else {
            update.setStatus(ResourceStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.NONE.getCode());
            action = ReviewAction.PUBLISH;
        }
        resourceMapper.editResourceInfo(update);
        // 写审核流水：作者提交(SUBMIT, AUTHOR) 或 系统直通(PUBLISH, SYSTEM)
        writeReviewLog(resourceId, action, userInfo, null);
        return true;
    }

    @Override
    @Transactional
    public Boolean revokeResource(Long resourceId) {
        Resource exist = resourceMapper.getResourceInfoById(resourceId);
        if (exist == null) {
            throw new ServiceException(500, "资源不存在");
        }
        // 状态机前置校验：仅 PUBLISHED 可撤回（与博客同构）
        if (!ResourceStatus.PUBLISHED.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅已发布资源可撤回");
        }
        checkOwnerOrAdmin(exist);
        UserInfo userInfo = currentUser();
        Resource update = new Resource();
        update.setResourceId(resourceId);
        update.setStatus(ResourceStatus.REVOKED.getCode());
        update.setReviewStatus(ReviewStatus.NONE.getCode());
        update.setUpdateBy(userInfo.getUsername());
        resourceMapper.editResourceInfo(update);
        // 写审核流水：作者撤回(REVOKE, AUTHOR)
        writeReviewLog(resourceId, ReviewAction.REVOKE, userInfo, null);
        return true;
    }

    @Override
    @Transactional
    public Boolean reviewResource(ResourceReviewVo vo) {
        if (vo.getResourceId() == null || vo.getPass() == null) {
            throw new ServiceException(500, "审核参数不完整");
        }
        Resource exist = resourceMapper.getResourceInfoById(vo.getResourceId());
        if (exist == null) {
            throw new ServiceException(500, "资源不存在");
        }
        // 状态机前置校验：仅 PENDING_REVIEW 可审核，防止对草稿/已发布等误调审核接口改状态
        if (!ResourceStatus.PENDING_REVIEW.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅待审核资源可审核");
        }
        UserInfo userInfo = currentUser();
        // 审核员回避：作者不能审自己资源（用 author_id 比对，比 username 更准）
        if (exist.getAuthorId() != null && exist.getAuthorId().equals(userInfo.getUserId())) {
            throw new ServiceException(500, "不能审核自己提交的资源");
        }
        Date now = new Date();
        Resource update = new Resource();
        update.setResourceId(vo.getResourceId());
        update.setUpdateBy(userInfo.getUsername());
        // 主表不存 reviewer/reviewTime/reviewAdvice（走流水表，比博客主表更干净）
        ReviewAction action;
        String advice = null;
        if (vo.getPass()) {
            update.setStatus(ResourceStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.APPROVED.getCode());
            action = ReviewAction.APPROVE;
            advice = vo.getAdvice(); // 通过时意见可选
        } else {
            if (vo.getAdvice() == null || vo.getAdvice().isEmpty()) {
                throw new ServiceException(500, "驳回需填写审核意见");
            }
            update.setStatus(ResourceStatus.REJECTED.getCode());
            update.setReviewStatus(ReviewStatus.REJECTED.getCode());
            action = ReviewAction.REJECT;
            advice = vo.getAdvice();
        }
        resourceMapper.editResourceInfo(update);
        // 写审核流水：通过(APPROVE, REVIEWER) 或 驳回(REJECT, REVIEWER)
        writeReviewLog(vo.getResourceId(), action, userInfo, advice);
        return true;
    }

    @Override
    public List<ResourceReviewLogVo> listReviewLog(Long resourceId) {
        List<ResourceReviewLog> logs = resourceReviewLogMapper.listByResourceId(resourceId);
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }
        // BeanUtil 拷贝（Date→String），operatorNickname 字段名一致自动带出
        return logs.stream()
                .map(log -> BeanUtil.toBean(log, ResourceReviewLogVo.class))
                .collect(Collectors.toList());
    }

    @Override
    public int reconcilePendingReview() {
        List<Long> ids = resourceMapper.listPendingReviewIds();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Date now = new Date();
        int released = 0;
        // 逐条放行，单条失败跳过不阻塞其它资源（对齐博客对账"状态优先、历史容错"哲学）
        // 不加 @Transactional：批量放行一条坏数据不该回滚已放行的其它资源
        for (Long resourceId : ids) {
            try {
                Resource update = new Resource();
                update.setResourceId(resourceId);
                update.setStatus(ResourceStatus.PUBLISHED.getCode());
                update.setPublishTime(now);
                update.setReviewStatus(ReviewStatus.APPROVED.getCode());
                update.setUpdateBy("system");
                resourceMapper.editResourceInfo(update);
                // 写 PUBLISH/SYSTEM 流水，advice 标注场景以区分作者直通发布
                writeReviewLog(resourceId, ReviewAction.PUBLISH, systemOperator(), "审核关闭后定时任务自动放行");
                released++;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ResourceServiceImpl.class)
                        .warn("对账放行失败 resourceId={}: {}", resourceId, e.getMessage());
            }
        }
        return released;
    }

    /**
     * 点赞 / 取消点赞（toggle）。
     * 计数不冗余主表：事实表 insert/delete 即可，读时聚合 COUNT。
     * liked=true：insert ignore（已赞则无操作），liked=false：delete（未赞则无操作）。
     */
    @Override
    @Transactional
    public Boolean toggleLike(Long resourceId, Boolean liked) {
        Resource exist = resourceMapper.getResourceInfoById(resourceId);
        if (exist == null) {
            throw new ServiceException(500, "资源不存在");
        }
        Long userId = currentUser().getUserId();
        ResourceLike record = new ResourceLike(resourceId, userId);
        if (Boolean.TRUE.equals(liked)) {
            resourceLikeMapper.addResourceLike(record);
        } else {
            resourceLikeMapper.deleteResourceLike(record);
        }
        return true;
    }

    /** 收藏 / 取消收藏（toggle），结构同 toggleLike */
    @Override
    @Transactional
    public Boolean toggleCollect(Long resourceId, Boolean collected) {
        Resource exist = resourceMapper.getResourceInfoById(resourceId);
        if (exist == null) {
            throw new ServiceException(500, "资源不存在");
        }
        Long userId = currentUser().getUserId();
        ResourceCollect record = new ResourceCollect(resourceId, userId);
        if (Boolean.TRUE.equals(collected)) {
            resourceCollectMapper.addResourceCollect(record);
        } else {
            resourceCollectMapper.deleteResourceCollect(record);
        }
        return true;
    }

    /**
     * 评分（upsert 事实表）。
     * 一人一资源一条（UNIQUE 支撑）：存在则改分(update)，不存在则新增(insert)。
     * 评分均值/计数不冗余主表，读时聚合 AVG/COUNT（详情/列表 fillListExtra 回填）。
     * score 取值 1-5，越界抛错。
     */
    @Override
    @Transactional
    public Boolean rateResource(Long resourceId, Integer score) {
        if (score == null || score < 1 || score > 5) {
            throw new ServiceException(500, "评分需在 1-5 之间");
        }
        Resource exist = resourceMapper.getResourceInfoById(resourceId);
        if (exist == null) {
            throw new ServiceException(500, "资源不存在");
        }
        Long userId = currentUser().getUserId();
        ResourceRating record = new ResourceRating(resourceId, userId, score);
        ResourceRating old = resourceRatingMapper.getResourceRating(record);
        if (old == null) {
            resourceRatingMapper.addResourceRating(record);
        } else {
            resourceRatingMapper.updateResourceRating(record);
        }
        return true;
    }

    /**
     * FILE 下载：校验 PUBLISHED + FILE 类型，下载量 +1，返回下载链接。
     * 中转模式：fileService.getDownloadUrl 返回 /file/proxy 形态或预签名；
     * 直链模式：返回私有预签名带 attachment;filename。资源层不关心模式，复用文件模块即可。
     * LINK 类型不走此接口（前端直接用 linkUrl 外链打开，不计 download_count）。
     */
    @Override
    public String downloadResource(Long resourceId) {
        Resource exist = resourceMapper.getResourceInfoById(resourceId);
        if (exist == null) {
            throw new ServiceException(500, "资源不存在");
        }
        if (!ResourceStatus.PUBLISHED.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "资源未发布，无法下载");
        }
        if (!ResourceType.FILE.getCode().equals(exist.getResourceType())
                || exist.getFileObjectId() == null) {
            throw new ServiceException(500, "非文件类资源，无法下载");
        }
        // 下载量 +1（原子自增，仅 FILE 下载）
        resourceMapper.incrDownloadCount(resourceId);
        // 取下载链接（复用文件模块，按访问模式返回中转/预签名）
        DownloadVo downloadVo = fileService.getDownloadUrl(exist.getFileObjectId());
        return downloadVo != null ? downloadVo.getDownloadUrl() : null;
    }

    // ============================ 私有辅助 ============================

    /**
     * 校验资源载体字段：
     * - title 必填
     * - FILE 类型须有 fileObjectId；LINK 类型须有 linkUrl
     * - 类型与载体字段一致性：FILE 不能只填 linkUrl，LINK 不能只填 fileObjectId
     */
    private void validateResourcePayload(ResourceVo vo) {
        if (vo.getTitle() == null || vo.getTitle().isEmpty()) {
            throw new ServiceException(500, "标题不能为空");
        }
        String type = vo.getResourceType();
        if (type == null || type.isEmpty()) {
            throw new ServiceException(500, "资源类型不能为空");
        }
        if (ResourceType.FILE.getCode().equals(type)) {
            if (vo.getFileObjectId() == null) {
                throw new ServiceException(500, "文件类资源须上传文件");
            }
        } else if (ResourceType.LINK.getCode().equals(type)) {
            if (vo.getLinkUrl() == null || vo.getLinkUrl().isEmpty()) {
                throw new ServiceException(500, "链接类资源须填写链接URL");
            }
        } else {
            throw new ServiceException(500, "资源类型非法");
        }
    }

    /** FILE 类资源：回填 file_object.biz_ref_id 为资源 id（绑定业务关联），LINK 类或无文件跳过 */
    private void bindFileBizRef(Long fileObjectId, Long resourceId) {
        if (fileObjectId == null || resourceId == null) {
            return;
        }
        try {
            BindVo bind = new BindVo();
            bind.setObjectId(fileObjectId);
            bind.setBizRefId(resourceId);
            fileService.bindBizRef(bind);
        } catch (Exception e) {
            // 绑定失败不阻断资源保存（资源已落库，文件关联可后续手动绑定或 GC 兜底）
            org.slf4j.LoggerFactory.getLogger(ResourceServiceImpl.class)
                    .warn("回填文件业务关联失败 resourceId={} fileObjectId={}: {}", resourceId, fileObjectId, e.getMessage());
        }
    }

    /** 校验当前用户是作者本人或管理员（具备 knowhub:resource:review 权限视为管理员） */
    private void checkOwnerOrAdmin(Resource resource) {
        UserInfo userInfo = currentUser();
        boolean isAdmin = userInfo.getPermissions() != null
                && userInfo.getPermissions().contains("knowhub:resource:review");
        if (!userInfo.getUserId().equals(resource.getAuthorId()) && !isAdmin) {
            throw new ServiceException(500, "无权操作他人资源");
        }
    }

    /**
     * 追加一条审核流水。role 由 ReviewAction 自带（AUTHOR/REVIEWER/SYSTEM），
     * operator_id 用 userId 稳定锁定，operator 存 username 快照便于直读。
     * 流水表只追加不改不删，写失败不阻断主流程（catch 吞异常仅 log，保证审核状态变更已落库）。
     */
    private void writeReviewLog(Long resourceId, ReviewAction action, UserInfo operator, String advice) {
        try {
            ResourceReviewLog log = new ResourceReviewLog(resourceId, action.getCode(),
                    operator.getUserId(), operator.getUsername(), action.getRole(), advice);
            resourceReviewLogMapper.insertReviewLog(log);
        } catch (Exception e) {
            // 流水写入失败不回滚审核状态变更（主表已改），仅记录日志便于事后补录
            org.slf4j.LoggerFactory.getLogger(ResourceServiceImpl.class)
                    .warn("写审核流水失败 resourceId={} action={}: {}", resourceId, action.getCode(), e.getMessage());
        }
    }

    /** 构造一个 system 操作者 UserInfo，用于对账放行时写流水（operator_id=0, operator=system） */
    private UserInfo systemOperator() {
        UserInfo sys = new UserInfo();
        sys.setUserId(0L);
        sys.setUsername("system");
        return sys;
    }

    /**
     * 回填列表/详情 VO 的扩展字段：互动计数(批量聚合)。
     * 作者昵称/分类名/文件元数据由 Mapper 列表/详情查询 join 带出（Resource 实体扩展字段），
     * 经 PageUtil.copyPageInfo(BeanUtil) 自动拷贝到 VO，无需 Service 层再回填。
     * 互动计数走事实表聚合（不冗余主表），三条 SQL 批量查后按 resourceId 收集到 VO。
     */
    private void fillListExtra(List<ResourceVo> voList) {
        if (voList == null || voList.isEmpty()) {
            return;
        }
        List<Long> ids = voList.stream().map(ResourceVo::getResourceId).collect(Collectors.toList());
        // 互动计数批量聚合（点赞/收藏/评分）
        fillInteractCounts(voList, ids);
    }

    /** 批量回填互动计数（点赞/收藏/评分均值与计数），从事实表聚合，按 resourceId 收集 */
    private void fillInteractCounts(List<ResourceVo> voList, List<Long> ids) {
        Map<Long, Long> likeMap = toCountMap(resourceMapper.countLikesByResourceIds(ids));
        Map<Long, Long> collectMap = toCountMap(resourceMapper.countCollectsByResourceIds(ids));
        // 评分均值与计数
        Map<Long, double[]> ratingMap = new HashMap<>();
        List<Map<String, Object>> ratingStats = resourceMapper.ratingStatsByResourceIds(ids);
        if (ratingStats != null) {
            for (Map<String, Object> row : ratingStats) {
                Long rid = toLong(row.get("resourceId"));
                if (rid == null) {
                    continue;
                }
                double avg = toDouble(row.get("ratingAvg"));
                long cnt = toLong(row.get("ratingCnt")) != null ? toLong(row.get("ratingCnt")) : 0L;
                ratingMap.put(rid, new double[]{avg, cnt});
            }
        }
        for (ResourceVo vo : voList) {
            vo.setLikeCount(likeMap.getOrDefault(vo.getResourceId(), 0L));
            vo.setCollectCount(collectMap.getOrDefault(vo.getResourceId(), 0L));
            double[] rating = ratingMap.get(vo.getResourceId());
            if (rating != null) {
                vo.setRatingAvg(rating[0]);
                vo.setRatingCount((long) rating[1]);
            } else {
                vo.setRatingAvg(0.0);
                vo.setRatingCount(0L);
            }
        }
    }

    /** 把 count 聚合结果 List<Map> 转 Map<resourceId, count> */
    private Map<Long, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<Long, Long> map = new HashMap<>();
        if (rows == null) {
            return map;
        }
        for (Map<String, Object> row : rows) {
            Long rid = toLong(row.get("resourceId"));
            Long cnt = toLong(row.get("cnt"));
            if (rid != null) {
                map.put(rid, cnt != null ? cnt : 0L);
            }
        }
        return map;
    }

    /** 详情接口回填当前用户的点赞/收藏/评分态 */
    private void fillCurrentUserInteract(ResourceVo vo) {
        if (vo == null || vo.getResourceId() == null) {
            return;
        }
        try {
            Long userId = currentUser().getUserId();
            vo.setHasLiked(resourceLikeMapper.getResourceLike(new ResourceLike(vo.getResourceId(), userId)) != null);
            vo.setHasCollected(resourceCollectMapper.getResourceCollect(new ResourceCollect(vo.getResourceId(), userId)) != null);
            ResourceRating rating = resourceRatingMapper.getResourceRating(new ResourceRating(vo.getResourceId(), userId, 0));
            vo.setMyScore(rating != null ? rating.getScore() : 0);
        } catch (Exception ignored) {
            // 未登录等场景不回填状态
        }
    }

    /** 详情接口回填 FILE 下载链接（LINK 类型不回填，前端用 linkUrl） */
    private void fillDownloadUrl(ResourceVo vo, Resource resource) {
        if (!ResourceType.FILE.getCode().equals(resource.getResourceType())
                || resource.getFileObjectId() == null) {
            return;
        }
        try {
            DownloadVo downloadVo = fileService.getDownloadUrl(resource.getFileObjectId());
            if (downloadVo != null) {
                vo.setDownloadUrl(downloadVo.getDownloadUrl());
                vo.setOriginalName(downloadVo.getOriginalName());
            }
        } catch (Exception ignored) {
            // 文件不可访问时不影响详情展示，下载链接留空
        }
    }

    /** Map 取值转 Long（兼容 Number/BigInteger/Long 等返回类型） */
    private Long toLong(Object obj) {
        if (obj == null) {
            return null;
        }
        if (obj instanceof Number) {
            return ((Number) obj).longValue();
        }
        try {
            return Long.parseLong(obj.toString());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    /** Map 取值转 double（兼容 BigDecimal/Double 等） */
    private double toDouble(Object obj) {
        if (obj == null) {
            return 0.0;
        }
        if (obj instanceof Number) {
            return ((Number) obj).doubleValue();
        }
        try {
            return Double.parseDouble(obj.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
