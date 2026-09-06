/**
 * 文件作用：
 * 前台标签类型，与后端 com.knowhub.pojo.entity.Tag / HotTagVo 对齐。
 * 标签是博客和文章共用（blog_tag + article_tag），前台标签云与热度榜消费。
 */

/** 受控标签（GET /tag/list 返回，不分页） */
export interface TagRecord {
  tagId: number
  tagName: string
  description?: string
  sort?: number
  status?: number
}

/** 标签热度榜项（GET /portal/tag/hot） */
export interface HotTagRecord {
  tagId: number
  tagName: string
  /** 关联的已发布+公开内容数（blog + article 合计） */
  contentCount?: number
  /** 热度分 */
  hotScore?: number
}
