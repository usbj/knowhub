package com.knowhub.pojo.storage.vo;

import java.io.InputStream;

/**
 * 对象中转回显/下载载体（PUBLIC 回显 + 中转下载共用）。
 * <p>
 * 由 {@code FileService.streamPublicObject} / {@code streamDownloadObject} 在校验通过后，
 * 用 {@code storageBackend.get(fo)} 拉取对象字节流并封装本对象返回给 Controller。
 * <p>
 * 纯数据 DTO，{@code stream} 的生命周期由 Controller 用 try-with-resources 管理——
 * StreamingResponseBody 拷贝完字节后必须 close，以归还后端连接（S3 实现归还 AWS SDK v2 同步客户端 HTTP 连接；
 * 本地实现关闭 FileInputStream），不依赖 GC / finalize。contentType 优先取元数据行存的值
 * （避免 RustFS 默认 octet-stream 导致 {@code <img>} 裂图），contentLength / etag 取后端实际响应兜底。
 * contentDisposition 非空时 Controller 下发给浏览器（PRIVATE 中转下载带 attachment;filename 强制下载，
 * PUBLIC 回显为 null 让浏览器内联显示）。
 * <p>
 * stream 类型为通用 {@link InputStream}：S3 后端返回的 {@code ResponseInputStream<GetObjectResponse>}
 * 本身就是 InputStream 子类可直接传入；本地后端返回 {@code FileInputStream}。这样载体与具体存储后端解耦，
 * 支持多后端 SPI（见 StorageBackend）。
 */
public class PublicObjectStream {

    /** 内容类型（优先用元数据存的，RustFS 返回 octet-stream 时兜底） */
    private final String contentType;

    /** 内容字节数（取后端实际响应值） */
    private final long contentLength;

    /** 对象 ETag（取后端实际响应，供 HTTP 缓存头下发；本地后端无 ETag 可传 null） */
    private final String etag;

    /** Content-Disposition 头值（PRIVATE 下载带 attachment;filename；PUBLIC 回显为 null） */
    private final String contentDisposition;

    /** 对象字节流，close 责任在 Controller */
    private final InputStream stream;

    public PublicObjectStream(String contentType, long contentLength, String etag,
                              InputStream stream) {
        this(contentType, contentLength, etag, null, stream);
    }

    public PublicObjectStream(String contentType, long contentLength, String etag,
                              String contentDisposition, InputStream stream) {
        this.contentType = contentType;
        this.contentLength = contentLength;
        this.etag = etag;
        this.contentDisposition = contentDisposition;
        this.stream = stream;
    }

    public String getContentType() {
        return contentType;
    }

    public long getContentLength() {
        return contentLength;
    }

    public String getEtag() {
        return etag;
    }

    public String getContentDisposition() {
        return contentDisposition;
    }

    public InputStream getStream() {
        return stream;
    }
}
