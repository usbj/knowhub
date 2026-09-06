package com.knowhub.service.storage;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.config.StorageConfigReader;
import com.knowhub.config.StorageProperties;
import com.knowhub.enums.storage.FileAccess;
import com.knowhub.enums.storage.FileAccessMode;
import com.knowhub.enums.storage.FileBusinessType;
import com.knowhub.enums.storage.UploadStatus;
import com.knowhub.mapper.storage.FileObjectMapper;
import com.knowhub.pojo.storage.entity.FileObject;
import com.knowhub.pojo.storage.quarry.FileQuarry;
import com.knowhub.pojo.storage.vo.PublicObjectStream;
import com.knowhub.pojo.storage.vo.StorageHead;
import com.knowhub.pojo.storage.vo.UploadApplyVo;
import com.knowhub.pojo.storage.vo.UploadTokenVo;
import com.knowhub.service.storage.backend.S3StorageBackend;
import com.knowhub.service.storage.backend.StorageBackend;
import com.knowhub.pojo.common.vo.BindVo;
import com.knowhub.pojo.storage.vo.DownloadVo;
import com.knowhub.pojo.storage.vo.FileObjectVo;
import com.knowhub.service.storage.impl.FileService;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * 文件存储服务实现。
 * <p>
 * 按 {@link StorageConfigReader#accessMode()} 分发到对应 {@link StorageBackend}（TRANSFER/DIRECT→S3，LOCAL→Local），
 * 字节级 put/get/head/delete 走 backend；预签名签发是 S3 后端专属能力（DIRECT 模式用），通过 {@link #s3Backend}
 * 直接调用。预签名纯本地计算（不触网）；HeadObject/DeleteObject 触网但放事务外，失败仅置 FAILED/GC，由定时任务兜底。
 * 元数据写操作 @Transactional。
 * <p>
 * LOCAL 模式访问链路与 TRANSFER 完全同构：PUBLIC 走 /file/public/{id}、PRIVATE 走 /file/proxy/{id}，
 * 仅 backend 读盘而非读 OSS，Controller 与鉴权链路零改动。
 */
@Service
public class FileServiceImpl implements FileService {

    private static final Logger log = LoggerFactory.getLogger(FileServiceImpl.class);

    @Autowired
    private FileObjectMapper fileObjectMapper;

    @Autowired
    private StorageConfigReader storageConfigReader;

    @Autowired
    private StorageProperties storageProperties;

    /** S3 后端（供 DIRECT 模式预签名直接调用，非接口方法） */
    @Autowired
    private S3StorageBackend s3Backend;

    /** 全部后端实现，按 mode() 建索引供分发 */
    private Map<FileAccessMode, StorageBackend> backends;

    @Autowired
    public void setBackends(List<StorageBackend> all) {
        this.backends = all.stream().collect(Collectors.toMap(StorageBackend::mode, Function.identity()));
    }

    /** 按当前访问模式选 backend。DIRECT 与 TRANSFER 都落 S3 后端（S3 实现 mode() 返回 TRANSFER）。 */
    private StorageBackend backend() {
        FileAccessMode mode = storageConfigReader.accessMode();
        StorageBackend b = backends.get(mode);
        if (b == null) {
            // DIRECT 落到 S3 后端（mode() 返回 TRANSFER）
            if (mode == FileAccessMode.DIRECT) {
                return s3Backend;
            }
            // 兜底：未注册的模式回落 S3 后端
            return s3Backend;
        }
        return b;
    }

    // ================================ 上传令牌 ================================

    @Override
    @Transactional
    public UploadTokenVo applyUploadToken(UploadApplyVo vo) {
        // 1. 校验 businessType 合法
        FileBusinessType type = FileBusinessType.ofCode(vo.getBusinessType());
        if (type == null) {
            throw new ServiceException(500, "非法的业务类型: " + vo.getBusinessType());
        }
        // 2. 校验 contentType/扩展名 落白名单（带文件名比对扩展名，避开 office 类 contentType 与扩展名不一致）
        if (!storageConfigReader.isContentTypeAllowed(type, vo.getContentType(), vo.getOriginalName())) {
            throw new ServiceException(500, "文件类型不在允许范围: " + vo.getContentType());
        }
        // 3. 校验 size 不超上限
        long limit = storageConfigReader.sizeLimitBytes(type);
        if (vo.getSize() == null || vo.getSize() <= 0) {
            throw new ServiceException(500, "文件大小声明缺失");
        }
        if (vo.getSize() > limit) {
            throw new ServiceException(500, "文件超出大小上限 " + (limit / 1024 / 1024) + "MB");
        }
        // 4. access 缺省按业务类型默认值
        FileAccess access = vo.getAccess() != null
                ? FileAccess.ofCode(vo.getAccess())
                : type.getDefaultAccess();
        if (access == null) {
            access = type.getDefaultAccess();
        }
        // 5. 生成 objectKey：{业务前缀}/{yyyy/MM/dd}/{uuid32}.{扩展名}
        String objectKey = buildObjectKey(type, vo.getOriginalName());

        // 6. insert PENDING 元数据行
        UserInfo userInfo = currentUser();
        FileObject fileObject = new FileObject();
        fileObject.setBucket(storageProperties.getBucket());
        fileObject.setObjectKey(objectKey);
        fileObject.setOriginalName(vo.getOriginalName());
        fileObject.setContentLength(vo.getSize());
        fileObject.setContentType(vo.getContentType());
        fileObject.setBusinessType(type.getCode());
        fileObject.setBizRefId(vo.getBizRefId());
        fileObject.setAccess(access.getCode());
        fileObject.setUploadStatus(UploadStatus.PENDING.getCode());
        fileObject.setCreateBy(userInfo.getUsername());
        fileObject.setUpdateBy(userInfo.getUsername());
        try {
            fileObjectMapper.addFileObject(fileObject);
        } catch (Exception e) {
            throw new ServiceException(500, "上传令牌签发失败", e.getMessage());
        }

        // 7. 按访问模式决定给前端的 uploadUrl：
        // - TRANSFER：/file/proxy-upload/{objectId}（前端 PUT 字节到后端，后端转发 OSS）
        // - DIRECT：直链预签名绝对 URL（host 用 directBaseUrl，前端直连 nginx 代理/OSS，需 CORS）
        // - LOCAL：/file/local-upload/{objectId}（前端 PUT 字节到后端，后端写本地磁盘，无预签名）
        FileAccessMode mode = storageConfigReader.accessMode();
        String uploadUrl;
        if (mode == FileAccessMode.TRANSFER) {
            uploadUrl = "/file/proxy-upload/" + fileObject.getObjectId();
        } else if (mode == FileAccessMode.LOCAL) {
            uploadUrl = "/file/local-upload/" + fileObject.getObjectId();
        } else {
            // DIRECT：签 PUT 预签名（纯本地计算不触网），host 改写为 directBaseUrl
            String presignedUrl = s3Backend.presignPut(fileObject, vo.getContentType());
            uploadUrl = rewriteHostToDirect(presignedUrl);
        }
        long expireMinutes = storageProperties.getUploadExpireMinutes();
        return new UploadTokenVo(
                uploadUrl,
                objectKey,
                fileObject.getObjectId(),
                expireMinutes * 60);
    }

    // ================================ 上传确认 ================================

    @Override
    @Transactional
    public Boolean confirmUpload(Long objectId, Long bizRefId) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            throw new ServiceException(500, "文件对象不存在");
        }
        if (!UploadStatus.PENDING.getCode().equals(fileObject.getUploadStatus())) {
            throw new ServiceException(500, "文件状态非待确认，无法确认: " + fileObject.getUploadStatus());
        }
        // 仅上传人或管理员可确认
        checkOwnerOrAdmin(fileObject);

        // backend head 核对真实值（S3 headObject / 本地 Files.size+probeContentType）
        StorageHead head;
        try {
            head = backend().head(fileObject);
        } catch (Exception e) {
            // 对象不存在 → 置 FAILED
            markFailed(fileObject);
            throw new ServiceException(500, "对象未上传或不存在", e.getMessage());
        }

        // 校验类型仍落白名单（防前端直传时改了 content-type）
        FileBusinessType type = FileBusinessType.ofCode(fileObject.getBusinessType());
        String realType = head.contentType();
        // 本地后端 probeContentType 可能探不出（null），回退元数据存的 contentType
        if (realType == null || realType.isEmpty()) {
            realType = fileObject.getContentType();
        }
        if (type != null && !storageConfigReader.isContentTypeAllowed(type, realType, fileObject.getOriginalName())) {
            markFailed(fileObject);
            throw new ServiceException(500, "实际上传类型不在允许范围: " + realType);
        }
        // 校验大小不超上限
        long limit = type != null ? storageConfigReader.sizeLimitBytes(type) : 0;
        if (type != null && head.contentLength() > limit) {
            markFailed(fileObject);
            throw new ServiceException(500, "实际上传大小超出上限");
        }

        // 通过：回填真实值并置 CONFIRMED
        FileObject update = new FileObject();
        update.setObjectId(objectId);
        update.setContentLength(head.contentLength());
        update.setContentType(realType);
        update.setChecksum(head.etag());
        update.setUploadStatus(UploadStatus.CONFIRMED.getCode());
        if (bizRefId != null) {
            update.setBizRefId(bizRefId);
        }
        update.setUpdateBy(currentUser().getUsername());
        fileObjectMapper.editFileObject(update);
        return true;
    }

    // ================================ PUBLIC 回显 ================================

    @Override
    public PublicObjectStream streamPublicObject(Long objectId) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            throw new ServiceException(404, "文件对象不存在");
        }
        if (!FileAccess.PUBLIC.getCode().equals(fileObject.getAccess())) {
            throw new ServiceException(403, "非公开对象，不可走 public 接口");
        }
        if (!UploadStatus.CONFIRMED.getCode().equals(fileObject.getUploadStatus())) {
            // 对外视作不存在，避免状态枚举探测
            throw new ServiceException(404, "文件未确认");
        }
        // backend 拉字节流；对象不存在抛异常由 Controller 兜底映射 404
        InputStream in = backend().get(fileObject);
        // Content-Type 优先用元数据存的，避免 RustFS 默认 octet-stream 导致 <img> 裂图
        String contentType = fileObject.getContentType() != null && !fileObject.getContentType().isEmpty()
                ? fileObject.getContentType() : null;
        long contentLength = fileObject.getContentLength() != null ? fileObject.getContentLength() : 0;
        return new PublicObjectStream(contentType, contentLength, null, in);
    }

    // ================================ 中转下载（PUBLIC + PRIVATE） ================================

    @Override
    public PublicObjectStream streamDownloadObject(Long objectId) {
        // 通用入口 → 走 owner 闸
        return streamDownloadObject(objectId, false);
    }

    @Override
    public PublicObjectStream streamDownloadObject(Long objectId, boolean bizAuthorized) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            throw new ServiceException(404, "文件对象不存在");
        }
        if (!UploadStatus.CONFIRMED.getCode().equals(fileObject.getUploadStatus())) {
            throw new ServiceException(404, "文件未确认");
        }
        //PRIVATE 走鉴权（上传人/管理员）或业务可见性（bizAuthorized=true 跳过）；PUBLIC 无鉴权
        if (FileAccess.PRIVATE.getCode().equals(fileObject.getAccess()) && !bizAuthorized) {
            checkOwnerOrAdmin(fileObject);
        }
        // backend 拉字节流；对象不存在抛异常由 Controller 兜底 404
        InputStream in = backend().get(fileObject);
        String contentType = fileObject.getContentType() != null && !fileObject.getContentType().isEmpty()
                ? fileObject.getContentType() : null;
        long contentLength = fileObject.getContentLength() != null ? fileObject.getContentLength() : 0;
        // PRIVATE 中转下载带 attachment;filename 强制下载（防浏览器直显私有文件）；PUBLIC 回显走 streamPublicObject 不带。
        // 双段：filename*=UTF-8''<enc> 走 RFC 5987 中文真名（现代浏览器用），旧段 filename="" 只放同源 ASCII 百分号
        // 编码兜底——旧式 filename 只能存 ISO-8859-1(0-255) 字节，中文会被 Tomcat 10 头校验抛 IllegalArgumentException 移除头，
        // 故旧段不塞原文，与 ProjectPortalController 项目打包下载同口径（见 buildDisposition）。
        String disposition = buildDisposition(fileObject.getOriginalName());
        return new PublicObjectStream(contentType, contentLength, null, disposition, in);
    }

    @Override
    public PublicObjectStream openRawStream(Long objectId) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            throw new ServiceException(404, "文件对象不存在");
        }
        if (!UploadStatus.CONFIRMED.getCode().equals(fileObject.getUploadStatus())) {
            throw new ServiceException(404, "文件未确认");
        }
        // 不做鉴权：调用方（项目打包 zip 等）自控业务级权限，本方法只负责把对象字节拉出来
        InputStream in = backend().get(fileObject);
        String contentType = fileObject.getContentType() != null && !fileObject.getContentType().isEmpty()
                ? fileObject.getContentType() : null;
        long contentLength = fileObject.getContentLength() != null ? fileObject.getContentLength() : 0;
        return new PublicObjectStream(contentType, contentLength, null, in);
    }

    @Override
    @Transactional
    public Boolean proxyUpload(Long objectId, java.io.InputStream in, long contentLength, String contentType) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            throw new ServiceException(500, "文件对象不存在");
        }
        if (!UploadStatus.PENDING.getCode().equals(fileObject.getUploadStatus())) {
            throw new ServiceException(500, "文件状态非待确认，无法代理上传: " + fileObject.getUploadStatus());
        }
        // 仅上传人或管理员可代理上传（防他人往已签发的 PENDING 行塞字节）
        checkOwnerOrAdmin(fileObject);
        // 请求体字节已由 Controller 用 @RequestBody byte[] 完整读入（绕开 request.getInputStream() 在
        // filter chain 中被上游消费导致的残缺问题），这里 in 已是 ByteArrayInputStream，readAllBytes 必拿到完整字节。
        // contentType 优先用请求头声明的，缺失时回退元数据存的（申请令牌时已校验落白名单）
        String realContentType = (contentType != null && !contentType.isEmpty())
                ? contentType : fileObject.getContentType();
        try {
            backend().put(fileObject, in, contentLength, realContentType);
        } catch (Exception e) {
            // 写入失败 → 置 FAILED，由 GC 清理（对象可能部分写入，delete 兜底）
            markFailed(fileObject);
            throw new ServiceException(500, "代理上传写入存储失败", e.getMessage());
        }
        // 仅写入后端，不在此 confirm：留给前端统一调 POST /file/confirm/{id} 走 head 核对并置 CONFIRMED。
        // 若在此内部 confirm，前端三步流程的第 3 步会因状态已 CONFIRMED 报"文件状态非待确认"，与直链模式流程不一致。
        return true;
    }

    // ================================ PUBLIC 回显链接（按模式真实 URL，不跳转） ================================

    @Override
    public String getPublicAccessUrl(Long objectId) {
        // 按 id 拿当前访问模式下的真实回显 URL（不跳转，供 /file/url/{id} 接口、详情接口顺带返回等
        // "需要直接拿到地址"的场景用）。逻辑复用 resolvePublicUrl 的校验+按模式分发：
        // - TRANSFER/LOCAL → /file/public/{objectId}；DIRECT → OSS 公开读直链或私有预签名。
        // 不可访问（对象不存在/非 PUBLIC/未确认）时不抛异常，回退 /file/public/{objectId}——
        // 由中转接口映射 403/404，供 VO 填充场景避免单条记录异常影响整页。
        String url = resolvePublicUrl(objectId);
        return url != null ? url : "/file/public/" + objectId;
    }

    @Override
    public String resolvePublicUrl(Long objectId) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            // 元数据不存在 → Controller 映射 404
            return null;
        }
        // 非 PUBLIC 或未确认 → Controller 映射 403/404（与中转回显 streamPublicObject 的可访问性校验对齐）
        boolean accessible = FileAccess.PUBLIC.getCode().equals(fileObject.getAccess())
                && UploadStatus.CONFIRMED.getCode().equals(fileObject.getUploadStatus());
        if (!accessible) {
            return null;
        }
        // 按访问模式分发 302 目标
        FileAccessMode mode = storageConfigReader.accessMode();
        if (mode == FileAccessMode.DIRECT) {
            if (storageConfigReader.publicBucketReadable()) {
                // 桶公开读：直链 {directBaseUrl}/{bucket}/{objectKey}，不带签名，永久有效
                return storageConfigReader.directBaseUrl() + "/" + fileObject.getBucket() + "/" + fileObject.getObjectKey();
            }
            // 桶私有：签短期 GET 预签名（host 用 directBaseUrl 走 rewriteHostToDirect），带签访问
            String url = s3Backend.presignGet(fileObject, null, null);
            return rewriteHostToDirect(url);
        }
        // 中转模式 / 本地模式：302 到后端字节流回显接口 /file/public/{objectId}（相对路径，浏览器按当前页 origin 解析同源命中代理）
        // 本地模式下 /file/public/{id} 由 backend 读盘回写，链路与中转完全同构
        return "/file/public/" + objectId;
    }


    // ================================ PRIVATE 下载 ================================

    @Override
    public DownloadVo getDownloadUrl(Long objectId) {
        // 通用入口（用户直选 objectId，无业务上下文）→ 走文件底座自有 owner 闸
        return getDownloadUrl(objectId, false);
    }

    @Override
    public DownloadVo getDownloadUrl(Long objectId, boolean bizAuthorized) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            throw new ServiceException(500, "文件对象不存在");
        }
        if (!UploadStatus.CONFIRMED.getCode().equals(fileObject.getUploadStatus())) {
            throw new ServiceException(500, "文件未确认，暂不可下载");
        }
        //PRIVATE 需鉴权 + 业务可见性；PUBLIC 不鉴权直接签发
        //bizAuthorized=true（业务模块已鉴权）跳过 owner 闸；false（通用入口）走 owner 闸
        if (FileAccess.PRIVATE.getCode().equals(fileObject.getAccess()) && !bizAuthorized) {
            checkOwnerOrAdmin(fileObject);
        }
        // 按访问模式决定给前端的 downloadUrl：
        // - TRANSFER/LOCAL：/file/proxy/{objectId}（后端拉字节回写，同源无 CORS；本地模式 backend 读盘）
        // - DIRECT：直链预签名绝对 URL（host 用 directBaseUrl，带 attachment;filename）
        FileAccessMode mode = storageConfigReader.accessMode();
        String downloadUrl;
        long expires;
        if (mode == FileAccessMode.DIRECT) {
            String disposition = buildDisposition(fileObject.getOriginalName());
            String url = s3Backend.presignGet(fileObject, fileObject.getOriginalName(), disposition);
            downloadUrl = rewriteHostToDirect(url);
            expires = storageProperties.getDownloadExpireMinutes() * 60;
        } else {
            // TRANSFER / LOCAL 都走中转下载接口（本地模式 backend 读盘，链路同构）
            downloadUrl = "/file/proxy/" + objectId;
            expires = storageProperties.getDownloadExpireMinutes() * 60;
        }
        return new DownloadVo(downloadUrl, expires, fileObject.getOriginalName());
    }

    // ================================ 查询 ================================

    @Override
    public PageInfo<FileObjectVo> quarryFile(FileQuarry quarry) {
        PageUtil.startPage();
        List<FileObject> list = fileObjectMapper.quarryFile(quarry);
        PageInfo<FileObject> page = PageUtil.packagedPageInfo(list);
        return PageUtil.copyPageInfo(page, FileObjectVo.class);
    }

    @Override
    public FileObjectVo getFileObjectInfo(Long objectId) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            throw new ServiceException(500, "文件对象不存在");
        }
        return BeanUtil.toBean(fileObject, FileObjectVo.class);
    }

    // ================================ 绑定 / 删除 ================================

    @Override
    @Transactional
    public Boolean bindBizRef(BindVo vo) {
        if (vo.getObjectId() == null || vo.getBizRefId() == null) {
            throw new ServiceException(500, "objectId/bizRefId 不能为空");
        }
        FileObject fileObject = fileObjectMapper.getFileObjectById(vo.getObjectId());
        if (fileObject == null) {
            throw new ServiceException(500, "文件对象不存在");
        }
        checkOwnerOrAdmin(fileObject);
        FileObject update = new FileObject();
        update.setObjectId(vo.getObjectId());
        update.setBizRefId(vo.getBizRefId());
        update.setUpdateBy(currentUser().getUsername());
        fileObjectMapper.editFileObject(update);
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteFileObjects(Long[] objectIds) {
        if (objectIds == null || objectIds.length == 0) {
            return true;
        }
        String username = currentUser().getUsername();
        try {
            for (Long id : objectIds) {
                fileObjectMapper.softDeleteFileObject(id, username);
            }
        } catch (Exception e) {
            throw new ServiceException(500, "文件删除失败", e.getMessage());
        }
        return true;
    }

    // ================================ GC ================================

    @Override
    public void gc() {
        // 1. 扫超时未确认 PENDING → delete + 物理删
        Date ttlBefore = new Date(System.currentTimeMillis()
                - storageProperties.getPendingTtlMinutes() * 60 * 1000);
        List<FileObject> expired = fileObjectMapper.findExpiredPending(ttlBefore);
        for (FileObject fo : expired) {
            deleteObjectQuietly(fo);
            fileObjectMapper.physicalDeleteFileObject(fo.getObjectId());
            log.info("[GC] 清理超时 PENDING object_id={} key={}", fo.getObjectId(), fo.getObjectKey());
        }
        // 2. 扫已软删行 → delete + 物理删
        List<FileObject> softDeleted = fileObjectMapper.findSoftDeleted();
        for (FileObject fo : softDeleted) {
            deleteObjectQuietly(fo);
            fileObjectMapper.physicalDeleteFileObject(fo.getObjectId());
            log.info("[GC] 清理软删对象 object_id={} key={}", fo.getObjectId(), fo.getObjectKey());
        }
        if (!expired.isEmpty() || !softDeleted.isEmpty()) {
            log.info("[GC] 本次清理：超时PENDING={} 软删={}", expired.size(), softDeleted.size());
        }
    }

    // ================================ 打包下载（扩展点1） ================================

    @Override
    public long packTotalSize() {
        Long sum = fileObjectMapper.sumConfirmedContentLength();
        return sum != null ? sum : 0L;
    }

    @Override
    public void streamPackDownload(java.io.OutputStream out) throws IOException {
        // 分页遍历所有未删 + CONFIRMED 行，每行 ZipEntry(objectKey) + backend.get 裸流 transferTo。
        // 目录结构对齐 OSS objectKey（形如 blog_cover/2026/08/17/uuid.png），解压即得 OSS 目录树。
        // 单对象不进内存（transferTo 8KB 缓冲）；分页查防一次拉十万行。
        int pageSize = 500;
        long offset = 0;
        java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(out);
        try {
            List<FileObject> batch;
            while (!(batch = fileObjectMapper.listAllConfirmedForPack(offset, pageSize)).isEmpty()) {
                for (FileObject fo : batch) {
                    try {
                        zos.putNextEntry(new java.util.zip.ZipEntry(fo.getObjectKey()));
                        try (InputStream in = backend().get(fo)) {
                            in.transferTo(zos);
                        }
                        zos.closeEntry();
                    } catch (Exception e) {
                        // 单个对象拉取失败不中断整个打包，记日志继续（与 ProjectPortalController 同口径）
                        log.warn("[打包下载] 拉取对象失败 object_id={} key={} reason={}",
                                fo.getObjectId(), fo.getObjectKey(), e.getMessage());
                    }
                }
                offset += batch.size();
            }
        } finally {
            zos.finish();
            // 不在此 close zos（out 由调用方管），仅 finish 刷尾
        }
    }

    @Override
    public String packDownloadToServer() {
        // 落 storage.local-base-path 下的临时 zip，返回绝对路径
        String basePath = storageConfigReader.localBasePath();
        java.nio.file.Path dir = java.nio.file.Paths.get(basePath).toAbsolutePath().normalize();
        try {
            java.nio.file.Files.createDirectories(dir);
        } catch (Exception e) {
            throw new ServiceException(500, "创建本地落盘目录失败: " + dir, e.getMessage());
        }
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
        String fileName = "knowhub-oss-backup-" + sdf.format(new Date()) + ".zip";
        java.nio.file.Path target = dir.resolve(fileName);
        try (java.io.OutputStream out = java.nio.file.Files.newOutputStream(target);
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(out)) {
            int pageSize = 500;
            long offset = 0;
            List<FileObject> batch;
            while (!(batch = fileObjectMapper.listAllConfirmedForPack(offset, pageSize)).isEmpty()) {
                for (FileObject fo : batch) {
                    try {
                        zos.putNextEntry(new java.util.zip.ZipEntry(fo.getObjectKey()));
                        try (InputStream in = backend().get(fo)) {
                            in.transferTo(zos);
                        }
                        zos.closeEntry();
                    } catch (Exception e) {
                        log.warn("[打包下载-服务器] 拉取对象失败 object_id={} key={} reason={}",
                                fo.getObjectId(), fo.getObjectKey(), e.getMessage());
                    }
                }
                offset += batch.size();
            }
        } catch (Exception e) {
            throw new ServiceException(500, "打包下载到服务器失败: " + target, e.getMessage());
        }
        return target.toString();
    }

    // ================================ 私有辅助 ================================

    /** 生成 objectKey：{业务类型小写}/{yyyy/MM/dd}/{uuid32}.{原始扩展名} */
    private String buildObjectKey(FileBusinessType type, String originalName) {
        String prefix = type.getCode().toLowerCase();
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyy/MM/dd");
        String datePath = sdf.format(new Date());
        String uuid = IdUtil.fastSimpleUUID();
        String ext = extractExtension(originalName);
        return prefix + "/" + datePath + "/" + uuid + (ext.isEmpty() ? "" : "." + ext);
    }

    private String extractExtension(String originalName) {
        if (originalName == null || originalName.isEmpty()) {
            return "";
        }
        int dot = originalName.lastIndexOf('.');
        if (dot < 0 || dot == originalName.length() - 1) {
            return "";
        }
        return originalName.substring(dot + 1).toLowerCase();
    }

    /**
     * 把预签名绝对 URL 的 host 替换为直链模式对外暴露的 base（directBaseUrl），
     * 保留桶名/对象 key/签名查询串原样，得到给前端的直链地址。
     * <p>
     * 形如 http://100.82.86.85:9000/knowhub/blog_cover/.../x.png?X-Amz-...
     * → {directBaseUrl}/knowhub/blog_cover/.../x.png?X-Amz-...
     * （directBaseUrl 通常是 nginx 公网反代域名，nginx 再转发到内网 OSS）。
     * <p>
     * 仅用于直链模式（DIRECT）：上传 PUT 与 PRIVATE 下载的预签名绝对 URL。
     * 若 url 不以配置 endpoint 开头（切到其他 OSS 等），原样返回不强制改写；
     * directBaseUrl 为空时回退用 yml endpoint（仅同网络段用户可达）。
     */
    private String rewriteHostToDirect(String absoluteUrl) {
        if (absoluteUrl == null || absoluteUrl.isEmpty()) {
            return absoluteUrl;
        }
        String endpoint = storageProperties.getEndpoint();
        if (endpoint == null || endpoint.isEmpty()) {
            return absoluteUrl;
        }
        String directBase = storageConfigReader.directBaseUrl();
        if (directBase == null || directBase.isEmpty()) {
            // 字典未配 directBaseUrl，回退用 yml endpoint（同网络段可达）
            directBase = endpoint;
        }
        if (endpoint.endsWith("/")) {
            endpoint = endpoint.substring(0, endpoint.length() - 1);
        }
        if (directBase.endsWith("/")) {
            directBase = directBase.substring(0, directBase.length() - 1);
        }
        if (absoluteUrl.startsWith(endpoint + "/")) {
            return directBase + absoluteUrl.substring(endpoint.length());
        }
        if (absoluteUrl.startsWith(endpoint)) {
            return directBase + absoluteUrl.substring(endpoint.length());
        }
        return absoluteUrl;
    }

    /** 文件名清洗：去引号防注入预签名 URL */
    private String sanitizeFilename(String name) {
        return name.replace("\"", "").replace("\\", "");
    }

    /**
     * 拼 RFC 6266 Content-Disposition 下载名（attachment；与 ProjectPortalController 项目打包下载同口径）。
     * <p>
     * 旧式 {@code filename="..."} 只能存 ISO-8859-1（0-255）字节，塞中文会被 Tomcat 10 MessageBytes 头校验
     * 抛 IllegalArgumentException 并移除整个头、中断响应；中文真名只走 {@code filename*=UTF-8''<enc>}，
     * 旧段放同源 ASCII 百分号编码兜底（旧浏览器只见 %xx 但不下错）。原文先经 sanitizeFilename 去 " 和 \，
     * 再 URLEncoder 编码（+→%20 避免空格歧义），两段共用同一编码串。
     */
    private String buildDisposition(String originalName) {
        String safe = sanitizeFilename(originalName == null ? "" : originalName);
        String enc = java.net.URLEncoder.encode(safe, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        return "attachment; filename=\"" + enc + "\"; filename*=UTF-8''" + enc;
    }

    /** 置 FAILED */
    private void markFailed(FileObject fo) {
        FileObject update = new FileObject();
        update.setObjectId(fo.getObjectId());
        update.setUploadStatus(UploadStatus.FAILED.getCode());
        update.setUpdateBy(currentUserSafe());
        fileObjectMapper.editFileObject(update);
    }

    /** delete 静默失败（GC 容错，下次再扫） */
    private void deleteObjectQuietly(FileObject fo) {
        try {
            backend().delete(fo);
        } catch (Exception e) {
            log.warn("[GC] DeleteObject 失败 object_id={} key={} reason={}",
                    fo.getObjectId(), fo.getObjectKey(), e.getMessage());
        }
    }

    /** 校验当前用户是上传人本人或管理员（具备 knowhub:file:review 权限视为管理员） */
    private void checkOwnerOrAdmin(FileObject fileObject) {
        UserInfo userInfo = currentUser();
        boolean isAdmin = userInfo.getPermissions() != null
                && userInfo.getPermissions().contains("knowhub:file:review");
        if (!userInfo.getUsername().equals(fileObject.getCreateBy()) && !isAdmin) {
            throw new ServiceException(500, "无权操作他人文件");
        }
    }

    private UserInfo currentUser() {
        return (UserInfo) org.springframework.security.core.context.SecurityContextHolder
                .getContext().getAuthentication().getPrincipal();
    }

    /** GC 场景无登录上下文，安全取用户名（取不到回退系统） */
    private String currentUserSafe() {
        try {
            return currentUser().getUsername();
        } catch (Exception e) {
            return "system";
        }
    }
}
