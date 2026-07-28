package com.knowhub.mapper.resource;

import com.knowhub.pojo.resource.entity.ResourceCategory;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 资源分类 Mapper。
 * 分类为自关联树（parent_id=0 顶级），列表全量查出后由 Service 层组装为树。
 * 删除分类前需调 ResourceMapper.countByCategoryId 校验是否有关联资源。
 */
@Mapper
public interface ResourceCategoryMapper {

    /** 查全量启用分类（status=1 且未删），按 parent_id asc、sort asc 排序，Service 层组树 */
    List<ResourceCategory> listAllCategories();

    /** 详情：按主键取未删除分类 */
    ResourceCategory getCategoryInfoById(Long categoryId);

    /** 新增分类，回填主键 */
    Boolean addCategory(ResourceCategory category);

    /** 编辑分类（动态列） */
    Boolean editCategoryInfo(ResourceCategory category);

    /** 软删分类 */
    Boolean softDeleteCategory(Long categoryId);

    /** 查子分类数（删分类前校验是否有子分类，有子分类拒绝删） */
    Long countChildren(@org.apache.ibatis.annotations.Param("parentId") Long parentId);
}
