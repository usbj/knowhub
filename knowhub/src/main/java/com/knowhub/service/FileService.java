package com.knowhub.service;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.entity.FileObject;
import com.knowhub.pojo.quarry.FileQuarry;
import com.knowhub.pojo.vo.BindVo;
import com.knowhub.pojo.vo.DownloadVo;
import com.knowhub.pojo.vo.FileObjectVo;
import com.knowhub.pojo.vo.PublicObjectStream;
import com.knowhub.pojo.vo.UploadApplyVo;
import com.knowhub.pojo.vo.UploadTokenVo;

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
     * 后端代理转发上传：接收前端 PUT 的字节流，用 s3Client.putObject 写入 OSS，再走 confirm 核对置 CONFIRMED。
     * 用于中转模式的上传接口 PUT /file/proxy-upload/{objectId}（前端拿不到 OSS 直连地址时的兜底通道）。
     * @param objectId  上传令牌签发时返回的元数据行主键（PENDING 行）
     * @param in        前端 PUT 请求体的字节流
     * @param contentLength 字节长度
     * @param contentType  内容类型（须与申请令牌时一致，否则 confirm 校验失败）
     */
    Boolean proxyUpload(Long objectId, java.io.InputStream in, long contentLength, String contentType);

    /**
     * 返回 PUBLIC 对象的回显链接（按当前访问模式）：
     * TRANSFER → /file/public/{objectId}（后端中转）；DIRECT → {directBaseUrl}/{bucket}/{objectKey}（公开读直链，不带签名）。
     * 用于博客详情/文件详情接口按模式填充 coverUrl/previewUrl，前端直接用。
     */
    String getPublicAccessUrl(Long objectId);

    /** PRIVATE 下载：鉴权 + 业务可见性后签发短期 GET 预签名（带 attachment;filename） */
    DownloadVo getDownloadUrl(Long objectId);

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
}
