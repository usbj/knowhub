package com.knowhub.pojo.tag.vo;

/**
 * 标签选项 VO（GET /portal/tag/list 出参）。
 * <p>
 * 专供前台创作页标签选择器：只带 tagId + tagName，不带热度字段（与 HotTagVo 区分，
 * 后者是热度榜、带 contentCount/hotScore；本 VO 是全量启用标签的选项列表，无热度语义）。
 */
public class TagOptionVo {

    private Long tagId;

    private String tagName;

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getTagName() {
        return tagName;
    }

    public void setTagName(String tagName) {
        this.tagName = tagName;
    }
}
