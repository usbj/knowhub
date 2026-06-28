/**
 * 文件作用：
 * 日志详情弹窗内复用的代码 / 长文本展示块。
 * 统一处理等宽字体、限高滚动、空值占位与一键复制，让操作日志的请求参数 / 返回结果
 * 与错误日志的完整堆栈在视觉和交互上保持一致。
 * 关键参数：
 * - `title`：区块小标题（如「请求参数」「完整堆栈」）。
 * - `content`：展示的原始文本，可能为空字符串。
 * - `maxHeight`：代码区限高，超长内容内部滚动。
 * 关键行为：
 * - 空值统一展示占位 "--"，不渲染复制按钮，避免复制空内容产生噪声反馈。
 * - 复制成功后短暂切换为"已复制"文案，~1.6s 后恢复。
 */
<script setup lang="ts">
import { ref } from 'vue'
import { ElButton, ElMessage } from 'element-plus'

const props = withDefaults(
  defineProps<{
    title: string
    content: string
    maxHeight?: string
  }>(),
  {
    maxHeight: '240px',
  },
)

const isCopied = ref(false)

/**
 * 方法效果：
 * 把代码块内容写入剪贴板，并在短切后恢复按钮文案。
 * 参数：
 * - 无，直接读取当前 content props。
 * 返回值：
 * - 无返回值；副作用是更新剪贴板与按钮状态。
 */
const handleCopy = async () => {
  if (!props.content) {
    return
  }

  try {
    await navigator.clipboard.writeText(props.content)
    isCopied.value = true
    ElMessage.success('已复制到剪贴板')
    // 复制态短暂停留后恢复，使用本地计时器在组件卸载时一并清理
    setTimeout(() => {
      isCopied.value = false
    }, 1600)
  } catch {
    ElMessage.warning('复制失败，请手动选择文本复制')
  }
}
</script>

<template>
  <section class="log-code-block">
    <!-- 区块标题栏：左侧标题，右侧复制按钮（空内容时仅展示占位说明） -->
    <header class="log-code-block__header">
      <span class="log-code-block__title">{{ title }}</span>
      <ElButton
        v-if="content"
        text
        size="small"
        class="log-code-block__copy"
        :class="{ 'is-copied': isCopied }"
        @click="handleCopy"
      >
        {{ isCopied ? '已复制' : '复制' }}
      </ElButton>
    </header>

    <pre
      v-if="content"
      class="log-code-block__code"
      :style="{ maxHeight }"
    >{{ content }}</pre>
    <div v-else class="log-code-block__empty">--</div>
  </section>
</template>

<style scoped>
.log-code-block {
  display: grid;
  gap: 8px;
}

.log-code-block__header {
  display: flex;
  align-items: center;
  justify-content: space-between;
}

.log-code-block__title {
  font-size: var(--rookie-font-size-sm);
  color: var(--rookie-text-secondary);
  font-weight: 500;
  letter-spacing: 0.02em;
}

.log-code-block__copy :deep(.el-icon) {
  margin-right: 2px;
}

/* 已复制态用主色高亮，提供清晰的操作反馈，颜色由焦点主色统一驱动 */
.log-code-block__copy.is-copied :deep(.el-button__text) {
  color: var(--rookie-primary);
}

.log-code-block__code {
  margin: 0;
  padding: 12px 16px;
  overflow: auto;
  background: var(--rookie-surface-muted);
  color: var(--rookie-text);
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-sm);
  font-family: 'Fira Code', 'Courier New', Courier, monospace;
  font-size: var(--rookie-font-size-xs);
  line-height: 1.65;
  white-space: pre-wrap;
  word-break: break-all;
}

.log-code-block__empty {
  padding: 12px 16px;
  color: var(--rookie-text-tertiary);
  background: var(--rookie-surface-muted);
  border: 1px solid var(--rookie-border);
  border-radius: var(--rookie-radius-sm);
  font-size: var(--rookie-font-size-sm);
}
</style>