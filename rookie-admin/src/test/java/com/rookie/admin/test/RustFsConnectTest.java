package com.rookie.admin.test;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.ResponseInputStream;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.CreateBucketResponse;
import software.amazon.awssdk.services.s3.model.GetObjectResponse;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;
import software.amazon.awssdk.services.s3.model.PutObjectResponse;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;
import software.amazon.awssdk.services.s3.presigner.model.PresignedGetObjectRequest;
import software.amazon.awssdk.services.s3.presigner.model.PresignedPutObjectRequest;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

/**
 * RustFS + AWS SDK for Java v2 连通性与预签名链路验证测试。
 *
 * 目的：在正式建 knowhub-storage 模块前，先用一个自包含的测试把整条链路跑通——
 *   1) S3Client 能连上 RustFS；
 *   2) 桶存在（不存在则创建）；
 *   3) 签发 PutObject 预签名 URL，用该 URL 直传一段文本对象成功；
 *   4) HeadObject 核对真实 contentLength/contentType；
 *   5) 签发 GET 预签名 URL，能下载回原内容；
 *   6) DeleteObject 清理测试对象。
 *
 * 全程不依赖任何 knowhub-storage 代码（模块尚未建），仅验证 SDK 与 RustFS 的对接姿势。
 * 跑通后即可确认设计稿 §4.4 的 S3Client/S3Presigner 构造与预签名用法可行。
 *
 * 配置：见 rookie-admin/src/main/resources/application.yml 末尾的 storage: 段（本测试用 @Value 读）。
 *       敏感值由环境变量覆盖，缺省值仅本地联调用。
 *
 * 运行：IDE 右键运行单个 @Test 方法，或
 *   ./mvnw -pl rookie-admin -am test -Dtest=RustFsConnectTest#testFullPresignFlow
 */
@SpringBootTest
public class RustFsConnectTest {

    /** RustFS 连接参数，从 application.yml 的 storage: 段读取 */
    @Value("${storage.endpoint}")
    private String endpoint;

    @Value("${storage.region}")
    private String region;

    @Value("${storage.access-key}")
    private String accessKey;

    @Value("${storage.secret-key}")
    private String secretKey;

    @Value("${storage.bucket}")
    private String bucket;

    /** path-style 必须 true（RustFS 默认 path-style，本地单节点无 DNS 通配） */
    @Value("${storage.path-style-access:true}")
    private boolean pathStyleAccess;

    /**
     * 端到端预签名链路验证：建桶(若缺) → 签发上传 URL → 直传 → Head 核对 → 签发下载 URL → 下载 → 删除。
     * 跑通即证明 RustFS + AWS SDK v2 + 预签名直传整条链路可用。
     */
    @Test
    void testFullPresignFlow() throws Exception {
        System.out.println("==== RustFS 预签名链路验证开始 ====");
        System.out.println("endpoint = " + endpoint);
        System.out.println("bucket   = " + bucket);
        System.out.println("region   = " + region);
        System.out.println("pathStyle= " + pathStyleAccess);

        // 1. 构造 S3Client（path-style + 占位 region + 静态凭证）
        AwsBasicCredentials creds = AwsBasicCredentials.create(accessKey, secretKey);
        StaticCredentialsProvider credProvider = StaticCredentialsProvider.create(creds);
        S3Configuration serviceCfg = S3Configuration.builder()
                .pathStyleAccessEnabled(pathStyleAccess)
                .build();

        try (S3Client s3 = S3Client.builder()
                .endpointOverride(URI.create(endpoint))
                .region(Region.of(region))
                .credentialsProvider(credProvider)
                .serviceConfiguration(serviceCfg)
                .build();

             // 预签名器与 S3Client 共用同一 endpoint/region/凭证
             S3Presigner presigner = S3Presigner.builder()
                     .endpointOverride(URI.create(endpoint))
                     .region(Region.of(region))
                     .credentialsProvider(credProvider)
                     .serviceConfiguration(serviceCfg)
                     .build()) {

            // 2. 确保桶存在（不存在则创建；已存在会抛 409，吞掉即可）
            ensureBucket(s3, bucket);

            // 3. 签发 PutObject 预签名 URL
            String objectKey = "test/presign-flow/" + UUID.randomUUID() + ".txt";
            String contentType = "text/plain; charset=UTF-8";
            String payload = "hello rustfs from knowhub storage test @ " + System.currentTimeMillis();

            PresignedPutObjectRequest presignedPut = presigner.presignPutObject(p -> p
                    .putObjectRequest(b -> b.bucket(bucket).key(objectKey).contentType(contentType))
                    .signatureDuration(Duration.ofMinutes(10)));
            String uploadUrl = presignedPut.url().toString();
            System.out.println("[上传] 预签名 URL = " + uploadUrl);

            // 4. 用预签名 URL 直传对象（模拟前端 PUT 直传：用 JDK HttpURLConnection，不走 SDK 上传）
            int putStatus = httpPut(uploadUrl, contentType, payload.getBytes(StandardCharsets.UTF_8));
            System.out.println("[上传] PUT 响应码 = " + putStatus);
            assert putStatus == 200 : "预签名 PUT 应返回 200，实际 " + putStatus;

            // 5. HeadObject 核对真实值（后端 confirm 流程会做这步）
            HeadObjectResponse head = s3.headObject(b -> b.bucket(bucket).key(objectKey));
            System.out.println("[确认] HeadObject contentLength = " + head.contentLength()
                    + ", contentType = " + head.contentType() + ", etag = " + head.eTag());
            assert head.contentLength() == payload.getBytes(StandardCharsets.UTF_8).length
                    : "HeadObject 字节数与上传内容不一致";

            // 6. 签发 GET 预签名 URL 并下载（模拟前端拿预签名下载）
            PresignedGetObjectRequest presignedGet = presigner.presignGetObject(p -> p
                    .getObjectRequest(b -> b.bucket(bucket).key(objectKey))
                    .signatureDuration(Duration.ofMinutes(5)));
            String downloadUrl = presignedGet.url().toString();
            System.out.println("[下载] 预签名 URL = " + downloadUrl);

            String downloaded = httpGetText(downloadUrl);
            System.out.println("[下载] 取回内容 = " + downloaded);
            assert payload.equals(downloaded) : "下载内容与上传不一致";

            // 7. 顺手验证一下 SDK 直传/直读（不走预签名）也能跑通
            String directKey = "test/presign-flow/direct-" + UUID.randomUUID() + ".txt";
            PutObjectResponse putResp = s3.putObject(b -> b.bucket(bucket).key(directKey)
                            .contentType("text/plain; charset=UTF-8"),
                    RequestBody.fromString("direct put via sdk"));
            System.out.println("[SDK直传] etag = " + putResp.eTag());
            try (ResponseInputStream<GetObjectResponse> in = s3.getObject(b -> b.bucket(bucket).key(directKey))) {
                String direct = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                System.out.println("[SDK直读] 内容 = " + direct);
                assert "direct put via sdk".equals(direct);
            }
            s3.deleteObject(b -> b.bucket(bucket).key(directKey));

            // 8. 清理本次测试对象
            s3.deleteObject(b -> b.bucket(bucket).key(objectKey));
            System.out.println("[清理] 已删除测试对象 " + objectKey);

            System.out.println("==== RustFS 预签名链路验证全部通过 ✅ ====");
        }
    }

    /** 桶不存在则创建；已存在忽略异常 */
    private void ensureBucket(S3Client s3, String bucketName) {
        try {
            s3.headBucket(b -> b.bucket(bucketName));
            System.out.println("[建桶] 桶已存在 " + bucketName);
        } catch (Exception e) {
            System.out.println("[建桶] 桶不存在，尝试创建 " + bucketName + "（原因: " + e.getMessage() + "）");
            CreateBucketResponse resp = s3.createBucket(b -> b.bucket(bucketName));
            System.out.println("[建桶] 创建结果 " + resp.location());
        }
    }

    /** 用预签名 URL 做 PUT 直传（模拟前端，不依赖 SDK 上传能力） */
    private int httpPut(String url, String contentType, byte[] body) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URI(url).toURL().openConnection();
        conn.setRequestMethod("PUT");
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", contentType);
        conn.setRequestProperty("Content-Length", String.valueOf(body.length));
        try (java.io.OutputStream os = conn.getOutputStream()) {
            os.write(body);
        }
        int code = conn.getResponseCode();
        // 读空响应体，释放连接
        try (java.io.InputStream is = (code >= 200 && code < 300) ? conn.getInputStream() : conn.getErrorStream()) {
            if (is != null) {
                byte[] buf = new byte[4096];
                while (is.read(buf) > 0) {
                    // drain
                }
            }
        }
        return code;
    }

    /** 用预签名 URL 做 GET 下载并返回文本 */
    private String httpGetText(String url) throws Exception {
        java.net.HttpURLConnection conn = (java.net.HttpURLConnection) new URI(url).toURL().openConnection();
        conn.setRequestMethod("GET");
        try (java.io.InputStream is = conn.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
