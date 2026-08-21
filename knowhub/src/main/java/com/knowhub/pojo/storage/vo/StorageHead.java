package com.knowhub.pojo.storage.vo;

/**
 * 存储后端 Head 结果：封装对象元数据（contentLength / contentType / etag），
 * 供 {@code FileService.confirmUpload} 核对与 {@code PublicObjectStream} 兜底用。
 * <p>
 * 各字段缺失时为 {@code null}（或 contentLength 为 -1），由调用方按需兜底。
 * 本地后端无 ETag 概念，etag 恒为 null。
 *
 * @param contentLength 对象字节数，未知为 -1
 * @param contentType   内容类型，探不出为 null
 * @param etag          对象 ETag，本地后端为 null
 */
public record StorageHead(long contentLength, String contentType, String etag) {
}
