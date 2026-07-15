/**
 * mock 项目数据
 * ------------------------------------------------------------------
 * 项目展示列表 + 首页推荐 + 详情。真实接口：GET /project/list、GET /project/{id}（已落地）。
 * 缺项目活跃度排序接口（见计划）。
 * 类型字典 COMPETITION/PRACTICE/OPS；状态 DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED/ARCHIVED；
 * 等级 L1 公开 / L2 内部 / L3 机密。
 */

export interface MockProjectFile {
  fileId: number
  name: string
  isDir: boolean
  children?: MockProjectFile[]
  /**
   * 【前端私加】后端 `ProjectFileTreeVo` 当前无时间字段（仅有 contentLength/contentType/businessType，
   * 见 doc/knowhub-api.md 项目文件树章节），mock 占位供前台文件页"上传时间"列展示。
   * 接口接入前需推动后端在 VO 回填时间字段（缺口 #11），接入后按回填字段名映射（暂定 uploadTime）。
   * 见 knowhub-ui README.dev.md §11.1。
   */
  uploadTime?: string
  /** 文件大小字节数（与后端 VO 的 contentLength 对齐，非私加） */
  contentLength?: number
}

export interface MockProjectMember {
  nickname: string
  role: 'LEADER' | 'MENTOR' | 'MEMBER'
  userId: number
}

export interface MockProject {
  projectId: number
  title: string
  summary: string
  description: string
  /**
   * 【前端私加】后端 `ProjectVo` 无封面字段——项目无封面是既定事实。
   * 纯前端 mock 占位，不应进 VO；项目卡片/详情已去封面卡片（见 ProjectCard.vue / detail.vue）。
   * 见 knowhub-ui README.dev.md §11.1。
   */
  cover: string
  /**
   * 【前端私加 / 列表图标占位】ProjectVo 无 icon 字段；
   * 仅项目列表卡顶部图标用，按 type 派生即可，不需后端补字段。
   */
  icon: 'trophy' | 'code' | 'flask' | 'graduation' | 'lightbulb'
  type: 'COMPETITION' | 'PRACTICE' | 'OPS'
  status: 'DRAFT' | 'PUBLISHED' | 'REVOKED' | 'PENDING_REVIEW' | 'REJECTED' | 'ARCHIVED'
  /** 等级 1/2/3（与后端 VO 的 level 对齐，非私加） */
  level: 1 | 2 | 3
  authorNickname: string
  members: MockProjectMember[]
  /**
   * 【前端私加】后端 `ProjectVo` 无 rating 字段（缺口 #12/#14）。
   * mock 占位供详情头星级展示，接入前需推动后端补评分聚合字段。
   */
  rating: number
  /**
   * 【前端私加】后端 `ProjectVo` 无 downloadCount 字段（缺口 #14）。
   * mock 占位供卡片/详情头"下载量"展示，接入前需推动后端补计数聚合。
   */
  downloadCount: number
  /** 是否当前最活跃（置顶徽标）（mock 派生，不进 VO） */
  hot?: boolean
  /** 比赛子表字段（type=COMPETITION 时，与后端子表对齐） */
  competition?: {
    name: string
    awardLevel: string
    time: string
  }
  /** 版本说明（详情页本轮已去掉版本说明卡，字段保留供后续可能恢复，暂不展示） */
  versions: { ver: string; note: string; time: string }[]
  /** 文件树 */
  files: MockProjectFile[]
  updateTime: string
}

export const projects: MockProject[] = [
  {
    projectId:1,
    title: 'knowhub 综合知识库博客系统',
    summary: '基于 Spring Boot 3 + Vue 3 的实验室知识沉淀与项目展示平台，含博客/项目/资源/审核四大业务模块。',
    description:
      '## 项目简介\n\nknowhub（知枢）是面向高校学生实验室、中小企业等组织场景的综合知识库博客系统，在 rookie 后台管理基础框架之上二次开发。\n\n## 技术栈\n\n- 后端：Java 17 + Spring Boot 3.4 + MyBatis + Redis + RustFS\n- 前端：Vue 3 + TypeScript + Vite + Element Plus\n- 存储：MySQL（正文）+ RustFS（对象存储）\n\n## 核心模块\n\n1. 博客/标签/文件模块\n2. 项目管理（GitHub 式文件树 + 分等级权限）\n3. 资源推荐（网站资源 + 文件资源）\n4. 内容审核范式（状态机 + 回避 + 流水 + 对账）\n\n## 状态\n\n当前在研，已落地 7 个业务模块，AI 日报与插件市场为下一阶段。',
    cover: 'linear-gradient(135deg,#2563eb,#0ea5e9)',
    icon: 'code',
    type: 'COMPETITION',
    status: 'PUBLISHED',
    level: 2,
    authorNickname: '陈一帆',
    members: [
      { nickname: '陈一帆', role: 'LEADER', userId: 2 },
      { nickname: '苏念', role: 'MENTOR', userId: 3 },
      { nickname: '何川', role: 'MEMBER', userId: 6 },
      { nickname: '周牧', role: 'MEMBER', userId: 4 },
      { nickname: '林溪', role: 'MEMBER', userId: 1 },
    ],
    rating: 4.9,
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
        fileId: 1,
        name: 'knowhub-backend',
        isDir: true,
        children: [
          { fileId:2, name: 'rookie-admin', isDir: true, children: [{ fileId:3, name: 'Application.java', isDir: false, uploadTime: '2026-07-09', contentLength: 2160 }] },
          { fileId:4, name: 'knowhub-blog', isDir: true, children: [
            { fileId:5, name: 'BlogController.java', isDir: false, uploadTime: '2026-07-09', contentLength: 3842 },
            { fileId:6, name: 'BlogServiceImpl.java', isDir: false, uploadTime: '2026-07-09', contentLength: 9568 },
          ] },
          { fileId:7, name: 'pom.xml', isDir: false, uploadTime: '2026-07-04', contentLength: 1120 },
        ],
      },
      {
        fileId: 8,
        name: 'knowhub-ui',
        isDir: true,
        children: [
          { fileId:9, name: 'src', isDir: true, children: [{ fileId:10, name: 'views', isDir: true }] },
          { fileId:11, name: 'package.json', isDir: false, uploadTime: '2026-07-08', contentLength: 2840 },
        ],
      },
      { fileId:12, name: 'README.md', isDir: false, uploadTime: '2026-07-04', contentLength: 8560 },
      { fileId:13, name: 'knowhub-source.zip', isDir: false, uploadTime: '2026-07-09', contentLength: 4_820_000 },
    ],
    updateTime: '2026-07-09 18:30',
  },
  {
    projectId:2,
    title: '分布式任务调度框架 mini-scheduler',
    summary: '基于 Redis 实现的轻量分布式任务调度，支持分片、失败重试、负载均衡，附可视化控制台。',
    description: '## 背景\n\n实验室多个项目都需要分布式调度，直接上 XXL-JOB 太重，于是造了个迷你轮子。\n\n## 特性\n\n- Redis 分布式锁防重复执行\n- 分片广播\n- 失败重试与死信\n- 控制台可视化',
    cover: 'linear-gradient(135deg,#6366f1,#a5b4fc)',
    icon: 'lightbulb',
    type: 'PRACTICE',
    status: 'PUBLISHED',
    level: 1,
    authorNickname: '何川',
    members: [
      { nickname: '何川', role: 'LEADER', userId: 6 },
      { nickname: '叶禾', role: 'MEMBER', userId: 5 },
    ],
    rating: 4.7,
    downloadCount: 892,
    versions: [
      { ver: 'v1.2.0', note: '增加分片广播', time: '2026-06-20' },
      { ver: 'v1.1.0', note: '失败重试与死信', time: '2026-06-05' },
    ],
    files: [
      { fileId:1, name: 'mini-scheduler.jar', isDir: false, uploadTime: '2026-06-20', contentLength: 1_280_000 },
      { fileId:2, name: 'README.md', isDir: false, uploadTime: '2026-06-20', contentLength: 4320 },
    ],
    updateTime: '2026-06-20 22:10',
  },
  {
    projectId:3,
    title: '实验室运维监控面板 lab-ops',
    summary: ' Prometheus + Grafana 二开，针对实验室机房服务器资源、容器、任务一体化监控告警。',
    description: '## 定位\n\n实验室机房多台服务器需要统一监控，基于 Prometheus + Grafana 二开定制面板。\n\n## 能力\n\n- CPU/内存/磁盘/网络\n- Docker 容器状态\n- 任务调度执行监控\n- 飞书告警',
    cover: 'linear-gradient(135deg,#16a34a,#86efac)',
    icon: 'flask',
    type: 'OPS',
    status: 'PUBLISHED',
    level: 2,
    authorNickname: '林知夏',
    members: [
      { nickname: '林知夏', role: 'LEADER', userId: 8 },
      { nickname: '安以', role: 'MEMBER', userId: 7 },
    ],
    rating: 4.5,
    downloadCount: 534,
    versions: [{ ver: 'v0.5.0', note: '飞书告警接入', time: '2026-06-12' }],
    files: [{ fileId:1, name: 'lab-ops-compose.zip', isDir: false, uploadTime: '2026-06-12', contentLength: 9_600_000 }],
    updateTime: '2026-06-12 14:00',
  },
  {
    projectId:4,
    title: '算法可视化学习平台 algo-viz',
    summary: '把常见算法（排序/图论/DP）做成可交互可视化，配合知识点讲解，备赛与教学两用。',
    description: '## 灵感\n\n备赛时发现纯文字讲算法很难理解，做成可视化动画后学弟学妹上手快很多。\n\n## 内容\n\n- 十大排序算法动画\n- 图论 BFS/DFS/Dijkstra\n- 经典 DP 状态转移可视化',
    cover: 'linear-gradient(135deg,#f59e0b,#fcd34d)',
    icon: 'graduation',
    type: 'COMPETITION',
    status: 'PUBLISHED',
    level: 1,
    authorNickname: '安以',
    members: [
      { nickname: '安以', role: 'LEADER', userId: 7 },
      { nickname: '周牧', role: 'MEMBER', userId: 4 },
      { nickname: '林溪', role: 'MEMBER', userId: 1 },
    ],
    rating: 4.8,
    downloadCount: 1024,
    hot: true,
    competition: { name: '蓝桥杯软件赛', awardLevel: '国家级二等奖', time: '2026-05' },
    versions: [
      { ver: 'v2.0.0', note: '图论算法可视化', time: '2026-05-18' },
      { ver: 'v1.0.0', note: '排序算法上线', time: '2026-04-01' },
    ],
    files: [
      { fileId:1, name: 'algo-viz.zip', isDir: false, uploadTime: '2026-05-18', contentLength: 6_400_000 },
      { fileId:2, name: 'README.md', isDir: false, uploadTime: '2026-05-18', contentLength: 5120 },
    ],
    updateTime: '2026-05-18 16:45',
  },
  {
    projectId:5,
    title: 'Markdown 协作编辑器 co-md',
    summary: '基于 Yjs 的实时协同 Markdown 编辑器，支持多人光标、版本快照、导出多格式。',
    description: '## 协同\n\n基于 Yjs CRDT 实现实时协同，多人光标互不干扰。\n\n## 导出\n\n支持导出 HTML/PDF/Word。',
    cover: 'linear-gradient(135deg,#0f766e,#5eead4)',
    icon: 'lightbulb',
    type: 'PRACTICE',
    status: 'PENDING_REVIEW',
    level: 1,
    authorNickname: '周牧',
    members: [{ nickname: '周牧', role: 'LEADER', userId: 4 }],
    rating: 4.3,
    downloadCount: 312,
    versions: [{ ver: 'v0.8.0', note: '协同光标', time: '2026-07-08' }],
    files: [{ fileId:1, name: 'co-md.zip', isDir: false, uploadTime: '2026-07-08', contentLength: 3_200_000 }],
    updateTime: '2026-07-08 11:20',
  },
  {
    projectId:6,
    title: '实验室设备借用系统 lab-borrow',
    summary: '扫码借还、库存盘点、到期提醒，解决实验室设备流失与管理混乱问题。',
    description: '## 痛点\n\n实验室设备借用靠纸笔登记，常丢常错。\n\n## 方案\n\n扫码借还 + 到期提醒 + 盘点报表。',
    cover: 'linear-gradient(135deg,#dc2626,#fca5a5)',
    icon: 'flask',
    type: 'OPS',
    status: 'ARCHIVED',
    level: 2,
    authorNickname: '叶禾',
    members: [{ nickname: '叶禾', role: 'LEADER', userId: 5 }],
    rating: 4.2,
    downloadCount: 178,
    versions: [{ ver: 'v1.0.0', note: '首版上线', time: '2026-03-15' }],
    files: [{ fileId:1, name: 'lab-borrow.zip', isDir: false, uploadTime: '2026-03-15', contentLength: 2_080_000 }],
    updateTime: '2026-03-15 10:00',
  },
]

export const getProjectById = (id: number): MockProject | undefined => projects.find((p) => p.projectId === id)
