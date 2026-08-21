package com.knowhub.service.storage;

import cn.hutool.core.bean.BeanUtil;
import com.knowhub.mapper.storage.FileMigrationTaskMapper;
import com.knowhub.pojo.storage.entity.FileMigrationTask;
import com.knowhub.pojo.storage.vo.MigrationApplyVo;
import com.knowhub.pojo.storage.vo.MigrationProgressVo;
import com.knowhub.service.storage.impl.FileMigrationService;
import com.rookie.common.exception.ServiceException;
import com.rookie.framework.security.pojo.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadBucketRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;

import java.io.InputStream;
import java.net.URI;

/**
 * OSS 数据迁移服务实现（扩展点2）。
 * <p>
 * 源/目标 S3Client 按入参临时构造（不注册 Bean，用完 close），避免污染单例。
 * 拷贝逐对象走 getObject 流 → putObject 写目标，单对象不进内存（SDK RequestBody 适配 InputStream）。
 * 目录结构一致：源 objectKey 原样作目标 objectKey。
 * <p>
 * @Async("migrationExecutor") 走 knowhub 模块独立线程池（见 MigrationAsyncConfig），
 * 不占 rookie 的 logExecutor。异步线程无 SecurityContext，createBy 在同步阶段取好塞进 task 行。
 */
@Service
public class FileMigrationServiceImpl implements FileMigrationService {

    private static final Logger log = LoggerFactory.getLogger(FileMigrationServiceImpl.class);

    /** 源端类型常量：OSS（源 OSS→目标 OSS）/ LOCAL（本地→目标 OSS） */
    private static final String SOURCE_TYPE_OSS = "OSS";
    private static final String SOURCE_TYPE_LOCAL = "LOCAL";

    @Autowired
    private FileMigrationTaskMapper migrationTaskMapper;

    /** 本地存储后端（本地→OSS 迁移读源磁盘用，仅 LOCAL 源类型时使用） */
    @Autowired
    private com.knowhub.service.storage.backend.LocalStorageBackend localBackend;

    /**
     * 启动迁移任务。同步阶段：校验参数 + 按 sourceType 校验源/目标（OSS 探活源桶，LOCAL 校验本地根存在）+
     * 建任务行 PENDING + 取当前用户名塞审计列；异步阶段：@Async 线程遍历源端逐文件拷贝，更新进度，置终态。
     * @param vo 源类型 + 目标 OSS 连接参数（OSS→OSS 还需源 OSS 连接参数；凭证内存用完即弃）
     * @return 迁移任务 ID
     */
    @Override
    public Long startMigration(MigrationApplyVo vo) {
        // 参数基础校验（按 sourceType 分流）
        validateVo(vo);
        boolean fromLocal = SOURCE_TYPE_LOCAL.equalsIgnoreCase(vo.getSourceType());

        // 源端校验：OSS→探活源桶；LOCAL→校验本地根目录存在
        if (!fromLocal) {
            try (S3Client source = buildClient(vo.getSourceEndpoint(), vo.getSourceRegion(),
                    vo.getSourceAccessKey(), vo.getSourceSecretKey(), boolOrDefault(vo.getSourcePathStyleAccess()))) {
                probeBucket(source, vo.getSourceBucket(), "源");
            }
        } else {
            // LOCAL 源：本地根目录必须存在，否则没东西可迁移
            java.util.List<com.knowhub.pojo.storage.vo.LocalFileEntry> probe = localBackend.listLocalEntries();
            log.info("[OSS 迁移] 本地→OSS 源端探测，本地根目录文件数={}", probe.size());
        }
        // 目标始终是 OSS，探活目标桶
        try (S3Client target = buildClient(vo.getTargetEndpoint(), vo.getTargetRegion(),
                vo.getTargetAccessKey(), vo.getTargetSecretKey(), boolOrDefault(vo.getTargetPathStyleAccess()))) {
            probeBucket(target, vo.getTargetBucket(), "目标");
        }

        // 建任务行 PENDING（同步阶段取好 createBy/updateBy，异步线程无 SecurityContext）
        String username = currentUserSafe();
        FileMigrationTask task = new FileMigrationTask();
        task.setSourceType(fromLocal ? SOURCE_TYPE_LOCAL : SOURCE_TYPE_OSS);
        if (!fromLocal) {
            // LOCAL 源不存源 OSS 连接元信息（源是本地磁盘）
            task.setSourceEndpoint(vo.getSourceEndpoint());
            task.setSourceRegion(vo.getSourceRegion());
            task.setSourceBucket(vo.getSourceBucket());
            task.setSourcePathStyleAccess(boolOrDefault(vo.getSourcePathStyleAccess()));
        }
        task.setTargetEndpoint(vo.getTargetEndpoint());
        task.setTargetRegion(vo.getTargetRegion());
        task.setTargetBucket(vo.getTargetBucket());
        task.setTargetPathStyleAccess(boolOrDefault(vo.getTargetPathStyleAccess()));
        task.setStatus("PENDING");
        task.setTotalCount(0L);
        task.setDoneCount(0L);
        task.setFailedCount(0L);
        task.setCreateBy(username);
        task.setUpdateBy(username);
        migrationTaskMapper.addTask(task);
        Long taskId = task.getTaskId();

        // 经 Spring 代理触发异步迁移（同类内 this 调用不走代理会使 @Async 失效，故经 ApplicationContext 取代理调用）
        triggerAsyncMigration(taskId, vo);

        return taskId;
    }

    /**
     * 经 Spring 代理触发异步迁移，确保 @Async 生效（同类内 this 调用不走代理会使 @Async 失效）。
     * 用 ApplicationContext.getBean 取本接口的 Bean 代理调用 runMigrationAsync。
     */
    @Autowired
    private org.springframework.context.ApplicationContext applicationContext;

    private void triggerAsyncMigration(Long taskId, MigrationApplyVo vo) {
        FileMigrationService self = applicationContext.getBean(FileMigrationService.class);
        self.runMigrationAsync(taskId, vo);
    }

    /**
     * 实际迁移执行（@Async，经代理调用生效）。按 sourceType 分流遍历源端：
     * - OSS 源：listObjectsV2 分页遍历源桶，getObject 流转 putObject 写目标；
     * - LOCAL 源：localBackend.listLocalEntries() 遍历本地磁盘，Files.newInputStream 流转 putObject 写目标。
     * 单对象失败计 failedCount 不中断；整体异常置 FAILED + errorMessage。
     * 凭证从 vo 取（内存用完即弃），不落库。目录结构一致：源 objectKey 原样作目标 objectKey。
     */
    @Async("migrationExecutor")
    @Override
    public void runMigrationAsync(Long taskId, MigrationApplyVo vo) {
        long done = 0L;
        long failed = 0L;
        long total = 0L;
        boolean fromLocal = SOURCE_TYPE_LOCAL.equalsIgnoreCase(vo.getSourceType());
        try (S3Client target = buildClient(vo.getTargetEndpoint(), vo.getTargetRegion(),
                vo.getTargetAccessKey(), vo.getTargetSecretKey(), boolOrDefault(vo.getTargetPathStyleAccess()))) {

            if (fromLocal) {
                // 本地→OSS：listLocalEntries 已含 size/contentType，total 即条目数
                java.util.List<com.knowhub.pojo.storage.vo.LocalFileEntry> entries = localBackend.listLocalEntries();
                total = entries.size();
                migrationTaskMapper.updateStatus(taskId, "RUNNING", total, 0L, 0L, null);

                int batch = 0;
                for (com.knowhub.pojo.storage.vo.LocalFileEntry entry : entries) {
                    String objectKey = entry.objectKey();
                    try {
                        copyOneLocal(target, vo.getTargetBucket(), objectKey, entry.size(), entry.contentType());
                        done++;
                    } catch (Exception e) {
                        failed++;
                        log.warn("[OSS 迁移] 本地单文件拷贝失败 taskId={} key={} reason={}", taskId, objectKey, e.getMessage());
                    }
                    if (++batch % 50 == 0) {
                        migrationTaskMapper.updateProgress(taskId, done, failed);
                    }
                }
            } else {
                // OSS→OSS：源端临时 S3Client（只在 OSS 源时构造，LOCAL 源不构造省连接）
                try (S3Client source = buildClient(vo.getSourceEndpoint(), vo.getSourceRegion(),
                        vo.getSourceAccessKey(), vo.getSourceSecretKey(), boolOrDefault(vo.getSourcePathStyleAccess()))) {

                    // 先全量 list 计总数（分页累加），供前端进度条展示 done/total
                    String continuation = null;
                    do {
                        ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                                .bucket(vo.getSourceBucket());
                        if (continuation != null) {
                            reqBuilder.continuationToken(continuation);
                        }
                        ListObjectsV2Response resp = source.listObjectsV2(reqBuilder.build());
                        total += resp.contents().size();
                        continuation = resp.isTruncated() ? resp.nextContinuationToken() : null;
                    } while (continuation != null);

                    migrationTaskMapper.updateStatus(taskId, "RUNNING", total, 0L, 0L, null);

                    // 逐对象拷贝
                    continuation = null;
                    int batch = 0;
                    do {
                        ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                                .bucket(vo.getSourceBucket());
                        if (continuation != null) {
                            reqBuilder.continuationToken(continuation);
                        }
                        ListObjectsV2Response resp = source.listObjectsV2(reqBuilder.build());
                        for (S3Object s3Obj : resp.contents()) {
                            String objectKey = s3Obj.key();
                            try {
                                copyOne(source, target, vo.getSourceBucket(), vo.getTargetBucket(), objectKey, s3Obj.size());
                                done++;
                            } catch (Exception e) {
                                failed++;
                                log.warn("[OSS 迁移] 单对象拷贝失败 taskId={} key={} reason={}", taskId, objectKey, e.getMessage());
                            }
                            if (++batch % 50 == 0) {
                                migrationTaskMapper.updateProgress(taskId, done, failed);
                            }
                        }
                        continuation = resp.isTruncated() ? resp.nextContinuationToken() : null;
                    } while (continuation != null);
                }
            }

            // 终态：failed=0 → SUCCESS，否则也置 SUCCESS（部分成功），errorMessage 注明失败数
            String errMsg = failed > 0 ? ("迁移完成，但 " + failed + " 个对象拷贝失败，详见日志") : null;
            migrationTaskMapper.updateStatus(taskId, "SUCCESS", total, done, failed, errMsg);
            log.info("[OSS 迁移] 任务完成 taskId={} sourceType={} total={} done={} failed={}",
                    taskId, fromLocal ? "LOCAL" : "OSS", total, done, failed);
        } catch (Exception e) {
            log.error("[OSS 迁移] 任务异常 taskId={}", taskId, e);
            String failMsg = e.getMessage() != null ? e.getMessage() : "迁移异常：" + e.getClass().getSimpleName();
            migrationTaskMapper.updateStatus(taskId, "FAILED", total, done, failed, failMsg);
        }
    }

    /**
     * 本地→OSS 单文件拷贝：localBackend.openStreamByObjectKey 拉本地流 → 目标 putObject 写。
     * contentType 优先用 listLocalEntries 探测值，探不出回退默认 application/octet-stream。
     * contentLength 用 listLocalEntries 的 size（已知，putObject 走分块写）。
     * 用 try-with-resources 关闭本地流。
     */
    private void copyOneLocal(S3Client target, String targetBucket, String objectKey,
                              long contentLength, String contentType) {
        String ct = (contentType != null && !contentType.isEmpty()) ? contentType : "application/octet-stream";
        try (InputStream in = localBackend.openStreamByObjectKey(objectKey)) {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .contentType(ct)
                    .build();
            target.putObject(putRequest, RequestBody.fromInputStream(in, contentLength));
        } catch (Exception e) {
            throw new RuntimeException("本地→OSS 拷贝失败 key=" + objectKey + " reason=" + e.getMessage(), e);
        }
    }

    /**
     * 单对象拷贝：源 getObject 流 → 目标 putObject 写。优先用源对象 Head 拿 contentType 回填目标。
     * contentLength 用 listObjectsV2 返回的 s3Obj.size()（列表项已含对象大小，避免对网络流调 available）。
     * 用 try-with-resources 关闭 ResponseInputStream 归还 SDK 连接池。
     */
    private void copyOne(S3Client source, S3Client target, String sourceBucket, String targetBucket,
                         String objectKey, long contentLength) {
        // Head 源对象拿 contentType（putObject 需显式指定，否则默认 application/octet-stream）
        String contentType = "application/octet-stream";
        try {
            HeadObjectResponse head = source.headObject(HeadObjectRequest.builder()
                    .bucket(sourceBucket).key(objectKey).build());
            if (head.contentType() != null) {
                contentType = head.contentType();
            }
        } catch (Exception e) {
            // Head 失败不阻断，用默认 contentType 继续
            log.debug("[OSS 迁移] Head 源对象失败 key={} 用默认 contentType reason={}", objectKey, e.getMessage());
        }

        GetObjectRequest getRequest = GetObjectRequest.builder().bucket(sourceBucket).key(objectKey).build();
        try (InputStream in = source.getObject(getRequest)) {
            PutObjectRequest putRequest = PutObjectRequest.builder()
                    .bucket(targetBucket)
                    .key(objectKey)
                    .contentType(contentType)
                    .build();
            // contentLength 来自列表项 size，已知长度传 RequestBody 让 SDK 走分块写
            target.putObject(putRequest, RequestBody.fromInputStream(in, contentLength));
        } catch (Exception e) {
            throw new RuntimeException("拷贝对象失败 key=" + objectKey + " reason=" + e.getMessage(), e);
        }
    }

    @Override
    public MigrationProgressVo getMigrationProgress(Long taskId) {
        FileMigrationTask task = migrationTaskMapper.getTaskById(taskId);
        if (task == null) {
            throw new ServiceException(404, "迁移任务不存在");
        }
        MigrationProgressVo vo = new MigrationProgressVo();
        BeanUtil.copyProperties(task, vo);
        return vo;
    }

    // ---- 工具方法 ----

    private void validateVo(MigrationApplyVo vo) {
        if (vo == null) {
            throw new ServiceException(400,"迁移参数不能为空" );
        }
        String sourceType = vo.getSourceType();
        boolean fromLocal = SOURCE_TYPE_LOCAL.equalsIgnoreCase(sourceType);
        // sourceType 必填且只能是 OSS / LOCAL
        if (isBlank(sourceType)) {
            throw new ServiceException(400, "源端类型不能为空（OSS 或 LOCAL）");
        }
        if (!fromLocal && !SOURCE_TYPE_OSS.equalsIgnoreCase(sourceType)) {
            throw new ServiceException(400, "源端类型非法（仅支持 OSS 或 LOCAL）：" + sourceType);
        }
        // LOCAL 源不需要源 OSS 连接参数（源是本地磁盘）；OSS 源需完整源 OSS 连接参数
        if (!fromLocal) {
            if (isBlank(vo.getSourceEndpoint()) || isBlank(vo.getSourceAccessKey()) || isBlank(vo.getSourceSecretKey())
                    || isBlank(vo.getSourceBucket())) {
                throw new ServiceException(400, "源 OSS 连接参数不完整（endpoint/accessKey/secretKey/bucket 必填）");
            }
        }
        // 目标始终是 OSS，连接参数必填
        if (isBlank(vo.getTargetEndpoint()) || isBlank(vo.getTargetAccessKey()) || isBlank(vo.getTargetSecretKey())
                || isBlank(vo.getTargetBucket())) {
            throw new ServiceException(400, "目标 OSS 连接参数不完整（endpoint/accessKey/secretKey/bucket 必填）");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }

    private boolean boolOrDefault(Boolean b) {
        return b != null && b;
    }

    /** 临时构造 S3Client（不注册 Bean，用完 close）。region 空给占位值（SDK 要求非空，RustFS 不校验）。 */
    private S3Client buildClient(String endpoint, String region, String accessKey, String secretKey, boolean pathStyle) {
        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credProvider = StaticCredentialsProvider.create(creds);
        S3Configuration serviceCfg = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyle)
                .build();
        return S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(isBlank(region) ? "us-east-1" : region))
                .credentialsProvider(credProvider)
                .serviceConfiguration(serviceCfg)
                .build();
    }

    /** headBucket 探活：连接/凭证/桶是否可达。失败抛 ServiceException 提示管理员。 */
    private void probeBucket(S3Client client, String bucket, String label) {
        try {
            client.headBucket(HeadBucketRequest.builder().bucket(bucket).build());
        } catch (Exception e) {
            throw new ServiceException( 400,label + " OSS 连接/桶探活失败：" + e.getMessage());
        }
    }

    private UserInfo currentUser() {
        return (UserInfo) org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
    }

    /** 异步线程无 SecurityContext，安全取用户名（取不到回退系统） */
    private String currentUserSafe() {
        try {
            return currentUser().getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
