package com.knowhub.service.storage.impl;

import com.knowhub.pojo.storage.vo.MigrationApplyVo;
import com.knowhub.pojo.storage.vo.MigrationProgressVo;

/**
 * 数据迁移服务（扩展点2）。
 * <p>
 * 支持两种迁移方向：
 * - OSS→OSS：换 OSS 时按源/目标地址完全迁移，目录结构一致（源 objectKey 原样作目标 objectKey）；
 * - 本地→OSS：把后端本地磁盘（storage.local.base-path 下的文件）批量迁移到目标 OSS，目录结构一致。
 * 由 vo.sourceType 标识源端类型（OSS / LOCAL）。
 * <p>
 * 凭证不落库：源/目标 accessKey/secretKey 由管理员后台表单临时传入，后端内存用完即弃，
 * file_migration_task 表只存 endpoint/bucket/进度/状态/错误信息（LOCAL 源的 source_* 列留空）。
 * <p>
 * 流程：startMigration 校验源/目标 → 建任务行 PENDING →
 * @Async 线程执行实际拷贝（OSS 源走 listObjectsV2 逐对象流转；LOCAL 源走 Files.walk 逐文件流转写目标 OSS）
 * → 完成置 SUCCESS/FAILED。立即返 taskId，前端轮询 getMigrationProgress 展示进度。
 */
public interface FileMigrationService {

    /**
     * 启动迁移任务。校验源/目标 → 建任务行 PENDING → 异步线程拷贝 → 立即返 taskId。
     * @param vo 源类型 + 目标 OSS 连接参数（OSS→OSS 还需源 OSS 连接参数；凭证内存用完即弃）
     * @return 迁移任务 ID（前端凭此轮询进度）
     */
    Long startMigration(MigrationApplyVo vo);

    /**
     * 查迁移进度。前端轮询展示进度条 done/total + 状态，完成/失败停轮询。
     * @param taskId startMigration 返回的任务 ID
     * @return 进度 VO（status/totalCount/doneCount/failedCount/errorMessage）
     */
    MigrationProgressVo getMigrationProgress(Long taskId);

    /**
     * 实际迁移执行（@Async，内部方法）。由 startMigration 经 Spring 代理触发，确保 @Async 生效
     * （同类内 this 调用不走代理会使 @Async 失效，故经 ApplicationContext.getBean 取代理调用）。
     * <p>
     * 不供外部直接调用——外部应调 startMigration 拿 taskId 后轮询 getMigrationProgress。
     * 留在接口上仅为 @Async 代理生效所需（代理需接口方法）。
     * @param taskId startMigration 建好的任务行 ID
     * @param vo 源/目标 OSS 连接参数（含凭证，内存用完即弃）
     */
    void runMigrationAsync(Long taskId, MigrationApplyVo vo);
}
