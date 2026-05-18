export type PromptMode = 'prompt' | 'notice'

export interface PromptAction {
  label: string
  value: string
  tone?: 'default' | 'primary' | 'danger'
}

export interface PromptNoticeMeta {
  publisher: string
  publishTime: string
  category?: string
}

export interface PromptPanelProps {
  visible: boolean
  mode: PromptMode
  title: string
  content: string
  cancelAction?: PromptAction
  confirmAction?: PromptAction
  noticeMeta?: PromptNoticeMeta
}
