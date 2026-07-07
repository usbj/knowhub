package com.knowhub.pojo.vo;

import java.util.List;

/**
 * 资源分类树形 VO，供前端 el-tree 渲染。
 * 由 Service 层把扁平 List<ResourceCategory> 按 parent_id 组装为树后返回。
 * 前端约定 -1=其他（不在分类树内，由前端硬编码加一个"其他"虚拟节点）。
 */
public class ResourceCategoryTreeVo {

    private Long categoryId;

    private Long parentId;

    private String categoryName;

    private Integer sort;

    private Integer status;

    private List<ResourceCategoryTreeVo> children;

    public ResourceCategoryTreeVo() {
    }

    public Long getCategoryId() {
        return categoryId;
    }

    public void setCategoryId(Long categoryId) {
        this.categoryId = categoryId;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public String getCategoryName() {
        return categoryName;
    }

    public void setCategoryName(String categoryName) {
        this.categoryName = categoryName;
    }

    public Integer getSort() {
        return sort;
    }

    public void setSort(Integer sort) {
        this.sort = sort;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public List<ResourceCategoryTreeVo> getChildren() {
        return children;
    }

    public void setChildren(List<ResourceCategoryTreeVo> children) {
        this.children = children;
    }

    @Override
    public String toString() {
        return "ResourceCategoryTreeVo{" +
                "categoryId=" + categoryId +
                ", categoryName='" + categoryName + '\'' +
                ", sort=" + sort +
                ", children=" + (children != null ? children.size() : 0) +
                '}';
    }
}
