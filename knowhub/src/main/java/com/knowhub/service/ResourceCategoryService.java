package com.knowhub.service;

import com.knowhub.pojo.vo.ResourceCategoryTreeVo;
import com.knowhub.pojo.vo.ResourceCategoryVo;

import java.util.List;

/**
 * 资源分类 Service。
 * 分类为自关联树，列表全量查后组树返回。删除分类时：
 * - 有子分类拒绝删（提示先处理子分类）
 * - 无子分类则把挂载该分类的资源置 -1（其他）后再软删分类行
 * 接口只暴露 DTO，不暴露实体。
 */
public interface ResourceCategoryService {

    /** 分类树（全量启用分类组树，前端 el-tree 渲染） */
    List<ResourceCategoryTreeVo> categoryTree();

    /** 详情 */
    ResourceCategoryVo getCategoryInfo(Long categoryId);

    /** 新增分类 */
    Boolean addCategoryInfo(ResourceCategoryVo vo);

    /** 编辑分类 */
    Boolean editCategoryInfo(ResourceCategoryVo vo);

    /** 删除分类（有子分类拒绝；无子分类把挂载资源置 -1 后软删） */
    Boolean deleteCategoryInfo(Long categoryId);
}
