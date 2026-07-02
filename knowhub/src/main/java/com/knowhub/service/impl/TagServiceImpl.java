package com.knowhub.service.impl;

import cn.hutool.core.bean.BeanUtil;
import com.knowhub.mapper.BlogTagMapper;
import com.knowhub.mapper.TagMapper;
import com.knowhub.pojo.vo.TagVo;
import com.knowhub.service.TagService;
import com.rookie.common.exception.ServiceException;
import com.knowhub.pojo.entity.Tag;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class TagServiceImpl implements TagService {

    @Autowired
    TagMapper tagMapper;

    @Autowired
    BlogTagMapper blogTagMapper;

    @Override
    public List<TagVo> quarryTag(TagVo quarry) {
        List<Tag> list = tagMapper.quarryTag(quarry);
        return list.stream().map(t -> BeanUtil.toBean(t, TagVo.class)).collect(Collectors.toList());
    }

    @Override
    public TagVo getTagInfo(Long tagId) {
        Tag tag = tagMapper.getTagInfoById(tagId);
        if (tag == null) {
            throw new ServiceException(500, "标签不存在");
        }
        return BeanUtil.toBean(tag, TagVo.class);
    }

    @Override
    @Transactional
    public Boolean addTagInfo(TagVo vo) {
        if (vo.getTagName() == null || vo.getTagName().isEmpty()) {
            throw new ServiceException(500, "标签名不能为空");
        }
        Tag tag = BeanUtil.toBean(vo, Tag.class);
        UserInfo userInfo = currentUser();
        tag.setCreateBy(userInfo.getUsername());
        tag.setUpdateBy(userInfo.getUsername());
        tag.setCreateTime(new Date());
        tag.setUpdateTime(new Date());
        try {
            tagMapper.addTag(tag);
        } catch (Exception e) {
            throw new ServiceException(500, "标签添加失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean editTagInfo(TagVo vo) {
        if (vo.getTagId() == null) {
            throw new ServiceException(500, "标签ID不能为空");
        }
        Tag tag = BeanUtil.toBean(vo, Tag.class);
        tag.setUpdateBy(currentUser().getUsername());
        try {
            tagMapper.editTagInfo(tag);
        } catch (Exception e) {
            throw new ServiceException(500, "标签修改失败", e.getMessage());
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteTagInfo(Long tagId) {
        try {
            // 级联清理 blog_tag 中该标签的关联（避免悬空引用）
            blogTagMapper.deleteBlogTagByTagId(tagId);
            tagMapper.softDeleteTag(tagId);
        } catch (Exception e) {
            throw new ServiceException(500, "标签删除失败", e.getMessage());
        }
        return true;
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}