# knowhub 接入 rookie 定时任务管理 —— 对 rookie 框架修改说明

> 用途：本次为让 knowhub 8 个定时任务纳入 rookie 的「定时任务管理」能力，对 rookie 上游框架做了少量增强。
> 本文件罗列这些改动，方便后续把这部分增强回填/同步到 rookie 上游框架。
> 生成时间：2026-08-17。

## 背景

rookie 合并进 knowhub 后自带定时任务管理能力（`sys_job`/`sys_job_log` 两表 + `SysJobScheduler` 调度器 + 后台菜单与前端页）。但其调度器 `SysJobScheduler` 的安全边界常量 `TASK_BEAN_PACKAGE_PREFIX = "com.rookie.system.task"` **写死**，只允许调度该包下的 Spring Bean。knowhub 的 8 个定时任务在 `com.knowhub.task` 包，被白名单挡住无法纳管。

为打通，把白名单从「写死单前缀」改为「配置项驱动的多前缀列表」。这是对框架能力的增强（通用化），不是 knowhub 业务特化改动，适合回填上游。

## 对 rookie 框架的改动清单（共 3 个文件）

### 1. `rookie-system/src/main/java/com/rookie/system/scheduler/SysJobScheduler.java`（改）

**改动性质**：安全边界从硬编码常量改为可配置列表，二开项目可追加自身任务包前缀。

**具体改动点**：

① 新增 import（line 15）：
```java
import org.springframework.beans.factory.annotation.Value;
```

② 类注释「安全约束」段（line 43-48）：
- 原：`调用目标限定 com.rookie.system.task 包下的 Spring Bean（白名单前缀校验）`
- 改：`调用目标限定 task.bean-package-prefixes 配置项所列前缀包下的 Spring Bean（白名单前缀校验，默认 com.rookie.system.task；二开项目可追加自身任务包前缀）`

③ 常量字段改为 @Value 注入的实例字段（line 57-61）：
```java
// 原：
private static final String TASK_BEAN_PACKAGE_PREFIX = "com.rookie.system.task";

// 改：
/** 任务 Bean 白名单包前缀列表：只有这些前缀下的 Spring Bean 允许被调度（反射调用安全边界）。
 *  由配置项 task.bean-package-prefixes 提供，默认 com.rookie.system.task；
 *  二开项目可追加自身任务包前缀。 */
@Value("${task.bean-package-prefixes:com.rookie.system.task}")
private List<String> taskBeanPackagePrefixes;
```

④ `validateJobConfig` 内校验（原 line 199-202）：
```java
// 原：
if (!bean.getClass().getName().startsWith(TASK_BEAN_PACKAGE_PREFIX)) {
    throw new ServiceException(500, "任务目标 Bean 不在白名单包内（" + TASK_BEAN_PACKAGE_PREFIX + "）");
}

// 改：
String beanClassName = bean.getClass().getName();
if (taskBeanPackagePrefixes.stream().noneMatch(p -> beanClassName.startsWith(p))) {
    throw new ServiceException(500, "任务目标 Bean 不在白名单包内（" + taskBeanPackagePrefixes + "）");
}
```

⑤ `invoke` 内校验（原 line 220-223）：与 ④ 同款改写（两处都要改，安全边界在保存前校验和执行期校验各一道）。

**回填上游要点**：
- `java.util.List` 原文件已 import（line 26），无需重复加。
- ⚠️ **`@Value` 注入 `List<String>` 的 YAML 列表绑定坑**（实测踩坑）：`@Value("${task.bean-package-prefixes:...}")` 走 SpEL + 默认 ConversionService，**只认逗号分隔字符串**拆 List；yml 写 YAML 列表语法（`- item`）时，Spring 的 `YamlPropertiesFactory` 会把列表压成带 `[ ]` 的单字符串或 `[key[0], key[1]]` 形态，`@Value` 的 SpEL 解析拿到的不是纯逗号串，拆 List 失败 → fallback 到默认值单元素 List，白名单只剩 `com.rookie.system.task`，二开包被静默丢弃。**故 yml 必须用逗号分隔单行写法**（见下方 §「YAML 列表绑定坑」）。后续要彻底解决，应把 `@Value` 换成 `@ConfigurationProperties` 绑定（原生支持 YAML 列表 + 逗号串两种形态），那时 yml 两种写法都工作——这是待回填上游的进一步优化。
- 默认值保持 `com.rookie.system.task`，对上游单项目行为完全不变（零配置时等价于原硬编码），向后兼容。

### 2. `rookie-admin/src/main/resources/application.yml`（改）

**改动性质**：新增一个配置项（默认值与原硬编码一致，不配也无副作用）。

**具体改动**：在 knowhub 配置块之后、`# 文件上传配置` 之前新增：
```yaml
# 定时任务调度白名单：允许被 sys_job 调度的 Spring Bean 包前缀（rookie SysJobScheduler 安全边界）
# 默认 com.rookie.system.task；knowhub 扩展 com.knowhub.task，使 knowhub 的 8 个 @Component 任务可纳入后台管理
# （后台「系统监控→定时任务」注册/启停/立即执行/查日志，cron 走 sys_job 表）
# 注意：@Value 注入 List<String> 只认逗号分隔字符串，不认 YAML 列表语法（- ），故必须用逗号分隔单行写法（见 §「YAML 列表绑定坑」）。
task:
  bean-package-prefixes: com.rookie.system.task,com.knowhub.task
```

**回填上游要点**：
- 上游 rookie 项目的 application.yml 只需保留第一项 `com.rookie.system.task`（或整段不配，走默认值）。
- `com.knowhub.task` 这一项是 knowhub 二开特有，上游不需要。
- 即：回填上游时，这段配置可只写默认项，或完全依赖 @Value 默认值不写配置块。
- **同样必须用逗号分隔写法**（若上游也要配多项），不能写 YAML 列表语法，原因见 §「YAML 列表绑定坑」。

### 3. `rookie-ui/src/views/system/job/config.ts`（改）

**改动性质**：前端表单 placeholder 文案同步（白名单放开后，提示用户可填的包范围）。

**具体改动点**：

① 文件头注释（line 8）：
- 原：`调用目标（beanName + methodName）限定 com.rookie.system.task 包，表单 placeholder 明确提示。`
- 改：`调用目标（beanName + methodName）限定 task.bean-package-prefixes 配置项所列包（默认 com.rookie.system.task，knowhub 扩展 com.knowhub.task），表单 placeholder 明确提示。`

② beanName 字段 placeholder（line 136）：
- 原：`如 demoTask（限 com.rookie.system.task 包）`
- 改：`如 blogReviewReconcileTask（限 com.rookie.system.task / com.knowhub.task 包）`

③ methodName 字段 placeholder（line 147）：
- 原：`如 execute（public，无参或单个 String 参数）`
- 改：`如 reconcile / gc（public，无参或单个 String 参数）`

**回填上游要点**：
- 上游的示例 beanName/methodName 可改回 demoTask/execute 或保留 knowhub 示例皆可，关键是 placeholder 里「限 com.rookie.system.task 包」要改成「限配置项所列包」语义，避免与后端配置化后的实际行为不符。
- 改完跑 `npm run type-check`（vue-tsc）确认无类型错误（本次仅字符串字面量改动）。

## 未改动 rookie 框架的部分（确认边界）

以下 rookie 文件本次**未动**，仅列出以示边界清晰：
- `rookie-common/.../pojo/entity/SysJob.java`、`SysJobLog.java`（实体，无需改）
- `rookie-system/.../service/{SysJobService,SysJobServiceImpl}.java`、`mapper/SysJob*Mapper.java`、`controller/SysJobController.java`、`pojo/{vo,quarry}/SysJob*.java`（调度业务链路，无需改）
- `sql/sys_system_monitor.sql`（建表 + 菜单 + 权限点，已就绪，无需改）

## 与 knowhub 侧改动的边界区分

本次 knowhub 侧的改动（不在本说明范围，属 knowhub 自身）：
- 8 个 `knowhub/.../task/*.java` 去除 `@Scheduled` 注解 + 更新注释（任务方法本身仍属 knowhub）
- 删除 `knowhub/.../config/SchedulingConfig.java`（knowhub 自带的 @EnableScheduling，无注解任务后失去存在意义）
- 新建 `sql/knowhub-job-init.sql`（knowhub 8 个任务的 sys_job 初始化数据，knowhub 专属）

## 回填上游的建议步骤

1. 在 rookie 上游仓库，对 `SysJobScheduler.java` 做上述 ①-⑤ 五处改动（import/类注释/字段/两处校验）。
2. 在上游 `application.yml` 加 `task.bean-package-prefixes` 配置块（只列 `com.rookie.system.task`，或不配走默认值）。
3. 在上游 `rookie-ui/.../job/config.ts` 同步 placeholder 文案为「配置项所列包」语义。
4. 上游若有 demo 任务（com.rookie.system.task 包下），可顺手登记进 sys_job 做联调验证；上游当前该包为空，可留空。
5. 验证：启动看「定时任务启动注册完成，共 N 个启用任务」日志；后台定时任务页注册/启停/立即执行/执行日志链路通。

## YAML 列表绑定坑（@Value + List<String>）

**现象**：knowhub 接入后实测，后台手动触发 knowhub 任务（含 `fileGcTask.gc`）报 `ServiceException: 任务目标 Bean 不在白名单包内（[com.rookie.system.task]）`——方括号里只有 `com.rookie.system.task`，`com.knowhub.task` 被静默丢弃。连带的次生现象：文件管理删除文件后磁盘对象本体不清（因为 `FileGcTask` 调度被白名单挡住，GC 不跑，软删后文件等不到物理清理；OSS 中转/直链同理）。

**根因**：`SysJobScheduler` 用 `@Value("${task.bean-package-prefixes:com.rookie.system.task}")` 注入 `List<String>`。`@Value` 走 SpEL + 默认 ConversionService，对 `List<String>` 目标类型**只认逗号分隔字符串**（按 `,` split）。而 yml 写成 YAML 列表语法：
```yaml
task:
  bean-package-prefixes:
    - com.rookie.system.task
    - com.knowhub.task
```
时，Spring Boot 的 `YamlPropertySourceLoader` 把它解析成 `List<String>` 存入 Environment，但 `@Value` 的 SpEL 解析器拿到 List 源值后转成字符串形态（如 `com.rookie.system.task,com.knowhub.task` 或带方括号），与「逗号分隔纯字符串」的预期路径不完全一致，部分场景拆 List 失败 → fallback 到 `@Value` 默认值 `com.rookie.system.task`，包成单元素 List。结果白名单只剩默认项，二开包前缀被静默丢弃，所有 knowhub task 调度全被白名单拦截。

**修复**：yml 改用逗号分隔单行写法（`@Value` 原生支持）：
```yaml
task:
  bean-package-prefixes: com.rookie.system.task,com.knowhub.task
```
application.yml 已按此修正，并在配置项上方注释标明此坑。

**后续优化（待回填上游）**：把 `SysJobScheduler` 的 `@Value` 注入改为 `@ConfigurationProperties` 绑定（新建 `TaskProperties` 类或在本类加 `@ConfigurationProperties(prefix = "task")`）。`@ConfigurationProperties` 原生支持 YAML 列表 + 逗号分隔字符串两种形态，届时 yml 两种写法都工作，可消除此坑。属框架进一步通用化，适合回填上游。

**排查备忘**：凡 `@Value` 注入 `List<String>`/`Set<String>` 且 yml 用 `- ` 列表语法写的，都要警惕此坑——值会被静默 fallback 到默认值，不会报错，只能从运行期行为反推（如本例的白名单拦截异常里方括号只含默认项）。

## 设计取舍备忘（回填时无需额外处理，仅备查）

- **fixedDelay → cron 折算**：knowhub 6 个原 fixedDelay 任务折算为 `0 */N * * * ?` cron（10min→`0 */10`、5min→`0 */5`、30min→`0 */30`），周报/月报 cron 原样。这是 knowhub 侧 SQL 的事，与框架改动无关。
- **单机调度边界**：`SysJobScheduler` 单机语义（类注释已声明），多实例需分布式锁——框架既有边界，本次未触及。
- **向后兼容**：框架改动对上游单项目零影响（不配 `task.bean-package-prefixes` 时默认值即原硬编码值）。
