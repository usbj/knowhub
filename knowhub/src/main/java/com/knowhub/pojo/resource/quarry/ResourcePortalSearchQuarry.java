package com.knowhub.pojo.resource.quarry;

import java.util.List;

/**
 * 前台资源搜索/列表查询条件（GET /portal/resource/search 入参）。
 * <p>
 * 支持全文关键字 + 资源类型 + 分类复合过滤 + 排序 + 分页。
 * <p>
 * 与后台 ResourceQuarry 的差异：前台固定只查 PUBLISHED 资源（SQL 硬编码 status='PUBLISHED'，
 * 不接受 status/reviewStatus/createBy 入参覆写）；不带时间区间入参（前台无管理台的时间筛选场景）；
 * 排序维度更聚焦（RELEVANCE/HOT/LATEST 三档，缺省 HOT——资源推荐是主用例）。
 * <p>
 * 资源无 level 等级概念（与博客 BlogPortalSearchQuarry 的差异点：无 userViewLevel 透传字段）。
 * <p>
 * 分类过滤多选：resourceCategoryIds（List&lt;Long&gt;）。资源主表 resource 单资源只属于一个分类，
 * 多选筛选用 IN(...) 自然 OR 语义（在所选任一分类内即可），-1=其他 也作为合法元素参与 IN。
 * Spring MVC 对 List&lt;Long&gt; 入参默认按逗号分隔绑定（?resourceCategoryIds=1,2,3）或
 * 多个同名参数绑定，两者均可命中。
 */
public class ResourcePortalSearchQuarry {

    /** 全文搜索关键字（命中 ft_resource_title_summary_desc 全文索引，ngram 分词） */
    private String keyword;

    /** 资源类型过滤：FILE 文件 / LINK 链接；不传不过滤 */
    private String resourceType;

    /** 分类 id 过滤（-1=其他）；不传不过滤。单选场景留作向后兼容，resourceCategoryIds 优先 */
    private Long resourceCategoryId;

    /** 分类 id 多选过滤（-1=其他 作为合法元素参与 IN）；不传/空列表不过滤。SQL 走 resource_category_id IN(...) */
    private List<Long> resourceCategoryIds;

    /** 排序：RELEVANCE 相关度 / HOT 热度 / LATEST 最新；缺省 HOT（资源推荐是主用例） */
    private String sort;

    /** 分页页码（PageUtil 从请求读取，此处声明便于约束/调试） */
    private Integer pageNum;

    /** 分页页大小（PageUtil 从请求读取） */
    private Integer pageSize;

    public String getKeyword() {
        return keyword;
    }

    public void setKeyword(String keyword) {
        this.keyword = keyword;
    }

    public String getResourceType() {
        return resourceType;
    }

    public void setResourceType(String resourceType) {
        this.resourceType = resourceType;
    }

    public Long getResourceCategoryId() {
        return resourceCategoryId;
    }

    public void setResourceCategoryId(Long resourceCategoryId) {
        this.resourceCategoryId = resourceCategoryId;
    }

    public List<Long> getResourceCategoryIds() {
        return resourceCategoryIds;
    }

    public void setResourceCategoryIds(List<Long> resourceCategoryIds) {
        this.resourceCategoryIds = resourceCategoryIds;
    }

    public String getSort() {
        return sort;
    }

    public void setSort(String sort) {
        this.sort = sort;
    }

    public Integer getPageNum() {
        return pageNum;
    }

    public void setPageNum(Integer pageNum) {
        this.pageNum = pageNum;
    }

    public Integer getPageSize() {
        return pageSize;
    }

    public void setPageSize(Integer pageSize) {
        this.pageSize = pageSize;
    }

    @Override
    public String toString() {
        return "ResourcePortalSearchQuarry{" +
                "keyword='" + keyword + '\'' +
                ", resourceType='" + resourceType + '\'' +
                ", resourceCategoryId=" + resourceCategoryId +
                ", resourceCategoryIds=" + resourceCategoryIds +
                ", sort='" + sort + '\'' +
                ", pageNum=" + pageNum +
                ", pageSize=" + pageSize +
                '}';
    }
}