package com.knowhub.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.presigner.S3Presigner;

import java.net.URI;

/**
 * S3Client / S3Presigner Bean 构造。
 * endpoint 指 RustFS，pathStyleAccessEnabled=true（RustFS 默认 path-style，本地单节点无 DNS 通配），
 * region 用占位值。S3Presigner 与 S3Client 共用同一 endpoint/region/凭证。
 * 预签名签发是纯本地计算（不触网），S3Client 的 HeadObject/DeleteObject 才触网。
 */
@Configuration
public class S3ClientConfig {

    @Bean
    public S3Client s3Client(StorageProperties props) {
        AwsBasicCredentials creds = AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey());
        StaticCredentialsProvider credProvider = StaticCredentialsProvider.create(creds);
        S3Configuration serviceCfg = S3Configuration.builder()
                .pathStyleAccessEnabled(props.isPathStyleAccess())
                .build();
        return S3Client.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(credProvider)
                .serviceConfiguration(serviceCfg)
                .build();
    }

    @Bean
    public S3Presigner s3Presigner(StorageProperties props) {
        AwsBasicCredentials creds = AwsBasicCredentials.create(props.getAccessKey(), props.getSecretKey());
        StaticCredentialsProvider credProvider = StaticCredentialsProvider.create(creds);
        S3Configuration serviceCfg = S3Configuration.builder()
                .pathStyleAccessEnabled(props.isPathStyleAccess())
                .build();
        return S3Presigner.builder()
                .endpointOverride(URI.create(props.getEndpoint()))
                .region(Region.of(props.getRegion()))
                .credentialsProvider(credProvider)
                .serviceConfiguration(serviceCfg)
                .build();
    }
}
