/**
 * 文件作用：
 * 评论区配图上传-插入 composable（轻量 textarea 模式专用）。
 * 封装「点图按钮 → 弹文件选择 → checkFileAllowed COMMENT_IMAGE 预检 → 手动校验 2MB + 9 张上限 →
 *   presignedUploadFlow 预签名直传 → 拿 /file/resolve/{id} 相对引用 → 在 textarea 当前光标处插入 ![name](url) markdown」
 * 全流程，供 KhCommentInput 顶级轻量态图按钮与 KhCommentList 回复态内联图按钮复用，避免两处复制上传/插字逻辑。
 *
 * 富文模式（KhMarkdownEditor）不走本 composable——其工具栏图片按钮/拖拽/粘贴上传由编辑器内置
 *   handleUploadImage（KhMarkdownEditor.vue L102-123）直调 presignedUploadFlow insertImage，自带 maxFileSize 透传。
 * 回复态仅轻量（无 KhMarkdownEditor），故回复图按钮恒走本 composable。
 *
 * 光标插入：用 textarea.setRangeText(md, start, end, 'end') 原地替换选区并置光标到插入尾，再回写 contentRef
 *   （setRangeText 只改 textarea.value 不通知 v-model，须手动同步 ref；contentRef 是父级 v-model 的同一 ref）。
 *   上传串行（await presignedUploadFlow 每张），故一张插入后光标自然落在其尾，下一张续插其后，顺序不乱。
 *
 * 校验顺序：白名单（checkFileAllowed，拉 sys_config knowhub.file.type_whitelist）→ 单图 ≤2MB → 当前已插图片张数 < 9。
 *   任意一步不通过弹中文提示跳过该张，继续处理后续张（多选时部分失败不影响其它）。
 *   9 张相对路径占 ~234 字符，留 ~1766 给正文不撑爆 varchar(2000)；后端服务端长度兜底仍校 >2000 拒。
 */
import { ref, onBeforeUnmount, nextTick, type Ref } from 'vue'
import { ElMessage } from 'element-plus'
import { checkFileAllowed } from '@/utils/upload-whitelist'
import { presignedUploadFlow } from '@/utils/upload'

export interface UseImageInsertOptions {
  /** 轻量态 textarea 的元素 ref（富文模式/组件未挂时为 null，回退尾部追加） */
  textareaRef: Ref<HTMLTextAreaElement | null | undefined>
  /** 内容 v-model ref（与 textarea 同源，插入后回写同步父级） */
  contentRef: Ref<string>
  /** 文件业务类型（FileBusinessType code），默认 COMMENT_IMAGE */
  businessType?: string
  /** 访问模式，默认 PUBLIC（评论配图需公开读，渲染 <img> 直引） */
  access?: 'PUBLIC' | 'PRIVATE'
  /** 配图张数上限，默认 9（9 张相对路径占 ~234 字符，留 ~1766 给正文不撑爆 varchar 2000） */
  maxImages?: number
  /** 单图体积上限 MB，默认 2 */
  maxSizeMB?: number
  /** 文件选择框 accept，默认 'image/*' */
  accept?: string
  /** 是否允许多选，默认 true */
  multiple?: boolean
}

/** 匹配 markdown 图片 ![alt](url)：按张计数配图上限（url 不含括号，buildFileResolveUrl 产出 /file/resolve/{id}） */
const IMAGE_MD_RE = /!\[[^\]]*\]\([^)]*\)/g

export function useImageInsert(options: UseImageInsertOptions) {
  const {
    textareaRef,
    contentRef,
    businessType = 'COMMENT_IMAGE',
    access = 'PUBLIC',
    maxImages = 9,
    maxSizeMB = 2,
    accept = 'image/*',
    multiple = true,
  } = options

  /** 上传进行中标志：整批文件串行传完归 false，按钮据此显「上传中…」并禁用 */
  const uploading = ref(false)

  // 自管隐藏 <input type="file">：动态建一个挂 body，点图按钮 pickFiles 触发其 click，
  //   避免调用方在模板里再插隐藏 input（回复态内联条等场景模板不便加）。unmount 时移除防泄漏。
  let inputEl: HTMLInputElement | null = null
  const ensureInput = () => {
    if (inputEl) return inputEl
    inputEl = document.createElement('input')
    inputEl.type = 'file'
    inputEl.accept = accept
    inputEl.multiple = multiple
    inputEl.style.display = 'none'
    inputEl.addEventListener('change', () => {
      const files = inputEl?.files
      void handleFiles(files ? Array.from(files) : [])
      // 清空以便下次相同文件仍能触发 change（不重启 input 元素）
      if (inputEl) inputEl.value = ''
    })
    document.body.appendChild(inputEl)
    return inputEl
  }
  onBeforeUnmount(() => {
    if (inputEl) {
      inputEl.remove()
      inputEl = null
    }
  })

  /**
   * 在 textareaRef 当前选区处插入文本：用 setRangeText 原地替换并置光标到插入尾，
   *   再回写 contentRef（setRangeText 不通知 v-model，须手动同步父级 ref）。
   *   无 textarea（富文模式 / 组件未挂）时回退到尾部追加并主动同步 ref。
   */
  const insertAtCursor = async (md: string) => {
    const ta = textareaRef.value
    if (!ta) {
      contentRef.value = contentRef.value + md
      return
    }
    const start = ta.selectionStart ?? contentRef.value.length
    const end = ta.selectionEnd ?? contentRef.value.length
    ta.setRangeText(md, start, end, 'end')
    contentRef.value = ta.value
    // setRangeText 已同步 textarea.value 与选区；等 nextTick 让 v-model 读取我们的 ref 后再 focus，避免抢光标
    await nextTick()
    ta.focus()
  }

  /**
   * 逐张处理选中的文件：白名单 → 2MB → 当前配图张数 < 上限 → presignedUploadFlow → 光标插入 markdown。
   * 上传串行（await 每张），uploading 在整批期间显真，终态归 false。部分张失败不影响其它。
   */
  const handleFiles = async (files: File[]) => {
    if (!files || files.length === 0) return
    uploading.value = true
    for (const file of files) {
      // 1. 白名单预检（拉 sys_config knowhub.file.type_whitelist 的 COMMENT_IMAGE 项）
      const allowed = await checkFileAllowed(file, businessType)
      if (!allowed.ok) {
        ElMessage.error(allowed.reason ?? '该文件类型不在允许范围')
        continue
      }
      // 2. 单图体积上限
      if (file.size > maxSizeMB * 1024 * 1024) {
        ElMessage.error(`图片不超过 ${maxSizeMB}MB`)
        continue
      }
      // 3. 当前已插图片张数（含本批之前已成功插入的）< 上限，到顶即拦整批后续张
      const currentCount = (contentRef.value.match(IMAGE_MD_RE) ?? []).length
      if (currentCount >= maxImages) {
        ElMessage.warning(`最多 ${maxImages} 张图片`)
        break
      }
      // 4. 预签名直传（apply token → PUT → confirm），PUBLIC 回填 /file/resolve/{id}
      let url: string | undefined
      try {
        const result = await presignedUploadFlow({ file, businessType, access })
        url = result.publicUrl
      } catch {
        // http 拦截器已弹错，此处不再重复提示，跳过该张继续后续
        continue
      }
      if (!url) {
        continue
      }
      // 5. 在 textarea 当前光标处插入 ![name](url) markdown
      await insertAtCursor(`![${file.name}](${url})`)
    }
    uploading.value = false
  }

  /** 点图按钮调用：触发隐藏文件选择框（须由用户手势发起的 click 调用，浏览器才放行选框） */
  const pickFiles = () => {
    ensureInput().click()
  }

  return { uploading, pickFiles }
}