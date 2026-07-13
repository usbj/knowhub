/**
 * mock 文档（文章）数据
 * ------------------------------------------------------------------
 * 文档学习页推荐 + 搜索。文章=章节集合（照文档站结构）。真实接口：文章模块待查（见计划）。
 */

export interface MockDocChapter {
  id: number
  title: string
}

export interface MockDoc {
  id: number
  title: string
  summary: string
  author: string
  /** 章节数 */
  chapterCount: number
  chapters: MockDocChapter[]
  tags: string[]
  readCount: number
  updateTime: string
  /** 难度 */
  level: '入门' | '进阶' | '高级'
  /** 封面色 */
  cover: string
}

export const docs: MockDoc[] = [
  {
    id: 1,
    title: 'Spring Boot 3 从入门到落地全路线',
    summary: '从 IoC/AOP 基础到自动配置原理、再到多模块项目与生产部署的完整学习路线。',
    author: '陈一帆',
    chapterCount: 18,
    chapters: [
      { id: 1, title: '第一章 IoC 容器与 Bean 生命周期' },
      { id: 2, title: '第二章 AOP 与切面编程' },
      { id: 3, title: '第三章 自动配置原理剖析' },
      { id: 4, title: '第四章 多模块项目实践' },
      { id: 5, title: '第五章 数据访问层整合' },
    ],
    tags: ['Spring Boot', 'Java', '后端'],
    readCount: 4521,
    updateTime: '2026-07-09',
    level: '进阶',
    cover: 'linear-gradient(135deg,#2563eb,#0ea5e9)',
  },
  {
    id: 2,
    title: 'Vue 3 Composition API 系统讲解',
    summary: 'setup、ref/reactive、computed、watch、composables 函数式复用一篇打通。',
    author: '周牧',
    chapterCount: 12,
    chapters: [
      { id: 1, title: '第一章 setup 与响应式基础' },
      { id: 2, title: '第二章 ref vs reactive' },
      { id: 3, title: '第三章 computed 与 watch' },
    ],
    tags: ['Vue3', '前端', 'TypeScript'],
    readCount: 3204,
    updateTime: '2026-07-08',
    level: '入门',
    cover: 'linear-gradient(135deg,#16a34a,#86efac)',
  },
  {
    id: 3,
    title: '分布式系统核心概念 12 讲',
    summary: 'CAP/BASE、一致性哈希、分布式锁、分布式事务、幂等、限流降级。',
    author: '何川',
    chapterCount: 12,
    chapters: [
      { id: 1, title: '第一讲 CAP 与 BASE' },
      { id: 2, title: '第二讲 一致性哈希' },
      { id: 3, title: '第三讲 分布式锁三种实现' },
    ],
    tags: ['分布式', '微服务', 'Redis'],
    readCount: 2876,
    updateTime: '2026-07-06',
    level: '高级',
    cover: 'linear-gradient(135deg,#6366f1,#a5b4fc)',
  },
  {
    id: 4,
    title: 'MySQL 索引与查询优化实战',
    summary: 'B+ 树原理、索引设计、执行计划解读、慢查询排查、分页优化。',
    author: '叶禾',
    chapterCount: 10,
    chapters: [
      { id: 1, title: '第一章 B+ 树与索引结构' },
      { id: 2, title: '第二章 索引设计原则' },
    ],
    tags: ['MySQL', '数据库', '性能优化'],
    readCount: 2103,
    updateTime: '2026-07-04',
    level: '进阶',
    cover: 'linear-gradient(135deg,#0ea5e9,#7dd3fc)',
  },
  {
    id: 5,
    title: '操作系统 408 核心知识图谱',
    summary: '进程线程、内存管理、文件系统、IO，配合考研 408 重点梳理。',
    author: '安以',
    chapterCount: 20,
    chapters: [
      { id: 1, title: '第一章 进程与线程' },
      { id: 2, title: '第二章 CPU 调度' },
    ],
    tags: ['操作系统', '考研', '基础'],
    readCount: 5234,
    updateTime: '2026-06-30',
    level: '入门',
    cover: 'linear-gradient(135deg,#f59e0b,#fcd34d)',
  },
  {
    id: 6,
    title: 'Docker + K8s 实验室速成',
    summary: '从容器化一个 Spring Boot 应用到本地起 K8s 集群的最短路径。',
    author: '林知夏',
    chapterCount: 8,
    chapters: [
      { id: 1, title: '第一章 Docker 基础' },
      { id: 2, title: '第二章 Dockerfile 最佳实践' },
    ],
    tags: ['Docker', 'K8s', '部署'],
    readCount: 1876,
    updateTime: '2026-06-28',
    level: '进阶',
    cover: 'linear-gradient(135deg,#0f766e,#5eead4)',
  },
]

export const getDocById = (id: number): MockDoc | undefined => docs.find((d) => d.id === id)
