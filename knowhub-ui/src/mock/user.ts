/**
 * mock 用户数据（静态 demo，不连后端）
 * ------------------------------------------------------------------
 * 当前登录用户 + 活跃成员列表。真实接口待定（个人中心聚合接口缺失，见计划文档）。
 */

export interface MockUser {
  id: number
  name: string
  username: string
  avatar?: string
  role: string
  bio: string
  stats: {
    blogs: number
    projects: number
    resources: number
    likes: number
    collections: number
  }
}

/** 当前登录用户（个人中心展示用） */
export const currentUser: MockUser = {
  id: 1,
  name: '林溪',
  username: 'linxi',
  role: '实验室成员',
  bio: '大三 · 软件工程 · 关注分布式系统与知识沉淀，喜欢把踩过的坑写成笔记。',
  stats: { blogs: 24, projects: 6, resources: 18, likes: 342, collections: 87 },
}

/** 活跃成员（首页/项目参与者用） */
export const activeMembers: MockUser[] = [
  { id: 2, name: '陈一帆', username: 'chenyf', role: '实验室负责人', bio: '研三 · 分布式系统方向', stats: { blogs: 56, projects: 12, resources: 34, likes: 1204, collections: 210 } },
  { id: 3, name: '苏念', username: 'sunian', role: '导师', bio: '软件工程导师', stats: { blogs: 38, projects: 8, resources: 22, likes: 876, collections: 156 } },
  { id: 4, name: '周牧', username: 'zhoumu', role: '实验室成员', bio: '大二 · 前端方向', stats: { blogs: 19, projects: 4, resources: 12, likes: 234, collections: 67 } },
  { id: 5, name: '叶禾', username: 'yehe', role: '实验室成员', bio: '研一 · 机器学习', stats: { blogs: 27, projects: 5, resources: 15, likes: 412, collections: 98 } },
  { id: 6, name: '何川', username: 'hechuan', role: '实验室成员', bio: '大三 · 后端方向', stats: { blogs: 31, projects: 7, resources: 20, likes: 528, collections: 134 } },
  { id: 7, name: '安以', username: 'anyi', role: '实验室成员', bio: '大二 · 算法竞赛', stats: { blogs: 22, projects: 9, resources: 11, likes: 367, collections: 73 } },
  { id: 8, name: '林知夏', username: 'linzx', role: '实验室成员', bio: '大三 · 全栈', stats: { blogs: 29, projects: 6, resources: 17, likes: 489, collections: 112 } },
]
