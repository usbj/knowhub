package com.knowhub.pojo.vo;

/**
 * 资源分类对外 VO，供 Controller 入参/出参。
 * 时间字段沿用项目约定用 String（BeanUtil 复制 Date→String）。
 * 树形展示用 {@link ResourceCategoryTreeVo}（带 children）；本 VO 为扁平结构，供列表/增改入参。
 */
public class ResourceCategoryVo {

    private Long categoryId;

    private Long parentId;

    private String categoryName;

    private Integer sort;

    /** 状态：0禁1启 */
    private Integer status;

    private String createBy;

    private String createTime;

    private String updateBy;

    private String updateTime;

    public ResourceCategoryVo() {
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
        return "ResourceCategoryVo{" +
                "categoryId=" + categoryId +
                ", parentId=" + parentId +
                ", categoryName='" + categoryName + '\'' +
                ", sort=" + sort +
                ", status=" + status +
                '}';
    }
}
