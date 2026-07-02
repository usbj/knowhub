package com.knowhub.service;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.entity.FileObject;
import com.knowhub.pojo.quarry.FileQuarry;
import com.knowhub.pojo.vo.BindVo;
import com.knowhub.pojo.vo.DownloadVo;
import com.knowhub.pojo.vo.FileObjectVo;
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

    /** PUBLIC 回显：返回真实 URL（公开桶直拼 或 短期 GET 预签名），Controller 用它做 302 */
    String getPublicUrl(Long objectId);

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
