package com.knowhub.controller.common;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.storage.quarry.FileQuarry;
import com.knowhub.pojo.common.vo.BindVo;
import com.knowhub.pojo.storage.vo.DownloadVo;
import com.knowhub.pojo.storage.vo.FileObjectVo;
import com.knowhub.pojo.storage.vo.PublicObjectStream;
import com.knowhub.pojo.storage.vo.UploadApplyVo;
import com.knowhub.pojo.storage.vo.UploadTokenVo;
import com.knowhub.service.storage.impl.FileService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.concurrent.TimeUnit;

/**
 * 文件存储接口。路由 /file，权限键三段式 knowhub:file:*。
 * 预签名直传：POST /file/upload-token 签发令牌 → 前端直传 RustFS → POST /file/confirm/{id} 确认。
 * PUBLIC 回显走 GET /file/public/{id} 后端中转字节流（s3Client.getObject 拉流，StreamingResponseBody 回写，
 *   供 Markdown <img>、封面对话框等直接引用；无鉴权，由 SecurityConfig permitAll 放行）。
 * PRIVATE 下载走 GET /file/download/{id} 返回预签名 URL（带 attachment;filename）。
 */
@Tag(name = "文件存储", description = "文件上传令牌/确认/回显/下载/管理相关接口")
@RestController
@RequestMapping("/file")
public class FileController {

    private static final Logger log = LoggerFactory.getLogger(FileController.class);

    @Autowired
    private FileService fileService;

    @PostMapping("/upload-token")
    @Operation(summary = "签发上传令牌")
    @Log(title = "文件对象", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:file:upload')")
    public Result<UploadTokenVo> applyUploadToken(@RequestBody UploadApplyVo vo) {
        UploadTokenVo token = fileService.applyUploadToken(vo);
        return Result.success(token);
    }

    @PostMapping("/confirm/{objectId}")
    @Operation(summary = "上传确认")
    @Log(title = "文件对象", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:file:upload')")
    public Result<Boolean> confirmUpload(@PathVariable Long objectId,
                                         @RequestParam(required = false) Long bizRefId) {
        Boolean b = fileService.confirmUpload(objectId, bizRefId);
        return Result.success(b);
    }

    @GetMapping("/public/{objectId}")
    @Operation(summary = "PUBLIC 对象回显（后端中转字节流，无鉴权）")
    public ResponseEntity<StreamingResponseBody> getPublic(@PathVariable Long objectId) {
        PublicObjectStream pos;
        try {
            pos = fileService.streamPublicObject(objectId);
        } catch (ServiceException e) {
            // 业务校验失败 → 映射对应 HTTP 状态码，返回纯状态码空体，
            // 绝不冒泡到 GlobalExceptionHandler（@RestControllerAdvice 会包成 Result JSON，对 <img> 无效）
            int code = e.getCode() == null ? 500 : e.getCode();
            HttpStatus status = switch (code) {
                case 404 -> HttpStatus.NOT_FOUND;
                case 403 -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            return ResponseEntity.status(status).build();
        } catch (Exception e) {
            // s3Client.getObject 抛 NoSuchKeyException / S3Exception 等触网异常兜底
            log.warn("[PUBLIC 回显] 拉取对象失败 objectId={} reason={}", objectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }

        // 流式回写：try-with-resources 关闭 ResponseInputStream，归还 SDK 连接池，不依赖 GC
        StreamingResponseBody body = out -> {
            try (var in = pos.getStream()) {
                // JDK 17 InputStream.transferTo，内部 8KB 缓冲逐块拷贝，大图不进内存
                in.transferTo(out);
            } catch (Exception e) {
                // 客户端中途断开等，仅日志；此时已无法再写响应，不能抛
                log.warn("[PUBLIC 回显] 流式拷贝中断 objectId={} reason={}", objectId, e.getMessage());
            }
        };

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(pos.getContentType()))
                .contentLength(pos.getContentLength())
                // PUBLIC 对象不修改、删除走 GC 不复用 id，可安全强缓存 7 天 + immutable
                .cacheControl(CacheControl.maxAge(7, TimeUnit.DAYS).cachePublic().immutable())
                .eTag(pos.getEtag())
                .header(HttpHeaders.ACCEPT_RANGES, "none") // 首版不支持 Range
                .body(body);
    }

    /**
     * PUBLIC 对象解析接口（双模式回显入口）：按当前 knowhub.file.access_mode 动态分发 302 跳转目标。
     * 库里（cover_url、正文 markdown 图片、文件详情预览）统一存 /file/resolve/{objectId} 稳定引用，
     * 渲染时 <img src> 命中本接口 → 后端按模式 302：中转→/file/public/{id}（字节流回显）；直链→OSS 直链/预签名。
     * 切模式时历史数据回显行为自动跟着切，双模式对存量生效。
     * <p>
     * 无鉴权（permitAll，与 /file/public 同，供 markdown <img> 直引）；不加 @Log（高频回显请求，记日志刷屏且无业务意义）。
     * 校验失败（对象不存在/非 PUBLIC/未确认）映射 404/403 纯状态码空体，绝不冒泡到 GlobalExceptionHandler
     * （@RestControllerAdvice 会包成 Result JSON，对 <img> 无效）。
     */
    @GetMapping("/resolve/{objectId}")
    @Operation(summary = "PUBLIC 对象解析（按访问模式 302 分发，无鉴权）")
    public ResponseEntity<Void> resolvePublic(@PathVariable Long objectId) {
        String location;
//        log.info("解析中");
        try {
            location = fileService.resolvePublicUrl(objectId);
        } catch (ServiceException e) {
            int code = e.getCode() == null ? 500 : e.getCode();
            HttpStatus status = switch (code) {
                case 404 -> HttpStatus.NOT_FOUND;
                case 403 -> HttpStatus.FORBIDDEN;
                default -> HttpStatus.INTERNAL_SERVER_ERROR;
            };
            return ResponseEntity.status(status).build();
        } catch (Exception e) {
            log.warn("[PUBLIC 解析] 解析失败 objectId={} reason={}", objectId, e.getMessage());
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        if (location == null) {
//            log.info("未通过");
            // service 校验未通过（对象不存在/非 PUBLIC/未确认）→ 404
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
//        log.info("解析地址{}",location);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, location)
                .build();
    }

    @GetMapping("/download/{objectId}")
    @Operation(summary = "获取下载预签名 URL（PRIVATE 鉴权）")
    @PreAuthorize("hasAuthority('knowhub:file:download')")
    public Result<DownloadVo> getDownloadUrl(@PathVariable Long objectId) {
        DownloadVo vo = fileService.getDownloadUrl(objectId);
        return Result.success(vo);
    }

    /**
     * 中转下载（TRANSFER 模式 PRIVATE 下载用）：后端用 s3Client.getObject 拉字节流回写，
     * 同源无 CORS、地址不暴露 OSS。PRIVATE 走下载鉴权 + 服务端再校验上传人/管理员。
     * 响应头带 attachment;filename 强制下载（防浏览器直显私有文件）。
     * <p>
     * 实现用同步写 HttpServletResponse（非 StreamingResponseBody）：StreamingResponseBody 走异步 dispatch，
     * 异步分发回来时 SecurityContext 不传播，AuthorizationFilter 会因 anyRequest().authenticated() 失败抛
     * AuthorizationDeniedException（即使同步阶段 @PreAuthorize 已通过）。同步写在原 servlet 线程完成，
     * filter 链只过一次，@PreAuthorize 在方法入口同步校验，无异步 dispatch 授权问题。
     */
    @GetMapping("/proxy/{objectId}")
    @Operation(summary = "中转下载（后端代理回写字节流，PRIVATE 鉴权）")
    @PreAuthorize("hasAuthority('knowhub:file:download')")
    public void proxyDownload(@PathVariable Long objectId, HttpServletResponse response) {
        PublicObjectStream pos;
        try {
            pos = fileService.streamDownloadObject(objectId);
        } catch (ServiceException e) {
            int code = e.getCode() == null ? 500 : e.getCode();
            response.setStatus(switch (code) {
                case 404 -> HttpServletResponse.SC_NOT_FOUND;
                case 403 -> HttpServletResponse.SC_FORBIDDEN;
                default -> HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            });
            return;
        } catch (Exception e) {
            log.warn("[中转下载] 拉取对象失败 objectId={} reason={}", objectId, e.getMessage());
            response.setStatus(HttpServletResponse.SC_NOT_FOUND);
            return;
        }

        // 设置响应头：Content-Type/Length/Cache-Control/Content-Disposition
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType(pos.getContentType());
        response.setContentLengthLong(pos.getContentLength());
        response.setHeader(HttpHeaders.ACCEPT_RANGES, "none");
        response.setHeader(HttpHeaders.CACHE_CONTROL, "no-cache");
        if (pos.getContentDisposition() != null && !pos.getContentDisposition().isEmpty()) {
            response.setHeader(HttpHeaders.CONTENT_DISPOSITION, pos.getContentDisposition());
        }

        // 同步流式拷贝：try-with-resources 关闭 S3 ResponseInputStream 归还连接池；客户端断开仅日志
        try (var in = pos.getStream(); var out = response.getOutputStream()) {
            in.transferTo(out);
            out.flush();
        } catch (Exception e) {
            log.warn("[中转下载] 流式拷贝中断 objectId={} reason={}", objectId, e.getMessage());
        }
    }

    /**
     * 后端代理转发上传（TRANSFER 模式上传用）：前端把文件字节流 PUT 到本接口，
     * 后端用 s3Client.putObject 写入 OSS，再走 confirm 核对置 CONFIRMED。
     * 前端拿不到 OSS 直连地址时的兜底上传通道，后端经上传字节流。
     * <p>
     * 请求体用 {@code @RequestBody byte[]} 读取而不是 {@code request.getInputStream()}：
     * 实测在经 RequestCachingFilter（ContentCachingRequestWrapper）+ @Log 切面 + Spring Security
     * 的多层包装后，controller 通过 request.getInputStream().readAllBytes() 拿到的是被上游 filter
     * 消费过的残缺流（缺前若干字节），导致写入 OSS 的对象本体损坏、回显裂图。@RequestBody 由 Spring
     * MVC 的 DispatcherServlet 在 controller 之前用 ByteArrayHttpMessageConverter 一次性读完 body，
     * 此时 body 尚未被任何 filter 后置消费，能拿到完整原始字节。
     */
    @PutMapping("/proxy-upload/{objectId}")
    @Operation(summary = "后端代理转发上传（接收字节流写入 OSS）")
    @Log(title = "文件对象", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:file:upload')")
    public Result<Boolean> proxyUpload(@PathVariable Long objectId,
                                       HttpServletRequest request,
                                       @RequestBody(required = false) byte[] body) throws java.io.IOException {
        long contentLength = (body != null) ? body.length : request.getContentLengthLong();
        String contentType = request.getContentType();
        java.io.InputStream in = (body != null) ? new java.io.ByteArrayInputStream(body) : request.getInputStream();
        Boolean b = fileService.proxyUpload(objectId, in, contentLength, contentType);
        return Result.success(b);
    }

    /**
     * 本地模式代理上传（LOCAL 模式上传用）：前端把文件字节流 PUT 到本接口，
     * 后端用本地后端写本地磁盘（objectKey 即相对路径），再走 confirm 核对置 CONFIRMED。
     * 与 {@link #proxyUpload} 同构，区别仅在 FileServiceImpl 内部按 accessMode 分发到 LocalStorageBackend。
     * <p>
     * 请求体同样用 {@code @RequestBody byte[]} 读取（与 proxy-upload 同口径避开 filter 消费流坑）。
     * 无预签名概念：本地模式下 applyUploadToken 下发的 uploadUrl 即本接口的相对路径。
     */
    @PutMapping("/local-upload/{objectId}")
    @Operation(summary = "本地模式代理上传（接收字节流写本地磁盘）")
    @Log(title = "文件对象", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:file:upload')")
    public Result<Boolean> localUpload(@PathVariable Long objectId,
                                       HttpServletRequest request,
                                       @RequestBody(required = false) byte[] body) throws java.io.IOException {
        long contentLength = (body != null) ? body.length : request.getContentLengthLong();
        String contentType = request.getContentType();
        java.io.InputStream in = (body != null) ? new java.io.ByteArrayInputStream(body) : request.getInputStream();
        Boolean b = fileService.proxyUpload(objectId, in, contentLength, contentType);
        return Result.success(b);
    }

    /**
     * 取 PUBLIC 对象回显链接（按当前访问模式）：
     * TRANSFER → /file/public/{id}（后端中转）；DIRECT → {directBaseUrl}/{bucket}/{objectKey}（公开读直链）。
     * 供不便在详情接口顺带返回链接的场景主动取用（多数场景由详情接口 coverUrl/previewUrl 顺带返回）。
     */
    @GetMapping("/url/{objectId}")
    @Operation(summary = "取 PUBLIC 回显链接（按访问模式）")
    public Result<String> getPublicAccessUrl(@PathVariable Long objectId) {
        return Result.success(fileService.getPublicAccessUrl(objectId));
    }

    /**
     * 打包下载预估总字节数（扩展点1）：供前端在发起打包下载前做大小预估、超 50G 弹警告确认。
     * 累加所有 deleted=0 + CONFIRMED 的 file_object.content_length。
     */
    @GetMapping("/pack-size")
    @Operation(summary = "打包下载预估总字节数")
    @PreAuthorize("hasAuthority('knowhub:file:pack-download')")
    public Result<Long> packSize() {
        return Result.success(fileService.packTotalSize());
    }

    /**
     * 打包下载到客户端（扩展点1，CLIENT 模式）：同步流式写 HttpServletResponse 的 ZipOutputStream，
     * 遍历所有 deleted=0 + CONFIRMED 行，每行 ZipEntry(objectKey) + backend.get 裸流 transferTo，
     * 目录结构对齐 OSS。单对象不进内存；分页查防一次拉十万行。
     * <p>
     * 同步写 HttpServletResponse（与 /file/proxy 同口径：异步 dispatch 不传播 SecurityContext）。
     * Content-Disposition 双段（RFC 5987 中文真名 + ASCII 百分号兜底），zip 名 knowhub-oss-backup-yyyyMMddHHmmss.zip。
     * 空文件集打空 zip（浏览器得到合法空压缩包）。
     */
    @GetMapping("/pack-download")
    @Operation(summary = "打包下载全部 OSS 文件到客户端（zip）")
    @PreAuthorize("hasAuthority('knowhub:file:pack-download')")
    public void packDownloadClient(HttpServletResponse response) {
        java.text.SimpleDateFormat sdf = new java.text.SimpleDateFormat("yyyyMMddHHmmss");
        String baseName = "knowhub-oss-backup-" + sdf.format(new java.util.Date());
        String asciiFallback = java.net.URLEncoder.encode(baseName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = "attachment; filename=\"" + asciiFallback + ".zip\"; filename*=UTF-8''" + asciiFallback + ".zip";
        response.setStatus(HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.setHeader(HttpHeaders.CONTENT_DISPOSITION, disposition);
        try (java.io.OutputStream out = response.getOutputStream()) {
            fileService.streamPackDownload(out);
            out.flush();
        } catch (Exception e) {
            log.warn("[打包下载-客户端] 流式中断 reason={}", e.getMessage());
        }
    }

    /**
     * 打包下载到服务器本地磁盘（扩展点1，SERVER 模式）：写 zip 到 storage.local-base-path 下，
     * 返回落盘绝对路径。供前端提示用户去服务器取包。中转模式下打包强制走本端点（用户浏览器不可达 OSS，
     * 打包到客户端意义不大，但后端到 OSS 通畅，落服务器本地后再人工取）。
     */
    @PostMapping("/pack-download-server")
    @Operation(summary = "打包下载全部 OSS 文件到服务器本地（zip，返回路径）")
    @PreAuthorize("hasAuthority('knowhub:file:pack-download')")
    public Result<String> packDownloadServer() {
        String path = fileService.packDownloadToServer();
        return Result.success(path);
    }

    @GetMapping("/list")
    @Operation(summary = "获取文件对象列表")
    @PreAuthorize("hasAuthority('knowhub:file:quarry')")
    public Result<PageInfo<FileObjectVo>> quarryFile(FileQuarry quarry) {
        PageInfo<FileObjectVo> page = fileService.quarryFile(quarry);
        return Result.success(page);
    }

    @GetMapping("/{objectId}")
    @Operation(summary = "获取文件对象详情")
    @PreAuthorize("hasAuthority('knowhub:file:info')")
    public Result<FileObjectVo> getFileObjectInfo(@PathVariable Long objectId) {
        FileObjectVo vo = fileService.getFileObjectInfo(objectId);
        return Result.success(vo);
    }

    @PutMapping("/bind")
    @Operation(summary = "绑定业务关联")
    @Log(title = "文件对象", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:file:upload')")
    public Result<Boolean> bindBizRef(@RequestBody BindVo vo) {
        Boolean b = fileService.bindBizRef(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{objectIds}")
    @Operation(summary = "批量删除文件对象")
    @Log(title = "文件对象", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:file:delete')")
    public Result<Boolean> deleteFileObjects(@PathVariable Long[] objectIds) {
        Boolean b = fileService.deleteFileObjects(objectIds);
        return Result.success(b);
    }
}
