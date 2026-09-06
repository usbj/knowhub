<!--
  KhContentToc —— 内容目录卡（可折叠树形 + 当前阅读高亮）
  ------------------------------------------------------------------
  从正文 ##/###/#### 标题构建的多级目录树，博客详情 / 文档阅读页共用。
  入参 items 为扁平 TocItem 列表（level=2/3/4 + text，按正文出现顺序），
  组件按 level 嵌套成树：h2 为根、h3 嵌其下、h4 嵌 h3 下。
  - 默认全部折叠（用户要求），点三角展开/合并；activeIndex 变化时自动展开其祖先链。
  - activeIndex：当前正在阅读的标题序号（由父页 scroll spy 计算后传入），该项高亮。
  - 无前置符号（去 - / · 等），仅缩进 + 三角表达层级；颜色统一不分浓淡。
  - 卡片最大高度 ≈ 视口一半，超出内滚动。
-->
<script setup lang="ts">
import { nextTick, ref, watch } from 'vue'
import { ArrowRight } from '@element-plus/icons-vue'
import KhCard from './KhCard.vue'
import KhIcon from './KhIcon.vue'

export interface TocItem {
  /** 标题层级：2=h2 / 3=h3 / 4=h4（与 markdown ##/###/#### 对齐） */
  level: number
  /** 标题文本（已去前缀 # 和首尾空白） */
  text: string
}

const props = withDefaults(
  defineProps<{
    items: TocItem[]
    title?: string
    /** 当前阅读标题序号（在 items 中的 index），-1/缺省表示无高亮；变化时自动展开其祖先链 */
    activeIndex?: number
  }>(),
  {
    title: '目录',
    activeIndex: -1,
  },
)

const emit = defineEmits<{ (e: 'select', index: number): void }>()

interface TocNode {
  item: TocItem
  flatIndex: number
  children: TocNode[]
  /** 父节点（根为 null）——用于 activeIndex 变化时回溯祖先链自动展开 */
  parent: TocNode | null
}

const buildTree = (items: TocItem[]): TocNode[] => {
  const roots: TocNode[] = []
  const stack: Record<number, TocNode> = {}
  // 层级归一化：文档起始标题非 h2（只有 h3/h4，或 h3/h4 混无 h2）时，原 buildTree 会把 h3 当根、
  // h4 嵌其下或跳级进 roots，目录树层级语义错位（用户反馈"没有二级目录的三级目录也不会解析"的真因——
  // h3 当根无缩进、视觉不像正常目录树）。把最小 level 归一到 2 当根：所有 item.level 减 (minLevel-2)，
  // 最小标题变 level=2，其余按原级差平移。归一化只用于本函数嵌套判定，不写进 node（item/flatIndex 原样），
  // 故跳转与 scroll-spy 仍按原始 DOM 顺序 idx（DOM 里 h3 还是 h3，scrollIntoView 不受影响）。
  // 例：全 h3/h4 → 归一化成 h2/h3，h3 当根、h4 嵌其下；h2/h3/h4 → minLevel=2 不偏移，原行为不变；
  // h2/h4 跳 h3 → 归一化不解决跨级，h4 仍进 roots 与 h2 平级（跳级标题本就无中间父，合理）。
  let minLevel = 6
  for (const it of items) if (it.level < minLevel) minLevel = it.level
  const shift = items.length ? minLevel - 2 : 0
  items.forEach((item, i) => {
    const level = item.level - shift
    const node: TocNode = { item, flatIndex: i, children: [], parent: null }
    const parentLevel = level - 1
    const parent = stack[parentLevel]
    if (parent) {
      node.parent = parent
      parent.children.push(node)
    } else {
      roots.push(node)
    }
    stack[level] = node
    for (const k of Object.keys(stack)) {
      if (Number(k) > level) delete stack[Number(k)]
    }
  })
  return roots
}

const tree = ref<TocNode[]>([])
/** flatIndex → node 的快表，用于按 activeIndex 找节点回溯祖先 */
const nodeMap = ref<Record<number, TocNode>>({})
/** 展开状态：key = flatIndex，true=展开。默认全折叠（用户要求）。 */
const expandedMap = ref<Record<number, boolean>>({})

const rebuild = () => {
  const roots = buildTree(props.items)
  tree.value = roots
  const map: Record<number, TocNode> = {}
  const walk = (nodes: TocNode[]) => {
    for (const n of nodes) {
      map[n.flatIndex] = n
      walk(n.children)
    }
  }
  walk(roots)
  nodeMap.value = map
  // 默认全部折叠
  expandedMap.value = {}
}
watch(() => props.items, rebuild, { immediate: true })

/**
 * activeIndex 变化：自动展开其全部祖先（让当前阅读项可见），并把该项滚进目录卡视口。
 * 不展开当前项自身的子节点（仅祖先链），保持用户折叠意图。
 */
watch(
  () => props.activeIndex,
  (idx) => {
    if (idx == null || idx < 0) return
    let n = nodeMap.value[idx]
    if (!n) return
    while (n.parent) {
      expandedMap.value[n.parent.flatIndex] = true
      n = n.parent
    }
  },
)

const toggle = (node: TocNode) => {
  if (!node.children.length) return
  expandedMap.value[node.flatIndex] = !expandedMap.value[node.flatIndex]
}

const onSelect = (node: TocNode) => {
  emit('select', node.flatIndex)
}

/** 列表容器 ref（.kh-toc__root），active 项滚进卡内可见区时用它做 scrollIntoView */
const listRef = ref<HTMLElement | null>(null)
/** active 项 DOM 按序号收集：itemRefs[flatIndex] = 该项的 div。函数 ref 用 setItemRef(idx)。 */
const itemRefs = ref<Record<number, HTMLElement | null>>({})
const setItemRef = (idx: number) => (el: unknown, _refs?: unknown) => {
  if (el instanceof HTMLElement) itemRefs.value[idx] = el
  else delete itemRefs.value[idx]
}

/**
 * 找元素最近的可滚动祖先（overflow auto/scroll 且有可见高度）：用于把 active 项滚进目录卡视口，
 * 不调 el.scrollIntoView 是因为它会沿祖先链向上滚 window，导致整页跳到 active 标题（用户反馈的"卡片外也可滑"）。
 */
const scrollParentOf = (el: HTMLElement): HTMLElement | null => {
  let p: HTMLElement | null = el.parentElement
  while (p) {
    const style = getComputedStyle(p)
    const overflowY = style.overflowY
    if ((overflowY === 'auto' || overflowY === 'scroll') && p.clientHeight < p.scrollHeight) {
      // 仅滚真正内容溢出 ancestor（避免给等高 ancestor 也滚）
      return p
    }
    p = p.parentElement
  }
  return null
}

/**
 * activeIndex 变化：自动展开其全部祖先（让当前阅读项可见），并把该项滚进「最近的 overflow 祖先」视口，
 * 不滚 window（避免整页跳）。不展开当前项自身的子节点（仅祖先链），保持用户折叠意图。
 */
watch(
  () => props.activeIndex,
  (idx) => {
    if (idx == null || idx < 0) return
    let n = nodeMap.value[idx]
    if (!n) return
    while (n.parent) {
      expandedMap.value[n.parent.flatIndex] = true
      n = n.parent
    }
    // 等展开渲染后取 DOM，手动滚 overflow 祖先把 active 项带到可见区
    nextTick(() => {
      const el = itemRefs.value[idx]
      if (!el) return
      const sp = scrollParentOf(el)
      if (!sp) return
      const spRect = sp.getBoundingClientRect()
      const elRect = el.getBoundingClientRect()
      // 在 overflow 祖先上方 → 上滚使 el 顶贴近 sp 顶部留 4px
      if (elRect.top < spRect.top + 4) {
        sp.scrollTop -= spRect.top + 4 - elRect.top
      } else if (elRect.bottom > spRect.bottom - 4) {
        // 在下方 → 上滚使 el 底贴近 sp 底部
        sp.scrollTop += elRect.bottom - (spRect.bottom - 4)
      }
    })
  },
)
</script>

<template>
  <KhCard padding="md" class="kh-toc">
    <div class="kh-toc__head">
      <KhIcon name="doc" :size="16" /> {{ title }}
    </div>
    <ul v-if="tree.length" ref="listRef" class="kh-toc__root">
      <li v-for="node in tree" :key="node.flatIndex">
        <div
          :ref="setItemRef(node.flatIndex)"
          class="kh-toc__item"
          :class="{ 'is-active': activeIndex === node.flatIndex }"
          role="button"
          tabindex="0"
          @click="onSelect(node)"
          @keydown.enter.prevent="onSelect(node)"
        >
          <button
            v-if="node.children.length"
            class="kh-toc__caret"
            type="button"
            :title="expandedMap[node.flatIndex] ? '折叠' : '展开'"
            @click.stop="toggle(node)"
          >
            <el-icon class="kh-toc__caret-icon" :class="{ 'is-open': expandedMap[node.flatIndex] }">
              <ArrowRight />
            </el-icon>
          </button>
          <span v-else class="kh-toc__caret is-leaf" />
          <span class="kh-toc__text kh-line-clamp-2">{{ node.item.text }}</span>
        </div>
        <ul v-if="node.children.length && expandedMap[node.flatIndex]" class="kh-toc__sub">
          <li v-for="child in node.children" :key="child.flatIndex">
            <div
              :ref="setItemRef(child.flatIndex)"
              class="kh-toc__item"
              :class="{ 'is-active': activeIndex === child.flatIndex }"
              role="button"
              tabindex="0"
              @click="onSelect(child)"
              @keydown.enter.prevent="onSelect(child)"
            >
              <button
                v-if="child.children.length"
                class="kh-toc__caret"
                type="button"
                :title="expandedMap[child.flatIndex] ? '折叠' : '展开'"
                @click.stop="toggle(child)"
              >
                <el-icon class="kh-toc__caret-icon" :class="{ 'is-open': expandedMap[child.flatIndex] }">
                  <ArrowRight />
                </el-icon>
              </button>
              <span v-else class="kh-toc__caret is-leaf" />
              <span class="kh-toc__text kh-line-clamp-2">{{ child.item.text }}</span>
            </div>
            <ul v-if="child.children.length && expandedMap[child.flatIndex]" class="kh-toc__sub">
              <li v-for="gchild in child.children" :key="gchild.flatIndex">
                <div
                  :ref="setItemRef(gchild.flatIndex)"
                  class="kh-toc__item"
                  :class="{ 'is-active': activeIndex === gchild.flatIndex }"
                  role="button"
                  tabindex="0"
                  @click="onSelect(gchild)"
                  @keydown.enter.prevent="onSelect(gchild)"
                >
                  <span class="kh-toc__caret is-leaf" />
                  <span class="kh-toc__text kh-line-clamp-2">{{ gchild.item.text }}</span>
                </div>
              </li>
            </ul>
          </li>
        </ul>
      </li>
    </ul>
    <div v-else class="kh-toc__empty">暂无目录</div>
  </KhCard>
</template>

<style scoped>
.kh-toc__head {
  display: flex;
  align-items: center;
  gap: 8px;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  margin-bottom: var(--kh-space-3);
}
.kh-toc__root,
.kh-toc__sub {
  list-style: none;
  margin: 0;
  padding: 0;
}
.kh-toc__root {
  display: flex;
  flex-direction: column;
  gap: 1px;
  /* 目录卡固定的最大长度（≈视口 40%），目录超长时**列表内部滚动**——卡片自身大小固定不滚，
     外层 aside 也不滚（避免卡片整列跳动）。 */
  max-height: var(--kh-toc-max-height, 40vh);
  overflow-y: auto;
  scrollbar-width: thin;
  padding-right: 2px;
}
.kh-toc__root::-webkit-scrollbar { width: 6px; }
.kh-toc__root::-webkit-scrollbar-thumb { background: var(--kh-border); border-radius: 3px; }
.kh-toc__sub { padding-left: 18px; }

.kh-toc__item {
  display: flex;
  align-items: flex-start;
  gap: 4px;
  padding: 5px 8px 5px 2px;
  border-radius: var(--kh-radius-sm);
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.kh-toc__item:hover { background: var(--kh-surface-muted); }
.kh-toc__item:hover .kh-toc__text { color: var(--kh-primary); }
.kh-toc__item:focus-visible {
  outline: 2px solid var(--kh-primary-border);
  outline-offset: -2px;
}
/* 当前阅读高亮：仅改字体色（用户要求），所有层级一致样式，无背景/无左竖条。 */
.kh-toc__item.is-active .kh-toc__text {
  color: var(--kh-primary-strong);
  font-weight: 600;
}
.kh-toc__item.is-active .kh-toc__caret {
  color: var(--kh-primary);
}

.kh-toc__caret {
  flex: none;
  width: 16px;
  height: 18px;
  display: inline-flex;
  align-items: center;
  justify-content: center;
  background: transparent;
  border: none;
  padding: 0;
  color: var(--kh-text-tertiary);
  cursor: pointer;
}
.kh-toc__caret.is-leaf { cursor: default; }
.kh-toc__caret-icon { transition: transform var(--kh-transition-fast); font-size: 12px; }
.kh-toc__caret-icon.is-open { transform: rotate(90deg); }

/* 颜色统一：所有层级文字同样颜色（用户要求不要浓淡分明），无前置符号 */
.kh-toc__text {
  font-size: 13px;
  color: var(--kh-text-secondary);
  line-height: 1.5;
}
.kh-toc__empty {
  padding: var(--kh-space-3) 0;
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
  text-align: center;
}
</style>