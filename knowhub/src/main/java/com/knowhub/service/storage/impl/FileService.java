package com.knowhub.service.storage.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.storage.entity.FileObject;
import com.knowhub.pojo.storage.quarry.FileQuarry;
import com.knowhub.pojo.common.vo.BindVo;
import com.knowhub.pojo.storage.vo.DownloadVo;
import com.knowhub.pojo.storage.vo.FileObjectVo;
import com.knowhub.pojo.storage.vo.PublicObjectStream;
import com.knowhub.pojo.storage.vo.UploadApplyVo;
import com.knowhub.pojo.storage.vo.UploadTokenVo;

import java.io.IOException;

/**
 * 文件存储服务。预签名直传链路：apply 签发上传令牌 → 前端直传 RustFS → confirm 用 HeadObject 核对。
 * 后端全程不经流文件字节，只管元数据 + 预签名签发。接口只暴露 DTO，不暴露实体。
 */
public interface FileService {

    /** 签发上传令牌：校验类型/大小 → insert PENDING 元数据行 → 签发 PutObject 预签名 */
    UploadTokenVo applyUploadToken(UploadApplyVo vo);

    /** 上传确认：HeadObject 核对真实值 → 置 CONFIRMED 并回填校验值；可选回填 bizRefId */
    Boolean confirmUpload(Long objectId, Long bizRefId);

    /** PUBLIC 回显：校验 PUBLIC + CONFIRMED 后用 s3Client.getObject 拉字节流，
     *  返回含元数据 + ResponseInputStream 的载体；stream 的 close 责任在 Controller（try-with-resources）。
     *  对象不存在/非公开/未确认抛 ServiceException(404/403)，由 Controller 映射 HTTP 状态码。 */
    PublicObjectStream streamPublicObject(Long objectId);

    /**
     * 中转下载（支持 PUBLIC + PRIVATE）：校验 CONFIRMED 后用 s3Client.getObject 拉字节流，
     * 返回含元数据 + ResponseInputStream 的载体；PRIVATE 走鉴权（上传人/管理员）并带 attachment;filename 强制下载。
     * stream 的 close 责任在 Controller。用于中转模式的 PRIVATE 下载接口 GET /file/proxy/{id}。
     */
    PublicObjectStream streamDownloadObject(Long objectId);

    /**
     * 中转下载重载：业务模块（资源/项目）已在本业务层完成业务可见性闸，传 bizAuthorized=true
     * 跳过文件底座 owner 闸直接拉字节流。与 {@link #getDownloadUrl(Long, boolean)} 对称——
     * TRANSFER 访问模式下业务下载最终命中 /file/proxy/{objectId}，凭调 getDownloadUrl 时传的 bizAuthorized
     * 不足以放行中转字节流，故此重载补齐中转路径。bizAuthorized=false 等价 {@link #streamDownloadObject(Long)}。
     * PRIVATE 文件传 true 时不再要求上传人/文件管理员身份，PUBLIC 两入参对称不鉴权。
     */
    PublicObjectStream streamDownloadObject(Long objectId, boolean bizAuthorized);

    /**
     * 按 objectId 拉对象字节流，不做任何鉴权（调用方自控权限，如项目打包下载走项目级 canDownload 校验）。
     * 仅校验对象存在 + CONFIRMED，用 s3Client.getObject 拉流并封 PublicObjectStream（contentDisposition=null、
     * 字段口径与 streamPublicObject 同：contentType 优先元数据、contentLength 优先 S3 响应）。
     * stream 的 close 责任在调用方。用于项目打包 zip 这种"业务侧自控权限、批量拉流"的场景。
     */
    PublicObjectStream openRawStream(Long objectId);

    /**
     * 后端代理转发上传：接收前端 PUT 的字节流，用 s3Client.putObject 写入 OSS，再走 confirm 核对置 CONFIRMED。
     * 用于中转模式的上传接口 PUT /file/proxy-upload/{objectId}（前端拿不到 OSS 直连地址时的兜底通道）。
     * @param objectId  上传令牌签发时返回的元数据行主键（PENDING 行）
     * @param in        前端 PUT 请求体的字节流
     * @param contentLength 字节长度
     * @param contentType  内容类型（须与申请令牌时一致，否则 confirm 校验失败）
     */
    Boolean proxyUpload(Long objectId, java.io.InputStream in, long contentLength, String contentType);

    /**
     * 返回 PUBLIC 对象按当前访问模式的真实回显 URL（不跳转，直接给地址）：
     * TRANSFER → /file/public/{objectId}（后端中转）；DIRECT → {directBaseUrl}/{bucket}/{objectKey}（公开读直链，不带签名）或私有预签名。
     * 用于 /file/url/{id} 接口、博客/文件详情接口按模式填充 coverUrl/previewUrl 等"需要直接拿地址"的场景。
     * 对象不存在/不可访问时回退 /file/public/{objectId}（由中转接口映射 403/404，不抛异常避免影响 VO 填充整页）。
     * 注意：落库的稳定引用统一用 /file/resolve/{objectId}（前端 buildFileResolveUrl 拼接），不走本方法——
     * 本方法仅用于"运行时取真实地址"，落库引用与运行时解析分离。
     */
    String getPublicAccessUrl(Long objectId);

    /**
     * 解析 PUBLIC 对象为按当前访问模式的回显目标 URL（供 /file/resolve/{objectId} 接口 302 跳转）。
     * 校验对象存在、access=PUBLIC、uploadStatus=CONFIRMED；不通过返回 null，由 Controller 映射 403/404。
     * 通过时按 {@code knowhub.file.access_mode} 分发：
     * - TRANSFER：返回 /file/public/{objectId}（相对路径作 302 Location，浏览器按当前页 origin 解析同源命中代理）。
     * - DIRECT：桶公开读返回 {directBaseUrl}/{bucket}/{objectKey}；桶私有返回 rewriteHostToDirect(presignGet) 带签 GET URL。
     * @param objectId 文件对象主键
     * @return 302 跳转目标 URL；不可访问返回 null
     */
    String resolvePublicUrl(Long objectId);

    /**
     * PRIVATE 下载：鉴权 + 业务可见性后签发短期 GET 预签名（带 attachment;filename）。
     * 该重载保留给"无业务上下文的通用下载入口"——用户直接指定 objectId 下载（如后台
     * /file/download/{objectId}），文件层没有业务行可校验可见性，只能走文件底座自有的 owner 闸
     * （上传人 OR 具 knowhub:file:review 的管理员）。PUBLIC 文件不鉴权仍可直接签发。
     */
    DownloadVo getDownloadUrl(Long objectId);

    /**
     * 业务代理下载重载：调用方为业务模块（资源/项目等），已在本业务层完成业务可见性闸
     * （资源判 PUBLISHED+下载权限、项目判 canDownload），传 bizAuthorized=true 跳过文件底座的
     * owner 闸直接签发。PRIVATE 文件不再要求是上传人/文件管理员——只要业务层放行即可签发。
     * bizAuthorized=false 等价于 {@link #getDownloadUrl(Long)}，通用入口走此重载传 false。
     * PUBLIC 文件两种入参都不鉴权，对称。
     */
    DownloadVo getDownloadUrl(Long objectId, boolean bizAuthorized);

    /** 列表查询（PageHelper 分页） */
    PageInfo<FileObjectVo> quarryFile(FileQuarry quarry);

    /** 详情 */
    FileObjectVo getFileObjectInfo(Long objectId);

    /** 绑定业务关联（业务行创建后回填 biz_ref_id） */
    Boolean bindBizRef(BindVo vo);

    /** 批量软删（对象本体由 GC 异步清） */
    Boolean deleteFileObjects(Long[] objectIds);

    /** GC：扫描超时 PENDING + 已软删行，DeleteObject + 物理删元数据 */
    void gc();

    /**
     * 打包下载预估总字节数：累加所有 deleted=0 + CONFIRMED 的 file_object.content_length。
     * 供前端在发起打包下载前做大小预估、超 50G 弹警告确认。
     * @return 总字节数
     */
    long packTotalSize();

    /**
     * 流式打包下载全部 OSS 文件到指定 OutputStream（CLIENT 模式写 HttpServletResponse）。
     * 遍历所有 deleted=0 + CONFIRMED 行，每行 ZipEntry(objectKey) + backend.get 裸流 transferTo，
     * 目录结构对齐 OSS objectKey。单对象不进内存（transferTo 8KB 缓冲）。
     * @param out 目标输出流（zip 字节流），调用方负责关闭
     */
    void streamPackDownload(java.io.OutputStream out) throws IOException;

    /**
     * 打包下载到服务器本地磁盘（SERVER 模式）：写 zip 到 storage.local-base-path 下的临时文件，
     * 返回落盘绝对路径。供前端提示用户去服务器取包。
     * @return 落盘绝对路径
     */
    String packDownloadToServer();
}
