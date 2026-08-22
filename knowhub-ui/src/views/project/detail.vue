<!--
  项目详情 /project/:id
  ------------------------------------------------------------------
  数据源：后端 /portal/project/{id}（详情，越级锁态降级）+ /file-tree + /members + /related + /file/download/{fileId}。
  无封面项目头（类型/等级标签 + 标题 + 摘要 + 负责人 + 下载/浏览量）+ 子页面切换（项目介绍 / 项目文件）+ 右栏（项目信息 + 参与人员）。
  越级锁态：level > userViewLevel 时 description 置 null + locked=true + lockReason 顶栈 ElMessage 提示 + 介绍区显示锁态占位（含 lockReason）。
  项目文件：GitHub 式扁平→树内存组装（按 parentId），文件叶子可下载（canDownload 控制按钮显隐）。
  说明：原 mock 的评分 rating / 比赛子表 competition / "最活跃" 徽标均未在后端 ProjectPortalDetailVo 上，
  按 README.dev §11.1 私加字段方向去除，保留 PUBLISHED 状态/类型/等级 + 计数。
-->
<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElButton, ElDialog, ElFormItem, ElInput, ElImageViewer, ElMessage, ElMessageBox, ElSelect, ElOption, ElUpload } from 'element-plus'
import type { UploadFile } from 'element-plus'
import { ArrowLeft, Download, Folder, Document, Edit, UploadFilled, FolderAdd, ChatDotRound } from '@element-plus/icons-vue'
import http from '@/utils/http'
import KhCard from '@/components/common/KhCard.vue'
import KhTag from '@/components/common/KhTag.vue'
import KhAvatar from '@/components/common/KhAvatar.vue'
import KhStatPill from '@/components/common/KhStatPill.vue'
import KhIcon from '@/components/common/KhIcon.vue'
import KhLoading from '@/components/common/KhLoading.vue'
import KhCommentList from '@/components/common/KhCommentList.vue'
import { useMarkdownImageZoom } from '@/composables/useMarkdownImageZoom'
import { useMarkdownCodeBlock } from '@/composables/useMarkdownCodeBlock'
import ProjectMemberPanel from '@/components/project/ProjectMemberPanel.vue'
import {
  getProjectDetailApi,
  getProjectFileTreeApi,
  getProjectMembersApi,
  relatedProjectsApi,
  downloadProjectFileApi,
} from '@/api/knowhub/project-portal'
import {
  getProjectFileTreeAuthoringApi,
  addProjectFolderApi,
  addProjectFileNodeApi,
  editProjectFileNodeApi,
  deleteProjectFileNodeApi,
  downloadProjectFileAuthoringApi,
  getProjectForEditApi,
  listProjectMembersApi,
  toggleProjectCollectApi,
} from '@/api/knowhub/project-authoring'
import {
  PROJECT_BUSINESS_TYPE,
  PROJECT_FILE_IS_DIR,
} from '@/types/api/knowhub/project-authoring'
import type {
  ProjectPortalDetailRecord,
  ProjectFileRecord,
  ProjectMemberRecord,
} from '@/types/api/knowhub/project-portal'
import type {
  ProjectFileTreeNode,
} from '@/types/api/knowhub/project-authoring'
import { useUserStore } from '@/stores/user'
import { formatDateTime } from '@/utils/format'
import { getViewLevelTagType, getViewLevelLabel } from '@/utils/viewLevel'
import { presignedUploadFlow } from '@/utils/upload'
import { checkFileAllowed, formatAllowedHint } from '@/utils/upload-whitelist'

const route = useRoute()
const router = useRouter()
const userStore = useUserStore()

const projectId = computed(() => Number(route.params.id))

/**
 * 详情页本地模型：在公开 ProjectPortalDetailRecord 基础上扩展 authoring 回填的权限态/状态字段。
 * 数据源双轨：登录态优先 getProjectForEditApi（拿 canEdit/canDownload/myMemberRole/status/description，
 * 草稿也能拿）；失败/未登录 fallback getProjectDetailApi（公开锁态，仅 canDownload+locked+元数据）。
 */
type DetailModel = ProjectPortalDetailRecord & {
  /** 后端 ProjectVo 回填：当前用户对该项目的编辑权限态 */
  canEdit?: boolean
  /** 当前用户在该项目的成员角色（LEADER/MENTOR/MEMBER，无则 null/未填） */
  myMemberRole?: string
  /** 项目状态：DRAFT/PUBLISHED/REVOKED/PENDING_REVIEW/REJECTED/ARCHIVED，驱动状态标签 */
  status?: string
}

const project = ref<DetailModel | null>(null)
/** 项目介绍正文 DOM ref：v-md-preview 在其内渲染，配图点击放大委托该容器的 <img> */
const contentRef = ref<HTMLElement | null>(null)
/** 项目介绍配图点击放大（与博客/文章正文同款 el-image-viewer 全屏画廊），复用 contentRef 委托 <img> */
const { viewerVisible, viewerUrls, viewerIndex, onContentClick, closeViewer } = useMarkdownImageZoom(contentRef)
// 代码块增强（语言标签 + 复制按钮）：与配图放大共用同一 contentRef，正交不冲突
useMarkdownCodeBlock(contentRef)
const files = ref<ProjectFileRecord[]>([])
const members = ref<ProjectMemberRecord[]>([])
const loading = ref(false)
/** 权限态：能否管文件/项目信息（作者 OR canEdit=true OR LEADER）；
 *  含接受邀请获 canEdit=1 的成员（可编辑文件/项目信息，但不可管成员，见 canManageMembers） */
const canManage = ref(false)
/** 权限态：能否管成员（仅作者 OR LEADER）。非负责人不显示成员管理入口，即便 canEdit=1 也不行。 */
const canManageMembers = ref(false)
/** 收藏交互态：interacting 期间禁用按钮防重复点击 */
const interacting = ref(false)
/** 成员管理弹窗显隐 */
const memberDialogVisible = ref(false)

const typeLabel: Record<string, string> = { COMPETITION: '比赛项目', PRACTICE: '练习项目', OPS: '运维项目' }
const memberRoleLabel: Record<string, string> = { LEADER: '负责人', MENTOR: '导师', MEMBER: '成员' }
/** 状态标签映射（与 profile statusMeta 同口径） */
const statusMeta: Record<string, { text: string; type: 'success' | 'warning' | 'danger' | 'neutral' | 'info' }> = {
  PUBLISHED: { text: '已发布', type: 'success' },
  PENDING_REVIEW: { text: '待审核', type: 'warning' },
  REJECTED: { text: '已驳回', type: 'danger' },
  DRAFT: { text: '草稿', type: 'neutral' },
  REVOKED: { text: '已撤回', type: 'neutral' },
  ARCHIVED: { text: '已归档', type: 'info' },
}

const goBack = () => router.back()

const leader = computed(() => project.value?.authorNickname ?? '未知负责人')
const statusTag = computed(() => {
  const s = project.value?.status
  return s ? (statusMeta[s] ?? { text: s, type: 'neutral' as const }) : { text: '已发布', type: 'success' as const }
})

/** 子页面切换：介绍 / 项目文件 / 评论 */
type SubTab = 'intro' | 'files' | 'comments'
const activeTab = ref<SubTab>('intro')

/**
 * 当前用户是否该项目作者（评论区 isAuthor flag：作者可 inline 精选 + 删任意评论）。
 * 与 canManage 不同：canManage 含 LEADER 成员/canEdit 成员，但评论作者的「精选/删任意」
 * 仅归作品作者（创建者）一人；author 来自 portal 详情 authorId 或 authoring e.authorId 比对。
 */
const isProjectAuthor = computed(
  () => Boolean(project.value?.authorId) && project.value!.authorId === userStore.userInfo?.userId,
)

/**
 * 扁平文件列表按 parentId 内存组装为树。后端 listFiles 返回扁平带 parentId
 * （与 admin listFiles 同口径），前端按 parentId=0/null 收集根节点，递归挂 children。
 */
interface FileTreeNode extends ProjectFileRecord {
  children?: FileTreeNode[]
}
const fileTree = computed<FileTreeNode[]>(() => {
  const nodes = files.value.map((f) => ({ ...f, children: [] as FileTreeNode[] }))
  const byId = new Map<number, FileTreeNode>()
  const roots: FileTreeNode[] = []
  for (const n of nodes) {
    if (n.fileId != null) byId.set(n.fileId, n)
  }
  for (const n of nodes) {
    const pid = n.parentId
    if (pid == null || pid === 0 || !byId.has(pid)) {
      roots.push(n)
    } else {
      byId.get(pid)?.children?.push(n)
    }
  }
  // 子节点按 sort 升序兜底
  const sortRec = (list: FileTreeNode[]) => {
    list.sort((a, b) => (a.sort ?? 0) - (b.sort ?? 0))
    list.forEach((n) => n.children && sortRec(n.children))
  }
  sortRec(roots)
  return roots
})

/** 文件树展开状态（按 fileId 记录）；首屏根目录默认展开 */
const expanded = ref<Record<number, boolean>>({})
const isNodeOpen = (node: FileTreeNode, depth: number) => expanded.value[node.fileId ?? -1] ?? depth === 0
const toggleNode = (node: FileTreeNode) => {
  if (node.fileId != null && node.isDir === 1) expanded.value[node.fileId] = !expanded.value[node.fileId]
}

/** 拍平后的可视节点列表（含缩进层级），GitHub 式拍平渲染 */
const visibleNodes = computed(() => {
  const out: { node: FileTreeNode; depth: number }[] = []
  const walk = (nodes: FileTreeNode[], depth: number) => {
    for (const n of nodes) {
      out.push({ node: n, depth })
      if (n.isDir === 1 && isNodeOpen(n, depth) && n.children) walk(n.children, depth + 1)
    }
  }
  walk(fileTree.value, 0)
  return out
})

/**
 * 可选父文件夹列表（用于上传/新建文件夹/重命名弹窗的"所属文件夹"下拉）。
 * 含根目录项 {fileId: 0, path: '根目录' } + 所有目录节点的"路径"展示（祖先名拼成前缀，便于同名文件夹区分）。
 */
const folderOptions = computed<{ fileId: number; path: string }[]>(() => {
  const pathMap = new Map<number, string>()
  const paths: { fileId: number; path: string }[] = [{ fileId: 0, path: '根目录' }]
  const build = (nodes: FileTreeNode[], prefix: string) => {
    for (const n of nodes) {
      const p = prefix ? `${prefix} / ${n.name}` : n.name
      if (n.fileId != null) pathMap.set(n.fileId, p)
      if (n.isDir === 1) {
        paths.push({ fileId: n.fileId!, path: p })
        if (n.children) build(n.children, p)
      }
    }
  }
  build(fileTree.value, '')
  return paths
})

/**
 * 拉取详情 + 文件树 + 参与人员。
 * 数据源双轨（详情页收口管理功能的核心）：
 * - 登录态优先调 getProjectForEditApi（/authoring/project/{id}）：拿全态含 canEdit/canDownload/myMemberRole/
 *   status/description（作者看自己草稿也放行，不强制 PUBLISHED），据此在详情页直接管文件/成员/编辑项目信息。
 * - 失败/未登录 fallback getProjectDetailApi（/portal/project/{id}）：公开锁态，仅 canDownload+locked+元数据，
 *   不下发内部权限态（铁律：不向访客暴露 canEdit/myMemberRole），游客仅看 PUBLISHED。
 * 文件树/成员接口同轨：授权态走 /authoring（草稿也能拉），公开态走 /portal（PUBLISHED only）。
 */
const fetchDetail = async () => {
  loading.value = true
  try {
    let authorized = false
    if (userStore.isAuthenticated) {
      // 优先 authoring：作者/有项目权限成员能拿全态（含草稿）
      try {
        const editRes = await getProjectForEditApi(projectId.value)
        const e = editRes.data
        if (e) {
          project.value = {
            projectId: e.projectId,
            title: e.title,
            type: e.type,
            level: e.level,
            summary: e.summary,
            description: e.description,
            authorId: e.authorId,
            authorNickname: e.authorNickname,
            status: e.status,
            publishTime: e.publishTime ? (formatDateTime(e.publishTime) as string) : undefined,
            // authoring 返回不带 view/like/collect/download 计数（ProjectVo 列表无冗余计数列场景下为空），
            // 详情页头部计数展示允许为空，卡片照常渲染 0。
            viewCount: 0,
            likeCount: 0,
            collectCount: 0,
            downloadCount: 0,
            canDownload: e.canDownload !== false,
            // 授权态无越级锁态概念（作者或有 view 权限成员可看全文），description 已下发即 locked=false
            locked: false,
            lockReason: null,
            canEdit: e.canEdit === true,
            myMemberRole: e.myMemberRole,
            commentEnabled: e.commentEnabled ?? 1,
            commentCurated: e.commentCurated ?? 0,
          }
          // 作者全权：author_id === 当前登录用户 → 即便后端 canEdit 因成员记录缺失/权限点缺位回填为 false，
          // 也强制可管（作者天然可管自己项目，后端 service canOp 对 LEADER 全权，作者即创建者=LEADER）。
          const isAuthor = e.authorId != null && userStore.userInfo?.userId === e.authorId
          canManage.value = isAuthor || e.canEdit === true || e.myMemberRole === 'LEADER'
          canManageMembers.value = isAuthor || e.myMemberRole === 'LEADER'
          authorized = true
          // authoring 返回不带互动态（hasCollected）与计数（ProjectVo 无冗余计数列场景下为空）。
          // 后台静默补拉一次公开 portal 详情：PUBLISHED 项目能拿到 hasCollected + 真实 view/like/collect/download 计数，
          // 合并进本地模型（草稿项目 portal 返回 null → 跳过，保留 0 与 undefined 态，收藏按钮仍可点）。
          try {
            const portalRes = await getProjectDetailApi(projectId.value)
            const p = portalRes.data
            if (p) {
              project.value!.hasCollected = p.hasCollected ?? project.value!.hasCollected
              project.value!.viewCount = p.viewCount ?? project.value!.viewCount
              project.value!.likeCount = p.likeCount ?? project.value!.likeCount
              project.value!.collectCount = p.collectCount ?? project.value!.collectCount
              project.value!.downloadCount = p.downloadCount ?? project.value!.downloadCount
            }
          } catch {
            // portal 拉取失败（草稿非公开/越级锁态等）不阻塞 authoring 态展示，保持 0 与 undefined
          }
        }
      } catch {
        // 未登录/无权/草稿非本人 → 落到公开 portal 接口
      }
    }
    if (!authorized) {
      const detailRes = await getProjectDetailApi(projectId.value)
      const d = detailRes.data
      if (!d) {
        // 项目不存在或已下架：跳专门 404 页（404 页文案自带描述，不再弹红条避免重复提示）
        router.replace({ name: 'not-found' })
        return
      }
      project.value = {
        ...d,
        publishTime: d.publishTime ? (formatDateTime(d.publishTime) as string) : d.publishTime,
        canEdit: false,
        myMemberRole: undefined,
      }
      // 公开接口不下发 canEdit/myMemberRole（service 不回填），但作者本人天然可管：
      // d.authorId 与当前登录用户匹配 → 强制可管（管理操作走 /authoring/**，后端 service LEADER 全权兜底）。
      const autoIsAuthor = d.authorId != null && userStore.isAuthenticated && userStore.userInfo?.userId === d.authorId
      canManage.value = autoIsAuthor
      canManageMembers.value = autoIsAuthor
      authorized = autoIsAuthor
      if (d.locked) {
        // 越级锁态：后端返回 locked=true + lockReason，description 仍下发（决策#5，项目越级只锁下载不锁 description）
        ElMessage.warning(d.lockReason ?? '当前项目需更高权限下载完整内容')
      }
    }

    // 文件树/成员：授权态走 authoring（草稿也拉得到），公开态走 portal（PUBLISHED only）
    if (authorized && canManage.value) {
      const [fileRes, memRes] = await Promise.all([
        getProjectFileTreeAuthoringApi(projectId.value),
        listProjectMembersApi(projectId.value),
      ])
      // authoring 树形接口已带 children，拍扁给现有 visibleNodes 渲染
      const flat: ProjectFileRecord[] = []
      const walk = (nodes: ProjectFileTreeNode[] | undefined) => {
        for (const n of nodes ?? []) {
          flat.push({
            fileId: n.fileId!,
            projectId: n.projectId ?? projectId.value,
            parentId: n.parentId,
            name: n.name,
            isDir: n.isDir,
            objectId: n.objectId,
            sort: n.sort,
            originalName: n.originalName,
            contentLength: n.contentLength,
            contentType: n.contentType,
            businessType: n.businessType,
            createTime: n.createTime,
            updateTime: n.updateTime,
          })
          walk(n.children)
        }
      }
      walk(fileRes.data ?? [])
      files.value = flat.map((f) => ({
        ...f,
        createTime: f.createTime ? (formatDateTime(f.createTime) as string) : f.createTime,
      }))
      // authoring 成员记录含 canView/canDownload/canEdit 标志位，但参与人员展示不带（与公开口径一致）
      members.value = (memRes.data ?? []).map((m) => ({
        memberId: m.memberId,
        projectId: m.projectId,
        userId: m.userId,
        memberRole: m.memberRole,
        nickname: m.nickname,
        username: m.username,
      })) as ProjectMemberRecord[]
    } else {
      const [fileRes, memRes] = await Promise.all([
        getProjectFileTreeApi(projectId.value),
        getProjectMembersApi(projectId.value),
      ])
      files.value = (fileRes.data ?? []).map((f) => ({
        ...f,
        createTime: f.createTime ? (formatDateTime(f.createTime) as string) : f.createTime,
      }))
      members.value = memRes.data ?? []
      // 越级锁态文件树占位由模板 v-if="project.locked" 持久渲染（锁图标+lockReason），不再靠切 tab toast 提示。
    }
  } finally {
    loading.value = false
  }
}

const related = ref<ProjectPortalDetailRecord[]>([])
const fetchRelated = async () => {
  const res = await relatedProjectsApi(projectId.value, 3)
  related.value = (res.data ?? []).map((r) => ({
    ...r,
    publishTime: r.publishTime ? (formatDateTime(r.publishTime) as string) : r.publishTime,
  })) as ProjectPortalDetailRecord[]
}

/** 字节大小格式化 B/KB/MB/GB（对齐后台 ProjectFileTree formatSize） */
const formatSize = (len?: number | null) => {
  if (len == null) return '--'
  if (len < 1024) return `${len} B`
  if (len < 1024 * 1024) return `${(len / 1024).toFixed(1)} KB`
  if (len < 1024 * 1024 * 1024) return `${(len / 1024 / 1024).toFixed(1)} MB`
  return `${(len / 1024 / 1024 / 1024).toFixed(2)} GB`
}

/** 文件下载：授权态走 /authoring/project/file/download（作者/有下载权成员均放行），否则公开 /portal/project/file/download */
const handleDownloadFile = async (node: FileTreeNode) => {
  if (node.isDir === 1) return
  if (project.value?.canDownload === false) {
    ElMessage.warning('无权下载该文件（需更高下载权限）')
    return
  }
  try {
    const res = canManage.value
      ? await downloadProjectFileAuthoringApi(node.fileId!)
      : await downloadProjectFileApi(node.fileId!)
    const url = res.data
    if (!url) {
      ElMessage.error('获取下载链接失败')
      return
    }
    window.open(url, '_blank', 'noopener')
  } catch {
    // http 拦截器已弹错误提示
  }
}

// ============================ 文件/文件夹管理（仅授权态 canManage 入口显示） ============================
/** 当前业务类型 PROJECT_DOC 允许的文件类型文案，灰字提示（上传 UI 用，空白名单=不限） */
const allowedHint = ref('')
/** 新建文件夹弹窗态（parentId 默认 0=根目录） */
const folderDialogVisible = ref(false)
const folderForm = ref<{ name: string; parentId: number }>({ name: '', parentId: 0 })
/** 上传文件弹窗态：选 file → 预检 → presigned 上传 → 挂节点 → fetchDetail 刷新 */
const uploadDialogVisible = ref(false)
const uploadFile = ref<File | null>(null)
const uploading = ref(false)
const uploadPercent = ref(0)
const uploadParentId = ref(0)
/** 重命名弹窗态（parentId 可改=移动所属文件夹） */
const renameDialogVisible = ref(false)
const renameForm = ref<{ fileId: number; name: string; parentId: number }>({ fileId: 0, name: '', parentId: 0 })

/** 拉一次白名单提示文案（onMounted 调，canManage 与否都拉，无副作用） */
const loadAllowedHint = async () => {
  try {
    allowedHint.value = await formatAllowedHint(PROJECT_BUSINESS_TYPE.DOC)
  } catch {
    allowedHint.value = ''
  }
}

/** 打开新建文件夹弹窗（parentId 默认 0=根目录） */
const openFolderDialog = () => {
  folderForm.value = { name: '', parentId: 0 }
  folderDialogVisible.value = true
}

const submitFolder = async () => {
  if (!projectId.value || !folderForm.value.name.trim()) {
    ElMessage.warning('请输入文件夹名称')
    return
  }
  try {
    await addProjectFolderApi({
      projectId: projectId.value,
      parentId: folderForm.value.parentId || undefined,
      name: folderForm.value.name.trim(),
      isDir: PROJECT_FILE_IS_DIR,
      sort: 0,
    })
    ElMessage.success('文件夹创建成功')
    folderDialogVisible.value = false
    await fetchDetail()
  } catch {
    // http.ts 已弹错
  }
}

/** 打开上传文件弹窗（重置选中的 file 与目标父文件夹） */
const openUploadDialog = () => {
  uploadFile.value = null
  uploadPercent.value = 0
  uploadParentId.value = 0
  uploadDialogVisible.value = true
}

/** ElUpload on-change：选/拖入文件后存到 uploadFile（单文件，覆盖式） */
const handleUploadFileChange = (uf: UploadFile) => {
  uploadFile.value = uf.raw ?? null
}

/** 项目文件访问语义：L1（及前置未知等级）→ PUBLIC（公开）；L2/L3 → PRIVATE（私有）。
 *  与后端 file_object.access 落库一致；项目层已 canDownload 鉴权，文件层走 bizAuthorized=true 跳过 owner 闸，
 *  access 仅决定 PUBLIC 直走 /file/public 回显 vs PRIVATE 走 /file/proxy 预签名/中转（强制文件名）。
 *  level 缺省（undefined/null）按最低等级 L1=公开处理，与详情兜底 ??1 口径一致。 */
const fileAccessForLevel = (level: number | undefined | null): 'PUBLIC' | 'PRIVATE' =>
  level != null && level >= 2 ? 'PRIVATE' : 'PUBLIC'

/** 提交上传：预检白名单 → presignedUploadFlow（businessType=PROJECT_DOC, access 按项目等级派生 L1=PUBLIC 否则 PRIVATE）→ 挂节点 → fetchDetail */
const submitUpload = async () => {
  const file = uploadFile.value
  if (!file || !projectId.value) {
    ElMessage.warning('请选择要上传的文件')
    return
  }
  // 白名单预检：不通过直接提示允许类型清单，不发请求
  const check = await checkFileAllowed(file, PROJECT_BUSINESS_TYPE.DOC)
  if (!check.ok) {
    ElMessage.warning(check.reason ?? '该文件类型不在允许范围')
    return
  }
  uploading.value = true
  uploadPercent.value = 0
  try {
    const result = await presignedUploadFlow({
      file,
      businessType: PROJECT_BUSINESS_TYPE.DOC,
      access: fileAccessForLevel(project.value?.level),
      onProgress: (percent) => {
        uploadPercent.value = percent
      },
    })
    await addProjectFileNodeApi({
      projectId: projectId.value,
      parentId: uploadParentId.value || undefined,
      name: file.name,
      isDir: PROJECT_FILE_IS_DIR,
      objectId: result.objectId,
      sort: 0,
    })
    ElMessage.success('文件上传成功')
    uploadDialogVisible.value = false
    await fetchDetail()
  } catch {
    // http.ts 已弹错（含白名单后端口径偏差、上传失败等）
  } finally {
    uploading.value = false
    uploadPercent.value = 0
  }
}

/** 打开重命名弹窗：预填当前名与所属文件夹 */
const openRenameDialog = (node: FileTreeNode) => {
  if (node.fileId == null) return
  renameForm.value = { fileId: node.fileId, name: node.name, parentId: node.parentId ?? 0 }
  renameDialogVisible.value = true
}

const renameFolderOptions = computed(() => {
  const editingId = renameForm.value.fileId
  // 编辑目录自身不能作为自己的父；递归剔除自身及其子孙目录（防止挂到自己子树里形成环）
  const excluded = new Set<number>()
  if (editingId) {
    excluded.add(editingId)
    const collect = (nodes: FileTreeNode[]) => {
      for (const n of nodes) {
        if (n.fileId != null) excluded.add(n.fileId)
        if (n.children) collect(n.children)
      }
    }
    const findSelf = (nodes: FileTreeNode[]): FileTreeNode | undefined => {
      for (const n of nodes) {
        if (n.fileId === editingId) return n
        if (n.children) {
          const r = findSelf(n.children)
          if (r) return r
        }
      }
      return undefined
    }
    const self = findSelf(fileTree.value)
    if (self && self.children) collect(self.children)
  }
  return folderOptions.value.filter((o) => !excluded.has(o.fileId))
})

const submitRename = async () => {
  if (!renameForm.value.name.trim()) {
    ElMessage.warning('请输入名称')
    return
  }
  try {
    await editProjectFileNodeApi({
      fileId: renameForm.value.fileId,
      name: renameForm.value.name.trim(),
      parentId: renameForm.value.parentId || undefined,
    })
    ElMessage.success('保存成功')
    renameDialogVisible.value = false
    await fetchDetail()
  } catch {
    // http.ts 已弹错
  }
}

/** 删除文件/文件夹（后端级联软删子节点，前端 confirm 二次确认） */
const handleDeleteFile = async (node: FileTreeNode) => {
  if (node.fileId == null) return
  try {
    await ElMessageBox.confirm(
      `确认删除「${node.name}」吗？${node.isDir === 1 ? '文件夹下所有内容将被一并删除。' : ''}`,
      '删除文件',
      { type: 'warning' },
    )
    await deleteProjectFileNodeApi(node.fileId)
    ElMessage.success('删除成功')
    await fetchDetail()
  } catch {
    // 用户取消或 http.ts 已弹错
  }
}

/** 跳转编辑项目信息页（/project/create?id=，创作页状态机仍拦 PUBLISHED 须先撤回） */
const editProjectInfo = () => {
  router.push(`/project/create?id=${projectId.value}`)
}

/** 收藏/取消收藏项目：未登录跳登录 + redirect 回填来源；登录态乐观更新 hasCollected + collectCount（对齐 resource 详情范式）。
 *  项目无点赞链路（后端无 ProjectLike/事实表/toggle 端点），仅收藏。 */
const requireAuth = (): boolean => {
  if (!userStore.isAuthenticated) {
    ElMessage.warning('请先登录后再操作')
    router.push({ path: '/login', query: { redirect: route.fullPath } })
    return false
  }
  return true
}
const handleCollect = async () => {
  if (!project.value || !requireAuth()) return
  interacting.value = true
  try {
    const collected = !project.value.hasCollected
    await toggleProjectCollectApi(projectId.value, collected)
    project.value.hasCollected = collected
    project.value.collectCount = Math.max(0, (project.value.collectCount ?? 0) + (collected ? 1 : -1))
  } finally {
    interacting.value = false
  }
}

/**
 * 下载项目整包（zip）：后端 /portal/project/{id}/package 直接 stream application/zip 字节流。
 * 用 axios GET 拿 blob 再用 a.download 触发下载——可捕获后端 4xx/500 给明确 ElMessage，避免 iframe/window.open
 * 失败静默的"没反应"。代价：zip 全量进前端内存（项目文件聚合通常几 MB 级，可接受；超大项目再走流式优化）。
 * 两种访问模式（DIRECT/TRANSFER）下 zip 都只能走后端中转（OSS 无静态 zip 对象），URL 与 axios 同口径。
 */
const downloadProjectPackage = async () => {
  if (!projectId.value) return
  try {
    const res = await http.get(`/portal/project/${projectId.value}/package`, {
      responseType: 'blob',
      // 后端 4xx 也是 blob（纯状态码空体或错误页），不进 axios 拦截器 200 判定，按 status 判
      validateStatus: () => true,
    })
    if (res.status >= 400) {
      ElMessage.error('下载失败：无权下载或项目无文件')
      return
    }
    // zip 文件名用项目标题（与后端 Content-Disposition filename 同源），去 Windows 文件名非法字符；
    // 标题空时回落 project-{id}，与后端 sanitize 口径一致。
    const rawTitle = (project.value?.title ?? '').trim()
    const safeName = rawTitle
      ? rawTitle.replace(/["\\/:*?<>|]/g, '').trim() || `project-${projectId.value}`
      : `project-${projectId.value}`
    const blob = new Blob([res.data], { type: 'application/zip' })
    const href = URL.createObjectURL(blob)
    const a = document.createElement('a')
    a.href = href
    a.download = `${safeName}.zip`
    document.body.appendChild(a)
    a.click()
    a.remove()
    URL.revokeObjectURL(href)
  } catch {
    ElMessage.error('下载接口不可达，请检查后端是否已重启加载新接口')
  }
}

/** 打开成员管理弹窗 */
const openMemberDialog = () => {
  memberDialogVisible.value = true
}

onMounted(() => {
  void fetchDetail()
  void fetchRelated()
  void loadAllowedHint()
})
watch(projectId, () => {
  void fetchDetail()
  void fetchRelated()
})
</script>

<template>
  <div class="pd">
    <!-- 面包屑 -->
    <div class="kh-container kh-container--wide pd__crumb">
      <button class="pd__back" type="button" @click="goBack">
        <el-icon><ArrowLeft /></el-icon> 返回
      </button>
      <RouterLink to="/">首页</RouterLink>
      <el-icon class="pd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <RouterLink to="/projects">项目展示</RouterLink>
      <el-icon class="pd__crumb-sep"><KhIcon name="chevron-right" :size="12" /></el-icon>
      <span class="pd__crumb-current">{{ project?.title ?? '项目详情' }}</span>
    </div>

    <!-- 首屏取数加载占位：loading 期间显 KhLoading，替代头卡"加载中…"文案 + 主体空白帧 -->
    <KhLoading v-if="loading" title="正在加载项目…" />

    <!-- 项目头：无封面，标签 + 标题 + 摘要 + 元信息 + 管理按钮（授权态显隐） -->
    <div v-else class="kh-container kh-container--wide">
      <KhCard padding="lg" class="pd__head">
        <div v-if="project" class="pd__head-tags">
          <KhTag type="primary">{{ typeLabel[project.type ?? ''] ?? project.type ?? '项目' }}</KhTag>
          <KhTag :type="statusTag.type" dot>{{ statusTag.text }}</KhTag>
          <KhTag :type="getViewLevelTagType(project.level ?? 1)">{{ getViewLevelLabel(project.level ?? 1) }}</KhTag>
        </div>
        <div class="pd__head-titlerow">
          <h1 class="pd__title">{{ project?.title ?? '加载中…' }}</h1>
          <!-- 按钮组：收藏项目（登录态可点）+ 下载项目整包（canDownload 权限，zip 字节流浏览器原生下载）+ 编辑项目信息（授权态可管） -->
          <div class="pd__head-actions">
            <button
              class="pd__head-btn pd__head-btn--collect"
              :class="{ 'is-active': project?.hasCollected }"
              type="button"
              title="收藏项目"
              :disabled="interacting"
              @click="handleCollect"
            >
              <el-icon><FolderAdd /></el-icon> {{ project?.hasCollected ? '已收藏' : '收藏' }}
            </button>
            <button
              v-if="project?.canDownload"
              class="pd__head-btn pd__head-btn--download"
              type="button"
              title="下载项目整包（zip）"
              @click="downloadProjectPackage"
            >
              <el-icon><Download /></el-icon> 下载项目
            </button>
            <button v-if="canManage" class="pd__head-btn" type="button" title="编辑项目信息" @click="editProjectInfo">
              <el-icon><Edit /></el-icon> 编辑项目信息
            </button>
          </div>
        </div>
        <p class="pd__summary">{{ project?.summary ?? '' }}</p>

        <div v-if="project" class="pd__head-meta">
          <div class="pd__head-leader">
            <KhAvatar :item="{ label: leader, src: project?.authorAvatar ?? undefined }" :size="40" />
            <div>
              <div class="pd__head-leader-name" @click="project?.authorId && router.push(`/user/${project.authorId}`)">{{ leader }}</div>
              <div class="pd__head-leader-role">负责人 · {{ members.length }} 人团队</div>
            </div>
          </div>
          <div class="pd__head-stats">
            <KhStatPill icon="eye" :value="project.viewCount ?? 0" label="浏览" />
            <KhStatPill icon="download" :value="project.downloadCount ?? 0" label="下载" />
            <KhStatPill icon="clock" :value="project.publishTime ?? '--'" />
          </div>
        </div>
      </KhCard>
    </div>

    <!-- 主体布局：左子页面切换（介绍 / 项目文件），右栏（项目信息 + 参与人员） -->
    <div class="kh-container kh-container--wide pd__layout" v-if="project">
      <!-- 左：子页面 -->
      <div class="pd__main">
        <!-- 子页面切换页签 -->
        <div class="pd__tabs">
          <div class="pd__tabs-group">
            <button
              class="pd__tab"
              :class="{ 'is-active': activeTab === 'intro' }"
              type="button"
              @click="activeTab = 'intro'"
            >
              <KhIcon name="doc" :size="15" /> 项目介绍
            </button>
            <button
              class="pd__tab"
              :class="{ 'is-active': activeTab === 'files' }"
              type="button"
              @click="activeTab = 'files'"
            >
              <KhIcon name="file" :size="15" /> 项目文件
            </button>
            <button
              class="pd__tab"
              :class="{ 'is-active': activeTab === 'comments' }"
              type="button"
              @click="activeTab = 'comments'"
            >
              <el-icon><ChatDotRound /></el-icon> 评论
            </button>
          </div>
          <!-- 授权态可管：项目文件 tab 行右侧追加"新建文件夹 / 上传文件"按钮（与项目介绍/项目文件同 pill 同字号，
               无权限者不显示）；支持类型文案已在上传弹窗内展示，此处不再重复 -->
          <div v-if="canManage && activeTab === 'files'" class="pd__tabs-group pd__tabs-actions">
            <button class="pd__tab pd__tab-action" type="button" @click="openFolderDialog">
              <el-icon><FolderAdd /></el-icon> 新建文件夹
            </button>
            <button class="pd__tab pd__tab-action is-active" type="button" @click="openUploadDialog">
              <el-icon><UploadFilled /></el-icon> 上传文件
            </button>
          </div>
        </div>

        <!-- 子页面：项目介绍（越级锁态 description 仍下发可见——决策#5，项目越级只锁下载不锁 description；
             故介绍正文始终渲染，锁态提示移到文件 tab 的下载按钮处） -->
        <KhCard v-show="activeTab === 'intro'" padding="lg" class="pd__section">
          <div ref="contentRef" class="pd__content" @click="onContentClick">
            <v-md-preview :text="project.description ?? ''" />
          </div>
          <!-- 越级锁态提示条：项目越级 description 可见，但文件下载已锁（canDownload=false） -->
          <div v-if="project.locked" class="pd__lock-tip">
            <KhIcon name="lock" :size="14" :stroke="1.5" />
            <span>{{ project.lockReason ?? '需更高权限下载项目文件' }}</span>
          </div>
        </KhCard>

        <!-- 子页面：项目文件（GitHub 式文件树只读渲染；授权态每行追加重命名/删除按钮，无授权仅下载）。
             越级锁态(project.locked)时不渲染文件树，改显锁态占位——项目越级应锁文件查看（不只是锁下载按钮），
             与资源/项目介绍越级锁口径一致；后端 listFiles 的 canViewProject 已挡内容返空列表，前端门控是双重保险+明确提示。 -->
        <section v-show="activeTab === 'files'" class="pd__files">
          <!-- 越级锁态占位：锁图标 + lockReason，替代文件树（避免"暂无文件"误导用户以为项目真没文件） -->
          <div v-if="project.locked" class="pd__files-locked">
            <KhIcon name="lock" :size="32" :stroke="1.4" />
            <p class="pd__files-locked-title">{{ project.lockReason ?? '需更高权限查看项目文件' }}</p>
            <p class="pd__files-locked-hint">登录并拥有对应等级权限后可查看与下载项目文件</p>
          </div>
          <template v-else>
            <!-- 表头（仅桌面端；有权限加"操作"列） -->
            <div class="pd__files-head">
              <span class="pd__files-col pd__files-col--name">名称</span>
              <span class="pd__files-col pd__files-col--time">上传时间</span>
              <span class="pd__files-col pd__files-col--size">大小</span>
              <span class="pd__files-col pd__files-col--action" />
            </div>
            <div class="pd__files-body">
              <div
                v-for="item in visibleNodes"
                :key="item.node.fileId"
                class="pd__tree-node"
                :class="{ 'is-dir': item.node.isDir === 1, 'is-file': item.node.isDir !== 1 }"
                :style="{ paddingLeft: `${item.depth * 18 + 12}px` }"
                @click="toggleNode(item.node)"
              >
                <span class="pd__tree-caret" :class="{ 'is-leaf': item.node.isDir !== 1 }">
                  {{ item.node.isDir === 1 ? (isNodeOpen(item.node, item.depth) ? '▾' : '▸') : '' }}
                </span>
                <el-icon v-if="item.node.isDir === 1" class="pd__tree-icon"><Folder /></el-icon>
                <el-icon v-else class="pd__tree-icon"><Document /></el-icon>
                <span class="pd__tree-name">{{ item.node.name }}</span>
                <span class="pd__tree-time">{{ item.node.isDir === 1 ? '' : (item.node.createTime ? formatDateTime(item.node.createTime) : '--') }}</span>
                <span class="pd__tree-size">{{ item.node.isDir === 1 ? '' : formatSize(item.node.contentLength) }}</span>
                <span class="pd__tree-action">
                  <!-- 文件叶子：下载（无论是否有权限，有 canDownload 才可下载） -->
                  <button
                    v-if="item.node.isDir !== 1"
                    class="pd__tree-download"
                    type="button"
                    title="下载"
                    @click.stop="handleDownloadFile(item.node)"
                  >
                    <el-icon><Download /></el-icon>
                  </button>
                  <!-- 有权限：追加重命名、删除按钮 -->
                  <template v-if="canManage">
                    <button
                      class="pd__tree-btn"
                      type="button"
                      title="重命名"
                      @click.stop="openRenameDialog(item.node)"
                    >
                      <el-icon><Edit /></el-icon>
                    </button>
                    <button
                      class="pd__tree-btn pd__tree-btn--danger"
                      type="button"
                      title="删除"
                      @click.stop="handleDeleteFile(item.node)"
                    >
                      ✕
                    </button>
                  </template>
                </span>
              </div>
              <div v-if="!visibleNodes.length" class="pd__tree-empty">暂无文件</div>
            </div>
          </template>
        </section>

        <!-- 子页面：评论（项目正文重，用 tab 承载更干净；KhCommentList 内置发表条/列表/回复/作者 inline 精选）。
             越级锁态时传 locked 禁发评论（项目越级 description 可见只锁下载，评论列表照常可看，只锁发不锁看）。 -->
        <KhCard v-show="activeTab === 'comments'" padding="lg" class="pd__section">
          <KhCommentList
            biz-type="PROJECT"
            :biz-id="projectId"
            :comment-enabled="project.commentEnabled"
            :comment-curated="project.commentCurated"
            :is-author="isProjectAuthor"
            :locked="project.locked"
          />
        </KhCard>
      </div>

      <!-- 右：项目信息 + 参与人员（参与人员卡内部可滚动，坐落项目信息下方） -->
      <aside class="pd__aside">
        <KhCard padding="md" class="pd__info">
          <h3 class="pd__info-title">项目信息</h3>
          <div class="pd__info-row">
            <span>类型</span><b>{{ typeLabel[project.type ?? ''] ?? project.type ?? '--' }}</b>
          </div>
          <div class="pd__info-row">
            <span>等级</span><b>{{ getViewLevelLabel(project.level ?? 1) }}</b>
          </div>
          <div class="pd__info-row">
            <span>浏览</span><b>{{ project.viewCount ?? 0 }}</b>
          </div>
          <div class="pd__info-row">
            <span>下载</span><b>{{ project.downloadCount ?? 0 }}</b>
          </div>
          <div class="pd__info-row">
            <span>发布</span><b>{{ project.publishTime ?? '--' }}</b>
          </div>
        </KhCard>

        <KhCard padding="md" class="pd__members-card">
          <div class="pd__members-head">
            <h3 class="pd__members-title">参与人员 · {{ members.length }}</h3>
            <!-- 成员管理按钮：仅负责人/作者可管成员（canEdit=1 的成员可见但不能管成员） -->
            <button
              v-if="canManageMembers"
              class="pd__members-mgmt"
              type="button"
              title="成员管理"
              @click="openMemberDialog"
            >
              成员管理
            </button>
          </div>
          <div class="pd__members-scroll">
            <div
              v-for="m in members"
              :key="m.memberId ?? m.userId"
              class="pd__member"
              :class="`pd__member--${(m.memberRole ?? 'member').toLowerCase()}`"
            >
              <KhAvatar :item="{ label: m.nickname ?? m.username ?? '成员' }" :size="40" />
              <div class="pd__member-info">
                <div class="pd__member-name">{{ m.nickname ?? m.username ?? '未知' }}</div>
                <div class="pd__member-role">{{ memberRoleLabel[m.memberRole] ?? m.memberRole }}</div>
              </div>
              <KhTag v-if="m.memberRole === 'LEADER'" type="warm" size="sm">负责人</KhTag>
              <KhTag v-else-if="m.memberRole === 'MENTOR'" type="info" size="sm">导师</KhTag>
            </div>
            <div v-if="!members.length" class="pd__tree-empty">暂无成员</div>
          </div>

          <!-- 相关推荐（公开态才有 PUBLISHED 相关；草稿态不展示） -->
          <h3 v-if="related.length && !canManage" class="pd__members-title pd__related-title">相关项目 · {{ related.length }}</h3>
          <div v-if="related.length && !canManage" class="pd__related">
            <RouterLink
              v-for="r in related"
              :key="r.projectId"
              :to="`/project/${r.projectId}`"
              class="pd__related-item"
            >
              <KhTag size="sm" type="primary">{{ typeLabel[r.type ?? ''] ?? r.type }}</KhTag>
              <span class="pd__related-name kh-line-clamp-1">{{ r.title }}</span>
            </RouterLink>
          </div>
        </KhCard>
      </aside>
    </div>

    <!-- 成员管理弹窗（仅授权态可管时入口可达）。
         与内层"添加成员/编辑成员"子弹窗互斥显示：Panel 打开任一子弹窗时 emit
         add-dialog-change/edit-dialog-change=true → memberDialogVisible=false 关闭本外层
         弹窗（ElDialog 默认 destroy-on-close=false，关闭时 v-show 隐藏不卸载内部 Panel
         与已 append-to-body 的子弹窗）；子弹窗关闭 emit =false → 恢复 memberDialogVisible=true。 -->
    <ElDialog
      v-model="memberDialogVisible"
      title="成员管理"
      width="780px"
      append-to-body
    >
      <!-- Panel 始终挂载：子弹窗（append-to-body）独立浮于 body，关闭外层不会卸载它 -->
      <ProjectMemberPanel
        :project-id="projectId"
        @add-dialog-change="(v) => (memberDialogVisible = !v)"
        @edit-dialog-change="(v) => (memberDialogVisible = !v)"
      />
    </ElDialog>

    <!-- 新建文件夹弹窗（可选所属文件夹，默认根目录） -->
    <ElDialog v-model="folderDialogVisible" title="新建文件夹" width="460px" append-to-body>
      <ElFormItem label="所属文件夹">
        <ElSelect v-model="folderForm.parentId" style="width: 100%" placeholder="选择归属文件夹">
          <ElOption v-for="o in folderOptions" :key="o.fileId" :label="o.path" :value="o.fileId" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="文件夹名称" required>
        <ElInput v-model="folderForm.name" placeholder="请输入文件夹名称" maxlength="255" />
      </ElFormItem>
      <template #footer>
        <ElButton @click="folderDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitFolder">确定</ElButton>
      </template>
    </ElDialog>

    <!-- 上传文件弹窗（选目标文件夹 → 选 file → 预检白名单 → presigned 上传 → 挂节点 → 刷新） -->
    <ElDialog v-model="uploadDialogVisible" title="上传文件" width="540px" append-to-body>
      <div class="pd__upload">
        <ElFormItem label="所属文件夹" class="pd__upload-folder">
          <ElSelect v-model="uploadParentId" style="width: 100%" placeholder="选择归属文件夹">
            <ElOption v-for="o in folderOptions" :key="o.fileId" :label="o.path" :value="o.fileId" />
          </ElSelect>
        </ElFormItem>
        <ElUpload
          drag
          :auto-upload="false"
          :limit="1"
          :show-file-list="true"
          :on-change="handleUploadFileChange"
          accept=""
          class="pd__upload-dragger"
        >
          <el-icon class="el-icon--upload"><UploadFilled /></el-icon>
          <div class="el-upload__text">将文件拖到此处，或<em>点击选择</em></div>
          <template #tip>
            <div v-if="allowedHint" class="pd__upload-hint">支持类型：{{ allowedHint }}</div>
          </template>
        </ElUpload>
        <p v-if="uploading" class="pd__upload-progress">上传中 {{ uploadPercent }}%</p>
      </div>
      <template #footer>
        <ElButton @click="uploadDialogVisible = false" :disabled="uploading">取消</ElButton>
        <ElButton type="primary" :loading="uploading" :disabled="!uploadFile" @click="submitUpload">上传</ElButton>
      </template>
    </ElDialog>

    <!-- 编辑文件/文件夹弹窗（改名 + 改所属文件夹；编辑目录时下拉剔除自身及子孙防环） -->
    <ElDialog v-model="renameDialogVisible" title="编辑" width="460px" append-to-body>
      <ElFormItem label="所属文件夹">
        <ElSelect v-model="renameForm.parentId" style="width: 100%" placeholder="选择归属文件夹">
          <ElOption v-for="o in renameFolderOptions" :key="o.fileId" :label="o.path" :value="o.fileId" />
        </ElSelect>
      </ElFormItem>
      <ElFormItem label="名称" required>
        <ElInput v-model="renameForm.name" placeholder="请输入名称" maxlength="255" />
      </ElFormItem>
      <template #footer>
        <ElButton @click="renameDialogVisible = false">取消</ElButton>
        <ElButton type="primary" @click="submitRename">确定</ElButton>
      </template>
    </ElDialog>
    <!-- 项目介绍配图点击放大画廊（el-image-viewer，teleported 至 body 全屏，z-index 3000） -->
    <el-image-viewer
      v-if="viewerVisible"
      :url-list="viewerUrls"
      :initial-index="viewerIndex"
      :z-index="3000"
      hide-on-click-modal
      teleported
      @close="closeViewer"
    />
  </div>
</template>

<style scoped>
.pd__crumb {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding-top: var(--kh-space-5);
  padding-bottom: var(--kh-space-4);
  font-size: var(--kh-font-size-sm);
  color: var(--kh-text-tertiary);
}
.pd__back {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 4px 10px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  cursor: pointer;
  margin-right: var(--kh-space-3);
  font-size: 12px;
}
.pd__back:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.pd__crumb a {
  color: var(--kh-text-secondary);
}
.pd__crumb a:hover {
  color: var(--kh-primary);
}
.pd__crumb-sep {
  color: var(--kh-text-tertiary);
}
.pd__crumb-current {
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 280px;
}

.pd__head {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  overflow: hidden;
}
.pd__head-tags {
  display: flex;
  flex-wrap: wrap;
  align-items: center;
  gap: 6px;
}
.pd__title {
  font-size: var(--kh-font-size-3xl);
  font-weight: 700;
  letter-spacing: -0.01em;
}
.pd__summary {
  margin-top: var(--kh-space-2);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-md);
  line-height: 1.7;
}
.pd__head-meta {
  display: flex;
  align-items: center;
  gap: var(--kh-space-5);
  margin-top: var(--kh-space-2);
  padding-top: var(--kh-space-2);
  border-top: 1px solid var(--kh-border-soft);
  flex-wrap: wrap;
}
.pd__head-leader {
  display: flex;
  align-items: center;
  gap: 10px;
}
.pd__head-leader-name {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  cursor: pointer;
  transition: color var(--kh-transition-fast);
}
.pd__head-leader-name:hover {
  color: var(--kh-primary);
  text-decoration: underline;
}
.pd__head-leader-role {
  font-size: 11px;
  color: var(--kh-text-tertiary);
}
.pd__head-stats {
  display: flex;
  align-items: center;
  gap: var(--kh-space-5);
}

.pd__layout {
  display: grid;
  grid-template-columns: 1fr 320px;
  gap: var(--kh-space-6);
  align-items: start;
  margin-top: var(--kh-space-6);
  padding-bottom: var(--kh-space-12);
}
.pd__main {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  min-width: 0;
}

/* —— 子页面切换页签 —— */
.pd__tabs {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  flex-wrap: wrap;
}
.pd__tabs-group {
  display: flex;
  gap: 4px;
  padding: 4px;
  background: var(--kh-surface-muted);
  border-radius: var(--kh-radius-pill);
  align-self: flex-start;
}
.pd__tabs-actions {
  display: flex;
  align-items: center;
  gap: 4px;
  padding: 4px;
  background: var(--kh-surface-muted);
  border-radius: var(--kh-radius-pill);
  align-self: flex-start;
}
.pd__tab-action {
  gap: 4px;
  padding: 7px 14px;
  font-size: var(--kh-font-size-sm);
  border-radius: var(--kh-radius-pill);
}
.pd__tab-action .el-icon {
  font-size: 14px;
}
/* 上传文件：主色实心强调 */
.pd__tab-action.is-active {
  display: inline-flex;
  align-items: center;
  background: var(--kh-primary);
  color: var(--kh-surface);
  border: none;
  box-shadow: none;
}
.pd__tab-action.is-active:hover {
  background: color-mix(in srgb, var(--kh-primary) 88%, black);
}
.pd__tabs-hint {
  color: var(--kh-text-tertiary);
  font-size: 12px;
  font-family: var(--kh-font-mono);
}
.pd__tab {
  display: inline-flex;
  align-items: center;
  gap: 6px;
  padding: 7px 18px;
  border: none;
  background: transparent;
  border-radius: var(--kh-radius-pill);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  font-weight: 500;
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.pd__tab:hover {
  color: var(--kh-primary);
}
.pd__tab.is-active {
  background: var(--kh-surface);
  color: var(--kh-primary);
  box-shadow: var(--kh-shadow-xs);
}

/* 项目介绍：v-md-preview github 主题根类是 github-markdown-body，清掉主题给 body 的左右内边距
   让正文与上方标签/标题左对齐、去一二级标题下横线 */
.pd__content {
  color: var(--kh-text);
}
.pd__content :deep(.github-markdown-body) {
  background: transparent;
  padding: 0;
  font-family: var(--kh-font-body);
  font-size: var(--kh-font-size-md);
  line-height: 1.9;
  color: var(--kh-text);
}
/* 项目介绍配图可点放大：cursor zoom-in 视觉提示，点击由 .pd__content @click 委托 onContentClick 开 el-image-viewer */
.pd__content :deep(.github-markdown-body img) {
  cursor: zoom-in;
}
/* 越级锁态提示条：项目越级 description 可见，文件下载已锁（canDownload=false），弱化次要色提示需更高权限 */
.pd__lock-tip {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: var(--kh-space-3) var(--kh-space-4);
  margin-top: var(--kh-space-4);
  background: var(--kh-bg-soft);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-sm);
  color: var(--kh-text-muted);
  font-size: 13px;
}

/* —— 项目文件：GitHub 式文件树独立区块（参考后台 ProjectFileTree） —— */
.pd__files {
  background: var(--kh-surface);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-lg);
  overflow: hidden;
  max-height: 640px;
  display: flex;
  flex-direction: column;
}
.pd__files-head {
  display: flex;
  align-items: center;
  padding: 10px 12px;
  border-bottom: 1px solid var(--kh-border-soft);
  font-size: 12px;
  font-weight: 600;
  color: var(--kh-text-tertiary);
  background: var(--kh-surface-muted);
  flex: none;
}
.pd__files-col--name {
  flex: 1;
  min-width: 0;
  padding-left: 41px;
}
.pd__files-col--time {
  width: 120px;
  flex: none;
}
.pd__files-col--size {
  width: 80px;
  flex: none;
}
.pd__files-col--action {
  width: 104px;
  flex: none;
}
.pd__files-body {
  padding: 8px 0;
  overflow: auto;
  flex: 1;
}
/* 越级锁态文件树占位：锁图标 + lockReason + 提示，替代文件树（项目越级锁文件查看，不只是锁下载） */
.pd__files-locked {
  display: flex;
  flex-direction: column;
  align-items: center;
  gap: var(--kh-space-2);
  padding: var(--kh-space-12) var(--kh-space-6);
  color: var(--kh-text-tertiary);
  text-align: center;
}
.pd__files-locked-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  color: var(--kh-text-secondary);
  margin: var(--kh-space-2) 0 0;
}
.pd__files-locked-hint {
  font-size: var(--kh-font-size-sm);
  margin: 0;
}
.pd__tree-node {
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 8px 12px;
  font-size: 13px;
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.pd__tree-node:hover {
  background: var(--kh-surface-muted);
}
.pd__tree-node.is-file:hover .pd__tree-download {
  opacity: 1;
}
.pd__tree-caret {
  width: 14px;
  text-align: center;
  color: var(--kh-text-tertiary);
  user-select: none;
  flex: none;
}
.pd__tree-caret.is-leaf {
  cursor: default;
}
.pd__tree-icon {
  font-size: 15px;
  flex: none;
}
.pd__tree-node.is-dir .pd__tree-icon {
  color: var(--kh-warm);
}
.pd__tree-node.is-file .pd__tree-icon {
  color: var(--kh-accent);
}
.pd__tree-name {
  flex: 1;
  min-width: 0;
  color: var(--kh-text);
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
}
.pd__tree-time {
  width: 120px;
  flex: none;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
  font-size: 12px;
  white-space: nowrap;
}
.pd__tree-size {
  width: 80px;
  flex: none;
  color: var(--kh-text-tertiary);
  font-family: var(--kh-font-mono);
  font-size: 12px;
  text-align: right;
}
.pd__tree-action {
  width: 104px;
  flex: none;
  display: flex;
  justify-content: flex-end;
  gap: 8px;
  white-space: nowrap;
}
.pd__tree-download {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  cursor: pointer;
  opacity: 0;
  transition: all var(--kh-transition-fast);
}
.pd__tree-download:hover {
  color: var(--kh-primary);
  border-color: var(--kh-primary-border);
  background: var(--kh-primary-soft);
}
/* 重命名/删除按钮（仅授权态显示，与下载同尺寸同悬停淡入逻辑） */
.pd__tree-btn {
  display: inline-flex;
  align-items: center;
  justify-content: center;
  width: 28px;
  height: 28px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  cursor: pointer;
  opacity: 0;
  transition: all var(--kh-transition-fast);
}
.pd__tree-node:hover .pd__tree-btn {
  opacity: 1;
}
.pd__tree-btn:hover {
  color: var(--kh-primary);
  border-color: var(--kh-primary-border);
  background: var(--kh-primary-soft);
}
.pd__tree-btn--danger:hover {
  color: var(--kh-danger);
  border-color: var(--kh-danger);
  background: var(--kh-danger-soft);
}
/* 上传弹窗内容 */
.pd__upload {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
}
/* ElUpload drag 主题贴合前台：边框/圆角/底色与 kh 一致 */
.pd__upload-dragger :deep(.el-upload-dragger) {
  width: 100%;
  padding: 28px 20px;
  border-radius: var(--kh-radius-lg);
  border: 1.5px dashed var(--kh-border-strong);
  background: var(--kh-surface-muted);
  transition: border-color var(--kh-transition-fast), background var(--kh-transition-fast);
}
.pd__upload-dragger :deep(.el-upload-dragger:hover) {
  border-color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.pd__upload-dragger :deep(.el-upload-dragger.is-dragover) {
  border-color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
.pd__upload-dragger :deep(.el-icon--upload) {
  font-size: 40px;
  color: var(--kh-primary);
  margin-bottom: 8px;
}
.pd__upload-dragger :deep(.el-upload__text) {
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
}
.pd__upload-dragger :deep(.el-upload__text em) {
  color: var(--kh-primary);
  font-style: normal;
  font-weight: 600;
}
.pd__upload-dragger :deep(.el-upload-list) {
  margin-top: 8px;
}
.pd__upload-dragger :deep(.el-upload-list__item) {
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  border-color: var(--kh-border-soft);
}
.pd__upload-hint {
  margin-top: 4px;
  color: var(--kh-text-tertiary);
  font-size: 12px;
  font-family: var(--kh-font-mono);
}
.pd__upload-progress {
  color: var(--kh-primary);
  font-size: var(--kh-font-size-sm);
}
.pd__tree-empty {
  padding: var(--kh-space-8);
  text-align: center;
  color: var(--kh-text-tertiary);
  font-size: var(--kh-font-size-sm);
}

/* —— 右栏：参与人员卡（内部可滚动 sticky） —— */
.pd__members-card {
  display: flex;
  flex-direction: column;
  min-height: 0;
}
.pd__members-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  margin-bottom: var(--kh-space-4);
}
.pd__related-title {
  margin-top: var(--kh-space-6);
}
.pd__members-scroll {
  max-height: 360px;
  overflow-y: auto;
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-3);
  padding-right: 2px;
}
.pd__member {
  display: flex;
  align-items: center;
  gap: 10px;
  padding: var(--kh-space-3);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius);
  transition: all var(--kh-transition-fast);
}
.pd__member:hover {
  border-color: var(--kh-border-strong);
  background: var(--kh-surface-muted);
}
.pd__member--leader {
  border-color: var(--kh-warm);
  background: var(--kh-warm-soft);
}
.pd__member-info {
  flex: 1;
  min-width: 0;
}
.pd__member-name {
  font-size: var(--kh-font-size-sm);
  font-weight: 600;
  color: var(--kh-text);
}
.pd__member-role {
  font-size: 11px;
  color: var(--kh-text-tertiary);
  margin-top: 2px;
}
.pd__related {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-2);
}
.pd__related-item {
  display: flex;
  align-items: center;
  gap: var(--kh-space-2);
  padding: var(--kh-space-2) var(--kh-space-3);
  border: 1px solid var(--kh-border-soft);
  border-radius: var(--kh-radius-sm);
  font-size: 12px;
  color: var(--kh-text-secondary);
  transition: all var(--kh-transition-fast);
}
.pd__related-item:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
}
.pd__related-name {
  flex: 1;
  min-width: 0;
}

/* 侧栏 */
.pd__aside {
  display: flex;
  flex-direction: column;
  gap: var(--kh-space-5);
  position: sticky;
  top: calc(var(--kh-header-height) + var(--kh-space-4));
}
.pd__info-title {
  font-size: var(--kh-font-size-md);
  font-weight: 600;
  margin-bottom: var(--kh-space-3);
}
.pd__info-row {
  display: flex;
  justify-content: space-between;
  align-items: center;
  padding: 8px 0;
  font-size: 12px;
  border-bottom: 1px dashed var(--kh-border-soft);
}
.pd__info-row:last-child {
  border-bottom: none;
}
.pd__info-row span {
  color: var(--kh-text-tertiary);
}
.pd__info-row b {
  color: var(--kh-text);
  font-weight: 500;
  text-align: right;
  max-width: 180px;
}

@media (max-width: 1024px) {
  .pd__layout {
    grid-template-columns: 1fr;
  }
  .pd__aside {
    position: static;
  }
  .pd__files-col--time,
  .pd__tree-time {
    width: 100px;
  }
}
@media (max-width: 640px) {
  .pd__files-col--time,
  .pd__tree-time,
  .pd__files-col--size,
  .pd__tree-size {
    display: none;
  }
}

/* 项目头管理按钮组（编辑项目信息等） */
.pd__head-titlerow {
  display: flex;
  align-items: center;
  gap: var(--kh-space-3);
  flex-wrap: wrap;
}
.pd__head-titlerow .pd__title {
  flex: 1;
  min-width: 0;
}
.pd__head-actions {
  display: flex;
  gap: 8px;
  flex-shrink: 0;
}
.pd__head-btn {
  display: inline-flex;
  align-items: center;
  gap: 4px;
  padding: 6px 12px;
  border: 1px solid var(--kh-primary-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-primary-soft);
  color: var(--kh-primary);
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  transition: background var(--kh-transition-fast);
}
.pd__head-btn:hover {
  background: var(--kh-primary);
  color: var(--kh-surface);
}
/* 下载项目整包按钮：与"编辑项目信息"主按钮区分，用边框中性态（参考 AppHeader kh-icon-btn 侧），悬停转主色 */
.pd__head-btn--download {
  border-color: var(--kh-border-strong);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
}
.pd__head-btn--download:hover {
  border-color: var(--kh-primary-border);
  background: var(--kh-primary-soft);
  color: var(--kh-primary);
}
/* 收藏按钮：默认中性边框态（与下载按钮同款），已收藏时主色实心高亮（对齐 resource .rd__interact-btn.is-on） */
.pd__head-btn--collect {
  border-color: var(--kh-border-strong);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
}
.pd__head-btn--collect:hover:not(:disabled) {
  border-color: var(--kh-primary-border);
  background: var(--kh-primary-soft);
  color: var(--kh-primary);
}
.pd__head-btn--collect.is-active {
  border-color: var(--kh-primary);
  background: linear-gradient(120deg, var(--kh-primary), var(--kh-primary-strong));
  color: #fff;
  box-shadow: var(--kh-shadow-primary);
}
.pd__head-btn--collect.is-active:hover:not(:disabled) {
  color: #fff;
  background: linear-gradient(120deg, var(--kh-primary-strong), var(--kh-primary));
}
.pd__head-btn--collect:disabled {
  opacity: 0.6;
  cursor: not-allowed;
}

/* 参与人员卡头部（标题 + 成员管理按钮） */
.pd__members-head {
  display: flex;
  align-items: center;
  justify-content: space-between;
  gap: var(--kh-space-2);
  margin-bottom: var(--kh-space-2);
}
.pd__members-mgmt {
  display: inline-flex;
  align-items: center;
  padding: 4px 10px;
  border: 1px solid var(--kh-border);
  border-radius: var(--kh-radius-sm);
  background: var(--kh-surface);
  color: var(--kh-text-secondary);
  font-size: var(--kh-font-size-sm);
  cursor: pointer;
  transition: all var(--kh-transition-fast);
}
.pd__members-mgmt:hover {
  border-color: var(--kh-primary-border);
  color: var(--kh-primary);
  background: var(--kh-primary-soft);
}
</style>