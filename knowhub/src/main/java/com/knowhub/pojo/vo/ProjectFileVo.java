package com.knowhub.pojo.vo;

import java.util.Date;

/**
 * 项目文件树节点对外 VO，供文件树接口出参/入参。
 * 时间字段一律用 java.util.Date（不要用 String），序列化由全局 jackson.date-format 统一格式化。
 * 扁平结构（带 parentId），前端按 parentId 内存组装 parent→children 递归渲染（GitHub 侧边栏）。
 * originalName/contentLength/contentType/businessType 由后端 join file_object 带出（目录为 null）。
 * isDir: 1=目录(objectId=null) / 0=文件(objectId 指向 file_object)。
 */
public class ProjectFileVo {

    private Long fileId;

    private Long projectId;

    private Long parentId;

    private String name;

    private Integer isDir;

    /** 关联 file_object.object_id，目录=null */
    private Long objectId;

    private Integer sort;

    /** 文件原始名（join file_object 带出，目录为 null） */
    private String originalName;

    /** 文件大小字节（join file_object 带出） */
    private Long contentLength;

    /** 文件 MIME 类型（join file_object 带出） */
    private String contentType;

    /** 文件业务类型（join file_object 带出 PROJECT_SRC/PKG/DOC，供前端分组展示） */
    private String businessType;

    private String createBy;

    private Date createTime;

    private String updateBy;

    private Date updateTime;

    public ProjectFileVo() {
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public Date getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(Date updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "ProjectFileVo{" +
                "fileId=" + fileId +
                ", projectId=" + projectId +
                ", parentId=" + parentId +
                ", name='" + name + '\'' +
                ", isDir=" + isDir +
                ", objectId=" + objectId +
                ", sort=" + sort +
                ", businessType='" + businessType + '\'' +
                '}';
    }
}
