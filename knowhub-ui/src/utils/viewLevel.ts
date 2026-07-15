/**
 * 文件作用：
 * 查看等级（与项目/文章对齐：1 公开 / 2 内部 / 3 机密）的展示映射。
 * 卡片/详情/筛选统一用文字「公开/内部/机密」，不用 L1/L2/L3 编号展示。
 * 搜索/筛选不涉及查看等级（后端按当前用户查询等级返回可见内容），前端只做展示映射。
 */

export type ViewLevel = 1 | 2 | 3

export const viewLevelLabel: Record<ViewLevel, string> = {
  1: '公开',
  2: '内部',
  3: '机密',
}

/** 等级 → KhTag type 着色：公开低调、内部提醒、机密警示 */
export const viewLevelTagType: Record<ViewLevel, 'neutral' | 'info' | 'warning' | 'danger'> = {
  1: 'neutral',
  2: 'info',
  3: 'danger',
}

/** 取等级文字，非法值兜底「未知」 */
export const getViewLevelLabel = (level: number): string =>
  (viewLevelLabel as Record<number, string>)[level] ?? '未知'
