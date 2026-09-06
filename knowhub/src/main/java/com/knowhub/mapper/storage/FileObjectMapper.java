package com.knowhub.mapper.storage;

import com.knowhub.pojo.storage.entity.FileObject;
import com.knowhub.pojo.storage.quarry.FileQuarry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.Date;
import java.util.List;

/**
 * 文件对象元数据 Mapper。
 * insert 用动态列（对齐 README.dev「Mapper XML insert 均改为动态列」约定）；
 * 元数据与 RustFS 对象解耦，对象本体的增删走 S3Client，不在此 Mapper 范围。
 */
@Mapper
public interface FileObjectMapper {

    /** 列表查询（PageHelper 在 Service 层 startPage 拦截） */
    List<FileObject> quarryFile(FileQuarry quarry);

    /** 详情：按主键取未删除行 */
    FileObject getFileObjectById(Long objectId);

    /** 新增元数据行，回填主键 */
    Boolean addFileObject(FileObject fileObject);

    /** 编辑元数据行（动态列，用于 confirm 回填/状态变更/bind/软删） */
    Boolean editFileObject(FileObject fileObject);

    /** 软删（deleted=1），对象本体由 GC 异步清 */
    Boolean softDeleteFileObject(@Param("objectId") Long objectId, @Param("updateBy") String updateBy);

    /**
     * GC 扫描：查超时未确认的 PENDING 行。
     * @param ttlBefore 早于此时间的 PENDING 视为超时
     */
    List<FileObject> findExpiredPending(@Param("ttlBefore") Date ttlBefore);

    /** GC 扫描：查所有已软删（deleted=1）的行，待 DeleteObject 后物理删 */
    List<FileObject> findSoftDeleted();

    /** 物理删元数据行（GC 清完对象后调用） */
    Boolean physicalDeleteFileObject(Long objectId);

    /** 按业务关联批量软删（删业务行时级联清文件元数据，对象本体仍由 GC 清） */
    Boolean softDeleteByBizRef(@Param("businessType") String businessType,
                               @Param("bizRefId") Long bizRefId,
                               @Param("updateBy") String updateBy);

    /**
     * 打包下载：分页查所有 deleted=0 + CONFIRMED 的行（按 objectId 升序）。
     * 用于扩展点1 打包下载遍历，防一次查十万行进内存。
     * @param offset 偏移量
     * @param size 每页条数
     */
    List<FileObject> listAllConfirmedForPack(@Param("offset") long offset, @Param("size") int size);

    /** 打包下载预估总字节数：累加所有 deleted=0 + CONFIRMED 的 content_length */
    Long sumConfirmedContentLength();
}
