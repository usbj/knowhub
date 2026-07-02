package com.knowhub.pojo.vo;

/**
 * 受控标签 VO，供管理员标签管理接口入参/出参。
 */
public class TagVo {

    private Long tagId;

    private String tagName;

    private String description;

    private Integer sort;

    private Integer status;

    private String createBy;

    private String createTime;

    private String updateBy;

    private String updateTime;

    public TagVo() {
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

    public String getCreateBy() {
        return createBy;
    }

    public void setCreateBy(String createBy) {
        this.createBy = createBy;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateBy() {
        return updateBy;
    }

    public void setUpdateBy(String updateBy) {
        this.updateBy = updateBy;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }

    @Override
    public String toString() {
        return "TagVo{" +
                "tagId=" + tagId +
                ", tagName='" + tagName + '\'' +
                ", sort=" + sort +
                ", status=" + status +
                '}';
    }
}
