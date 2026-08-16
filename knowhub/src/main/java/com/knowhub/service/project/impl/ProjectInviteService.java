package com.knowhub.service.project.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.project.vo.ProjectInviteVo;

/**
 * 项目成员邀请 Service（受邀人侧处理接口）。
 * <p>
 * 负责人发起邀请入口在 ProjectService.inviteMember（canOp(edit) 校验，写 PENDING + 通知受邀人）；
 * 本接口给受邀人在「我的协作」页「我收到的项目邀请」tab 处理：
 * - listReceived：列出当前用户收到的邀请（可按 status 过滤），PENDING 行行内「同意/拒绝」；
 * - accept：同意 → 调既有 projectMemberMapper.addMember 写一行 MEMBER 成员（复用 addMembersBatch 默认产出）
 *           + 邀请置 ACCEPTED；重复接受幂等（既有 getMember(projectId,userId) 查重挡重复入成员表）；
 *           accept 成员行插完不删 invite 行（留审计迹，uk_project_invite 也防重复）；
 * - reject：仅置 REJECTED（不写成员表）。
 * <p>
 * 同意/拒绝放本独立页，不调 confirmNoticeApi（NotifySupport needConfirm 仍 0，通知仅提醒+跳转 /collaboration）。
 */
public interface ProjectInviteService {

    /** 受邀人侧：列出当前登录用户收到的邀请（可按 status 过滤） */
    PageInfo<ProjectInviteVo> listReceived(String status);

    /** 受邀人同意邀请（service 校验操作人是受邀人本人 → 写成员 MEMBER 行 + 邀请置 ACCEPTED + 通知负责人）。
     *  幂等：已是项目成员时跳过加成员仅置邀请 ACCEPTED（避免 uk_project_member 重复入）。 */
    Boolean accept(Long inviteId);

    /** 受邀人拒绝邀请。仅置 REJECTED + 通知负责人；不写成员表。 */
    Boolean reject(Long inviteId);
}