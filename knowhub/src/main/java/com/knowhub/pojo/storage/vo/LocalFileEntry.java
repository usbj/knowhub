package com.knowhub.pojo.storage.vo;

/**
 * 本地文件条目（本地→OSS 迁移用）。承载本地磁盘上一个文件的 objectKey + size + contentType，
 * 供迁移线程逐条读本地流写目标 OSS（contentLength 已知，putObject 走分块写，单文件不进内存）。
 * <p>
 * objectKey 即相对本地根目录的相对路径（'/' 分隔，与 S3 objectKey 语义对齐），作目标 OSS 的 objectKey 原样写入，
 * 目录结构与本地一致。contentType 由 Files.probeContentType 探测，探不出为 null（putObject 时回退默认）。
 */
public record LocalFileEntry(String objectKey, long size, String contentType) {
}
