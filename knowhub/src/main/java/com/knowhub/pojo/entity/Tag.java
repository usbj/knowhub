package com.knowhub.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

/**
 * 受控标签实体，对应 tag 表。
 * 仅管理员可维护；博客发文时从中选择。
 */
public class Tag extends BaseEntity {

    private Long tagId;

    private String tagName;

    private String description;

    private Integer sort;

    private Integer status;

    private Integer deleted;

    public Tag() {
    }

    public Tag(Date createTime, Date updateTime, String createBy, String updateBy,
               Long tagId, String tagName, String description, Integer sort, Integer status, Integer deleted) {
        super(createTime, updateTime, createBy, updateBy);
        this.tagId = tagId;
        this.tagName = tagName;
        this.description = description;
        this.sort = sort;
        this.status = status;
        this.deleted = deleted;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
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
        return "Tag{" +
                "tagId=" + tagId +
                ", tagName='" + tagName + '\'' +
                ", sort=" + sort +
                ", status=" + status +
                ", deleted=" + deleted +
                ", createTime=" + getCreateTime() +
                ", updateTime=" + getUpdateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                ", updateBy='" + getUpdateBy() + '\'' +
                '}';
    }
}