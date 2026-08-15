/**
 * 文件作用：
 * 前台资源门户 VO 类型，与后端 com.knowhub.pojo.resource.vo.ResourcePortalVo /
 * ResourcePortalDetailVo / ResourceCategoryTreeVo 对齐。
 * 资源无 level 等级、无标签体系（与博客/文章门户差异点），故无 level/locked/tagIds 字段。
 * 互动计数（点赞/收藏/评分）后端不冗余主表，由 mapper inline 子查询回填，列表与详情均带回。
 * key 约定：noUncheckedIndexedAccess 下所有可选字段访问需 ?. 守卫。
 */

/** 前台资源列表项（搜索/推荐/相关推荐出参） */
export interface ResourcePortalRecord {
  resourceId: number
  authorId?: number
  /** 作者昵称（后端 join sys_user on author_id 带出） */
  authorNickname?: string
  /** 资源类型：FILE 文件 / LINK 链接 */
  resourceType: string
  resourceCategoryId?: number
  /** 分类名（join 带出；-1=其他时为 null，前端硬编码展示"其他"） */
  categoryName?: string | null
  title: string
  summary?: string
  /** LINK 类型的外链 URL */
  linkUrl?: string | null
  /** LINK 类型的图标 URL（可空，前端可运行时拼 favicon 兜底） */
  linkIcon?: string | null
  /** FILE 类型关联 file_object.object_id */
  fileObjectId?: number | null
  /** FILE 原始文件名（join file_object 带出） */
  originalName?: string | null
  /** FILE 文件大小字节（join file_object 带出） */
  contentLength?: number | null
  /** FILE MIME 类型（join file_object 带出） */
  contentType?: string | null
  publishTime?: string
  viewCount?: number
  downloadCount?: number
  /** 点赞数（inline 子查询回填，不冗余主表） */
  likeCount?: number
  /** 收藏数（inline 子查询回填，不冗余主表） */
  collectCount?: number
  /** 平均评分 0-5（保留 2 位小数，inline 子查询回填） */
  ratingAvg?: number
  /** 评分人数 */
  ratingCount?: number
}

/** 前台资源详情（继承列表字段 + description + 当前用户互动态）
 *  不含 downloadUrl：详情不下发下载链接（避免 permitAll 区触发 fileService checkOwnerOrAdmin 强转 CCE）。
 *  FILE 下载链接由前端点"下载资源"按钮时调 /authoring/resource/{id}/download 现取（带 download_count +1）。 */
export interface ResourcePortalDetailRecord extends ResourcePortalRecord {
  /** 详细说明（支持 Markdown，仅详情接口下发） */
  description?: string | null
  /** 当前用户是否已点赞（登录态回填，未登录为 null/false） */
  hasLiked?: boolean | null
  /** 当前用户是否已收藏（登录态回填，未登录为 null/false） */
  hasCollected?: boolean | null
  /** 当前用户评分 1-5（未评分为 0/null，登录态回填） */
  myScore?: number | null
  /** 评论区开关 1开/0关（详情接口带出，前端据此渲染评论区开关态） */
  commentEnabled?: number
  /** 评论精选开关 0=新评论直接可见 / 1=新评论仅发表人+作者可见，作者同意展示后他人可见 */
  commentCurated?: number
}

/** 搜索查询条件（GET /portal/resource/search） */
export interface ResourcePortalSearchQuery {
  /** 全文关键字（命中 ft_resource_title_summary_desc 全文索引，ngram 分词） */
  keyword?: string
  /** 资源类型过滤：FILE / LINK；不传不过滤 */
  resourceType?: 'FILE' | 'LINK'
  /** 分类 id 单选过滤（-1=其他）；不传不过滤（向后兼容，多选 resourceCategoryIds 优先） */
  resourceCategoryId?: number
  /** 分类 id 多选过滤（-1=其他作为合法元素参与 IN）；不传/空数组不过滤。
   *  前端 paramsSerializer 把数组 join 成逗号串（1,2,3），Spring MVC 顺序绑定 + String→List<Long> 转换接收。 */
  resourceCategoryIds?: number[]
  /** 排序：RELEVANCE 相关度 / HOT 热度 / LATEST 最新；缺省 HOT（资源推荐是主用例） */
  sort?: 'RELEVANCE' | 'HOT' | 'LATEST'
  pageNum?: number
  pageSize?: number
}

/**
 * 资源分类树节点（与后端 ResourceCategoryTreeVo 对齐，自关联树）。
 * 前端硬编码加一个"其他"虚拟节点（categoryId=-1），不在此树内。
 */
export interface ResourceCategoryTreeNode {
  categoryId: number
  parentId?: number
  categoryName: string
  sort?: number
  status?: number
  children?: ResourceCategoryTreeNode[]
}