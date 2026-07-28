/**
 * 文件作用：
 * 浏览历史类型，与后端 com.knowhub.pojo.vo.ViewHistoryVo 对齐。
 * 前台 /history 页消费，join blog/article/resource 带出标题封面。
 */

/** 浏览历史列表项（当前用户历史分页） */
export interface ViewHistoryRecord {
  viewId: number
  /** 业务类型 BLOG/ARTICLE/CHAPTER/RESOURCE */
  bizType: 'BLOG' | 'ARTICLE' | 'CHAPTER' | 'RESOURCE'
  /** 业务ID */
  bizId: number
  /** 内容标题（blog.title / article.title；章节取所属 article 标题） */
  title?: string
  /** 封面图对象key */
  coverObjectKey?: string
  /** 作者昵称（blog 取 create_by(username)，article/resource join sys_user） */
  authorName?: string
  /** 首次浏览时间 */
  viewTime?: string
  /** 最近浏览时间 */
  lastViewTime?: string
  /** 累计浏览次数 */
  viewCount?: number
}
