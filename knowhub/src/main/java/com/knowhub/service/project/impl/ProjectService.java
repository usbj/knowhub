package com.knowhub.service.project.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.project.quarry.ProjectQuarry;
import com.knowhub.pojo.project.vo.ProjectFileTreeVo;
import com.knowhub.pojo.project.vo.ProjectFileVo;
import com.knowhub.pojo.project.vo.ProjectMemberVo;
import com.knowhub.pojo.project.vo.ProjectReviewLogVo;
import com.knowhub.pojo.project.vo.ProjectReviewVo;
import com.knowhub.pojo.project.vo.ProjectVo;

import java.util.List;

/**
 * 项目管理 Service。
 * 权限分级：系统权限(view/download/edit:lN，由 ProjectPermissionResolver 取最高等级) +
 * 项目内权限(project_member.can_view/can_download/can_edit) + LEADER 全权。
 * 审核流程复用博客/资源范式（状态机+回避+流水表+对账任务），ReviewAction 枚举复用。
 */
public interface ProjectService {

    /** 列表查询（权限过滤：level<=userViewLevel OR 参与的项目 can_view=1） */
    PageInfo<ProjectVo> quarryProject(ProjectQuarry quarry);

    /** 详情（二次权限校验 canOp(view)，回填权限态/成员/文件树/审核历史） */
    ProjectVo getProjectInfo(Long projectId);

    /** 新增项目（创建者默认 LEADER，按 type 配套写子表） */
    Boolean addProjectInfo(ProjectVo vo);

    /** 编辑项目（状态机前置 + canOp(edit) 校验，按 type 改子表） */
    Boolean editProjectInfo(ProjectVo vo);

    /** 删除项目（LEADER 或 delete 权限，级联软删 member/file/file_object 三类） */
    Boolean deleteProjectInfo(Long[] projectIds);

    /** 发布项目（审核开关决定 PENDING_REVIEW 或 PUBLISHED，写流水 SUBMIT/PUBLISH） */
    Boolean publishProject(Long projectId);

    /** 撤回项目（仅 PUBLISHED 可撤回，写流水 REVOKE） */
    Boolean revokeProject(Long projectId);

    /** 审核项目（回避：负责人不能审自己，写流水 APPROVE/REJECT） */
    Boolean reviewProject(ProjectReviewVo vo);

    /** 审核历史流水（按时间升序） */
    List<ProjectReviewLogVo> listReviewLog(Long projectId);

    /** 对账：审核开关关闭后批量放行遗留待审项目 */
    int reconcilePendingReview();

    // ---- 成员管理 ----

    /** 成员列表（join sys_user 带昵称） */
    List<ProjectMemberVo> listMembers(Long projectId);

    /** 新增成员（按角色给默认标志位，LEADER 唯一性校验） */
    Boolean addMember(ProjectMemberVo vo);

    /** 批量新增成员（参考通知分组，默认 MEMBER 角色，按角色给默认标志位；已存在的跳过） */
    Boolean addMembersBatch(Long projectId, List<Long> userIds);

    /** 编辑成员（调角色/标志位，LEADER 唯一性 + 换负责人同步 author_id） */
    Boolean editMember(ProjectMemberVo vo);

    /** 删除成员（LEADER 不可删，需先换负责人） */
    Boolean deleteMember(Long memberId);

    /** 发起项目成员邀请（PENDING 待受邀人同意，canOp(edit) 校验；去重/重邀幂等，受邀请人随后在个人中心「我的协作」处理） */
    Boolean inviteMember(Long projectId, Long inviteeUserId);

    /** 判定指定用户对项目是否为「负责人或作者」（前台成员管理操作前置鉴权用，后台 admin controller 不走此口径） */
    boolean isLeaderOrAuthor(Long projectId, Long userId);

    /** 同 isLeaderOrAuthor，但按 memberId 反查 projectId（前台删除成员路由只有 memberId 入参时用） */
    boolean isLeaderOrAuthorByMemberId(Long memberId, Long userId);

    // ---- 文件树管理 ----

    /** 文件树（扁平节点 + join file_object 元数据，供前端组装树） */
    List<ProjectFileVo> listFiles(Long projectId);

    /** 文件树（service 层组装为树形，供前端 GitHub 式侧边栏递归渲染） */
    List<ProjectFileTreeVo> listFileTree(Long projectId);

    /** 新建文件夹（is_dir=1，object_id=null） */
    Boolean addFolder(ProjectFileVo vo);

    /** 新增文件叶子（is_dir=0，关联 file_object.object_id，绑定 biz_ref_id） */
    Boolean addFileNode(ProjectFileVo vo);

    /** 编辑文件节点（改名/移动/排序） */
    Boolean editFileNode(ProjectFileVo vo);

    /** 删除文件节点（目录级联软删子节点；文件叶子级联软删 file_object） */
    Boolean deleteFileNode(Long fileId);

    /** 下载文件（canOp(download) 校验，返回中转/预签名链接） */
    String downloadFile(Long fileId);

    /** 收藏/取消收藏项目（collected=true 收藏,false 取消,主表 collect_count 同步;照 BlogServiceImpl.toggleCollect 范式） */
    Boolean toggleCollect(Long projectId, Boolean collected);
}
