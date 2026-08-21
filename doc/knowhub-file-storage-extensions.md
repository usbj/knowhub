# knowhub 文件存储功能三扩展点设计说明

> 配套 SQL：`sql/knowhub-file-storage-extensions.sql`
> 适用范围：knowhub 模块（rookie-* 模块零改动，rookie-admin 的 application.yml 为已许可的配置改动）。

## 1. 背景与目标

knowhub 文件存储原走单一 OSS（RustFS）+ 双访问模式（中转/直链），`FileServiceImpl` 直接 `@Autowired S3Client/S3Presigner`，S3 SDK 调用散落各方法，`PublicObjectStream` 的 `stream` 字段类型 `ResponseInputStream<GetObjectResponse>` 把抽象层与 S3 绑死，无多后端 SPI。

本次落地三个扩展点：

1. **OSS 数据打包下载**：后台文件管理页一键下载 OSS 中所有文件成一个 zip，目录结构对齐 OSS；打包大小超 50G 弹警告确认；直链模式可选下载到客户端/服务器本地，中转模式下到服务器本地。
2. **OSS 数据传输（迁移）**：换 OSS 时按源/目标地址完全迁移，目录结构一致。
3. **新增本地模式**：系统设置「文件访问模式」加 `local`，此模式下上传落本地磁盘、访问走后端中转读盘。

## 2. 核心抽象：StorageBackend SPI

为支持多后端（S3/Local）并让扩展点 1/2 复用，抽 `StorageBackend` 接口，`FileServiceImpl` 按 `accessMode()` 分发。

### 2.1 接口定义

`com.knowhub.service.storage.backend.StorageBackend`：

```java
public interface StorageBackend {
    FileAccessMode mode();
    void put(FileObject fo, InputStream in, long contentLength, String contentType);
    InputStream get(FileObject fo);
    StorageHead head(FileObject fo);
    void delete(FileObject fo);
    List<String> listKeys(String bucket);
}
```

`StorageHead` 为 `record StorageHead(long contentLength, String contentType, String etag)`（`pojo/storage/vo/StorageHead.java`）。

### 2.2 S3StorageBackend（收口现有 S3 调用）

`@Component`，`@Autowired S3Client/S3Presigner/StorageProperties`。`mode()` 返回 `FileAccessMode.TRANSFER`（DIRECT 也落本类，分发逻辑里 DIRECT 回退到本实现）。把原 `FileServiceImpl` 散落的 S3 调用收口：`put`/`get`/`head`/`delete`/`listKeys`（`listKeys` 新增 `listObjectsV2` 分页）。

预签名是 S3 后端专属能力（DIRECT 模式用），不进接口，作为本类的非接口方法 `presignPut(FileObject, String contentType)` 与 `presignGet(FileObject, String originalName, String contentDisposition)`，由 `FileServiceImpl` 直接注入 `S3StorageBackend` 调用。

### 2.3 LocalStorageBackend（本地模式核心）

`@Component`，构造时 `@Value("${storage.local.base-path:./knowhub-upload}")` 读本地根目录。`mode()` 返回 `FileAccessMode.LOCAL`。用 `java.nio.file.Files` API 实现：
- `put`：`Files.createDirectories(parent)` + `Files.copy(in, path, REPLACE_EXISTING)`。
- `get`：`Files.newInputStream(path)`。
- `head`：`Files.size` + `Files.probeContentType`（探不出回退元数据 contentType）。
- `delete`：`Files.deleteIfExists`。
- `listKeys`：`Files.walk(basePath)` 过滤文件转相对路径（`\` 转 `/` 还原 objectKey）。
- 路径解析 `resolvePath(fo)`：`Paths.get(basePath).resolve(fo.getObjectKey()).normalize()`，**防穿越**：校验 `startsWith(basePath)`，不通过抛 `ServiceException`（仿 rookie `SysFileServiceImpl` 双重校验）。

`objectKey` 即磁盘相对路径，S3 与 Local 两实现共用同一 objectKey 语义。

### 2.4 PublicObjectStream 解耦 S3

`pojo/storage/vo/PublicObjectStream.java` 的 `stream` 字段类型从 `ResponseInputStream<GetObjectResponse>` 改为 `java.io.InputStream`，删 S3 import。`ResponseInputStream<GetObjectResponse>` 本身是 `InputStream` 子类，S3 实现直接传入无需包一层。Controller 与 `ProjectPortalController` 的 `try (var in = pos.getStream())` + `transferTo` 对 `InputStream` 同样工作。

### 2.5 FileServiceImpl 分发

- 注入 `List<StorageBackend>`（Spring 自动收集两实现）+ 私有 `backend()` 按 `storageConfigReader.accessMode()` 选 `mode()` 匹配实现；DIRECT 回退到 `s3Backend`。
- 删 `@Autowired S3Client/S3Presigner`（移入 S3StorageBackend），保留 `storageConfigReader`/`storageProperties`/`fileObjectMapper`。
- `applyUploadToken`：TRANSFER→`/file/proxy-upload/{id}`，LOCAL→`/file/local-upload/{id}`，DIRECT→`s3Backend.presignPut` + `rewriteHostToDirect`。LOCAL 跳过预签名签发。
- `confirmUpload`：`backend().head(fo)`；local `probeContentType` null 回退元数据 contentType。
- `streamPublicObject`/`streamDownloadObject`/`openRawStream`：`backend().get(fo)`。
- `proxyUpload`：`backend().put(fo, in, contentLength, contentType)`。
- `gc` 的 `deleteObjectQuietly`：`backend().delete(fo)`。
- `resolvePublicUrl`/`getDownloadUrl`：LOCAL 落 `/file/public/{id}` 与 `/file/proxy/{id}`（与 TRANSFER 完全同构，仅 backend 读盘非读 OSS）。

## 3. 扩展点1：OSS 数据打包下载

### 3.1 后端端点

`FileController` 新增三个端点，权限键 `knowhub:file:pack-download`：

- `GET /file/pack-size` → `Result<Long>`：累加所有 `deleted=0 + CONFIRMED` 的 `file_object.content_length`（`FileObjectMapper.sumConfirmedContentLength`），供前端弹警告确认。
- `GET /file/pack-download`：CLIENT 模式，同步流式写 `HttpServletResponse` 的 `ZipOutputStream`，遍历 `file_object` 行，每行 `ZipEntry(fo.getObjectKey())` + `backend().get(fo)` 裸流 `transferTo`，目录结构对齐 OSS objectKey。`Content-Disposition` 双段（RFC 5987 中文真名 + ASCII 百分号兜底），zip 名 `knowhub-oss-backup-yyyyMMddHHmmss.zip`。同步写 `HttpServletResponse`（与 `/file/proxy` 同口径：异步 dispatch 不传播 SecurityContext）。
- `POST /file/pack-download-server` → `Result<String>`：SERVER 模式，写 zip 到 `storage.local-base-path` 下，返回落盘绝对路径。

遍历用 `FileObjectMapper.listAllConfirmedForPack(offset, size)` 分页查（`pageSize=500`），防一次查十万行进内存。单对象不进内存（`transferTo` 8KB 缓冲）。

### 3.2 前端

`rookie-ui/src/views/knowhub/file/index.vue` 工具栏加「打包下载 OSS」按钮，`hasPermission(['knowhub:file:pack-download'])` 守卫。点击流程：
1. 调 `packSizeApi()` 取总字节；超 50G（50×1024³）弹 `ElMessageBox.confirm` 警告确认。
2. 弹「服务器本地 / 客户端本地」二选一（中转模式下选客户端会失败回退提示，由管理员决定）。
3. 客户端：`fetch` 带 Token 头取 `/file/pack-download` 的 zip blob 再 `a.click()` 触发下载（同中转下载口径）。
4. 服务器：调 `packDownloadServerApi()` 返路径，`ElMessage.success` 提示。

api：`rookie-ui/src/api/knowhub/file.ts` 加 `packSizeApi`/`packDownloadServerApi`；类型在 `rookie-ui/src/types/api/knowhub/file.ts`（后端 `Result<Long>`/`Result<String>` 裸值，无需包装类型）。

## 4. 扩展点2：OSS 数据迁移（OSS→OSS 与 本地→OSS）

迁移支持两种方向，由 `sourceType` 字段区分，共用同一任务表、同一 Service、同一对话框，仅源端读取方式不同：

- **OSS→OSS**（`sourceType=OSS`）：换 OSS 时按源/目标地址完全迁移，源端临时构造 S3Client 走 `listObjectsV2`。
- **本地→OSS**（`sourceType=LOCAL`）：把后端本地磁盘（`storage.local.base-path` 下的文件）批量迁移到目标 OSS，源端走 `LocalStorageBackend.listLocalEntries()`。

两种方向目标始终是 OSS（临时构造 S3Client 写入），目录结构一致（源 objectKey 原样作目标 objectKey）。

### 4.1 迁移任务表

`file_migration_task`（见 `sql/knowhub-file-storage-extensions.sql` §4）：记录连接元信息与进度。**敏感凭证不落库**：源/目标 `accessKey`/`secretKey` 由管理员后台表单临时传入，后端内存用完即弃，表只存 `endpoint`/`bucket`/`path_style_access`/进度/状态/错误信息。

`source_type` 列区分迁移方向（`OSS` / `LOCAL`）。LOCAL 源时 `source_*` 列留空（源是本地磁盘，无 OSS 连接元信息）。旧库已跑过无 `source_type` 列的建表语句时，SQL §4b 用 `information_schema` 判断幂等补列 + 把 `source_endpoint`/`source_bucket` 放宽为可空。

`status` 状态机：`PENDING` 建任务待跑 → `RUNNING` @Async 线程拷贝中 → `SUCCESS`/`FAILED`/`CANCELED`。

### 4.2 后端 Service + Controller

`com.knowhub.service.storage.impl.FileMigrationService` + `FileMigrationServiceImpl`：
- `startMigration(MigrationApplyVo vo)`：参数基础校验（按 `sourceType` 分流，LOCAL 源不校验 `source*`）→ 源端校验（OSS→`headBucket` 探活源桶；LOCAL→`localBackend.listLocalEntries()` 探活本地根目录存在）→ 目标 `headBucket` 探活 → 建任务行 PENDING（同步阶段取好 `createBy`，异步线程无 SecurityContext；LOCAL 源不写 `source_*` 列）→ 经 Spring 代理触发 `runMigrationAsync` → 立即返 taskId。
- `runMigrationAsync(taskId, vo)`：`@Async("migrationExecutor")`，按 `sourceType` 分流遍历源端：
  - **OSS 源**：临时构造源 S3Client，`listObjectsV2` 分页先全量计总数再逐对象 `getObject` 流转 `putObject` 写目标，contentLength 用 `s3Obj.size()`。
  - **LOCAL 源**：`localBackend.listLocalEntries()` 拿 `List<LocalFileEntry>`（含 size/contentType），total 即条目数，逐条 `openStreamByObjectKey` 拉本地流转 `putObject` 写目标，contentLength 用 `entry.size()`、contentType 用 `entry.contentType()`（探不出回退 `application/octet-stream`）。源端不构造 S3Client 省连接。
  - 单对象失败计 failedCount 不中断；每 50 个刷一次进度；终态置 SUCCESS/FAILED。
- `getMigrationProgress(taskId)`：查任务行返 `MigrationProgressVo`。

源/目标 S3Client 用 `S3Client.builder()` 按入参临时构造（不注册 Bean，`try-with-resources` 用完 close），避免污染单例。LOCAL 源时只构造目标 S3Client。

`FileMigrationController`（路由 `/file/migration`，权限键 `knowhub:file:transfer`）：
- `POST /file/migration/start`：传 `sourceType` + 源/目标连接参数（LOCAL 源 `source*` 留空），返 taskId。
- `GET /file/migration/progress/{taskId}`：返进度。

### 4.3 @Async 线程池

rookie 框架已有 `AsyncConfig`（@EnableAsync + `logExecutor`），但 `logExecutor` 队列小（2000）+ `DiscardOldestPolicy`（日志可丢），不适合长耗时迁移任务。knowhub 模块新建 `MigrationAsyncConfig` 产 `migrationExecutor` 线程池：corePoolSize=2、maxPoolSize=4、queueCapacity=10、`AbortPolicy`（超限拒绝并提示管理员）、`waitForTasksToCompleteOnShutdown=true` + `awaitTerminationSeconds=600`。`@Async("migrationExecutor")` 显式指定，不占日志池。

### 4.4 同类内 @Async 代理生效

`@Async` 经 Spring AOP 代理生效，同类内 `this` 调用不走代理会使 @Async 失效。`startMigration` 通过 `ApplicationContext.getBean(FileMigrationService.class)` 取本 Bean 代理调用 `runMigrationAsync`，确保异步生效。`runMigrationAsync` 留在接口上仅为代理生效所需，不供外部直接调用。

### 4.5 前端

`rookie-ui/src/views/knowhub/file/components/MigrationDialog.vue`（新）：表单顶部「迁移方向」单选（`OSS → OSS` / `本地 → OSS`）切换 `sourceType`。OSS→OSS 时显示源 OSS 表单（endpoint/region/accessKey/secretKey/bucket/pathStyleAccess）；本地→OSS 时源端隐藏，改显示本地磁盘提示（源为 `storage.local.base-path` 根目录，无需填写连接参数）。目标 OSS 表单始终展示。提交时按 `sourceType` 构造 payload（LOCAL 源不传 `source*`），调 `startMigrationApi` 返 taskId，进入进度轮询（`setInterval` 3 秒调 `getMigrationProgressApi`，展示进度条 done/total + 状态，终态停轮询）。凭证 `show-password`、提交后不持久化不回显、关闭弹窗清空表单并重置 `sourceType` 为 OSS。

`file/index.vue` 工具栏加「数据迁移」按钮，`hasPermission(['knowhub:file:transfer'])` 守卫，点开 MigrationDialog。api 加 `startMigrationApi`/`getMigrationProgressApi`，类型加 `MigrationApplyPayload`（含 `sourceType` + 可选 `source*` + 必填 `target*`）/`MigrationProgressRecord`。

### 4.6 本地→OSS 方案选型：直读磁盘流转写 OSS（不选 zip 上传）

本地→OSS 迁移曾考虑两种方案：

- **方案 A（zip 打包上传）**：后端先把本地目录打包成 zip，前端上传 zip 到目标 OSS，再解包。需要后端打包 + 前端上传 + OSS 侧解包三段，且 OSS 本身不支持解包，要么后端再从 OSS 拉 zip 解包写回，要么引额外工具，链路长、双倍流量、临时 zip 占空间。
- **方案 B（直读磁盘流转写 OSS）**：后端 `Files.walk` 遍历本地根目录，逐文件 `openStreamByObjectKey` 拉本地流转 `putObject` 写目标 OSS。本地文件已在后端磁盘，无需经前端中转，无临时 zip，单文件流式不进内存，目录结构天然由 objectKey 相对路径保持。

选 **方案 B**：本地文件本就在后端磁盘，直读直写最省事，避免 zip 打包/解包/上传的额外开销与双倍流量。与 OSS→OSS 共用同一任务表/Service/对话框/进度轮询，仅源端从「临时 S3Client」换成「LocalStorageBackend 读盘」，改动最小。`LocalStorageBackend.listLocalEntries()` 返回 `LocalFileEntry(objectKey, size, contentType)` 一次拿齐迁移所需元信息，避免迁移线程里对每个文件再 stat。

> 本地→OSS 典型场景：历史从本地模式切回 OSS 模式前，把本地磁盘文件批量回迁 OSS；或本地模式做临时落盘后归档到 OSS。迁移完成后人工切换 `access_mode` 回 `transfer`/`direct`。

## 5. 扩展点3：本地模式

### 5.1 FileAccessMode 加 LOCAL

`com.knowhub.enums.storage.FileAccessMode` 加 `LOCAL("local", "本地存储模式")`。`StorageConfigReader.accessMode()` 不变（`ofCode` 自动命中新枚举）。

### 5.2 配置

`StorageProperties` 加 `localBasePath`（默认 `./knowhub-upload`）+ `StorageConfigReader.localBasePath()`。`rookie-admin/src/main/resources/application.yml` 的 `storage:` 段加 `local-base-path: ./knowhub-upload`。改 yml 需重启；access_mode 走 sys_config 运行时切，但切到 local 前需确保 `local-base-path` 已配且历史数据已打包下载到本地。

### 5.3 本地上传端点

`FileController` 新增 `PUT /file/local-upload/{objectId}`：`@RequestBody byte[]` 读字节流（与 `/file/proxy-upload` 同口径避开 filter 消费流坑），复用 `fileService.proxyUpload`（内部 `backend().put` 在 LOCAL 写盘）。`@PreAuthorize('knowhub:file:upload')`。SecurityConfig 无需改：`/file/local-upload/**` 走 `anyRequest().authenticated()` + `@PreAuthorize`。

### 5.4 访问链路

LOCAL 模式访问链路与 TRANSFER 完全同构：PUBLIC 走 `/file/public/{id}`、PRIVATE 走 `/file/proxy/{id}`，仅 backend 读盘而非读 OSS，Controller 与鉴权链路零改动。`resolvePublicUrl` 返回 `/file/public/{id}`，`getDownloadUrl` 返回 `/file/proxy/{id}`。

### 5.5 前端系统设置下拉

`rookie-ui/src/views/system/system-config/config.ts`：`createSysConfigSchema` 签名加 `configKey` 参数；`configValue` 字段按 `configKey === 'knowhub.file.access_mode'` 切 `inputType='select'` + 挂三选项（中转 `transfer` / 直链 `direct` / 本地 `local`）。`index.vue` 调用处传当前行 `configKey`。

### 5.6 历史数据不自动迁移

切 local 后历史 OSS 文件访问时 backend 为 LocalStorageBackend 读盘，本地无此文件抛 404。用户决策「只影响新上传、历史不动」，预期行为；`access_mode` 的 remark 已提示「切前先打包下载到本地」。不做双源回退（决策已否决）。

## 6. 权限点 SQL

`sql/knowhub-file-storage-extensions.sql`：

1. `sys_config`：`knowhub.file.access_mode` remark UPDATE（加 local 说明 + 切换提示），仅改 remark 不动 config_value，幂等。
2. `sys_menu`：`knowhub:file:pack-download`（menu_id=212，挂 79 下）按钮权限，NOT EXISTS 判重。
3. `sys_menu`：`knowhub:file:transfer`（menu_id=213，挂 79 下）按钮权限，NOT EXISTS 判重。
4. `file_migration_task` 建表（IF NOT EXISTS，含 `source_type` 列、`source_*` 可空，支持 OSS→OSS 与本地→OSS）。
5. `file_migration_task` 补列/放宽约束段（§4b，仅作用于已存在的旧表）：`source_type` 列不存在则 ADD（`information_schema` 判断，兼容 MySQL 8.0.29 之前无 `ADD COLUMN IF NOT EXISTS`），`source_endpoint`/`source_bucket` MODIFY 成可空。
6. 末尾验证 SELECT（注释形式，供手动核对）。

menu_id 续编：rookie 上游新增菜单后原 196/197 已被占，两权限点改用 212/213（查库 MAX 后续编）。续编前查库 MAX 是项目约定（rookie 上游可能新增模块占 ID）。

## 7. 改动文件清单

### 后端新建
- `knowhub/.../service/storage/backend/StorageBackend.java`（接口）
- `knowhub/.../service/storage/backend/S3StorageBackend.java`（S3 实现）
- `knowhub/.../service/storage/backend/LocalStorageBackend.java`（本地实现）
- `knowhub/.../pojo/storage/vo/StorageHead.java`（head 结果 VO）
- `knowhub/.../pojo/storage/vo/LocalFileEntry.java`（本地文件条目 record，本地→OSS 迁移用）
- `knowhub/.../pojo/storage/entity/FileMigrationTask.java`（迁移任务实体，含 `sourceType`）
- `knowhub/.../pojo/storage/vo/MigrationApplyVo.java` / `MigrationProgressVo.java`
- `knowhub/.../mapper/storage/FileMigrationTaskMapper.java` + XML
- `knowhub/.../service/storage/impl/FileMigrationService.java` + `FileMigrationServiceImpl.java`
- `knowhub/.../controller/common/FileMigrationController.java`
- `knowhub/.../config/MigrationAsyncConfig.java`

### 后端修改
- `knowhub/.../pojo/storage/vo/PublicObjectStream.java`（stream 解耦 S3）
- `knowhub/.../enums/storage/FileAccessMode.java`（加 LOCAL）
- `knowhub/.../config/StorageConfigReader.java`（加 localBasePath）
- `knowhub/.../config/StorageProperties.java`（加 localBasePath）
- `knowhub/.../service/storage/FileServiceImpl.java`（分发 backend + LOCAL 分支 + 打包方法）
- `knowhub/.../service/storage/impl/FileService.java`（接口加 pack 方法签名）
- `knowhub/.../controller/common/FileController.java`（加 local-upload / pack-download / pack-size / pack-download-server）
- `knowhub/.../mapper/storage/FileObjectMapper.java` + XML（加 listAllConfirmedForPack / sumConfirmedContentLength）

### 后端框架配置（已许可）
- `rookie-admin/src/main/resources/application.yml`（加 storage.local-base-path）

### 前端
- `rookie-ui/src/views/system/system-config/config.ts`（access_mode 渲染成 select + createSysConfigSchema 加 configKey）
- `rookie-ui/src/views/system/system-config/index.vue`（调用处传 configKey）
- `rookie-ui/src/views/knowhub/file/index.vue`（工具栏加打包下载 + 数据迁移按钮）
- `rookie-ui/src/views/knowhub/file/components/MigrationDialog.vue`（新，迁移对话框 + 进度轮询，支持 OSS→OSS 与本地→OSS 双方向）
- `rookie-ui/src/api/knowhub/file.ts` + `types/api/knowhub/file.ts`（加 pack/migration api 与类型）
- `rookie-ui/src/constants/systemPermissions.ts`（加 file.packDownload / file.transfer）

### SQL
- `sql/knowhub-file-storage-extensions.sql`（remark UPDATE + 两权限点 + 迁移任务表 + 验证）

## 8. 风险与取舍

1. **本地模式无预签名**：LOCAL 分支跳过 `presignPutObject`，uploadUrl 走 `/file/local-upload/{id}` 后端中转写盘。三步上传协议（令牌→PUT→confirm）形态保持，前端 `presignedUploadFlow` 的 `isRelative` 判断自动带 Token，前台 8 个上传入口零改动。
2. **迁移凭证不落库**：源/目标 accessKey/secretKey 由管理员表单临时传入，后端内存用完即弃，表只存 endpoint/bucket/进度。避免凭证泄漏。代价：迁移任务重启后无法续传（需重新填凭证）——迁移是低频运维操作，可接受。
3. **@Async 迁移线程池**：独立 `migrationExecutor`（core=2/max=4/queue=10/AbortPolicy），不占 rookie 日志池。
4. **打包下载大文件内存**：zip 流式写 + `backend().get` 逐对象 `transferTo`（8KB 缓冲，单对象不进内存）。`file_object` 行数多时分页查（pageSize=500）。
5. **历史数据不自动迁移**：切 local 后历史 OSS 文件访问读盘 404，预期行为；remark 已提示「切前先打包下载到本地」。不做双源回退。
6. **rookie 模块零改动**：所有后端业务改动在 knowhub 模块（S3ClientConfig 是 knowhub 模块的）。rookie-admin 的 application.yml 是已许可的配置文件。

## 9. 验证

### SQL 验证
- 跑 `sql/knowhub-file-storage-extensions.sql`，确认 access_mode remark 已更新、两权限点入库、`file_migration_task` 建表且含 `source_type` 列。重跑验幂等（含 §4b 补列段：旧表补 `source_type`、`source_*` 放宽可空）。

### 启动 + 功能验证
- 启动 `rookie-admin`，默认 access_mode=transfer，现有上传/下载/回显链路全通（回归）。
- 后台系统设置改 access_mode=local，上传一个文件：确认落 `{local-base-path}/{objectKey}`；PUBLIC 回显走 `/file/public/{id}` 读盘正常；PRIVATE 下载走 `/file/proxy/{id}` 读盘正常。
- 切回 transfer，上传仍走 OSS（回归）。
- 文件管理页点「打包下载」：先弹大小预估，超 50G 弹警告确认；客户端模式浏览器下到 zip，解压确认目录结构对齐 objectKey；服务器模式落服务器本地，提示路径。
- 文件管理页点「数据迁移」选「OSS → OSS」：填源/目标 OSS 连接，提交后进度条轮询，完成后去目标 OSS 确认目录结构与源一致。
- 文件管理页点「数据迁移」选「本地 → OSS」：源端表单隐藏、显示本地磁盘提示，只填目标 OSS 连接，提交后进度条轮询，完成后去目标 OSS 确认目录结构与 `storage.local.base-path` 下的相对路径一致。

### 前端验证
- `rookie-ui/` 跑 `npm run type-check`，无类型错误。
- 系统设置页编辑「文件访问模式」：渲染成三选项下拉（中转/直链/本地），保存后后端读 sys_config 即时生效。
- 文件管理页工具栏见「打包下载」「数据迁移」两按钮，权限守卫生效。
