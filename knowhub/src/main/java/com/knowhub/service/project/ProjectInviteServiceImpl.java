package com.knowhub.service.project;

import com.github.pagehelper.PageInfo;
import com.knowhub.enums.project.ProjectInviteStatus;
import com.knowhub.enums.project.ProjectMemberRole;
import com.knowhub.mapper.project.ProjectInviteMapper;
import com.knowhub.mapper.project.ProjectMapper;
import com.knowhub.mapper.project.ProjectMemberMapper;
import com.knowhub.pojo.project.entity.Project;
import com.knowhub.pojo.project.entity.ProjectInvite;
import com.knowhub.pojo.project.entity.ProjectMember;
import com.knowhub.pojo.project.vo.ProjectInviteVo;
import com.knowhub.service.project.impl.ProjectInviteService;
import com.knowhub.support.NotifySupport;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.pojo.entity.SysUser;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import com.rookie.system.mapper.SysUserMapper;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Date;
import java.util.List;

/**
 * 项目成员邀请 Service 实现（受邀人侧处理）。
 * <p>
 * accept 转调既有 ProjectMemberMapper.addMember 写一行 MEMBER 成员 + 邀请置 ACCEPTED + 通知负责人
 * （告知有人加入，便于成员面板刷新）。reject 仅置 REJECTED + 通知负责人，不写成员表。
 * 防越权：accept/reject 校验当前登录用户即受邀人本人（invite.invitee_user_id==currentUser.userId）。
 * 幂等：accept 前查 getMember(projectId,userId)，已是成员则跳过 addMember 仅置邀请 ACCEPTED（避免 uk_project_member 重复入）。
 * 通知 best-effort：由 NotifySupport 内部 try/catch 吞失败不阻断主流程。
 * MEMBER 默认标志位 1/0/0（can_view=1/can_download=0/can_edit=0，照 ProjectServiceImpl.applyDefaultFlagsByRole MEMBER 分支）。
 */
@Service
public class ProjectInviteServiceImpl implements ProjectInviteService {

    @Autowired
    private ProjectInviteMapper projectInviteMapper;

    @Autowired
    private ProjectMemberMapper projectMemberMapper;

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private NotifySupport notifySupport;

    @Autowired
    private SysUserMapper sysUserMapper;

    @Override
    public PageInfo<ProjectInviteVo> listReceived(String status) {
        Long userId = currentUser().getUserId();
        PageUtil.startPage();
        List<ProjectInvite> list = projectInviteMapper.listReceived(userId, status);
        PageInfo<ProjectInvite> page = PageUtil.packagedPageInfo(list);
        return PageUtil.copyPageInfo(page, ProjectInviteVo.class);
    }

    @Override
    @Transactional
    public Boolean accept(Long inviteId) {
        ProjectInvite invite = loadAndAuthorize(inviteId);
        if (!ProjectInviteStatus.PENDING.getCode().equals(invite.getStatus())) {
            throw new ServiceException(500, "仅待回应邀请可接受");
        }
        UserInfo me = currentUser();
        Date now = new Date();
        invite.setStatus(ProjectInviteStatus.ACCEPTED.getCode());
        invite.setHandleBy(me.getUsername());
        invite.setHandleTime(now);
        invite.setUpdateBy(me.getUsername());
        projectInviteMapper.updateStatus(invite);
        // 写成员行（接受邀请自动获全部内容权限 view/download/edit 全开，文件管理/项目信息编辑都能用；
        // 成员管理仍由负责人专属——前台 ProjectAuthoringController 的成员路由 isLeaderOrAuthor gate 兜底拦截，
        // 故接受者虽 flag 全 1 但管不了成员）。幂等：已是成员时跳过加成员（重接受/成员已手动加过场景），
        // 邀请新状态仍置 ACCEPTED 表示"已处理"。
        ProjectMember existing = projectMemberMapper.getMember(invite.getProjectId(), invite.getInviteeUserId());
        if (existing == null) {
            ProjectMember member = new ProjectMember();
            member.setProjectId(invite.getProjectId());
            member.setUserId(invite.getInviteeUserId());
            member.setMemberRole(ProjectMemberRole.MEMBER.getCode());
            member.setCanView(1);
            member.setCanDownload(1);
            member.setCanEdit(1);
            member.setCreateBy(me.getUsername());
            member.setUpdateBy(me.getUsername());
            projectMemberMapper.addMember(member);
        }
        // 通知项目负责人：有人加入了项目，便于成员面板刷新
        Project project = projectMapper.getProjectInfoById(invite.getProjectId());
        if (project != null && project.getAuthorId() != null) {
            String joineeNick = nicknameOf(me.getUserId());
            String who = joineeNick != null ? joineeNick : me.getUsername();
            String title = "项目新成员加入";
            String content = "用户「" + who + "」已接受邀请加入项目《" + project.getTitle() + "》。";
            notifySupport.notifyUser(project.getAuthorId(), title, content,
                    "/project/" + invite.getProjectId(), "system");
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean reject(Long inviteId) {
        ProjectInvite invite = loadAndAuthorize(inviteId);
        if (!ProjectInviteStatus.PENDING.getCode().equals(invite.getStatus())) {
            throw new ServiceException(500, "仅待回应邀请可拒绝");
        }
        UserInfo me = currentUser();
        Date now = new Date();
        invite.setStatus(ProjectInviteStatus.REJECTED.getCode());
        invite.setHandleBy(me.getUsername());
        invite.setHandleTime(now);
        invite.setUpdateBy(me.getUsername());
        projectInviteMapper.updateStatus(invite);
        // 通知负责人：受邀人婉拒，便于继续邀请别人
        Project project = projectMapper.getProjectInfoById(invite.getProjectId());
        if (project != null && project.getAuthorId() != null) {
            String joineeNick = nicknameOf(me.getUserId());
            String who = joineeNick != null ? joineeNick : me.getUsername();
            String title = "项目邀请被拒绝";
            String content = "用户「" + who + "」婉拒了加入项目《" + project.getTitle() + "》的邀请。";
            notifySupport.notifyUser(project.getAuthorId(), title, content,
                    "/project/" + invite.getProjectId(), "system");
        }
        return true;
    }

    // ============================ 私有辅助 ============================

    /** 载入邀请并校验操作人是受邀人本人（防越权处理别人的邀请） */
    private ProjectInvite loadAndAuthorize(Long inviteId) {
        if (inviteId == null) {
            throw new ServiceException(500, "需指定邀请记录 id");
        }
        ProjectInvite exist = projectInviteMapper.getById(inviteId);
        if (exist == null || (exist.getDeleted() != null && exist.getDeleted() == 1)) {
            throw new ServiceException(500, "邀请不存在");
        }
        Long me = currentUser().getUserId();
        if (exist.getInviteeUserId() == null || !exist.getInviteeUserId().equals(me)) {
            throw new ServiceException(500, "仅受邀人本人可处理该邀请");
        }
        return exist;
    }

    /** 当前登录用户 UserInfo（/authoring/** 已 authenticated 兜底） */
    private UserInfo currentUser() {
        return ((UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal());
    }

    /** 按 userId 取昵称快照（通知文案用；user 不存在返 null） */
    private String nicknameOf(Long userId) {
        if (userId == null) {
            return null;
        }
        SysUser u = sysUserMapper.getSysUserInfoById(userId);
        return u == null ? null : u.getNickName();
    }
}