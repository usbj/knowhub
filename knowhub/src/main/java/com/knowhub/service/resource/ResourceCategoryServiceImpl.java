package com.knowhub.service.resource;

import cn.hutool.core.bean.BeanUtil;
import com.knowhub.mapper.resource.ResourceCategoryMapper;
import com.knowhub.mapper.resource.ResourceMapper;
import com.knowhub.pojo.resource.entity.ResourceCategory;
import com.knowhub.pojo.resource.vo.ResourceCategoryTreeVo;
import com.knowhub.pojo.resource.vo.ResourceCategoryVo;
import com.knowhub.service.resource.impl.ResourceCategoryService;
import com.rookie.common.exception.ServiceException;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 资源分类 Service 实现。
 * 分类为自关联树（parent_id=0 顶级）。列表全量查扁平 List 后在内存组树返回前端。
 * 删除分类：有子分类拒绝删；无子分类则把挂载该分类的资源置 -1（其他）后再软删分类行。
 */
@Service
public class ResourceCategoryServiceImpl implements ResourceCategoryService {

    @Autowired
    ResourceCategoryMapper resourceCategoryMapper;

    @Autowired
    ResourceMapper resourceMapper;

    @Override
    public List<ResourceCategoryTreeVo> categoryTree() {
        // 1. 全量查启用分类（已按 parent_id/sort 排序）
        List<ResourceCategory> list = resourceCategoryMapper.listAllCategories();
        if (list == null || list.isEmpty()) {
            return new ArrayList<>();
        }
        // 2. 扁平转树形：按 parent_id 分组，递归组装 children
        List<ResourceCategoryTreeVo> voList = list.stream()
                .map(c -> BeanUtil.toBean(c, ResourceCategoryTreeVo.class))
                .collect(Collectors.toList());
        Map<Long, List<ResourceCategoryTreeVo>> byParent = new HashMap<>();
        for (ResourceCategoryTreeVo vo : voList) {
            byParent.computeIfAbsent(vo.getParentId() != null ? vo.getParentId() : 0L, k -> new ArrayList<>()).add(vo);
        }
        List<ResourceCategoryTreeVo> roots = new ArrayList<>();
        for (ResourceCategoryTreeVo vo : voList) {
            vo.setChildren(byParent.get(vo.getCategoryId()));
            if (vo.getParentId() == null || vo.getParentId() == 0L) {
                roots.add(vo);
            }
        }
        return roots;
    }

    @Override
    public ResourceCategoryVo getCategoryInfo(Long categoryId) {
        ResourceCategory category = resourceCategoryMapper.getCategoryInfoById(categoryId);
        if (category == null) {
            throw new ServiceException(500, "分类不存在");
        }
        return BeanUtil.toBean(category, ResourceCategoryVo.class);
    }

    @Override
    @Transactional
    public Boolean addCategoryInfo(ResourceCategoryVo vo) {
        if (vo.getCategoryName() == null || vo.getCategoryName().isEmpty()) {
            throw new ServiceException(500, "分类名不能为空");
        }
        ResourceCategory category = BeanUtil.toBean(vo, ResourceCategory.class);
        // parentId 缺省置 0（顶级）
        if (category.getParentId() == null) {
            category.setParentId(0L);
        }
        UserInfo userInfo = currentUser();
        category.setCreateBy(userInfo.getUsername());
        category.setUpdateBy(userInfo.getUsername());
        category.setCreateTime(new Date());
        category.setUpdateTime(new Date());
        try {
            resourceCategoryMapper.addCategory(category);
        } catch (Exception e) {
            throw new ServiceException(500, "分类添加失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean editCategoryInfo(ResourceCategoryVo vo) {
        if (vo.getCategoryId() == null) {
            throw new ServiceException(500, "分类ID不能为空");
        }
        ResourceCategory category = BeanUtil.toBean(vo, ResourceCategory.class);
        category.setUpdateBy(currentUser().getUsername());
        try {
            resourceCategoryMapper.editCategoryInfo(category);
        } catch (Exception e) {
            throw new ServiceException(500, "分类修改失败", e.getMessage());
        }
        return true;
    }

    /**
     * 删除分类：
     * - 有子分类拒绝删（提示先处理子分类），避免删父分类后子分类变孤儿
     * - 无子分类则把挂载该分类的资源置 -1（其他）后软删分类行
     * 事务保证"资源置 -1 + 分类软删"原子，避免删分类后资源悬空非 -1 旧值。
     */
    @Override
    @Transactional
    public Boolean deleteCategoryInfo(Long categoryId) {
        ResourceCategory category = resourceCategoryMapper.getCategoryInfoById(categoryId);
        if (category == null) {
            throw new ServiceException(500, "分类不存在");
        }
        // 1. 有子分类拒绝删
        Long childCount = resourceCategoryMapper.countChildren(categoryId);
        if (childCount != null && childCount > 0) {
            throw new ServiceException(500, "该分类下有子分类，请先处理子分类后再删除");
        }
        // 2. 把挂载该分类的资源置 -1（其他），避免资源悬空旧分类值
        resourceMapper.resetCategoryToOther(categoryId);
        // 3. 软删分类行
        try {
            resourceCategoryMapper.softDeleteCategory(categoryId);
        } catch (Exception e) {
            throw new ServiceException(500, "分类删除失败", e.getMessage());
        }
        return true;
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
