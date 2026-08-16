<!--
  ProjectMemberAddDialog —— 前台项目邀请成员子弹窗
  ------------------------------------------------------------------
  承接项目创作页成员管理面板"邀请成员"时打开。顶部按字段类型（昵称/用户名/手机号）搜索
  前台轻量选人接口（/authoring/user/search），下部展示用户分页表格，行内"发邀请"按钮把
  用户抛给父层统一发起邀请，已在父层已选集合中的用户显示"已邀请"禁用态。
  与后台 ProjectMemberAddDialog 的唯一差异：API 源从 /sys/user/list（系统 admin 接口）
  换为 /authoring/user/search（前台轻量选人接口，权限键 knowhub:authoring:user-search，
  默认分配给实验室成员角色），不依赖后台 system:user:quarry 按钮权限。
  关键参数：
  - `excludeUserIds`：父层已加入项目成员的 userId 集合，控制"已邀请"禁用态，避免对已是成员者重复邀请。
    （注意：仅含已落库 project_member 行；对处于 project_invite PENDING 的被邀请人不再点邀请，
    后端 inviteMember 对 PENDING 会拒报"已邀请过待回应"，http 拦截器弹错、本子弹窗不卸载保留开启。）
  关键事件：
  - `add`：抛出待邀请的用户记录，父层统一调 inviteProjectMemberApi 发起邀请。
-->
<script setup lang="ts">
import { onMounted, ref } from 'vue'
import {
  ElButton,
  ElDialog,
  ElEmpty,
  ElInput,
  ElOption,
  ElPagination,
  ElSelect,
  ElTable,
  ElTableColumn,
  ElTag,
} from 'element-plus'
import { Search } from '@element-plus/icons-vue'
import { searchAuthoringUsersApi } from '@/api/knowhub/authoring-user'
import type {
  AuthoringUserRecord,
  AuthoringUserSearchQuery,
} from '@/api/knowhub/authoring-user'
import type { NormalizedPageResult } from '@/types/api/common'

const props = defineProps<{
  excludeUserIds: Set<number>
}>()

const emit = defineEmits<{
  add: [user: AuthoringUserRecord]
  /** 关闭事件：父层用 v-if 控制卸载，关闭时回传让父层 addDialogVisible=false */
  close: []
}>()

/**
 * 搜索字段类型选项，与后端 AuthoringUserSearchQuarry 的 nickName/username/phoneNumber
 * 三个字段对齐，复用前台轻量选人接口 GET /authoring/user/search。
 */
const SEARCH_FIELD_OPTIONS = [
  { label: '昵称', value: 'nickName' as const },
  { label: '用户名', value: 'username' as const },
  { label: '手机号', value: 'phoneNumber' as const },
]

type SearchField = (typeof SEARCH_FIELD_OPTIONS)[number]['value']

/**
 * 弹窗显隐：父层用 v-if 挂载本组件即表示要打开，挂载即 true；
 * 关闭时（点完成/遮罩/Esc）emit close 让父层置 addDialogVisible=false 卸载本组件，
 * 下次再打开走重新挂载 + onMounted 复位搜索条件。
 */
const visible = ref(true)
const listLoading = ref(false)
const searchField = ref<SearchField>('nickName')
const keyword = ref('')
const userPageState = ref<NormalizedPageResult<AuthoringUserRecord>>({
  records: [],
  pageNum: 1,
  pageSize: 10,
  pages: 0,
  total: 0,
})

/**
 * 方法效果：
 * 按当前搜索条件与分页参数拉取用户候选列表（前台轻量选人接口）。
 */
const fetchUserPage = async () => {
  listLoading.value = true
  try {
    const trimmed = keyword.value.trim()
    const params: AuthoringUserSearchQuery = {
      pageNum: userPageState.value.pageNum,
      pageSize: userPageState.value.pageSize,
    }
    if (trimmed) {
      params[searchField.value] = trimmed
    }
    userPageState.value = await searchAuthoringUsersApi(params)
  } finally {
    listLoading.value = false
  }
}

/**
 * 方法效果：
 * 执行用户搜索，关键词为空时清空表格不拉全量，有关键词时从第一页拉取候选列表。
 */
const handleSearch = async () => {
  if (!keyword.value.trim()) {
    resetSearch()
    return
  }
  userPageState.value.pageNum = 1
  await fetchUserPage()
}

/**
 * 方法效果：
 * 重置搜索条件并清空表格结果，不向后端发请求。
 * 重置后表格回到空态，待作者重新输入关键词搜索，避免无筛选拉全量用户。
 */
const resetSearch = () => {
  keyword.value = ''
  searchField.value = 'nickName'
  userPageState.value = {
    records: [],
    pageNum: 1,
    pageSize: 10,
    pages: 0,
    total: 0,
  }
}

const handleAdd = (user: AuthoringUserRecord) => {
  emit('add', user)
}

const handlePageChange = async () => {
  await fetchUserPage()
}

/**
 * 弹窗关闭处理：ElDialog 关闭（点完成/遮罩/Esc/点 X）时 visible 变 false，
 * emit close 让父层置 addDialogVisible=false 卸载本组件，下次打开重新挂载走 onMounted 复位。
 */
const handleClose = () => {
  emit('close')
}

// 父层用 v-if 挂载即表示要打开，挂载时复位搜索条件（不拉全量，待作者输关键词）
onMounted(() => {
  searchField.value = 'nickName'
  keyword.value = ''
  userPageState.value = {
    records: [],
    pageNum: 1,
    pageSize: 10,
    pages: 0,
    total: 0,
  }
})
</script>

<template>
  <ElDialog
    v-model="visible"
    title="邀请成员"
    width="720px"
    destroy-on-close
    append-to-body
    class="kh-member-add-dialog"
    @close="handleClose"
  >
    <!-- 搜索区：字段类型 + 关键词 + 搜索/重置 -->
    <div class="kh-member-add-search">
      <ElSelect v-model="searchField" class="kh-member-add-search__field">
        <ElOption
          v-for="item in SEARCH_FIELD_OPTIONS"
          :key="item.value"
          :label="item.label"
          :value="item.value"
        />
      </ElSelect>
      <ElInput
        v-model="keyword"
        :placeholder="`请输入${SEARCH_FIELD_OPTIONS.find((item) => item.value === searchField)?.label ?? '关键词'}`"
        clearable
        class="kh-member-add-search__keyword"
        @keyup.enter="handleSearch"
      >
        <template #prefix>
          <Search />
        </template>
      </ElInput>
      <ElButton type="primary" :loading="listLoading" @click="handleSearch">搜索</ElButton>
      <ElButton @click="resetSearch">重置</ElButton>
    </div>

    <!-- 用户候选分页表格 -->
    <ElTable
      :data="userPageState.records"
      v-loading="listLoading"
      class="kh-member-add-table"
      max-height="360"
      row-key="userId"
    >
      <template #empty>
        <ElEmpty description="未找到匹配的用户，试试换个关键词或字段" :image-size="64" />
      </template>
      <ElTableColumn label="昵称" prop="nickName" min-width="120" show-overflow-tooltip />
      <ElTableColumn label="用户名" prop="username" min-width="120" show-overflow-tooltip />
      <ElTableColumn label="手机号" prop="phoneNumber" min-width="130" show-overflow-tooltip />
      <ElTableColumn label="操作" width="110" fixed="right">
        <template #default="{ row }">
          <ElButton
            v-if="!excludeUserIds.has(Number(row.userId))"
            type="primary"
            size="small"
            @click="handleAdd(row as AuthoringUserRecord)"
          >
            发邀请
          </ElButton>
          <ElButton v-else type="info" size="small" plain disabled>已邀请</ElButton>
        </template>
      </ElTableColumn>
    </ElTable>

    <ElPagination
      class="kh-member-add-pagination"
      :current-page="userPageState.pageNum"
      :page-size="userPageState.pageSize"
      :total="userPageState.total"
      :page-sizes="[10, 20, 50]"
      layout="total, prev, pager, next, sizes"
      background
      @update:current-page="userPageState.pageNum = $event; handlePageChange()"
      @update:page-size="userPageState.pageSize = $event; userPageState.pageNum = 1; handlePageChange()"
    />

    <template #footer>
      <ElButton type="primary" @click="handleClose">完成</ElButton>
    </template>
  </ElDialog>
</template>

<style scoped>
.kh-member-add-search {
  display: flex;
  align-items: center;
  gap: 8px;
  flex-wrap: wrap;
  margin-bottom: 14px;
}

.kh-member-add-search__field {
  width: 110px;
  flex: none;
}

.kh-member-add-search__keyword {
  flex: 1;
  min-width: 220px;
}

.kh-member-add-table {
  margin-bottom: 12px;
}

.kh-member-add-pagination {
  justify-content: flex-end;
}
</style>
