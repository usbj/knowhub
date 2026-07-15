/**
 * mock 资源数据
 * ------------------------------------------------------------------
 * 资源推荐列表 + 首页推荐。真实接口：GET /resource/list（已落地）。
 * 分类：网站资源(WEBSITE) / 软件(SOFTWARE) / 脚本(SCRIPT) / 文档(DOCUMENT) / 工具链接(TOOL)。
 * 缺热门下载榜 + 分类聚合接口（见计划）。
 */

export interface MockResource {
  resourceId: number
  title: string
  description: string
  category: 'WEBSITE' | 'SOFTWARE' | 'SCRIPT' | 'DOCUMENT' | 'TOOL'
  /** 网站类有 linkUrl + 缩略色块；文件类有 contentLength */
  linkUrl?: string
  /** 文件大小字节数（与后端 ResourceVo.contentLength 对齐，非私加） */
  contentLength?: number
  /**
   * 【前端私加】后端 ResourceVo 无 cover 字段（有 fileObjectId/linkIcon，无封面），
   * mock 用缩略色块/图标色占位，接入时按实际 VO 字段映射。
   */
  cover: string
  /** 链接图标（与后端 ResourceVo.linkIcon 对齐，非私加） */
  linkIcon: 'link' | 'software' | 'script' | 'doc' | 'tool'
  /** 作者昵称（与后端 ResourceVo.authorNickname 对齐，非私加） */
  authorNickname: string
  /** 下载量（与后端 ResourceVo.downloadCount 对齐，非私加） */
  downloadCount: number
  /**
   * 【前端私加】后端 ResourceVo 无 viewCount（counts 仅有 likeCount/collectCount/downloadCount），
   * mock 占位供卡片"访问"展示，接入前需推动后端补计数聚合或用 downloadCount 替代。
   */
  views: number
  /** 创建/上传时间（与后端 ResourceVo.createTime 对齐，非私加） */
  createTime: string
  /** 资源状态 */
  status: 'PUBLISHED' | 'PENDING_REVIEW' | 'ARCHIVED'
}

export const resources: MockResource[] = [
  {
    resourceId: 1,
    title: 'Excalidraw 手绘风白板',
    description: '开源在线手绘风白板工具，适合画架构草图、流程图，支持协同。',
    category: 'WEBSITE',
    linkUrl: 'https://excalidraw.com',
    cover: 'linear-gradient(135deg,#2563eb,#0ea5e9)',
    linkIcon: 'link',
    authorNickname: '周牧',
    downloadCount: 0,
    views: 3204,
    createTime: '2026-07-10',
    status: 'PUBLISHED',
  },
  {
    resourceId: 2,
    title: 'IntelliJ IDEA 通用插件精选包',
    description: '实验室日常开发常用插件合集（代码格式化、Git 增强、AI 补全等），一键安装。',
    category: 'SOFTWARE',
    contentLength: 25_798_758, // ~24.6 MB
    cover: 'linear-gradient(135deg,#6366f1,#a5b4fc)',
    linkIcon: 'software',
    authorNickname: '何川',
    downloadCount: 892,
    views: 1542,
    createTime: '2026-07-08',
    status: 'PUBLISHED',
  },
  {
    resourceId: 3,
    title: '一键部署 Spring Boot 的 shell 脚本',
    description: '含 jar 替换、优雅停机、日志切割、健康检查的通用部署脚本。',
    category: 'SCRIPT',
    contentLength: 4301, // ~4.2 KB
    cover: 'linear-gradient(135deg,#16a34a,#86efac)',
    linkIcon: 'script',
    authorNickname: '林知夏',
    downloadCount: 678,
    views: 1024,
    createTime: '2026-07-06',
    status: 'PUBLISHED',
  },
  {
    resourceId: 4,
    title: 'Spring Boot 3 官方文档中文精读笔记',
    description: '把官方文档重点章节整理成中文精读笔记，PDF 格式，含目录书签。',
    category: 'DOCUMENT',
    contentLength: 8_519_849, // ~8.1 MB
    cover: 'linear-gradient(135deg,#f59e0b,#fcd34d)',
    linkIcon: 'doc',
    authorNickname: '陈一帆',
    downloadCount: 2048,
    views: 3201,
    createTime: '2026-07-05',
    status: 'PUBLISHED',
  },
  {
    resourceId: 5,
    title: 'Carbon 代码美化生成器',
    description: '把代码片段生成漂亮图片，适合写博客配图，支持多种主题。',
    category: 'WEBSITE',
    linkUrl: 'https://carbon.now.sh',
    cover: 'linear-gradient(135deg,#0f766e,#5eead4)',
    linkIcon: 'link',
    authorNickname: '林溪',
    downloadCount: 0,
    views: 1876,
    createTime: '2026-07-04',
    status: 'PUBLISHED',
  },
  {
    resourceId: 6,
    title: 'DBeaver 数据库通用客户端',
    description: '开源数据库客户端，支持 MySQL/PG/Redis 等多源连接，实验室常备。',
    category: 'SOFTWARE',
    contentLength: 117_440_512, // ~112 MB
    cover: 'linear-gradient(135deg,#dc2626,#fca5a5)',
    linkIcon: 'software',
    authorNickname: '叶禾',
    downloadCount: 534,
    views: 876,
    createTime: '2026-07-02',
    status: 'PUBLISHED',
  },
  {
    resourceId: 7,
    title: 'Git 提交规范 + 分支管理速查表',
    description: 'Angular 提交规范 + Git Flow 分支模型速查 PDF，贴墙用。',
    category: 'DOCUMENT',
    contentLength: 1_258_291, // ~1.2 MB
    cover: 'linear-gradient(135deg,#6366f1,#c7d2fe)',
    linkIcon: 'doc',
    authorNickname: '安以',
    downloadCount: 1247,
    views: 2103,
    createTime: '2026-06-30',
    status: 'PUBLISHED',
  },
  {
    resourceId: 8,
    title: '服务器初始化运维脚本集',
    description: '新装 CentOS/Ubuntu 一键初始化：换源、装 Docker、配防火墙、加用户。',
    category: 'SCRIPT',
    contentLength: 13_107, // ~12.8 KB
    cover: 'linear-gradient(135deg,#2563eb,#93c5fd)',
    linkIcon: 'script',
    authorNickname: '林知夏',
    downloadCount: 412,
    views: 689,
    createTime: '2026-06-28',
    status: 'PUBLISHED',
  },
  {
    resourceId: 9,
    title: 'Regex101 正则可视化调试',
    description: '在线正则编写+解释+测试，支持多语言 Flavor，写正则必备。',
    category: 'WEBSITE',
    linkUrl: 'https://regex101.com',
    cover: 'linear-gradient(135deg,#f59e0b,#fed7aa)',
    linkIcon: 'link',
    authorNickname: '周牧',
    downloadCount: 0,
    views: 1456,
    createTime: '2026-06-25',
    status: 'PUBLISHED',
  },
  {
    resourceId: 10,
    title: 'Warp 现代终端',
    description: 'AI 加持的现代终端，块状命令、自动补全、协作分享。',
    category: 'SOFTWARE',
    contentLength: 81_738_137, // ~78 MB
    cover: 'linear-gradient(135deg,#0ea5e9,#7dd3fc)',
    linkIcon: 'software',
    authorNickname: '何川',
    downloadCount: 367,
    views: 942,
    createTime: '2026-06-22',
    status: 'PUBLISHED',
  },
  {
    resourceId: 11,
    title: '实验室 Java 后端学习路线图',
    description: '从 Java 基础到 Spring Boot 到分布式，配套资源链接的高清路线图。',
    category: 'DOCUMENT',
    contentLength: 3_565_325, // ~3.4 MB
    cover: 'linear-gradient(135deg,#16a34a,#bbf7d0)',
    linkIcon: 'doc',
    authorNickname: '苏念',
    downloadCount: 1892,
    views: 2876,
    createTime: '2026-06-20',
    status: 'PUBLISHED',
  },
  {
    resourceId: 12,
    title: 'Squoosh 图片压缩工具',
    description: 'Google 开源在线图片压缩，支持 WebP/AVIF 转换，质量与体积可视化对比。',
    category: 'WEBSITE',
    linkUrl: 'https://squoosh.app',
    cover: 'linear-gradient(135deg,#6366f1,#a5b4fc)',
    linkIcon: 'tool',
    authorNickname: '林溪',
    downloadCount: 0,
    views: 1124,
    createTime: '2026-06-18',
    status: 'PENDING_REVIEW',
  },
]

export const getResourceById = (resourceId: number): MockResource | undefined =>
  resources.find((r) => r.resourceId === resourceId)

/** 资源分类元信息 */
export const resourceCategories = [
  { key: 'WEBSITE', label: '网站资源', linkIcon: 'link' as const, color: '#2563eb' },
  { key: 'SOFTWARE', label: '软件资源', linkIcon: 'software' as const, color: '#6366f1' },
  { key: 'SCRIPT', label: '脚本工具', linkIcon: 'script' as const, color: '#16a34a' },
  { key: 'DOCUMENT', label: '文档资料', linkIcon: 'doc' as const, color: '#f59e0b' },
  { key: 'TOOL', label: '工具链接', linkIcon: 'tool' as const, color: '#0ea5e9' },
]