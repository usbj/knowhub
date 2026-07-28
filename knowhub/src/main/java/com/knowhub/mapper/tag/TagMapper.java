package com.knowhub.mapper.tag;

import com.knowhub.pojo.tag.entity.Tag;
import com.knowhub.pojo.tag.vo.TagVo;
import org.apache.ibatis.annotations.Mapper;

import java.util.List;

/**
 * 受控标签 Mapper（仅管理员维护）。
 */
@Mapper
public interface TagMapper {

    /** 标签列表查询（不分页，标签数量少；亦支持按状态过滤） */
    List<Tag> quarryTag(TagVo quarry);

    /** 详情 */
    Tag getTagInfoById(Long tagId);

    /** 新增 */
    Boolean addTag(Tag tag);

    /** 编辑（动态列） */
    Boolean editTagInfo(Tag tag);

    /** 软删 */
    Boolean softDeleteTag(Long tagId);

    /** 校验用：按 id 集合取受控标签中启用且未删除的标签（用于发文时校验 tagId 是否落在受控集） */
    List<Tag> getEnabledTagsByIds(List<Long> tagIds);
}
