package com.knowhub.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

/**
 * 项目文件树实体，对应 project_file 表（支撑 GitHub 式侧边栏布局）。
 * 目录骨架 + 叶子指向 file_object。isDir=1 目录(objectId=null)/isDir=0 文件(关联 file_object)。
 * 一个项目按 project_id 拉全树，前端内存组装 parent→children 递归渲染（展开/折叠）。
 * 与 file_object 分工：file_object=对象存储元数据(扁平,对接 RustFS)；
 * project_file=项目内目录树骨架,叶子 objectId 指向 file_object。
 * 文件本体复用 file_object（business_type ∈ PROJECT_SRC/PKG/DOC，biz_ref_id=project_id）。
 * 删项目：事务内级联软删 project_file + fileService.softDeleteByBizRef 三类。
 * 审计列由 BaseEntity 承载；软删 deleted 独立字段。
 */
public class ProjectFile extends BaseEntity {

    private Long fileId;

    private Long projectId;

    /** 父目录ID，根节点 null */
    private Long parentId;

    private String name;

    /** 1=目录 / 0=文件（见 ProjectFileType 枚举） */
    private Integer isDir;

    /** 关联 file_object.object_id，目录=null */
    private Long objectId;

    private Integer sort;

    private Integer deleted;

    // ---- 非表字段（文件树查询 join file_object 带出，供前端展示文件元数据） ----
    /** 文件原始名（join file_object on object_id 带出，目录为 null，非表字段） */
    private String originalName;

    /** 文件大小字节（join file_object 带出，非表字段） */
    private Long contentLength;

    /** 文件 MIME 类型（join file_object 带出，非表字段） */
    private String contentType;

    /** 文件业务类型（join file_object 带出 PROJECT_SRC/PKG/DOC，非表字段，供前端分组展示） */
    private String businessType;

    public ProjectFile() {
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getProjectId() {
        return projectId;
    }

    public void setProjectId(Long projectId) {
        this.projectId = projectId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getIsDir() {
        return isDir;
    }

    public void setIsDir(Integer isDir) {
        this.isDir = isDir;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    public Long getContentLength() {
        return contentLength;
    }

    public void setContentLength(Long contentLength) {
        this.contentLength = contentLength;
    }

    public String getContentType() {
        return contentType;
    }

    public void setContentType(String contentType) {
        this.contentType = contentType;
    }

    public String getBusinessType() {
        return businessType;
    }

    public void setBusinessType(String businessType) {
        this.businessType = businessType;
    }

    @Override
    public String toString() {
        return "ProjectFile{" +
                "fileId=" + fileId +
                ", projectId=" + projectId +
                ", parentId=" + parentId +
                ", name='" + name + '\'' +
                ", isDir=" + isDir +
                ", objectId=" + objectId +
                ", sort=" + sort +
                ", deleted=" + deleted +
                ", originalName='" + originalName + '\'' +
                ", businessType='" + businessType + '\'' +
                '}';
    }
}
