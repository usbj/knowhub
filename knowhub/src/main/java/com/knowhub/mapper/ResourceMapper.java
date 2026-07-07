package com.knowhub.mapper;

import com.knowhub.pojo.entity.Resource;
import com.knowhub.pojo.quarry.ResourceQuarry;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.util.Map;

/**
 * 资源主表 Mapper。
 * 列表查询带 author_nickname(join sys_user on author_id) + category_name(join resource_category)。
 * 互动计数(点赞/收藏/评分)不冗余主表，由 Service 层聚合事实表回填，主表 Mapper 不涉及。
 * 列表查询不带 description（大字段，详情接口单独查），避免拖列表。
 */
@Mapper
public interface ResourceMapper {

    /**
     * 列表查询（PageHelper 在 Service 层 startPage 拦截）。
     * 返回行带 author_nickname / category_name / file_object 元数据（join），由 resultMap 映射。
     */
    List<Resource> quarryResource(ResourceQuarry quarry);

    /** 详情：按主键取未删除资源（带 author_nickname / category_name / file_object 元数据） */
    Resource getResourceInfoById(Long resourceId);

    /** 新增资源，回填主键 */
    Boolean addResource(Resource resource);

    /** 编辑资源（动态列） */
    Boolean editResourceInfo(Resource resource);

    /** 软删资源 */
    Boolean softDeleteResource(Long resourceId);

    /** 下载量 +1（仅 FILE 下载调用，原子自增） */
    Boolean incrDownloadCount(Long resourceId);

    /** 对账用：查所有处于待审核且未删除的资源 ID（审核开关关闭后定时任务批量放行） */
    List<Long> listPendingReviewIds();

    /** 按 categoryId 统计挂载该分类的资源数（删分类前校验是否有关联资源） */
    Long countByCategoryId(@Param("categoryId") Long categoryId);

    /** 批量把挂载某分类的资源置 -1（其他），删分类前调用 */
    Boolean resetCategoryToOther(@Param("categoryId") Long categoryId);

    /**
     * 批量查多个资源的点赞数（事实表聚合，列表回填用）。
     * 返回每行 Map: {resourceId, cnt}，Service 层按 resourceId 收集到 VO。
     */
    List<Map<String, Object>> countLikesByResourceIds(@Param("resourceIds") List<Long> resourceIds);

    /** 批量查多个资源的收藏数 */
    List<Map<String, Object>> countCollectsByResourceIds(@Param("resourceIds") List<Long> resourceIds);

    /** 批量查多个资源的评分均值与计数 */
    List<Map<String, Object>> ratingStatsByResourceIds(@Param("resourceIds") List<Long> resourceIds);
}
