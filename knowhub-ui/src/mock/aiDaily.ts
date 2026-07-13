/**
 * mock AI 日报数据
 * ------------------------------------------------------------------
 * 首页 AI 日报卡片预留 + 独立日报页。真实接口：未实施（见计划 §9）。
 */

export interface MockAiDaily {
  id: number
  date: string
  title: string
  summary: string
  /** 涵盖主题 */
  topics: string[]
  /** 阅读量 */
  readCount: number
}

export const aiDailyList: MockAiDaily[] = [
  {
    id: 1,
    date: '2026-07-12',
    title: '知枢日报 · 0712：文章管理双重审核流落地、暑期挑战赛启动',
    summary: '今日知识库重点：文章管理模块引入系统+章节作者双重审核流；暑期知识沉淀挑战赛启动；AI 日报功能即将公测。',
    topics: ['文章管理', '审核流', '暑期活动', 'AI日报'],
    readCount: 412,
  },
  {
    id: 2,
    date: '2026-07-11',
    title: '知枢日报 · 0711：博客权限模型收紧、资源模块优化',
    summary: '博客编辑权限从分等级收紧为作者+超管；资源推荐模块补充分类聚合能力。',
    topics: ['博客权限', '资源模块'],
    readCount: 387,
  },
  {
    id: 3,
    date: '2026-07-10',
    title: '知枢日报 · 0710：项目管理文件树设计、对账任务增强',
    summary: 'GitHub 式文件树 project_file 与 file_object 分工落地；审核对账任务覆盖项目模块。',
    topics: ['项目管理', '文件树', '对账任务'],
    readCount: 356,
  },
]

export const latestAiDaily: MockAiDaily = aiDailyList[0] ?? {
  id: 0,
  date: '2026-07-12',
  title: '知枢日报 · 即将上线',
  summary: 'AI 日报功能即将公测，每日早 7:00 自动生成当日知识库报道。',
  topics: ['AI日报'],
  readCount: 0,
}
