package com.knowhub.pojo.project.vo;

import java.util.List;

/**
 * 项目文件树形 VO，供文件树接口出参（前端 GitHub 式侧边栏直接递归渲染）。
 * 由 ProjectFileVo 扁平列表在 service 层组装为树：按 parentId 归集 children。
 * 字段与 ProjectFileVo 对齐，额外带 children 列表（目录才有，文件为 null）。
 */
public class ProjectFileTreeVo {

    private Long fileId;

    private Long projectId;

    private Long parentId;

    private String name;

    private Integer isDir;

    private Long objectId;

    private Integer sort;

    private String originalName;

    private Long contentLength;

    private String contentType;

    private String businessType;

    /** 子节点（目录才有，文件为 null）；前端递归渲染展开/折叠 */
    private List<ProjectFileTreeVo> children;

    public ProjectFileTreeVo() {
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

    public List<ProjectFileTreeVo> getChildren() {
        return children;
    }

    public void setChildren(List<ProjectFileTreeVo> children) {
        this.children = children;
    }

    @Override
    public String toString() {
        return "ProjectFileTreeVo{" +
                "fileId=" + fileId +
                ", name='" + name + '\'' +
                ", isDir=" + isDir +
                ", objectId=" + objectId +
                ", childrenCount=" + (children != null ? children.size() : 0) +
                '}';
    }
}
