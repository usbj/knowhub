package com.knowhub.controller.admin;

import com.knowhub.pojo.resource.vo.ResourceCategoryTreeVo;
import com.knowhub.pojo.resource.vo.ResourceCategoryVo;
import com.knowhub.service.resource.impl.ResourceCategoryService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 资源分类管理接口。
 * 分类为自关联树，列表全量查后组树返回前端 el-tree。
 * 权限键三段式 knowhub:resource:category:动作，路由 /resource-category。
 * 删除分类：有子分类拒绝；无子分类把挂载资源置 -1（其他）后软删。
 */
@Tag(name = "资源分类", description = "资源分类树管理相关接口（管理员维护）")
@RestController
@RequestMapping("/resource-category")
public class ResourceCategoryController {

    @Autowired
    ResourceCategoryService resourceCategoryService;

    @GetMapping("/tree")
    @Operation(summary = "获取资源分类树")
    @PreAuthorize("hasAuthority('knowhub:resource:category:quarry')")
    public Result<List<ResourceCategoryTreeVo>> categoryTree() {
        List<ResourceCategoryTreeVo> tree = resourceCategoryService.categoryTree();
        return Result.success(tree);
    }

    @GetMapping("/{categoryId}")
    @Operation(summary = "获取分类详情")
    @PreAuthorize("hasAuthority('knowhub:resource:category:quarry')")
    public Result<ResourceCategoryVo> getCategoryInfo(@PathVariable Long categoryId) {
        ResourceCategoryVo vo = resourceCategoryService.getCategoryInfo(categoryId);
        return Result.success(vo);
    }

    @PostMapping()
    @Operation(summary = "添加分类")
    @Log(title = "资源分类", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:resource:category:add')")
    public Result<Boolean> addCategory(@RequestBody ResourceCategoryVo vo) {
        Boolean b = resourceCategoryService.addCategoryInfo(vo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "编辑分类")
    @Log(title = "资源分类", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:resource:category:edit')")
    public Result<Boolean> editCategory(@RequestBody ResourceCategoryVo vo) {
        Boolean b = resourceCategoryService.editCategoryInfo(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{categoryId}")
    @Operation(summary = "删除分类")
    @Log(title = "资源分类", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:resource:category:delete')")
    public Result<Boolean> deleteCategory(@PathVariable Long categoryId) {
        Boolean b = resourceCategoryService.deleteCategoryInfo(categoryId);
        return Result.success(b);
    }
}