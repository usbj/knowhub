package com.knowhub.service;

import com.knowhub.pojo.vo.TagVo;

import java.util.List;

/**
 * 受控标签 Service（仅管理员维护）。
 */
public interface TagService {

    /** 标签列表（按状态可选过滤） */
    List<TagVo> quarryTag(TagVo quarry);

    /** 详情 */
    TagVo getTagInfo(Long tagId);

    /** 新增 */
    Boolean addTagInfo(TagVo vo);

    /** 编辑 */
    Boolean editTagInfo(TagVo vo);

    /** 软删（级联清理 blog_tag 中该标签的关联） */
    Boolean deleteTagInfo(Long tagId);
}
