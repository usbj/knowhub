package com.knowhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.util.IdUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.config.StorageConfigReader;
import com.knowhub.config.StorageProperties;
import com.knowhub.enums.FileAccess;
import com.knowhub.enums.FileAccessMode;
import com.knowhub.enums.FileBusinessType;
import com.knowhub.enums.UploadStatus;
import com.knowhub.mapper.FileObjectMapper;
import com.knowhub.pojo.entity.FileObject;
import com.knowhub.pojo.quarry.FileQuarry;
import com.knowhub.pojo.vo.BindVo;
import com.knowhub.pojo.vo.DownloadVo;
import com.knowhub.pojo.vo.FileObjectVo;
import com.knowhub.pojo.vo.PublicObjectStream;
import com.knowhub.pojo.vo.UploadApplyVo;
import com.knowhub.pojo.vo.UploadTokenVo;
import com.knowhub.service.FileService;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.time.Duration;
import java.util.Date;
import java.util.List;

/**
 * 文件存储服务实现。
 * 预签名签发是纯本地计算（不触网）；HeadObject/DeleteObject 触网但放事务外，
 * 失败时仅置 FAILED/GC，由定时任务兜底。元数据写操作 @Transactional。
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

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    // ================================ 上传令牌 ================================

    @Override
    @Transactional
    public UploadTokenVo applyUploadToken(UploadApplyVo vo) {
        // 1. 校验 businessType 合法
        FileBusinessType type = FileBusinessType.ofCode(vo.getBusinessType());
        if (type == null) {
            throw new ServiceException(500, "非法的业务类型: " + vo.getBusinessType());
        }
        // 2. 校验 contentType 落白名单
        if (!storageConfigReader.isContentTypeAllowed(type, vo.getContentType())) {
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

        // 7. 签发 PutObject 预签名（带 Content-Type 约束；Content-Length 由前端 PUT 时传 header 保证）
        long expireMinutes = storageProperties.getUploadExpireMinutes();
        PutObjectRequest putReq = PutObjectRequest.builder()
                .bucket(fileObject.getBucket())
                .key(objectKey)
                .contentType(vo.getContentType())
                .build();
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(p -> p
                .putObjectRequest(putReq)
                .signatureDuration(Duration.ofMinutes(expireMinutes)));

        // 按访问模式决定给前端的 uploadUrl：
        // - TRANSFER：填后端代理上传接口 /file/proxy-upload/{objectId}（前端 PUT 字节到后端，后端转发到 OSS）
        // - DIRECT：填直链预签名绝对 URL（host 用 directBaseUrl，前端直连 nginx 代理/OSS，需 CORS）
        String uploadUrl;
        if (storageConfigReader.accessMode() == FileAccessMode.TRANSFER) {
            uploadUrl = "/file/proxy-upload/" + fileObject.getObjectId();
        } else {
            uploadUrl = rewriteHostToDirect(presigned.url().toString());
        }
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

        // HeadObject 核对真实值
        HeadObjectResponse head;
        try {
            head = s3Client.headObject(HeadObjectRequest.builder()
                    .bucket(fileObject.getBucket())
                    .key(fileObject.getObjectKey())
                    .build());
        } catch (Exception e) {
            // 对象不存在 → 置 FAILED
            markFailed(fileObject);
            throw new ServiceException(500, "对象未上传或不存在", e.getMessage());
        }

        // 校验类型仍落白名单（防前端直传时改了 content-type）
        FileBusinessType type = FileBusinessType.ofCode(fileObject.getBusinessType());
        String realType = head.contentType();
        if (type != null && !storageConfigReader.isContentTypeAllowed(type, realType)) {
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
        update.setChecksum(head.eTag());
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
        // 用 s3Client.getObject 拉字节流；NoSuchKeyException（元数据与对象不一致）不在此 catch，
        // 向上抛由 Controller 兜底映射 404
        ResponseInputStream<GetObjectResponse> ris = s3Client.getObject(GetObjectRequest.builder()
                .bucket(fileObject.getBucket())
                .key(fileObject.getObjectKey())
                .build());
        GetObjectResponse resp = ris.response();
        // Content-Type 优先用元数据存的，避免 RustFS 默认 octet-stream 导致 <img> 裂图；
        // contentLength/etag 用 S3 实际响应兜底
        String contentType = fileObject.getContentType() != null && !fileObject.getContentType().isEmpty()
                ? fileObject.getContentType()
                : resp.contentType();
        long contentLength = resp.contentLength() > 0 ? resp.contentLength() : fileObject.getContentLength();
        return new PublicObjectStream(contentType, contentLength, resp.eTag(), ris);
    }

    // ================================ 中转下载（PUBLIC + PRIVATE） ================================

    @Override
    public PublicObjectStream streamDownloadObject(Long objectId) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            throw new ServiceException(404, "文件对象不存在");
        }
        if (!UploadStatus.CONFIRMED.getCode().equals(fileObject.getUploadStatus())) {
            throw new ServiceException(404, "文件未确认");
        }
        // PRIVATE 走鉴权（上传人/管理员）；PUBLIC 无鉴权（中转下载接口本身已 permitAll 或走权限键，见 Controller）
        if (FileAccess.PRIVATE.getCode().equals(fileObject.getAccess())) {
            checkOwnerOrAdmin(fileObject);
        }
        // 拉字节流；NoSuchKeyException（元数据与对象不一致）不在此 catch，向上抛由 Controller 兜底 404
        ResponseInputStream<GetObjectResponse> ris = s3Client.getObject(GetObjectRequest.builder()
                .bucket(fileObject.getBucket())
                .key(fileObject.getObjectKey())
                .build());
        GetObjectResponse resp = ris.response();
        String contentType = fileObject.getContentType() != null && !fileObject.getContentType().isEmpty()
                ? fileObject.getContentType()
                : resp.contentType();
        long contentLength = resp.contentLength() > 0 ? resp.contentLength() : fileObject.getContentLength();
        // PRIVATE 中转下载带 attachment;filename 强制下载（防浏览器直显私有文件）；PUBLIC 回显走 streamPublicObject 不带
        String disposition = "attachment;filename=\"" + sanitizeFilename(fileObject.getOriginalName()) + "\"";
        return new PublicObjectStream(contentType, contentLength, resp.eTag(), disposition, ris);
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
        // 写入 OSS：用前端声明的 contentType（须与申请令牌时一致）；contentLength 传给 SDK 以正确分块
        try {
            s3Client.putObject(PutObjectRequest.builder()
                            .bucket(fileObject.getBucket())
                            .key(fileObject.getObjectKey())
                            .contentType(contentType)
                            .contentLength(contentLength)
                            .build(),
                    software.amazon.awssdk.core.sync.RequestBody.fromInputStream(in, contentLength));
        } catch (Exception e) {
            // 写入失败 → 置 FAILED，由 GC 清理（对象可能部分写入，DeleteObject 兜底）
            markFailed(fileObject);
            throw new ServiceException(500, "代理上传写入 OSS 失败", e.getMessage());
        }
        // 写入成功后走 confirm 核对真实值并置 CONFIRMED（复用既有逻辑：HeadObject 校验类型/大小）
        return confirmUpload(objectId, null);
    }

    // ================================ PUBLIC 回显链接（按模式） ================================

    @Override
    public String getPublicAccessUrl(Long objectId) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            // 元数据不存在时回退中转相对路径——访问时由 Controller 返回 404，前端拿到的是同源链接不会暴露状态
            return "/file/public/" + objectId;
        }
        // 非 PUBLIC 或未确认时回退中转相对路径——由中转接口映射 403/404，不在此抛异常（供 VO 填充场景，避免单条记录异常影响整页）
        boolean accessible = FileAccess.PUBLIC.getCode().equals(fileObject.getAccess())
                && UploadStatus.CONFIRMED.getCode().equals(fileObject.getUploadStatus());
        if (storageConfigReader.accessMode() == FileAccessMode.DIRECT && accessible) {
            // 直链模式：拼公开读直链 {directBaseUrl}/{bucket}/{objectKey}（不带签名，永不过期，依赖 OSS 桶公开可读）
            return storageConfigReader.directBaseUrl() + "/" + fileObject.getBucket() + "/" + fileObject.getObjectKey();
        }
        // 中转模式或不可访问：回退后端中转接口（不可访问时由 Controller 返 403/404）
        return "/file/public/" + objectId;
    }

    // ================================ PRIVATE 下载 ================================

    @Override
    public DownloadVo getDownloadUrl(Long objectId) {
        FileObject fileObject = fileObjectMapper.getFileObjectById(objectId);
        if (fileObject == null) {
            throw new ServiceException(500, "文件对象不存在");
        }
        if (!UploadStatus.CONFIRMED.getCode().equals(fileObject.getUploadStatus())) {
            throw new ServiceException(500, "文件未确认，暂不可下载");
        }
        // PUBLIC 也可走下载（享受强制文件名）；PRIVATE 需鉴权 + 业务可见性
        if (FileAccess.PRIVATE.getCode().equals(fileObject.getAccess())) {
            checkOwnerOrAdmin(fileObject); // 首版最简：上传人/管理员可见，业务模块接入后细化
        }
        // 签短期 GET 预签名，带 attachment;filename 强制下载
        PresignedGetObjectRequest presigned = presignGet(fileObject, fileObject.getOriginalName());
        long expires = storageProperties.getDownloadExpireMinutes() * 60;
        // 按访问模式决定给前端的 downloadUrl：
        // - TRANSFER：填后端中转下载接口 /file/proxy/{id}（后端拉 OSS 字节回写，同源无 CORS）
        // - DIRECT：填直链预签名绝对 URL（host 用 directBaseUrl，前端直连 nginx 代理/OSS）
        String downloadUrl;
        if (storageConfigReader.accessMode() == FileAccessMode.TRANSFER) {
            downloadUrl = "/file/proxy/" + objectId;
        } else {
            downloadUrl = rewriteHostToDirect(presigned.url().toString());
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
        // 1. 扫超时未确认 PENDING → DeleteObject + 物理删
        Date ttlBefore = new Date(System.currentTimeMillis()
                - storageProperties.getPendingTtlMinutes() * 60 * 1000);
        List<FileObject> expired = fileObjectMapper.findExpiredPending(ttlBefore);
        for (FileObject fo : expired) {
            deleteObjectQuietly(fo);
            fileObjectMapper.physicalDeleteFileObject(fo.getObjectId());
            log.info("[GC] 清理超时 PENDING object_id={} key={}", fo.getObjectId(), fo.getObjectKey());
        }
        // 2. 扫已软删行 → DeleteObject + 物理删
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

    /** 签 GET 预签名；originalName 非空时带 attachment;filename 强制下载 */
    private PresignedGetObjectRequest presignGet(FileObject fo, String originalName) {
        return s3Presigner.presignGetObject(p -> p
                .getObjectRequest(b -> {
                    b.bucket(fo.getBucket()).key(fo.getObjectKey());
                    if (originalName != null && !originalName.isEmpty()) {
                        b.responseContentDisposition("attachment;filename=\""
                                + sanitizeFilename(originalName) + "\"");
                    }
                })
                .signatureDuration(Duration.ofMinutes(storageProperties.getDownloadExpireMinutes())));
    }

    /** 文件名清洗：去引号防注入预签名 URL */
    private String sanitizeFilename(String name) {
        return name.replace("\"", "").replace("\\", "");
    }

    /** 置 FAILED */
    private void markFailed(FileObject fo) {
        FileObject update = new FileObject();
        update.setObjectId(fo.getObjectId());
        update.setUploadStatus(UploadStatus.FAILED.getCode());
        update.setUpdateBy(currentUserSafe());
        fileObjectMapper.editFileObject(update);
    }

    /** DeleteObject 静默失败（GC 容错，下次再扫） */
    private void deleteObjectQuietly(FileObject fo) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(fo.getBucket())
                    .key(fo.getObjectKey())
                    .build());
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
