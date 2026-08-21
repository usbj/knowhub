package com.knowhub.service.storage.backend;

import com.knowhub.config.StorageProperties;
import com.knowhub.enums.storage.FileAccessMode;
import com.knowhub.pojo.storage.entity.FileObject;
import com.knowhub.pojo.storage.vo.StorageHead;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectRequest;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Request;
import software.amazon.awssdk.services.s3.model.ListObjectsV2Response;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import software.amazon.awssdk.services.s3.model.S3Object;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.io.InputStream;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

/**
 * S3 兼容存储后端实现（RustFS）。收口原 {@code FileServiceImpl} 散落的 S3 SDK 调用，
 * 供 TRANSFER / DIRECT 模式分发。预签名签发仍在 {@code FileServiceImpl}（与上传/下载 URL 形态耦合，
 * 不属于通用后端能力），本类只负责字节级 put / get / head / delete / list。
 * <p>
 * {@link #get} 返回的 {@code ResponseInputStream<GetObjectResponse>} 是 {@link InputStream} 子类，
 * 直接作为通用 InputStream 返回，{@code PublicObjectStream} 与 Controller 无感知。
 */
@Component
public class S3StorageBackend implements StorageBackend {

    private static final Logger log = LoggerFactory.getLogger(S3StorageBackend.class);

    @Autowired
    private S3Client s3Client;

    @Autowired
    private S3Presigner s3Presigner;

    @Autowired
    private StorageProperties storageProperties;

    @Override
    public FileAccessMode mode() {
        // TRANSFER / DIRECT 共用本实现：FileServiceImpl 的 backend() 对 DIRECT 也命中本类（mode() 返回 TRANSFER，
        // 分发逻辑里 DIRECT 落到 S3 后端）。这里返回 TRANSFER 作为 S3 后端的标识。
        return FileAccessMode.TRANSFER;
    }

    @Override
    public void put(FileObject fo, InputStream in, long contentLength, String contentType) {
        // 读全量字节到 byte[] 后用 RequestBody.fromBytes 写入（与原 proxyUpload 同口径：
        // 中转上传链路 in 已是 ByteArrayInputStream，readAllBytes 必拿到完整字节；图片等小文件进内存可接受）。
        byte[] bytes;
        try {
            bytes = in.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("S3 put 读取字节流失败: " + e.getMessage(), e);
        }
        PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                .bucket(fo.getBucket())
                .key(fo.getObjectKey())
                .contentLength((long) bytes.length);
        if (contentType != null && !contentType.isEmpty()) {
            reqBuilder.contentType(contentType);
        }
        try {
            s3Client.putObject(reqBuilder.build(), RequestBody.fromBytes(bytes));
        } catch (Exception e) {
            throw new RuntimeException("S3 putObject 失败 key=" + fo.getObjectKey() + " reason=" + e.getMessage(), e);
        }
    }

    @Override
    public InputStream get(FileObject fo) {
        // NoSuchKeyException（元数据与对象不一致）不在此 catch，向上抛由 Controller 兜底映射 404
        ResponseInputStream<GetObjectResponse> ris = s3Client.getObject(GetObjectRequest.builder()
                .bucket(fo.getBucket())
                .key(fo.getObjectKey())
                .build());
        return ris;
    }

    @Override
    public StorageHead head(FileObject fo) {
        HeadObjectResponse head = s3Client.headObject(HeadObjectRequest.builder()
                .bucket(fo.getBucket())
                .key(fo.getObjectKey())
                .build());
        return new StorageHead(head.contentLength(), head.contentType(), head.eTag());
    }

    @Override
    public void delete(FileObject fo) {
        try {
            s3Client.deleteObject(DeleteObjectRequest.builder()
                    .bucket(fo.getBucket())
                    .key(fo.getObjectKey())
                    .build());
        } catch (Exception e) {
            log.warn("[S3] DeleteObject 失败 key={} reason={}", fo.getObjectKey(), e.getMessage());
        }
    }

    @Override
    public List<String> listKeys(String bucket) {
        // listObjectsV2 分页拉取：continuationToken 循环到 isTruncated=false
        List<String> keys = new ArrayList<>();
        String token = null;
        do {
            ListObjectsV2Request.Builder reqBuilder = ListObjectsV2Request.builder()
                    .bucket(bucket)
                    .maxKeys(1000);
            if (token != null) {
                reqBuilder.continuationToken(token);
            }
            ListObjectsV2Response resp = s3Client.listObjectsV2(reqBuilder.build());
            for (S3Object obj : resp.contents()) {
                keys.add(obj.key());
            }
            token = resp.isTruncated() ? resp.nextContinuationToken() : null;
        } while (token != null);
        return keys;
    }

    /** 暴露 bucket 给迁移 / 打包下载等需知桶名的场景 */
    public String getBucket() {
        return storageProperties.getBucket();
    }

    /**
     * 签 PUT 预签名（DIRECT 模式上传用）。带 Content-Type 约束，有效期由 uploadExpireMinutes 决定。
     * 返回预签名绝对 URL（host 为 yml storage.endpoint，由 FileServiceImpl 的 rewriteHostToDirect 改写为 directBaseUrl）。
     */
    public String presignPut(FileObject fo, String contentType) {
        PutObjectRequest.Builder reqBuilder = PutObjectRequest.builder()
                .bucket(fo.getBucket())
                .key(fo.getObjectKey());
        if (contentType != null && !contentType.isEmpty()) {
            reqBuilder.contentType(contentType);
        }
        PresignedPutObjectRequest presigned = s3Presigner.presignPutObject(p -> p
                .putObjectRequest(reqBuilder.build())
                .signatureDuration(Duration.ofMinutes(storageProperties.getUploadExpireMinutes())));
        return presigned.url().toString();
    }

    /**
     * 签 GET 预签名（DIRECT 模式 PRIVATE 下载 / 桶私有 PUBLIC 回显用）。
     * originalName 非空时带 response-content-disposition（attachment;filename 双段）。
     * 返回预签名绝对 URL，由 FileServiceImpl 的 rewriteHostToDirect 改写 host。
     */
    public String presignGet(FileObject fo, String originalName, String contentDisposition) {
        PresignedGetObjectRequest presigned = s3Presigner.presignGetObject(p -> p
                .getObjectRequest(b -> {
                    b.bucket(fo.getBucket()).key(fo.getObjectKey());
                    if (contentDisposition != null && !contentDisposition.isEmpty()) {
                        b.responseContentDisposition(contentDisposition);
                    }
                })
                .signatureDuration(Duration.ofMinutes(storageProperties.getDownloadExpireMinutes())));
        return presigned.url().toString();
    }
}
