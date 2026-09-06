package com.knowhub.service.storage.backend;

import com.knowhub.enums.storage.FileAccessMode;
import com.knowhub.pojo.storage.entity.FileObject;
import com.knowhub.pojo.storage.vo.LocalFileEntry;
import com.knowhub.pojo.storage.vo.StorageHead;
import com.rookie.common.exception.ServiceException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Stream;

/**
 * 本地磁盘存储后端实现（access_mode=local 模式）。
 * <p>
 * 文件落 {@code storage.local.base-path} 根目录下，相对路径即 {@code FileObject.objectKey}
 * （形如 {@code blog_cover/2026/08/17/uuid.png}），与 S3 后端目录结构完全对齐——
 * 故从 S3 迁移到本地 / 本地迁移到 S3 时 objectKey 原样复用，目录结构一致。
 * <p>
 * 防路径穿越：{@link #resolvePath} 校验规范化后的绝对路径仍以 basePath 开头，否则抛异常
 * （仿 rookie {@code SysFileServiceImpl} 的 normalize + startsWith 双重校验）。
 * <p>
 * 本后端无 ETag 概念，{@link #head} 的 etag 返回 null；{@link #head} 的 contentType 用
 * {@code Files.probeContentType} 探测，探不出时返回 null（由调用方回退元数据存的 contentType）。
 */
@Component
public class LocalStorageBackend implements StorageBackend {

    private static final Logger log = LoggerFactory.getLogger(LocalStorageBackend.class);

    private final Path basePath;

    public LocalStorageBackend(@Value("${storage.local.base-path:./knowhub-upload}") String basePath) {
        this.basePath = Paths.get(basePath).toAbsolutePath().normalize();
    }

    @Override
    public FileAccessMode mode() {
        return FileAccessMode.LOCAL;
    }

    @Override
    public void put(FileObject fo, InputStream in, long contentLength, String contentType) {
        Path target = resolvePath(fo);
        try {
            Files.createDirectories(target.getParent());
        } catch (IOException e) {
            throw new RuntimeException("本地存储创建目录失败 path=" + target.getParent() + " reason=" + e.getMessage(), e);
        }
        try {
            // 流式拷贝（Files.copy 内部缓冲），单对象不强制全量进内存；
            // 与 S3 后端 readAllBytes 写法略异，但本地 IO 无需 ByteArrayInputStream 避坑。
            Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            throw new RuntimeException("本地存储写入失败 key=" + fo.getObjectKey() + " reason=" + e.getMessage(), e);
        }
    }

    @Override
    public InputStream get(FileObject fo) {
        Path target = resolvePath(fo);
        if (!Files.exists(target)) {
            // 对外视作不存在，与 S3 后端 NoSuchKeyException 同语义，向上抛由 Controller 兜底 404
            throw new RuntimeException("本地对象不存在 key=" + fo.getObjectKey());
        }
        try {
            return Files.newInputStream(target);
        } catch (IOException e) {
            throw new RuntimeException("本地存储读取失败 key=" + fo.getObjectKey() + " reason=" + e.getMessage(), e);
        }
    }

    @Override
    public StorageHead head(FileObject fo) {
        Path target = resolvePath(fo);
        if (!Files.exists(target)) {
            throw new RuntimeException("本地对象不存在 key=" + fo.getObjectKey());
        }
        try {
            long size = Files.size(target);
            String ct = Files.probeContentType(target);
            return new StorageHead(size, ct, null);
        } catch (IOException e) {
            throw new RuntimeException("本地存储 head 失败 key=" + fo.getObjectKey() + " reason=" + e.getMessage(), e);
        }
    }

    @Override
    public void delete(FileObject fo) {
        Path target = resolvePath(fo);
        try {
            Files.deleteIfExists(target);
        } catch (Exception e) {
            log.warn("[Local] 删除失败 key={} reason={}", fo.getObjectKey(), e.getMessage());
        }
    }

    @Override
    public List<String> listKeys(String bucket) {
        // 本地后端 bucket 忽略，固定走 basePath；Files.walk 遍历所有文件，相对路径即 objectKey
        List<String> keys = new ArrayList<>();
        if (!Files.exists(basePath)) {
            return keys;
        }
        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                Path rel = basePath.relativize(p.toAbsolutePath().normalize());
                // 统一用 '/' 作分隔符，与 S3 objectKey 一致（Windows 下 relativize 用 '\'）
                keys.add(rel.toString().replace('\\', '/'));
            });
        } catch (IOException e) {
            throw new RuntimeException("本地存储遍历失败 basePath=" + basePath + " reason=" + e.getMessage(), e);
        }
        return keys;
    }

    /**
     * 列出本地根目录下所有文件条目（含 objectKey + size + contentType），供本地→OSS 迁移遍历。
     * 与 {@link #listKeys} 同走 Files.walk，区别是顺带取 size/probeContentType，避免迁移线程再 stat 一次。
     * basePath 不存在返回空列表（空目录迁移，total=0 直接置 SUCCESS）。
     */
    public List<LocalFileEntry> listLocalEntries() {
        List<LocalFileEntry> entries = new ArrayList<>();
        if (!Files.exists(basePath)) {
            return entries;
        }
        try (Stream<Path> walk = Files.walk(basePath)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                Path normalized = p.toAbsolutePath().normalize();
                Path rel = basePath.relativize(normalized);
                String objectKey = rel.toString().replace('\\', '/');
                try {
                    long size = Files.size(normalized);
                    String ct = Files.probeContentType(normalized);
                    entries.add(new LocalFileEntry(objectKey, size, ct));
                } catch (IOException e) {
                    // 单文件 stat 失败不中断遍历，跳过该文件（迁移时该 key 缺失计 failed）
                    log.warn("[Local] 遍历取条目失败 key={} reason={}", objectKey, e.getMessage());
                }
            });
        } catch (IOException e) {
            throw new RuntimeException("本地存储遍历失败 basePath=" + basePath + " reason=" + e.getMessage(), e);
        }
        return entries;
    }

    /**
     * 按 objectKey 解析本地绝对路径并开输入流（本地→OSS 迁移线程拉源流用）。
     * 复用 {@link #resolvePath} 的防穿越校验；文件不存在抛异常由迁移线程计 failed。
     */
    public InputStream openStreamByObjectKey(String objectKey) {
        FileObject fo = new FileObject();
        fo.setObjectKey(objectKey);
        return get(fo);
    }

    /**
     * 解析 objectKey 到本地绝对路径，并做防穿越校验。
     * 规范化后的路径必须仍在 basePath 之下，否则抛 ServiceException（防 {@code ../../etc/passwd} 之类）。
     */
    private Path resolvePath(FileObject fo) {
        String objectKey = fo.getObjectKey();
        if (objectKey == null || objectKey.isEmpty()) {
            throw new ServiceException(500, "objectKey 为空");
        }
        Path resolved = basePath.resolve(objectKey).normalize();
        if (!resolved.startsWith(basePath)) {
            throw new ServiceException(500, "objectKey 路径越界: " + objectKey);
        }
        return resolved;
    }
}
