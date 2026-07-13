/**
 * mock 资源数据
 * ------------------------------------------------------------------
 * 资源推荐列表 + 首页推荐。真实接口：GET /resource/list（已落地）。
 * 分类：网站资源(WEBSITE) / 软件(SOFTWARE) / 脚本(SCRIPT) / 文档(DOCUMENT) / 工具链接(TOOL)。
 * 缺热门下载榜 + 分类聚合接口（见计划）。
 */

export interface MockResource {
  id: number
  title: string
  description: string
  category: 'WEBSITE' | 'SOFTWARE' | 'SCRIPT' | 'DOCUMENT' | 'TOOL'
  /** 网站类有 url + 缩略色块；文件类有 size */
  url?: string
  size?: string
  /** 缩略色块/图标色 */
  cover: string
  icon: 'link' | 'software' | 'script' | 'doc' | 'tool'
  author: string
  downloadCount: number
  views: number
  uploadTime: string
  /** 资源状态 */
  status: 'PUBLISHED' | 'PENDING_REVIEW' | 'ARCHIVED'
}

export const resources: MockResource[] = [
  {
    id: 1,
    title: 'Excalidraw 手绘风白板',
    description: '开源在线手绘风白板工具，适合画架构草图、流程图，支持协同。',
    category: 'WEBSITE',
    url: 'https://excalidraw.com',
    cover: 'linear-gradient(135deg,#2563eb,#0ea5e9)',
    icon: 'link',
    author: '周牧',
    downloadCount: 0,
    views: 3204,
    uploadTime: '2026-07-10',
    status: 'PUBLISHED',
  },
  {
    id: 2,
    title: 'IntelliJ IDEA 通用插件精选包',
    description: '实验室日常开发常用插件合集（代码格式化、Git 增强、AI 补全等），一键安装。',
    category: 'SOFTWARE',
    size: '24.6 MB',
    cover: 'linear-gradient(135deg,#6366f1,#a5b4fc)',
    icon: 'software',
    author: '何川',
    downloadCount: 892,
    views: 1542,
    uploadTime: '2026-07-08',
    status: 'PUBLISHED',
  },
  {
    id: 3,
    title: '一键部署 Spring Boot 的 shell 脚本',
    description: '含 jar 替换、优雅停机、日志切割、健康检查的通用部署脚本。',
    category: 'SCRIPT',
    size: '4.2 KB',
    cover: 'linear-gradient(135deg,#16a34a,#86efac)',
    icon: 'script',
    author: '林知夏',
    downloadCount: 678,
    views: 1024,
    uploadTime: '2026-07-06',
    status: 'PUBLISHED',
  },
  {
    id: 4,
    title: 'Spring Boot 3 官方文档中文精读笔记',
    description: '把官方文档重点章节整理成中文精读笔记，PDF 格式，含目录书签。',
    category: 'DOCUMENT',
    size: '8.1 MB',
    cover: 'linear-gradient(135deg,#f59e0b,#fcd34d)',
    icon: 'doc',
    author: '陈一帆',
    downloadCount: 2048,
    views: 3201,
    uploadTime: '2026-07-05',
    status: 'PUBLISHED',
  },
  {
    id: 5,
    title: 'Carbon 代码美化生成器',
    description: '把代码片段生成漂亮图片，适合写博客配图，支持多种主题。',
    category: 'WEBSITE',
    url: 'https://carbon.now.sh',
    cover: 'linear-gradient(135deg,#0f766e,#5eead4)',
    icon: 'link',
    author: '林溪',
    downloadCount: 0,
    views: 1876,
    uploadTime: '2026-07-04',
    status: 'PUBLISHED',
  },
  {
    id: 6,
    title: 'DBeaver 数据库通用客户端',
    description: '开源数据库客户端，支持 MySQL/PG/Redis 等多源连接，实验室常备。',
    category: 'SOFTWARE',
    size: '112 MB',
    cover: 'linear-gradient(135deg,#dc2626,#fca5a5)',
    icon: 'software',
    author: '叶禾',
    downloadCount: 534,
    views: 876,
    uploadTime: '2026-07-02',
    status: 'PUBLISHED',
  },
  {
    id: 7,
    title: 'Git 提交规范 + 分支管理速查表',
    description: 'Angular 提交规范 + Git Flow 分支模型速查 PDF，贴墙用。',
    category: 'DOCUMENT',
    size: '1.2 MB',
    cover: 'linear-gradient(135deg,#6366f1,#c7d2fe)',
    icon: 'doc',
    author: '安以',
    downloadCount: 1247,
    views: 2103,
    uploadTime: '2026-06-30',
    status: 'PUBLISHED',
  },
  {
    id: 8,
    title: '服务器初始化运维脚本集',
    description: '新装 CentOS/Ubuntu 一键初始化：换源、装 Docker、配防火墙、加用户。',
    category: 'SCRIPT',
    size: '12.8 KB',
    cover: 'linear-gradient(135deg,#2563eb,#93c5fd)',
    icon: 'script',
    author: '林知夏',
    downloadCount: 412,
    views: 689,
    uploadTime: '2026-06-28',
    status: 'PUBLISHED',
  },
  {
    id: 9,
    title: 'Regex101 正则可视化调试',
    description: '在线正则编写+解释+测试，支持多语言 Flavor，写正则必备。',
    category: 'WEBSITE',
    url: 'https://regex101.com',
    cover: 'linear-gradient(135deg,#f59e0b,#fed7aa)',
    icon: 'link',
    author: '周牧',
    downloadCount: 0,
    views: 1456,
    uploadTime: '2026-06-25',
    status: 'PUBLISHED',
  },
  {
    id: 10,
    title: 'Warp 现代终端',
    description: 'AI 加持的现代终端，块状命令、自动补全、协作分享。',
    category: 'SOFTWARE',
    size: '78 MB',
    cover: 'linear-gradient(135deg,#0ea5e9,#7dd3fc)',
    icon: 'software',
    author: '何川',
    downloadCount: 367,
    views: 942,
    uploadTime: '2026-06-22',
    status: 'PUBLISHED',
  },
  {
    id: 11,
    title: '实验室 Java 后端学习路线图',
    description: '从 Java 基础到 Spring Boot 到分布式，配套资源链接的高清路线图。',
    category: 'DOCUMENT',
    size: '3.4 MB',
    cover: 'linear-gradient(135deg,#16a34a,#bbf7d0)',
    icon: 'doc',
    author: '苏念',
    downloadCount: 1892,
    views: 2876,
    uploadTime: '2026-06-20',
    status: 'PUBLISHED',
  },
  {
    id: 12,
    title: 'Squoosh 图片压缩工具',
    description: 'Google 开源在线图片压缩，支持 WebP/AVIF 转换，质量与体积可视化对比。',
    category: 'WEBSITE',
    url: 'https://squoosh.app',
    cover: 'linear-gradient(135deg,#6366f1,#a5b4fc)',
    icon: 'tool',
    author: '林溪',
    downloadCount: 0,
    views: 1124,
    uploadTime: '2026-06-18',
    status: 'PENDING_REVIEW',
  },
]

export const getResourceById = (id: number): MockResource | undefined => resources.find((r) => r.id === id)

/** 资源分类元信息 */
export const resourceCategories = [
  { key: 'WEBSITE', label: '网站资源', icon: 'link' as const, color: '#2563eb' },
  { key: 'SOFTWARE', label: '软件资源', icon: 'software' as const, color: '#6366f1' },
  { key: 'SCRIPT', label: '脚本工具', icon: 'script' as const, color: '#16a34a' },
  { key: 'DOCUMENT', label: '文档资料', icon: 'doc' as const, color: '#f59e0b' },
  { key: 'TOOL', label: '工具链接', icon: 'tool' as const, color: '#0ea5e9' },
]
