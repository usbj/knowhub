/**
 * 文件作用：
 * 前台用户公开主页 VO 类型，与后端 com.knowhub.pojo.user.vo.UserPortalVo 对齐。
 * <p>
 * 仅公开字段集 userId/nickName/avatar/username/sex/createTime——后端 UserPortalVo 不含
 * phoneNumber/password/status/delete 等敏感字段（隐私保护，对游客隐藏手机号）。
 * 与 AuthoringUserRecord 区别：本接口按 userId 单查（非模糊搜索）、不含 phoneNumber。
 */

/** 前台用户公开主页记录（GET /portal/user/{userId}） */
export interface UserPublicRecord {
  userId: number
  /** 昵称（后端 sys_user.nick_name） */
  nickName?: string
  /** 头像 URL（后端 sys_user.avatar，可空） */
  avatar?: string
  /** 用户名（后端 sys_user.username，登录账号） */
  username?: string
  /** 性别字典值（后端 sys_user.sex，可空） */
  sex?: string
  /** 注册时间（后端 sys_user.create_time，公开展示「加入于」） */
  createTime?: string
}
