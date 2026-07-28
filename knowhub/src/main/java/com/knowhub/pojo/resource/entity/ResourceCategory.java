package com.knowhub.pojo.resource.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 资源分类实体，对应 resource_category 表（自关联树）。
 * parent_id=0 表示顶级；同级按 sort asc 排序。status:0禁1启。
 * 删除分类时：有子分类拒绝删（提示先处理子分类）；无子分类则把挂载该分类的资源
 * 置 resource_category_id=-1（其他）后再软删分类行。
 * 审计列由 BaseEntity 承载；软删 deleted 独立字段。
 */
public class ResourceCategory extends BaseEntity {

    private Long categoryId;

    /** 父分类 id（0=顶级） */
    private Long parentId;

    private String categoryName;

    private Integer sort;

    /** 状态：0禁1启 */
    private Integer status;

    private Integer deleted;

    public ResourceCategory() {
    }

    public ResourceCategory(Date createTime, Date updateTime, String createBy, String updateBy,
                            Long categoryId, Long parentId, String categoryName,
                            Integer sort, Integer status, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.categoryId = categoryId;
        this.parentId = parentId;
        this.categoryName = categoryName;
        this.sort = sort;
        this.status = status;
        this.deleted = deleted;
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

    public Integer getDeleted() {
        return deleted;
    }

    public void setDeleted(Integer deleted) {
        this.deleted = deleted;
    }

    @Override
    public String toString() {
        return "ResourceCategory{" +
                "categoryId=" + categoryId +
                ", parentId=" + parentId +
                ", categoryName='" + categoryName + '\'' +
                ", sort=" + sort +
                ", status=" + status +
                ", deleted=" + deleted +
                '}';
    }
}
