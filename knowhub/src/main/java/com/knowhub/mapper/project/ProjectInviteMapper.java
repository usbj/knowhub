package com.knowhub.mapper.project;

import com.knowhub.pojo.project.entity.ProjectInvite;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 项目成员邀请 Mapper。
 * - addInvite：发起一条邀请（PENDING）
 * - getByProjectAndInvitee：查 (projectId, inviteeUserId) 当前 ACTIVE 行（去重与已邀请判定）
 * - getById：处理回填 + 防越权校验用（受邀人本人）
 * - listReceived：受邀人侧列出收到的邀请（service 注入 inviteeUserId）
 * - updateStatus：accept/reject 改 status + handle_by + handle_time + 审计
 * - softDeleteById：重邀旧 REJECTED/已换人时软删避 uk 冲突
 * 不走 project 复用清单查重，project_member 的成员查重仍走 ProjectMemberMapper.getMember。
 */
@Mapper
public interface ProjectInviteMapper {

    /** 发起一条邀请（invite_time/create_time/update_time 由 DB 默认 CURRENT_TIMESTAMP 填充，status 默认 PENDING） */
    Boolean addInvite(ProjectInvite invite);

    /** 查 (projectId, inviteeUserId) 当前 ACTIVE 行（deleted=0），null 表示无遗留需处理 */
    ProjectInvite getByProjectAndInvitee(@Param("projectId") Long projectId, @Param("inviteeUserId") Long inviteeUserId);

    /** 按主键查（含 deleted 字段，accept/reject 防越权回填用） */
    ProjectInvite getById(Long inviteId);

    /** 受邀人侧：列出该用户收到的邀请（service 注入 inviteeeUserId，按 invite_time 倒序） */
    List<ProjectInvite> listReceived(@Param("inviteeUserId") Long inviteeUserId, @Param("status") String status);

    /** accept/reject 改 status + handle_by + handle_time + 审计 */
    Boolean updateStatus(ProjectInvite invite);

    /** 软删（重邀旧 REJECTED/已换人时避开 uk_project_invite 冲突） */
    Boolean softDeleteById(Long inviteId);
}