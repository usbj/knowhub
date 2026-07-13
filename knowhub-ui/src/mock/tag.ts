/**
 * mock 标签数据
 * ------------------------------------------------------------------
 * 笔记导航页"展示博客标签最多的地方"。tag.count 为该标签关联博客数（后端缺 GET /tag/hot，见计划）。
 */

export interface MockTag {
  id: number
  name: string
  /** 关联博客数 */
  count: number
  /** 标签色系（用于云图区分） */
  tone: 'primary' | 'accent' | 'warm' | 'info' | 'success'
}

export const tags: MockTag[] = [
  { id: 1, name: 'Spring Boot', count: 48, tone: 'primary' },
  { id: 2, name: 'Vue3', count: 42, tone: 'success' },
  { id: 3, name: '分布式', count: 31, tone: 'info' },
  { id: 4, name: 'MySQL', count: 29, tone: 'accent' },
  { id: 5, name: 'Redis', count: 26, tone: 'warm' },
  { id: 6, name: 'Docker', count: 24, tone: 'primary' },
  { id: 7, name: '算法', count: 22, tone: 'info' },
  { id: 8, name: '操作系统', count: 19, tone: 'accent' },
  { id: 9, name: '计算机网络', count: 18, tone: 'success' },
  { id: 10, name: 'TypeScript', count: 17, tone: 'primary' },
  { id: 11, name: 'RustFS', count: 15, tone: 'warm' },
  { id: 12, name: '微服务', count: 14, tone: 'info' },
  { id: 13, name: 'JWT', count: 12, tone: 'accent' },
  { id: 14, name: 'MyBatis', count: 11, tone: 'primary' },
  { id: 15, name: '部署', count: 10, tone: 'success' },
  { id: 16, name: '性能优化', count: 9, tone: 'warm' },
  { id: 17, name: '设计模式', count: 8, tone: 'info' },
  { id: 18, name: '工具链', count: 7, tone: 'accent' },
]
