/**
 * mock 系统公告数据
 * ------------------------------------------------------------------
 * 首页公告轮播用。真实接口：缺面向访客的公开公告接口（见计划）。
 */

export interface MockNotice {
  id: number
  title: string
  content: string
  /** 类型 */
  type: '系统' | '活动' | '维护' | '更新'
  publisher: string
  publishTime: string
  /** 是否置顶 */
  pinned?: boolean
}

export const notices: MockNotice[] = [
  {
    id: 1,
    title: 'knowhub v0.9.0 发布：文章管理模块 + 双重审核流上线',
    content: '本次更新落地文章管理模块（章节集合型），并引入文章系统审核 + 章节作者审核的双重审核流。详情见更新日志。',
    type: '更新',
    publisher: '系统管理员',
    publishTime: '2026-07-09',
    pinned: true,
  },
  {
    id: 2,
    title: '暑期实验室知识沉淀挑战赛启动',
    content: '7 月 15 日 - 8 月 15 日，发布博客/项目/资源累计积分可兑换实验室周边，欢迎参与。',
    type: '活动',
    publisher: '实验室负责人',
    publishTime: '2026-07-10',
    pinned: true,
  },
  {
    id: 3,
    title: 'AI 日报功能即将上线公测',
    content: '系统将于 7 月 15 日上线 AI 日报公测，每日早 7:00 自动生成当日知识库报道，首页可查看。',
    type: '系统',
    publisher: '系统管理员',
    publishTime: '2026-07-11',
  },
  {
    id: 4,
    title: '7 月 14 日 23:00-24:00 RustFS 存储维护通知',
    content: '为优化对象存储性能，将于 7 月 14 日 23:00-24:00 对 RustFS 进行维护，期间文件上传/下载可能短暂不可用。',
    type: '维护',
    publisher: '运维',
    publishTime: '2026-07-12',
  },
]
