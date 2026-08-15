/**
 * 文件作用：
 * 前台项目门户 VO 类型，与后端 com.knowhub.pojo.project.vo.ProjectPortalVo / ProjectPortalDetailVo /
 * ProjectFileVo 对齐。
 * 前台只消费展示子集（列表不含 description；详情含 description + 锁态 + canDownload）。
 * 项目无标签体系，故无 tagIds/tagNames（与 BlogPortalRecord 差异点）。
 * 项目无 like/collect 互动接口，但主表已建 like_count/collect_count 列（预留），照后端 VO 回显（搜索/推荐卡片暂直接展示 downloadCount + viewCount）。
 */

/** 前台项目列表项（搜索/推荐/相关推荐出参） */
export interface ProjectPortalRecord {
  projectId: number
  title: string
  /** 项目类型：COMPETITION/PRACTICE/OPS（字典 project_type） */
  type?: string
  /** 项目等级 1/2/3（同 type 一道按 userViewLevel 过滤，越级永不下发） */
  level?: number
  summary?: string
  authorId?: number
  /** 作者昵称（后端 join sys_user on author_id 带出） */
  authorNickname?: string
  publishTime?: string
  viewCount?: number
  likeCount?: number
  collectCount?: number
  downloadCount?: number
}

/** 前台项目详情（继承列表字段 + 正文 + 锁态 + 下载权限态） */
export interface ProjectPortalDetailRecord extends ProjectPortalRecord {
  /** 介绍正文（越级锁态时为 null，不下发） */
  description?: string | null
  /** 是否越级锁态：true=无权看完整正文，只给元数据 */
  locked?: boolean
  /** 锁态原因提示（如"需 L2 权限查看完整内容"，正常态为 null） */
  lockReason?: string | null
  /** 当前用户对该项目的下载权限态（前端据此控制文件树下载按钮显隐） */
  canDownload?: boolean
  /** 当前用户是否已收藏（登录态回填，未登录为 null；项目无点赞链路，仅收藏） */
  hasCollected?: boolean | null
  /** 评论区开关 1开/0关（详情接口带出，前端据此渲染评论区开关态） */
  commentEnabled?: number
  /** 评论精选开关 0=新评论直接可见 / 1=新评论仅发表人+作者可见，作者同意展示后他人可见 */
  commentCurated?: number
}

/**
 * 项目文件树节点（扁平带 parentId，前端按 parentId 内存组装树渲染）。
 * 与后端 ProjectFileVo 对齐：目录 objectId=null、文件叶子关联 file_object 元数据（originalName/contentLength/contentType/businessType）。
 */
export interface ProjectFileRecord {
  fileId: number
  projectId: number
  parentId?: number
  name: string
  /** 1=目录(objectId=null) / 0=文件(关联 file_object) */
  isDir: number
  /** 关联 file_object.object_id，目录=null */
  objectId?: number | null
  sort?: number
  /** 文件原始名（join file_object 带出，目录为 null） */
  originalName?: string | null
  /** 文件大小字节（join file_object 带出） */
  contentLength?: number | null
  /** 文件 MIME 类型（join file_object 带出） */
  contentType?: string | null
  /** 文件业务类型（PROJECT_SRC/PKG/DOC，供前端分组展示） */
  businessType?: string | null
  createTime?: string
  updateTime?: string
}

/** 搜索查询条件（GET /portal/project/search） */
export interface ProjectPortalSearchQuery {
  keyword?: string
  /** 项目类型过滤 COMPETITION/PRACTICE/OPS */
  type?: string
  /** 项目等级过滤 1/2/3（通常不传——前台按 userViewLevel 自动收窄，不暴露等级筛选项给无权者） */
  level?: number
  /** 排序：HOT 热度（默认）/ LATEST 最新 */
  sort?: 'HOT' | 'LATEST'
  /**
   * 分页页码（后端 PageUtil.startPage 从 HTTP 请求读，默认 1）。
   * 前台分页需显式带，与 BlogPortalSearchQuery/ArticlePortalSearchQuery 同口径。
   */
  pageNum?: number
  /** 分页每页条数（后端 PageUtil 从 HTTP 请求读，默认 10） */
  pageSize?: number
}

/**
 * 前台项目成员记录（公开详情页右栏"参与人员"用，与后端 ProjectMemberVo 对齐但裁剪内部权限态）。
 * memberRole: LEADER/MENTOR/MEMBER（字典 project_member_role）。
 * 公开接口不下发 canView/canDownload/canEdit（service 层置 null，不暴露内部权限给访客）。
 */
export interface ProjectMemberRecord {
  memberId?: number
  projectId?: number
  userId: number
  memberRole: string
  nickname?: string
  username?: string
  /** 前台公开接口不下发，恒 null（参与人员展示不需要内部权限态） */
  canView?: number | null
  canDownload?: number | null
  canEdit?: number | null
  createTime?: string
}