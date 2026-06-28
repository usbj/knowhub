import type { FormRules } from 'element-plus'
import type { SharedFieldSchemaMap } from '@/types/components/data-display'
import type { SysUserFormData } from '@/types/api/system/user'
import { formatDateTime } from '@/utils/format'

export interface UserQueryFormState {
  username: string
  nickName: string
  phoneNumber: string
  status: number | undefined
  dateRange: string[]
}

export const createDefaultUserQueryForm = (): UserQueryFormState => ({
  username: '',
  nickName: '',
  phoneNumber: '',
  status: undefined,
  dateRange: [],
})

export const createDefaultUserForm = (): SysUserFormData => ({
  username: '',
  password: '',
  nickName: '',
  phoneNumber: '',
  sex: '1',
  status: 1,
  roleId: [],
  userRole: [],
})

export const createUserQuerySchema = (): SharedFieldSchemaMap<UserQueryFormState> => ({
  username: {
    label: '账号',
    inputType: 'text',
    placeholder: '请输入账号',
    tableVisible: false,
    formVisible: true,
    formOrder: 1,
    span: 5,
    props: { style: { width: '100%' } },
  },
  nickName: {
    label: '昵称',
    inputType: 'text',
    placeholder: '请输入昵称',
    tableVisible: false,
    formVisible: true,
    formOrder: 2,
    span: 5,
    props: { style: { width: '100%' } },
  },
  phoneNumber: {
    label: '手机号',
    inputType: 'text',
    placeholder: '请输入手机号',
    tableVisible: false,
    formVisible: true,
    formOrder: 3,
    span: 5,
    props: { style: { width: '100%' } },
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 4,
    props: { style: { width: '100%' } },
    options: [
      { label: '正常', value: 1 },
      { label: '停用', value: 0 },
    ],
  },
  dateRange: {
    label: '创建时间',
    inputType: 'daterange',
    tableVisible: false,
    formVisible: true,
    formOrder: 5,
    span: 5,
    props: {
      unlinkPanels: true,
      style: { width: '100%' },
    },
  },
})

export const createUserSchema = (
  roleOptions: Array<{ label: string; value: number }>,
  dialogMode: 'create' | 'edit',
): SharedFieldSchemaMap<SysUserFormData> => ({
  username: {
    label: '账号',
    inputType: 'text',
    placeholder: '请输入账号',
    tableVisible: true,
    formVisible: dialogMode === 'create',
    tableOrder: 2,
    formOrder: 1,
    span: 12,
    tableMinWidth: 140,
  },
  nickName: {
    label: '昵称',
    inputType: 'text',
    placeholder: '请输入昵称',
    tableVisible: true,
    formVisible: true,
    tableOrder: 3,
    formOrder: 2,
    span: 12,
    tableMinWidth: 140,
  },
  phoneNumber: {
    label: '手机号',
    inputType: 'text',
    placeholder: '请输入手机号',
    tableVisible: true,
    formVisible: true,
    tableOrder: 4,
    formOrder: 3,
    span: 12,
    tableMinWidth: 160,
  },
  sex: {
    label: '性别',
    inputType: 'select',
    placeholder: '请选择性别',
    tableVisible: false,
    formVisible: true,
    formOrder: 4,
    span: 12,
    options: [
      { label: '男', value: '1' },
      { label: '女', value: '0' },
    ],
    formatter: (value) => (String(value) === '0' ? '女' : '男'),
  },
  status: {
    label: '状态',
    inputType: 'select',
    placeholder: '请选择状态',
    tableVisible: true,
    formVisible: true,
    tableOrder: 5,
    formOrder: 5,
    span: 12,
    options: [
      { label: '正常', value: 1 },
      { label: '停用', value: 0 },
    ],
    formatter: (value) => (Number(value) === 1 ? '正常' : '停用'),
  },
  roleId: {
    label: '角色',
    inputType: 'select',
    placeholder: '请选择角色',
    tableVisible: false,
    formVisible: true,
    formOrder: 6,
    span: 12,
    options: roleOptions,
    props: {
      multiple: true,
      style: { width: '100%' },
    },
  },
  password: {
    label: '密码',
    inputType: 'password',
    placeholder: '不填写则默认 123456',
    tableVisible: false,
    formVisible: dialogMode === 'create',
    formOrder: 7,
    span: 12,
    props: {
      autocomplete: 'new-password',
    },
  },
  userId: {
    label: '用户编号',
    tableVisible: true,
    formVisible: false,
    tableOrder: 1,
    tableWidth: 110,
  },
  createTime: {
    label: '创建时间',
    tableVisible: true,
    formVisible: false,
    tableOrder: 6,
    tableMinWidth: 180,
    formatter: (value) => formatDateTime(value),
  },
})

export const createUserFormRules = (dialogMode: 'create' | 'edit'): FormRules => ({
  username:
    dialogMode === 'create'
      ? [
          { required: true, message: '请输入账号', trigger: 'blur' },
          { min: 2, max: 20, message: '账号长度需在 2 到 20 位之间', trigger: 'blur' },
        ]
      : [],
  nickName: [
    { required: true, message: '请输入昵称', trigger: 'blur' },
    { min: 2, max: 20, message: '昵称长度需在 2 到 20 位之间', trigger: 'blur' },
  ],
  phoneNumber: [
    { required: true, message: '请输入手机号', trigger: 'blur' },
    { pattern: /^1\d{10}$/, message: '请输入正确的手机号', trigger: 'blur' },
  ],
  sex: [{ required: true, message: '请选择性别', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }],
  roleId: [{ required: true, message: '请至少选择一个角色', trigger: 'change', type: 'array' }],
  password:
    dialogMode === 'create'
      ? [
          {
            trigger: 'blur',
            validator: (_rule, value, callback) => {
              if (!value) {
                callback()
                return
              }

              if (String(value).length < 6 || String(value).length > 20) {
                callback(new Error('密码长度需在 6 到 20 位之间'))
                return
              }

              callback()
            },
          },
        ]
      : [],
})
