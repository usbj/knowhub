/**
 * mock 项目数据
 * ------------------------------------------------------------------
 * 项目展示列表 + 首页推荐 + 详情。真实接口：GET /project/list、GET /project/{id}（已落地）。
 * 缺项目活跃度排序接口（见计划）。
 * 类型字典 COMPETITION/PRACTICE/OPS；状态 DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED/ARCHIVED；
 * 等级 L1 公开 / L2 内部 / L3 机密。
 */

export interface MockProjectFile {
  id: number
  name: string
  isDir: boolean
  children?: MockProjectFile[]
}

export interface MockProjectMember {
  name: string
  role: 'LEADER' | 'MENTOR' | 'MEMBER'
  userId: number
}

export interface MockProject {
  id: number
  title: string
  summary: string
  description: string
  /** 封面色块 */
  cover: string
  /** 项目图标名（KhIcon） */
  icon: 'trophy' | 'code' | 'flask' | 'graduation' | 'lightbulb'
  type: 'COMPETITION' | 'PRACTICE' | 'OPS'
  status: 'DRAFT' | 'PUBLISHED' | 'REVOKED' | 'PENDING_REVIEW' | 'REJECTED' | 'ARCHIVED'
  /** 等级 1/2/3 */
  level: 1 | 2 | 3
  leader: string
  members: MockProjectMember[]
  rating: number
  /** 活跃度（综合下载/更新/成员） */
  activity: number
  downloadCount: number
  /** 是否当前最活跃（置顶徽标） */
  hot?: boolean
  /** 比赛子表字段（type=COMPETITION 时） */
  competition?: {
    name: string
    awardLevel: string
    time: string
  }
  /** 版本说明 */
  versions: { ver: string; note: string; time: string }[]
  /** 文件树 */
  files: MockProjectFile[]
  updateTime: string
}

export const projects: MockProject[] = [
  {
    id: 1,
    title: 'knowhub 综合知识库博客系统',
    summary: '基于 Spring Boot 3 + Vue 3 的实验室知识沉淀与项目展示平台，含博客/项目/资源/审核四大业务模块。',
    description:
      '## 项目简介\n\nknowhub（知枢）是面向高校学生实验室、中小企业等组织场景的综合知识库博客系统，在 rookie 后台管理基础框架之上二次开发。\n\n## 技术栈\n\n- 后端：Java 17 + Spring Boot 3.4 + MyBatis + Redis + RustFS\n- 前端：Vue 3 + TypeScript + Vite + Element Plus\n- 存储：MySQL（正文）+ RustFS（对象存储）\n\n## 核心模块\n\n1. 博客/标签/文件模块\n2. 项目管理（GitHub 式文件树 + 分等级权限）\n3. 资源推荐（网站资源 + 文件资源）\n4. 内容审核范式（状态机 + 回避 + 流水 + 对账）\n\n## 状态\n\n当前在研，已落地 7 个业务模块，AI 日报与插件市场为下一阶段。',
    cover: 'linear-gradient(135deg,#2563eb,#0ea5e9)',
    icon: 'code',
    type: 'COMPETITION',
    status: 'PUBLISHED',
    level: 2,
    leader: '陈一帆',
    members: [
      { name: '陈一帆', role: 'LEADER', userId: 2 },
      { name: '苏念', role: 'MENTOR', userId: 3 },
      { name: '何川', role: 'MEMBER', userId: 6 },
      { name: '周牧', role: 'MEMBER', userId: 4 },
      { name: '林溪', role: 'MEMBER', userId: 1 },
    ],
    rating: 4.9,
    activity: 98,
    downloadCount: 1247,
    hot: true,
    competition: { name: '中国大学生计算机设计大赛', awardLevel: '省级一等奖', time: '2026-07' },
    versions: [
      { ver: 'v0.9.0', note: '完成文章管理模块 + 双重审核流', time: '2026-07-09' },
      { ver: 'v0.8.0', note: '项目管理模块落地（分等级权限 + 文件树）', time: '2026-07-07' },
      { ver: 'v0.7.0', note: '资源推荐模块 + 系统设置迁移', time: '2026-07-06' },
      { ver: 'v0.6.0', note: '博客审核流水 + 对账定时任务', time: '2026-07-04' },
    ],
    files: [
      {
        id: 1,
        name: 'knowhub-backend',
        isDir: true,
        children: [
          { id: 2, name: 'rookie-admin', isDir: true, children: [{ id: 3, name: 'Application.java', isDir: false }] },
          { id: 4, name: 'knowhub-blog', isDir: true, children: [{ id: 5, name: 'BlogController.java', isDir: false }, { id: 6, name: 'BlogServiceImpl.java', isDir: false }] },
          { id: 7, name: 'pom.xml', isDir: false },
        ],
      },
      {
        id: 8,
        name: 'knowhub-ui',
        isDir: true,
        children: [
          { id: 9, name: 'src', isDir: true, children: [{ id: 10, name: 'views', isDir: true }] },
          { id: 11, name: 'package.json', isDir: false },
        ],
      },
      { id: 12, name: 'README.md', isDir: false },
      { id: 13, name: 'knowhub-source.zip', isDir: false },
    ],
    updateTime: '2026-07-09 18:30',
  },
  {
    id: 2,
    title: '分布式任务调度框架 mini-scheduler',
    summary: '基于 Redis 实现的轻量分布式任务调度，支持分片、失败重试、负载均衡，附可视化控制台。',
    description: '## 背景\n\n实验室多个项目都需要分布式调度，直接上 XXL-JOB 太重，于是造了个迷你轮子。\n\n## 特性\n\n- Redis 分布式锁防重复执行\n- 分片广播\n- 失败重试与死信\n- 控制台可视化',
    cover: 'linear-gradient(135deg,#6366f1,#a5b4fc)',
    icon: 'lightbulb',
    type: 'PRACTICE',
    status: 'PUBLISHED',
    level: 1,
    leader: '何川',
    members: [
      { name: '何川', role: 'LEADER', userId: 6 },
      { name: '叶禾', role: 'MEMBER', userId: 5 },
    ],
    rating: 4.7,
    activity: 86,
    downloadCount: 892,
    versions: [
      { ver: 'v1.2.0', note: '增加分片广播', time: '2026-06-20' },
      { ver: 'v1.1.0', note: '失败重试与死信', time: '2026-06-05' },
    ],
    files: [
      { id: 1, name: 'mini-scheduler.jar', isDir: false },
      { id: 2, name: 'README.md', isDir: false },
    ],
    updateTime: '2026-06-20 22:10',
  },
  {
    id: 3,
    title: '实验室运维监控面板 lab-ops',
    summary: ' Prometheus + Grafana 二开，针对实验室机房服务器资源、容器、任务一体化监控告警。',
    description: '## 定位\n\n实验室机房多台服务器需要统一监控，基于 Prometheus + Grafana 二开定制面板。\n\n## 能力\n\n- CPU/内存/磁盘/网络\n- Docker 容器状态\n- 任务调度执行监控\n- 飞书告警',
    cover: 'linear-gradient(135deg,#16a34a,#86efac)',
    icon: 'flask',
    type: 'OPS',
    status: 'PUBLISHED',
    level: 2,
    leader: '林知夏',
    members: [
      { name: '林知夏', role: 'LEADER', userId: 8 },
      { name: '安以', role: 'MEMBER', userId: 7 },
    ],
    rating: 4.5,
    activity: 72,
    downloadCount: 534,
    versions: [{ ver: 'v0.5.0', note: '飞书告警接入', time: '2026-06-12' }],
    files: [{ id: 1, name: 'lab-ops-compose.zip', isDir: false }],
    updateTime: '2026-06-12 14:00',
  },
  {
    id: 4,
    title: '算法可视化学习平台 algo-viz',
    summary: '把常见算法（排序/图论/DP）做成可交互可视化，配合知识点讲解，备赛与教学两用。',
    description: '## 灵感\n\n备赛时发现纯文字讲算法很难理解，做成可视化动画后学弟学妹上手快很多。\n\n## 内容\n\n- 十大排序算法动画\n- 图论 BFS/DFS/Dijkstra\n- 经典 DP 状态转移可视化',
    cover: 'linear-gradient(135deg,#f59e0b,#fcd34d)',
    icon: 'graduation',
    type: 'COMPETITION',
    status: 'PUBLISHED',
    level: 1,
    leader: '安以',
    members: [
      { name: '安以', role: 'LEADER', userId: 7 },
      { name: '周牧', role: 'MEMBER', userId: 4 },
      { name: '林溪', role: 'MEMBER', userId: 1 },
    ],
    rating: 4.8,
    activity: 91,
    downloadCount: 1024,
    hot: true,
    competition: { name: '蓝桥杯软件赛', awardLevel: '国家级二等奖', time: '2026-05' },
    versions: [
      { ver: 'v2.0.0', note: '图论算法可视化', time: '2026-05-18' },
      { ver: 'v1.0.0', note: '排序算法上线', time: '2026-04-01' },
    ],
    files: [{ id: 1, name: 'algo-viz.zip', isDir: false }, { id: 2, name: 'README.md', isDir: false }],
    updateTime: '2026-05-18 16:45',
  },
  {
    id: 5,
    title: 'Markdown 协作编辑器 co-md',
    summary: '基于 Yjs 的实时协同 Markdown 编辑器，支持多人光标、版本快照、导出多格式。',
    description: '## 协同\n\n基于 Yjs CRDT 实现实时协同，多人光标互不干扰。\n\n## 导出\n\n支持导出 HTML/PDF/Word。',
    cover: 'linear-gradient(135deg,#0f766e,#5eead4)',
    icon: 'lightbulb',
    type: 'PRACTICE',
    status: 'PENDING_REVIEW',
    level: 1,
    leader: '周牧',
    members: [{ name: '周牧', role: 'LEADER', userId: 4 }],
    rating: 4.3,
    activity: 58,
    downloadCount: 312,
    versions: [{ ver: 'v0.8.0', note: '协同光标', time: '2026-07-08' }],
    files: [{ id: 1, name: 'co-md.zip', isDir: false }],
    updateTime: '2026-07-08 11:20',
  },
  {
    id: 6,
    title: '实验室设备借用系统 lab-borrow',
    summary: '扫码借还、库存盘点、到期提醒，解决实验室设备流失与管理混乱问题。',
    description: '## 痛点\n\n实验室设备借用靠纸笔登记，常丢常错。\n\n## 方案\n\n扫码借还 + 到期提醒 + 盘点报表。',
    cover: 'linear-gradient(135deg,#dc2626,#fca5a5)',
    icon: 'flask',
    type: 'OPS',
    status: 'ARCHIVED',
    level: 2,
    leader: '叶禾',
    members: [{ name: '叶禾', role: 'LEADER', userId: 5 }],
    rating: 4.2,
    activity: 24,
    downloadCount: 178,
    versions: [{ ver: 'v1.0.0', note: '首版上线', time: '2026-03-15' }],
    files: [{ id: 1, name: 'lab-borrow.zip', isDir: false }],
    updateTime: '2026-03-15 10:00',
  },
]

export const getProjectById = (id: number): MockProject | undefined => projects.find((p) => p.id === id)
