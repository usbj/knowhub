package com.knowhub.controller;

import com.knowhub.pojo.vo.TagVo;
import com.knowhub.service.TagService;
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

@Tag(name = "博客标签", description = "受控标签管理相关接口（管理员维护）")
@RestController
@RequestMapping("/tag")
public class TagController {

    @Autowired
    TagService tagService;

    @GetMapping("/list")
    @Operation(summary = "获取标签列表")
    @PreAuthorize("hasAuthority('knowhub:tag:quarry')")
    public Result<List<TagVo>> quarryTag(TagVo quarry) {
        List<TagVo> list = tagService.quarryTag(quarry);
        return Result.success(list);
    }

    @GetMapping("/{tagId}")
    @Operation(summary = "获取标签详情")
    @PreAuthorize("hasAuthority('knowhub:tag:info')")
    public Result<TagVo> getTagInfo(@PathVariable Long tagId) {
        TagVo vo = tagService.getTagInfo(tagId);
        return Result.success(vo);
    }

    @PostMapping()
    @Operation(summary = "添加标签")
    @Log(title = "博客标签", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:tag:add')")
    public Result<Boolean> addTag(@RequestBody TagVo vo) {
        Boolean b = tagService.addTagInfo(vo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "编辑标签")
    @Log(title = "博客标签", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:tag:edit')")
    public Result<Boolean> editTag(@RequestBody TagVo vo) {
        Boolean b = tagService.editTagInfo(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{tagIds}")
    @Operation(summary = "批量删除标签")
    @Log(title = "博客标签", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:tag:delete')")
    public Result<Boolean> deleteTag(@PathVariable Long[] tagIds) {
        Boolean b = false;
        for (Long tagId : tagIds) {
            b = tagService.deleteTagInfo(tagId);
        }
        return Result.success(b);
    }
}