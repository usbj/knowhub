/**
 * mock 文档（文章）数据
 * ------------------------------------------------------------------
 * 文档学习页推荐 + 搜索。文章=章节集合（照文档站结构）。真实接口：文章模块待查（见计划）。
 * 章节正文：仅 doc=1 的 7 章带短正文（演示文档详情站正文渲染），其余文档只给 2 章标题、
 * 正文留空，详情页回退占位文案"正文接口接入后渲染"。
 */

export interface MockDocChapter {
  id: number
  title: string
  /** 章节正文（mark down）。仅 doc=1 的前几章有完整短正文，其余为占位（详情页渲染时回退占位文案） */
  content?: string
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
  /** 查看等级 1 公开 / 2 内部 / 3 机密（与项目对齐，非"难度"） */
  level: 1 | 2 | 3
  /** 封面色 */
  cover: string
}

export const docs: MockDoc[] = [
  {
    id: 1,
    title: 'Spring Boot 3 从入门到落地全路线',
    summary:
      '## 这篇文档讲什么\n\n这是一份面向**已有 Java 基础、想系统掌握 Spring Boot 3** 的同学的学习路线。从 IoC 容器与 Bean 生命周期讲起，一路到自动配置原理、多模块项目实践、数据访问整合、安全鉴权，最后落到生产部署。\n\n> 目标：读完这篇，你能独立用 Spring Boot 3 从零搭一个可上线的多模块后端，并讲清每个"约定大于配置"背后的原理。\n\n## 适合谁读\n\n- 写过 Java、用过 Spring 但没系统梳理过 Boot 的人\n- 想理解"引个 starter 就能用"背后到底发生了什么的人\n- 即将搭第一个真实后端项目、怕踩坑的人\n\n## 学习建议\n\n1. **按章节顺序读**：前几章是地基，跳着看后面会卡\n2. **边读边敲**：每章末尾都有最小可运行例子，照着跑一遍\n3. **不要死记生命周期**：理解"为什么这样设计"比记步骤重要\n\n## 你将掌握\n\n- IoC / AOP / 自动配置 三大基石\n- 多模块项目怎么拆、依赖怎么治理\n- MyBatis 整合与事务边界\n- 鉴权链路：认证与授权分离、admin 短路\n- 生产可用的配置外置、日志、打包部署\n\n## 章节速览\n\n| 章 | 主题 | 难度 |\n|---|---|---|\n| 1 | IoC 容器与 Bean 生命周期 | ★★ |\n| 2 | AOP 与切面编程 | ★★★ |\n| 3 | 自动配置原理剖析 | ★★★ |\n| 4 | 多模块项目实践 | ★★ |\n| 5 | 数据访问层整合 | ★★ |\n| 6 | 安全与鉴权 | ★★★ |\n| 7 | 生产部署 | ★ |\n\n准备好就点右上角「开始阅读」，从第一章开始。',
    author: '陈一帆',
    chapterCount: 7,
    chapters: [
      {
        id: 1,
        title: '第一章 IoC 容器与 Bean 生命周期',
        content:
          '## IoC 是什么\n\n控制反转（IoC）把对象的创建与依赖交给容器， Beans 之间不再 new。Spring 用 `ApplicationContext` 管理这些。\n\n## Bean 生命周期\n\n实例化 → 属性注入 → `BeanNameAware`/`BeanFactoryAware` → `BeanPostProcessor#postProcessBeforeInitialization` → 初始化（`@PostConstruct`/`InitializingBean`/`init-method`）→ `postProcessAfterInitialization` → 使用 → 销毁。\n\n> 关键点：`BeanPostProcessor` 是 AOP 与大多数框架扩展的入口。\n\n## 一个最小例子\n\n```java\n@Component\npublic class Demo {\n  private final Repo repo;\n  public Demo(Repo repo) { this.repo = repo; } // 构造注入推荐\n}\n```',
      },
      {
        id: 2,
        title: '第二章 AOP 与切面编程',
        content:
          '## 为什么需要 AOP\n\n日志、事务、缓存这类横切逻辑散落各处会让代码难维护，AOP 把它们抽成切面统一织入。\n\n## 核心概念\n\n- 切点（Pointcut）：在哪织入\n- 通知（Advice）：织入什么（前置/后置/环绕）\n- 切面（Aspect）：切点 + 通知的组合\n\n## 示例\n\n```java\n@Aspect\n@Component\npublic class LogAspect {\n  @Around(\"execution(* com.knowhub..*(..))\")\n  public Object log(ProceedingJoinPoint pjp) throws Throwable {\n    return pjp.proceed();\n  }\n}\n```',
      },
      {
        id: 3,
        title: '第三章 自动配置原理剖析',
        content:
          '## 入口\n\n`@SpringBootApplication` = `@SpringBootConfiguration` + `@EnableAutoConfiguration` + `@ComponentScan`。\n\n## 自动配置如何发生\n\n`@EnableAutoConfiguration` 借 `spring.factories`（旧）/ `AutoConfiguration.imports`（新）加载候选配置类，每个配置类在 `@Conditional` 满足时才生效。\n\n> 这解释了"引个 starter 就能用"的原理。\n\n## 条件注解速记\n\n`@ConditionalOnClass` / `@ConditionalOnMissingBean` / `@ConditionalOnProperty`。',
      },
      {
        id: 4,
        title: '第四章 多模块项目实践',
        content:
          '## 拆分原则\n\n按职责分模块：admin（启动）/ common（公共）/ framework（安全）/ system（系统）/ 业务模块各自独立。\n\n## 关键取舍\n\n- **不在框架里加内容**：上游稳定层不碰，业务全归自己模块。\n- **业务实体继承框架 BaseEntity**：引用而非修改框架。\n\n## 父 pom 管依赖版本\n\n用 `dependencyManagement` 收口版本，子模块只引不带版本，避免冲突。',
      },
      {
        id: 5,
        title: '第五章 数据访问层整合',
        content:
          '## MyBatis 整合\n\n引 `mybatis-spring-boot-starter`，`@MapperScan` 扫描，Mapper 接口配 XML 或注解 SQL。\n\n## 事务\n\n`@Transactional` 标在 Service 方法上，注意默认只对运行时异常回滚，检查异常需 `rollbackFor`。\n\n```java\n@Transactional(rollbackFor = Exception.class)\npublic void doWork() { ... }\n```',
      },
      {
        id: 6,
        title: '第六章 安全与鉴权',
        content:
          '## 认证与授权分离\n\n认证（你是谁）走登录/JWT，授权（你能干嘛）走角色/权限。knowhub 复用 rookie 的 `TokenVerifyFilter` + perm_key 机制。\n\n## 短路\n\nadmin 角色登录时物理塞入全部 perm_key，无需额外 `isAdmin` 判断——这是后台管理端很多"超管直通"的来源。',
      },
      {
        id: 7,
        title: '第七章 生产部署',
        content:
          '## 配置外置\n\n`application.yml` 区分 `dev`/`prod`；敏感项走环境变量或配置中心，不进仓库。\n\n## 日志与监控\n\n日志按天切割、级别可调；关键链路加 actuator 健康检查。\n\n## 打包\n\n`mvn package -Pprod` 产出可执行 jar，`java -jar` 启动。',
      },
    ],
    tags: ['Spring Boot', 'Java', '后端'],
    readCount: 4521,
    updateTime: '2026-07-09',
    level: 2,
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
    ],
    tags: ['Vue3', '前端', 'TypeScript'],
    readCount: 3204,
    updateTime: '2026-07-08',
    level: 1,
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
    ],
    tags: ['分布式', '微服务', 'Redis'],
    readCount: 2876,
    updateTime: '2026-07-06',
    level: 3,
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
    level: 2,
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
    level: 1,
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
    level: 2,
    cover: 'linear-gradient(135deg,#0f766e,#5eead4)',
  },
]

export const getDocById = (id: number): MockDoc | undefined => docs.find((d) => d.id === id)
