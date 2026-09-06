package com.knowhub.service.storage.backend;

import com.knowhub.enums.storage.FileAccessMode;
import com.knowhub.pojo.storage.entity.FileObject;
import com.knowhub.pojo.storage.vo.StorageHead;

import java.io.InputStream;
import java.util.List;

/**
 * 存储后端抽象：S3 / Local 共同实现，{@code FileServiceImpl} 按 {@code StorageConfigReader.accessMode()}
 * 选 {@link #mode()} 匹配的实现分发。objectKey 即相对路径——本地实现直接当磁盘相对路径用，
 * S3 实现作为对象 key，两者目录结构天然对齐，迁移 / 打包下载复用同一套 key。
 * <p>
 * 设计要点：
 * - {@link #put} 与 {@link #get} 均为流式，单对象不强制进内存（{@code transferTo} 8KB 缓冲逐块拷贝）。
 * - {@link #get} 返回的 {@link InputStream} close 责任在调用方（Controller try-with-resources），
 *   后端实现需保证 close 归还底层连接 / 文件句柄。
 * - {@link #listKeys} 用于打包下载 / 迁移，分页由实现内部处理（S3 走 continuationToken，本地走 Files.walk）。
 * - 后端不感知元数据表（file_object），只按 FileObject 里的 bucket / objectKey 操作字节，元数据读写仍在 FileServiceImpl。
 */
public interface StorageBackend {

    /** 本后端对应的访问模式（TRANSFER→S3，LOCAL→Local；DIRECT 复用 S3 后端） */
    FileAccessMode mode();

    /**
     * 写入对象字节。{@code contentLength} 为 0 表未知（本地实现按实际字节落盘，S3 实现按 bytes.length）。
     * 调用方保证 {@code in} 为可重复读的字节流（中转上传链路 {@code @RequestBody byte[]} 包的 ByteArrayInputStream）。
     *
     * @param fo           对象元数据（取 bucket / objectKey）
     * @param in           字节输入流
     * @param contentLength 声明字节数，0 表未知
     * @param contentType  内容类型，null 则后端默认
     */
    void put(FileObject fo, InputStream in, long contentLength, String contentType);

    /**
     * 读取对象字节流。close 责任在调用方。
     *
     * @param fo 对象元数据（取 bucket / objectKey）
     * @return 对象字节输入流
     */
    InputStream get(FileObject fo);

    /**
     * 取对象 Head 元数据（contentLength / contentType / etag）。
     * 对象不存在时抛运行时异常，由调用方兜底处理（confirmUpload 置 FAILED 等）。
     *
     * @param fo 对象元数据
     * @return Head 结果
     */
    StorageHead head(FileObject fo);

    /**
     * 删除对象。静默失败由调用方日志（GC 容错，下次再扫）。
     *
     * @param fo 对象元数据
     */
    void delete(FileObject fo);

    /**
     * 列举指定 bucket 下全部对象 key（打包下载 / 迁移用）。
     * 内部分页拉取，返回所有 key。
     *
     * @param bucket 桶名（本地后端忽略，固定走 basePath）
     * @return 对象 key 列表
     */
    List<String> listKeys(String bucket);
}
