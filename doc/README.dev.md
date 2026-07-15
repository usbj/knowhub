# doc 目录说明

本目录用于存放 rookie 项目在协作过程中产生的各类**临时/本地说明文档**（设计草稿、模块分析、决策记录、对接说明等）。这些内容通常带有较强的时效性和本地语境，不适合直接进入仓库的版本历史。

## 为什么被 Git 忽略

根目录的 `.gitignore` 中已包含 `/doc/` 入口，将这个目录从 Git 跟踪中排除。这样做的考虑是：

- 这里的内容多为过程性文档，频繁改动会污染提交历史
- 部分文档可能涉及本地配置、临时结论，不适合对外公开
- 真正需要长期保存并对外披露的稳定文档，应写入仓库内对应的 `README.md` 或独立目录（如模块各自的说明）

## 目录结构约定

后续新增文档时，建议按以下方式组织：

```
doc/
├── README.md         本说明文件
├── <模块名>/         按后端模块划分，如 notice/、user/、dict/
├── <话题名>/         按跨模块话题划分，如 design/、migration/、troubleshooting/
└── archive/          归档的过期文档
```

文件命名建议：

- 使用小写英文 + 连字符命名（如 `notice-module-design.md`）
- 日期信息建议放到文件内容首部，而非文件名
- 单文件过长时优先拆成多文件而非长文件

## 与 graphify-out 的关系

`doc/` 与 `graphify-out/` 都是本地忽略目录，但定位不同：

- `graphify-out/` —— graphify 工具自动生成的项目知识图谱产物，由工具维护，**不要手动编辑**
- `doc/` —— 人工撰写的过程性文档，协作过程中持续沉淀

两目录互不引用，但 `doc/` 下的设计文档可以引导 README 中要长期保留的内容沉淀方向。

## 与根 README 的衔接

当某份 `doc/` 文档经过验证、需要长期保留时，应将其核心内容整理后合并到根 `README.md` 或对应模块的说明中，再从 `doc/` 删除或移入 `doc/archive/`。避免稳定的对外说明长期只存在于本地忽略目录里。

## 全局开关 / 配置项落地约定

knowhub 阶段"管理员可在后台改、全站生效"的配置分两类落地，按用途选机制：

**① 键值型 / 开关型配置 → 系统设置模块（`sys_config`）**
- rookie 层已落地独立的系统设置模块（`SysConfig`/`SysConfigUtil`/`SysConfigWarmUpRunner`/`/sys/system-config` 接口 + 前端管理页，见 `sql/sys_config.sql`），专门承载"后台可改、全站生效"的键值型配置，支持 STRING/BOOLEAN/NUMBER/JSON 四种值类型，Redis 永久缓存、`SysConfigUtil` 只读缓存。
- 全局布尔开关（如博客审核开关）、单值配置（如文件访问模式、直链 OSS 地址）、按维度聚合的多值配置（如各业务类型体积上限/类型白名单，用 JSON 对象一条设置项承载）**统一走 `sys_config`**，不再走字典。
- 后端读取收口到一个读取方法/类（如 `BlogConfigReader.isReviewEnabled()`、`StorageConfigReader.sizeLimitBytes()`），业务侧不直接调用 `SysConfigUtil`，换存储/换阈值时只改该类内部实现、调用方零改动。
- 缓存生效语义：设置页编辑保存（`editSysConfig`）写库后立即 `setConfig` 重写 Redis，**即时生效无需重启**；绕过接口直接改 DB 时点「刷新缓存」（`POST /sys/system-config/refresh` 清空重预热）兜底；启动时 `SysConfigWarmUpRunner` 首次预热。前端无独立缓存层，进页实时查 DB。
- knowhub 业务项用 `knowhub.` 前缀（如 `knowhub.blog.review_enabled`、`knowhub.file.size_limit`），与 rookie 内置项 `sys.*` 区分；`is_system=1` 标记代码硬依赖项，受内置项保护（禁删/禁改键与类型/禁停用，仅可改值）。

**② 枚举型字典（前端下拉 + 后端校验共用）→ 仍走 `sys_dict` + `sys_dict_data`**
- 有限枚举值且需前端下拉渲染、`DictTag` 标签着色、后端校验共用的（如 `file_business_type`/`file_access`/`upload_status`/`blog_status`/`review_status`）仍走字典，`DictUtil` 读缓存、`useDict()` 渲染。
- 这类是"枚举值集合"而非"可调阈值/开关"，与配置型用途不同，不迁系统设置。

> 历史背景：2026-07-03 之前系统设置模块未落地时，全局开关曾走字典（`blog_review_enabled` 等）。系统设置模块落地后已迁移，旧约定"不新建系统配置表、统一走字典"作废，以本节为准。迁移脚本见 `sql/knowhub-sys-config-migration.sql`。

## SQL 文件字符与导入约定

**所有含中文的 SQL 文件必须以 UTF-8（无 BOM）编码保存，并在导入时显式指定 utf8mb4 字符集，否则中文会变成乱码。**

### 现象与根因

历史上多次出现过：SQL 源文件里的中文是正确的（hex dump 验证字节正确，如『公』= `e5 85 ac`），但入库后 `sys_dict` / `sys_dict_data` 等表的中文列变成乱码。根因不在文件本身，而在**导入时 mysql 客户端连接字符集不是 utf8mb4**（典型是默认 `latin1` 或执行时未 `SET NAMES`）：UTF-8 字节被按 latin1 解读后重新存库 → 乱码。部分历史字典（如 `blog_level`、`article_level`）曾因此乱码，靠手动重写中文修复。

### 写 SQL 时的要求

- 文件统一 **UTF-8 无 BOM** 编码；中文直接写明文（`'博客等级'`、`'公开'`），不要转 `\u` / `CONVERT(... USING utf8mb4)` 之类绕路。
- 含中文写入的 SQL 文件**首行加 `SET NAMES utf8mb4;`**，保证无论客户端默认字符集如何，本次连接都按 utf8mb4 读写（示例见 `sql/knowhub-blog-level-dict-charset-fix.sql`）。这一行兜底，比依赖执行者记得带 `--default-character-set` 更稳。
- 修已乱码的数据用 `UPDATE` 覆盖正确中文（行已存在只是乱码，不是缺数据）；新增数据才用 `INSERT`。改完附 `SELECT` 验证语句供人工核对。

### 导入时的要求

执行含中文 SQL 时务必带字符集，二选一即可（文件首行已 `SET NAMES utf8mb4` 时，后者直接 source 也行）：

```bash
mysql --default-character-set=utf8mb4 -u<user> -p<db> < sql/xxx.sql
# 或进客户端后
mysql> source sql/xxx.sql;   # 依赖文件首行 SET NAMES utf8mb4 生效
```

切勿用默认字符集直接 `< sql/xxx.sql` 导入，否则再正确的文件也会乱码。

### 易错：sys_dict 与 sys_dict_data 的「备注」列名不一致

rookie 框架里两张字典表的备注列**拼写不同**，写 SQL 时别想当然：

- `sys_dict` 的备注列是 **`remake`**（框架历史拼写错误，不是 remark）
- `sys_dict_data` 的备注列才是 **`remark`**（正确拼写）

写 UPDATE/INSERT 时务必按目标表用对列名，错用会报 `Unknown column 'remark'/'remake' in 'field list'`。验证列名可 `SHOW COLUMNS FROM sys_dict LIKE 'rem%';`。已在 `sql/knowhub-blog-level-dict-charset-fix.sql` 踩过此坑并修正。

## rookie 框架代码修改禁令

**未经用户明确许可，不得修改 `rookie-*` 模块的任何代码与配置**（`rookie-admin`、`rookie-framework`、`rookie-system`、`rookie-common`，包括其中的 `application.yml`、`ApplicationConfig` 等框架级文件）。新模块（如 `knowhub-blog`）的全部产物应在属于自己的模块目录内，避免触碰上游框架层。若某项能力确实需要改框架才能实现，必须先向用户说明并取得同意后再动手。

### knowhub 业务产物不得放入 rookie 模块

**knowhub 阶段新增的业务产物（实体类、DTO、Mapper、Service、Controller、配置类、工具类等）一律放在对应的 knowhub 模块内，严禁放进 `rookie-*` 模块。** `rookie-*` 是上游框架层，承载通用基础设施（用户/角色/菜单/字典/通知/日志等）；knowhub 二开新增的博客/文档/项目/资源/审核等业务内容，归属在自己的模块（如 `knowhub-blog`）对应的包下。

具体落地：
- 业务实体放在模块内 `com.knowhub.<模块>.pojo.entity.*`（与 `pojo.vo`/`pojo.quarry` 并列），**不要**塞进 `com.rookie.common.pojo.entity`。
- 业务 Mapper/Service/Controller/配置同理，全部归模块自身包。
- 业务实体仍可**继承/引用** `com.rookie.common.pojo.BaseEntity` 等框架公共基类（这属于引用框架、不是在框架里加内容，不违规）；但**不得向 `rookie-*` 新增任何类、接口、配置**。
- 违规示例：把 `Blog` 实体建到 `rookie-common/.../pojo/entity/Blog.java` —— 错。应建 `knowhub-blog/.../pojo/entity/Blog.java`。
- 判定口径：凡文件物理路径落在 `rookie-*` 模块目录下的新增 `.java`/`.xml`/`.yml`，都视为违反本禁令，除非已获用户明确同意。
