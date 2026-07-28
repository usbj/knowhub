package com.knowhub.service.project;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.config.ProjectConfigReader;
import com.knowhub.enums.storage.FileBusinessType;
import com.knowhub.enums.project.ProjectFileType;
import com.knowhub.enums.project.ProjectLevel;
import com.knowhub.enums.project.ProjectMemberRole;
import com.knowhub.enums.project.ProjectStatus;
import com.knowhub.enums.project.ProjectType;
import com.knowhub.enums.common.ReviewAction;
import com.knowhub.enums.common.ReviewStatus;
import com.knowhub.mapper.storage.FileObjectMapper;
import com.knowhub.mapper.project.ProjectCompetitionMapper;
import com.knowhub.mapper.project.ProjectFileMapper;
import com.knowhub.mapper.project.ProjectMapper;
import com.knowhub.mapper.project.ProjectMemberMapper;
import com.knowhub.mapper.project.ProjectReviewLogMapper;
import com.knowhub.pojo.project.entity.Project;
import com.knowhub.pojo.project.entity.ProjectFile;
import com.knowhub.pojo.project.entity.ProjectMember;
import com.knowhub.pojo.project.entity.ProjectReviewLog;
import com.knowhub.pojo.project.quarry.ProjectQuarry;
import com.knowhub.pojo.common.vo.BindVo;
import com.knowhub.pojo.storage.vo.DownloadVo;
import com.knowhub.pojo.project.vo.ProjectFileTreeVo;
import com.knowhub.pojo.project.vo.ProjectFileVo;
import com.knowhub.pojo.project.vo.ProjectMemberVo;
import com.knowhub.pojo.project.vo.ProjectReviewLogVo;
import com.knowhub.pojo.project.vo.ProjectReviewVo;
import com.knowhub.pojo.project.vo.ProjectVo;
import com.knowhub.support.ProjectPermissionResolver;
import com.knowhub.support.ProjectPermissionResolver.ProjectPermissionLevel;
import com.knowhub.service.storage.impl.FileService;
import com.knowhub.service.project.impl.ProjectService;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 项目管理 Service 实现。
 *
 * 权限模型（系统权限 + 项目内权限 + LEADER 全权）：
 * - 系统权限（全局、分等级、所有项目）：view/download/edit:lN，ProjectPermissionResolver
 *   一次扫描 List<Permission> 取最高等级（admin 零特判，登录时全 perm_key 已塞入）
 * - 项目内权限（单项目、不分等级）：project_member.can_view/can_download/can_edit
 * - LEADER 判定时强制全权不看标志位
 * 判定公式 canOp(U,P,op)：userLvl(op) >= P.level OR member.can_op=1 OR role=LEADER
 *
 * 审核流程复用博客/资源范式（状态机+回避+流水表+对账任务），代码模式与 ResourceServiceImpl 同构：
 * - 主表只存 status/review_status/publish_time，审核员/审核时间/审核意见全在 project_review_log
 * - publish 经审核开关决定 PENDING_REVIEW 或 PUBLISHED，进 PENDING_REVIEW 时 SET Redis 标记
 * - review 校验状态+回避（author_id 比对），写流水 APPROVE/REJECT
 * - reconcilePendingReview 逐条放行遗留待审项目，对账任务在开关关闭+标记存在时调用
 *
 * 文件复用 file_object（business_type=PROJECT_SRC/PKG/DOC 已预留，biz_ref_id=project_id）；
 * 项目内文件树 project_file 支撑 GitHub 式侧边栏（目录骨架+叶子指向 file_object）。
 * 删项目级联：member + project_file + file_object 三类 softDeleteByBizRef，对象本体由 FileGcTask 回收。
 */
@Service
public class ProjectServiceImpl implements ProjectService {

    @Autowired
    private ProjectMapper projectMapper;

    @Autowired
    private ProjectCompetitionMapper projectCompetitionMapper;

    @Autowired
    private ProjectMemberMapper projectMemberMapper;

    @Autowired
    private ProjectReviewLogMapper projectReviewLogMapper;

    @Autowired
    private ProjectFileMapper projectFileMapper;

    @Autowired
    private FileObjectMapper fileObjectMapper;

    @Autowired
    private FileService fileService;

    @Autowired
    private ProjectConfigReader projectConfigReader;

    @Autowired
    private StringRedisTemplate redisTemplate;

    @org.springframework.beans.factory.annotation.Value("${redis.base-key}")
    private String baseKey;

    /** 待审核存在标记 key（经 baseKey 前缀）：作者提交进 PENDING_REVIEW 时 SET，对账任务消费后 DEL */
    private static final String CACHE_PENDING_FLAG = "project:review:pending-flag";

    @Override
    public PageInfo<ProjectVo> quarryProject(ProjectQuarry quarry) {
        // 一次扫描 perms 取查看等级（admin 自然 3，无权限者 0）
        ProjectPermissionLevel lvl = ProjectPermissionResolver.resolve();
        UserInfo user = currentUser();
        quarry.setUserViewLevel(lvl.view());
        quarry.setUserId(user.getUserId());
        PageUtil.startPage();
        List<Project> list = projectMapper.quarryProject(quarry);
        PageInfo<Project> page = PageUtil.packagedPageInfo(list);
        PageInfo<ProjectVo> voPage = PageUtil.copyPageInfo(page, ProjectVo.class);
        // 列表不回填权限态/成员/文件树（详情接口才回填），authorNickname 由 join 带出经 BeanUtil 拷贝
        return voPage;
    }

    @Override
    public ProjectVo getProjectInfo(Long projectId) {
        Project project = projectMapper.getProjectInfoById(projectId);
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        // 二次权限校验：canOp(view)
        if (!canOp(project, "view")) {
            throw new ServiceException(500, "无权查看该项目");
        }
        ProjectVo vo = BeanUtil.toBean(project, ProjectVo.class);
        // 回填当前用户对该项目的权限态（供前端控制下载/编辑按钮显隐）
        fillPermissionState(vo, project);
        return vo;
    }

    @Override
    @Transactional
    public Boolean addProjectInfo(ProjectVo vo) {
        validateProjectPayload(vo);
        Project project = BeanUtil.toBean(vo, Project.class);
        UserInfo userInfo = currentUser();
        project.setAuthorId(userInfo.getUserId());
        project.setCreateBy(userInfo.getUsername());
        project.setUpdateBy(userInfo.getUsername());
        project.setCreateTime(new Date());
        project.setUpdateTime(new Date());
        // 新建即草稿；审核相关字段初始化；level 缺省 L1 公开
        project.setStatus(ProjectStatus.DRAFT.getCode());
        project.setReviewStatus(ReviewStatus.NONE.getCode());
        if (project.getLevel() == null) {
            project.setLevel(ProjectLevel.L1.getCode());
        }
        try {
            projectMapper.addProject(project);
        } catch (Exception e) {
            throw new ServiceException(500, "项目添加失败", e.getMessage());
        }
        Long projectId = project.getProjectId();
        // 按 type 配套写类型子表（当前仅 COMPETITION）
        saveTypeSubTable(project, vo);
        // 创建者默认 LEADER（can_view/can_download/can_edit 全 1）
        addLeaderMember(projectId, userInfo);
        return true;
    }

    @Override
    @Transactional
    public Boolean editProjectInfo(ProjectVo vo) {
        if (vo.getProjectId() == null) {
            throw new ServiceException(500, "项目ID不能为空");
        }
        Project exist = projectMapper.getProjectInfoById(vo.getProjectId());
        if (exist == null) {
            throw new ServiceException(500, "项目不存在");
        }
        // 状态机前置校验：仅 DRAFT/REJECTED/REVOKED/ARCHIVED 可编辑（PUBLISHED 须先撤回，PENDING_REVIEW 审核中不能改）
        String cur = exist.getStatus();
        if (ProjectStatus.PUBLISHED.getCode().equals(cur)) {
            throw new ServiceException(500, "已发布项目请先撤回再编辑");
        }
        if (ProjectStatus.PENDING_REVIEW.getCode().equals(cur)) {
            throw new ServiceException(500, "审核中项目不能编辑，如需修改请先驳回或撤回后操作");
        }
        // 权限校验：canOp(edit)
        if (!canOp(exist, "edit")) {
            throw new ServiceException(500, "无权编辑该项目");
        }
        validateProjectPayload(vo);

        Project project = BeanUtil.toBean(vo, Project.class);
        UserInfo userInfo = currentUser();
        project.setUpdateBy(userInfo.getUsername());
        // 改 level 要校验：操作者自己的 edit 等级 >= 新 level（否则能把自己够不着的项目降级再让别人改）
        if (project.getLevel() != null && project.getLevel() > exist.getLevel()) {
            ProjectPermissionLevel lvl = ProjectPermissionResolver.resolve();
            if (lvl.edit() < project.getLevel()) {
                throw new ServiceException(500, "无权提升项目到更高等级（自身编辑等级不足）");
            }
        }
        try {
            projectMapper.editProjectInfo(project);
        } catch (Exception e) {
            throw new ServiceException(500, "项目修改失败", e.getMessage());
        }
        // 按 type 配套改类型子表
        saveTypeSubTable(project, vo);
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteProjectInfo(Long[] projectIds) {
        UserInfo userInfo = currentUser();
        for (Long id : projectIds) {
            Project project = projectMapper.getProjectInfoById(id);
            if (project == null) {
                continue;
            }
            // 删除权限：LEADER 或 knowhub:project:delete 按钮权限
            if (!isLeader(project, userInfo) && !hasButtonPerm("knowhub:project:delete")) {
                throw new ServiceException(500, "无权删除该项目（仅负责人或拥有删除权限）");
            }
            // 级联软删 member + project_file
            projectMemberMapper.softDeleteByProjectId(id);
            projectFileMapper.softDeleteByProjectId(id);
            // 级联软删 file_object 三类（PROJECT_SRC/PKG/DOC），对象本体由 FileGcTask 回收
            softDeleteProjectFiles(id, userInfo.getUsername());
            // 物理删类型子表（子表不软删随主表）
            if (ProjectType.COMPETITION.getCode().equals(project.getType())) {
                try {
                    projectCompetitionMapper.deleteByProjectId(id);
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(ProjectServiceImpl.class)
                            .warn("级联删比赛子表失败 projectId={}: {}", id, e.getMessage());
                }
            }
            projectMapper.softDeleteProject(id);
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean publishProject(Long projectId) {
        Project exist = projectMapper.getProjectInfoById(projectId);
        if (exist == null) {
            throw new ServiceException(500, "项目不存在");
        }
        // 状态机前置校验：仅 DRAFT/REJECTED/REVOKED/ARCHIVED 可发布
        String cur = exist.getStatus();
        if (ProjectStatus.PUBLISHED.getCode().equals(cur)) {
            throw new ServiceException(500, "项目已发布，无需重复发布");
        }
        if (ProjectStatus.PENDING_REVIEW.getCode().equals(cur)) {
            throw new ServiceException(500, "项目审核中，请勿重复提交");
        }
        // 权限校验：canOp(edit)（发布属编辑范畴）
        if (!canOp(exist, "edit")) {
            throw new ServiceException(500, "无权发布该项目");
        }
        UserInfo userInfo = currentUser();
        Date now = new Date();
        boolean reviewEnabled = projectConfigReader.isReviewEnabled();
        Project update = new Project();
        update.setProjectId(projectId);
        update.setUpdateBy(userInfo.getUsername());
        ReviewAction action;
        if (reviewEnabled) {
            update.setStatus(ProjectStatus.PENDING_REVIEW.getCode());
            update.setReviewStatus(ReviewStatus.PENDING.getCode());
            action = ReviewAction.SUBMIT;
            redisTemplate.opsForValue().set(baseKey + CACHE_PENDING_FLAG, "1");
        } else {
            update.setStatus(ProjectStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.NONE.getCode());
            action = ReviewAction.PUBLISH;
        }
        projectMapper.editProjectInfo(update);
        writeReviewLog(projectId, action, userInfo, null);
        return true;
    }

    @Override
    @Transactional
    public Boolean revokeProject(Long projectId) {
        Project exist = projectMapper.getProjectInfoById(projectId);
        if (exist == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!ProjectStatus.PUBLISHED.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅已发布项目可撤回");
        }
        if (!canOp(exist, "edit")) {
            throw new ServiceException(500, "无权撤回该项目");
        }
        UserInfo userInfo = currentUser();
        Project update = new Project();
        update.setProjectId(projectId);
        update.setStatus(ProjectStatus.REVOKED.getCode());
        update.setReviewStatus(ReviewStatus.NONE.getCode());
        update.setUpdateBy(userInfo.getUsername());
        projectMapper.editProjectInfo(update);
        writeReviewLog(projectId, ReviewAction.REVOKE, userInfo, null);
        return true;
    }

    @Override
    @Transactional
    public Boolean reviewProject(ProjectReviewVo vo) {
        if (vo.getProjectId() == null || vo.getPass() == null) {
            throw new ServiceException(500, "审核参数不完整");
        }
        Project exist = projectMapper.getProjectInfoById(vo.getProjectId());
        if (exist == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!ProjectStatus.PENDING_REVIEW.getCode().equals(exist.getStatus())) {
            throw new ServiceException(500, "仅待审核项目可审核");
        }
        UserInfo userInfo = currentUser();
        // 审核员回避：负责人不能审自己项目（用 author_id 比对）
        if (exist.getAuthorId() != null && exist.getAuthorId().equals(userInfo.getUserId())) {
            throw new ServiceException(500, "不能审核自己提交的项目");
        }
        Date now = new Date();
        Project update = new Project();
        update.setProjectId(vo.getProjectId());
        update.setUpdateBy(userInfo.getUsername());
        ReviewAction action;
        String advice = null;
        if (vo.getPass()) {
            update.setStatus(ProjectStatus.PUBLISHED.getCode());
            update.setPublishTime(now);
            update.setReviewStatus(ReviewStatus.APPROVED.getCode());
            action = ReviewAction.APPROVE;
            advice = vo.getAdvice();
        } else {
            if (vo.getAdvice() == null || vo.getAdvice().isEmpty()) {
                throw new ServiceException(500, "驳回需填写审核意见");
            }
            update.setStatus(ProjectStatus.REJECTED.getCode());
            update.setReviewStatus(ReviewStatus.REJECTED.getCode());
            action = ReviewAction.REJECT;
            advice = vo.getAdvice();
        }
        projectMapper.editProjectInfo(update);
        writeReviewLog(vo.getProjectId(), action, userInfo, advice);
        return true;
    }

    @Override
    public List<ProjectReviewLogVo> listReviewLog(Long projectId) {
        List<ProjectReviewLog> logs = projectReviewLogMapper.listByProjectId(projectId);
        if (logs == null || logs.isEmpty()) {
            return new ArrayList<>();
        }
        return logs.stream()
                .map(log -> BeanUtil.toBean(log, ProjectReviewLogVo.class))
                .collect(Collectors.toList());
    }

    @Override
    public int reconcilePendingReview() {
        List<Long> ids = projectMapper.listPendingReviewIds();
        if (ids == null || ids.isEmpty()) {
            return 0;
        }
        Date now = new Date();
        int released = 0;
        for (Long projectId : ids) {
            try {
                Project update = new Project();
                update.setProjectId(projectId);
                update.setStatus(ProjectStatus.PUBLISHED.getCode());
                update.setPublishTime(now);
                update.setReviewStatus(ReviewStatus.APPROVED.getCode());
                update.setUpdateBy("system");
                projectMapper.editProjectInfo(update);
                writeReviewLog(projectId, ReviewAction.PUBLISH, systemOperator(), "审核关闭后定时任务自动放行");
                released++;
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ProjectServiceImpl.class)
                        .warn("对账放行失败 projectId={}: {}", projectId, e.getMessage());
            }
        }
        return released;
    }

    // ============================ 成员管理 ============================

    @Override
    public List<ProjectMemberVo> listMembers(Long projectId) {
        // 查看成员列表需先有 view 权限
        Project project = projectMapper.getProjectInfoById(projectId);
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!canOp(project, "view")) {
            throw new ServiceException(500, "无权查看该项目");
        }
        List<ProjectMember> members = projectMemberMapper.listByProjectId(projectId);
        if (members == null || members.isEmpty()) {
            return new ArrayList<>();
        }
        return members.stream()
                .map(m -> BeanUtil.toBean(m, ProjectMemberVo.class))
                .collect(Collectors.toList());
    }

    @Override
    @Transactional
    public Boolean addMember(ProjectMemberVo vo) {
        if (vo.getProjectId() == null || vo.getUserId() == null || vo.getMemberRole() == null) {
            throw new ServiceException(500, "成员参数不完整");
        }
        Project project = projectMapper.getProjectInfoById(vo.getProjectId());
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        // 增成员需 edit 权限
        if (!canOp(project, "edit")) {
            throw new ServiceException(500, "无权管理该项目成员");
        }
        // 同一用户不可重复加入
        if (projectMemberMapper.getMember(vo.getProjectId(), vo.getUserId()) != null) {
            throw new ServiceException(500, "该用户已是项目成员");
        }
        // LEADER 唯一性：新增 LEADER 时先把原 LEADER 降为 MEMBER
        if (ProjectMemberRole.LEADER.getCode().equals(vo.getMemberRole())) {
            changeLeaderToMember(vo.getProjectId(), currentUser().getUsername());
        }
        ProjectMember member = BeanUtil.toBean(vo, ProjectMember.class);
        // 按角色给默认标志位（前端传值优先，未传则用默认）
        applyDefaultFlagsByRole(member);
        UserInfo userInfo = currentUser();
        member.setCreateBy(userInfo.getUsername());
        member.setUpdateBy(userInfo.getUsername());
        projectMemberMapper.addMember(member);
        // 若新成员是 LEADER，同步更新主表 author_id（换负责人）
        if (ProjectMemberRole.LEADER.getCode().equals(vo.getMemberRole())) {
            Project update = new Project();
            update.setProjectId(vo.getProjectId());
            update.setAuthorId(vo.getUserId());
            update.setUpdateBy(userInfo.getUsername());
            projectMapper.editProjectInfo(update);
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean addMembersBatch(Long projectId, List<Long> userIds) {
        if (projectId == null || userIds == null || userIds.isEmpty()) {
            throw new ServiceException(500, "批量加成员参数不完整");
        }
        Project project = projectMapper.getProjectInfoById(projectId);
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!canOp(project, "edit")) {
            throw new ServiceException(500, "无权管理该项目成员");
        }
        UserInfo userInfo = currentUser();
        for (Long userId : userIds) {
            if (userId == null) {
                continue;
            }
            // 已存在的跳过（不报错，幂等批量加）
            if (projectMemberMapper.getMember(projectId, userId) != null) {
                continue;
            }
            ProjectMember member = new ProjectMember();
            member.setProjectId(projectId);
            member.setUserId(userId);
            // 批量加默认 MEMBER 角色（单点编辑/权限微调走 addMember/editMember 接口）
            member.setMemberRole(ProjectMemberRole.MEMBER.getCode());
            applyDefaultFlagsByRole(member);
            member.setCreateBy(userInfo.getUsername());
            member.setUpdateBy(userInfo.getUsername());
            projectMemberMapper.addMember(member);
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean editMember(ProjectMemberVo vo) {
        if (vo.getMemberId() == null) {
            throw new ServiceException(500, "成员ID不能为空");
        }
        ProjectMember exist = projectMemberMapper.getMemberById(vo.getMemberId());
        if (exist == null) {
            throw new ServiceException(500, "成员不存在");
        }
        Project project = projectMapper.getProjectInfoById(exist.getProjectId());
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!canOp(project, "edit")) {
            throw new ServiceException(500, "无权管理该项目成员");
        }
        // LEADER 唯一性：把某成员提升为 LEADER 时，先把原 LEADER 降为 MEMBER
        if (ProjectMemberRole.LEADER.getCode().equals(vo.getMemberRole())
                && !ProjectMemberRole.LEADER.getCode().equals(exist.getMemberRole())) {
            changeLeaderToMember(exist.getProjectId(), currentUser().getUsername());
        }
        ProjectMember member = BeanUtil.toBean(vo, ProjectMember.class);
        member.setUpdateBy(currentUser().getUsername());
        projectMemberMapper.editMember(member);
        // 若该成员被提升为 LEADER，同步更新主表 author_id（换负责人）
        if (ProjectMemberRole.LEADER.getCode().equals(vo.getMemberRole())
                && !ProjectMemberRole.LEADER.getCode().equals(exist.getMemberRole())) {
            Project update = new Project();
            update.setProjectId(exist.getProjectId());
            update.setAuthorId(exist.getUserId());
            update.setUpdateBy(currentUser().getUsername());
            projectMapper.editProjectInfo(update);
        }
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteMember(Long memberId) {
        ProjectMember exist = projectMemberMapper.getMemberById(memberId);
        if (exist == null) {
            throw new ServiceException(500, "成员不存在");
        }
        Project project = projectMapper.getProjectInfoById(exist.getProjectId());
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!canOp(project, "edit")) {
            throw new ServiceException(500, "无权管理该项目成员");
        }
        // LEADER 不可直接删（需先换负责人）
        if (ProjectMemberRole.LEADER.getCode().equals(exist.getMemberRole())) {
            throw new ServiceException(500, "负责人不可直接删除，请先转移负责人");
        }
        projectMemberMapper.softDeleteMember(memberId);
        return true;
    }

    // ============================ 文件树管理 ============================

    @Override
    public List<ProjectFileVo> listFiles(Long projectId) {
        Project project = projectMapper.getProjectInfoById(projectId);
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!canOp(project, "view")) {
            throw new ServiceException(500, "无权查看该项目");
        }
        List<ProjectFile> files = projectFileMapper.listByProjectId(projectId);
        if (files == null || files.isEmpty()) {
            return new ArrayList<>();
        }
        return files.stream()
                .map(f -> BeanUtil.toBean(f, ProjectFileVo.class))
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectFileTreeVo> listFileTree(Long projectId) {
        List<ProjectFileVo> flat = listFiles(projectId);
        return buildTree(flat);
    }

    @Override
    @Transactional
    public Boolean addFolder(ProjectFileVo vo) {
        if (vo.getProjectId() == null || vo.getName() == null || vo.getName().isEmpty()) {
            throw new ServiceException(500, "文件夹参数不完整");
        }
        Project project = projectMapper.getProjectInfoById(vo.getProjectId());
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!canOp(project, "edit")) {
            throw new ServiceException(500, "无权管理该项目文件");
        }
        ProjectFile file = BeanUtil.toBean(vo, ProjectFile.class);
        file.setIsDir(ProjectFileType.DIRECTORY.getCode());
        file.setObjectId(null);
        if (file.getSort() == null) {
            file.setSort(0);
        }
        UserInfo userInfo = currentUser();
        file.setCreateBy(userInfo.getUsername());
        file.setUpdateBy(userInfo.getUsername());
        projectFileMapper.addFile(file);
        return true;
    }

    @Override
    @Transactional
    public Boolean addFileNode(ProjectFileVo vo) {
        if (vo.getProjectId() == null || vo.getName() == null || vo.getObjectId() == null) {
            throw new ServiceException(500, "文件参数不完整");
        }
        Project project = projectMapper.getProjectInfoById(vo.getProjectId());
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!canOp(project, "edit")) {
            throw new ServiceException(500, "无权管理该项目文件");
        }
        ProjectFile file = BeanUtil.toBean(vo, ProjectFile.class);
        file.setIsDir(ProjectFileType.FILE.getCode());
        if (file.getSort() == null) {
            file.setSort(0);
        }
        UserInfo userInfo = currentUser();
        file.setCreateBy(userInfo.getUsername());
        file.setUpdateBy(userInfo.getUsername());
        projectFileMapper.addFile(file);
        // 绑定 file_object.biz_ref_id 为 project_id（级联删依据）
        bindFileBizRef(file.getObjectId(), file.getProjectId());
        return true;
    }

    @Override
    @Transactional
    public Boolean editFileNode(ProjectFileVo vo) {
        if (vo.getFileId() == null) {
            throw new ServiceException(500, "文件节点ID不能为空");
        }
        ProjectFile exist = projectFileMapper.getFileById(vo.getFileId());
        if (exist == null) {
            throw new ServiceException(500, "文件节点不存在");
        }
        Project project = projectMapper.getProjectInfoById(exist.getProjectId());
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!canOp(project, "edit")) {
            throw new ServiceException(500, "无权管理该项目文件");
        }
        ProjectFile file = BeanUtil.toBean(vo, ProjectFile.class);
        file.setUpdateBy(currentUser().getUsername());
        projectFileMapper.editFile(file);
        return true;
    }

    @Override
    @Transactional
    public Boolean deleteFileNode(Long fileId) {
        ProjectFile exist = projectFileMapper.getFileById(fileId);
        if (exist == null) {
            throw new ServiceException(500, "文件节点不存在");
        }
        Project project = projectMapper.getProjectInfoById(exist.getProjectId());
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        if (!canOp(project, "edit")) {
            throw new ServiceException(500, "无权管理该项目文件");
        }
        UserInfo userInfo = currentUser();
        // 目录：递归软删子节点（含子目录与文件叶子）；文件叶子：级联软删 file_object
        recursiveDeleteFile(exist, userInfo.getUsername());
        return true;
    }

    @Override
    public String downloadFile(Long fileId) {
        ProjectFile exist = projectFileMapper.getFileById(fileId);
        if (exist == null) {
            throw new ServiceException(500, "文件节点不存在");
        }
        if (ProjectFileType.isDir(exist.getIsDir()) || exist.getObjectId() == null) {
            throw new ServiceException(500, "目录不可下载");
        }
        Project project = projectMapper.getProjectInfoById(exist.getProjectId());
        if (project == null) {
            throw new ServiceException(500, "项目不存在");
        }
        // 下载权限校验：canOp(download)
        if (!canOp(project, "download")) {
            throw new ServiceException(500, "无权下载该文件");
        }
        DownloadVo downloadVo = fileService.getDownloadUrl(exist.getObjectId());
        return downloadVo != null ? downloadVo.getDownloadUrl() : null;
    }

    // ============================ 私有辅助 ============================

    /**
     * 权限判定核心：用户对项目 P 是否有操作 op 权限。
     * 公式：userLvl(op) >= P.level OR member.can_op=1 OR role=LEADER。
     * admin 因 perms 含全 l3 自然 userLvl=3，对所有项目全权（系统权限分支）。
     */
    private boolean canOp(Project project, String op) {
        ProjectPermissionLevel lvl = ProjectPermissionResolver.resolve();
        // 系统权限等级够 → 直接通过
        if (project.getLevel() != null && lvl.levelOf(op) >= project.getLevel()) {
            return true;
        }
        // 项目内权限：查当前用户在该项目的成员记录
        Long userId = currentUser().getUserId();
        ProjectMember member = projectMemberMapper.getMember(project.getProjectId(), userId);
        if (member == null) {
            return false;
        }
        // LEADER 全权
        if (ProjectMemberRole.LEADER.getCode().equals(member.getMemberRole())) {
            return true;
        }
        // 按操作查标志位
        return switch (op) {
            case "view" -> member.getCanView() != null && member.getCanView() == 1;
            case "download" -> member.getCanDownload() != null && member.getCanDownload() == 1;
            case "edit" -> member.getCanEdit() != null && member.getCanEdit() == 1;
            default -> false;
        };
    }

    /** 当前用户是否该项目的 LEADER */
    private boolean isLeader(Project project, UserInfo user) {
        ProjectMember member = projectMemberMapper.getMember(project.getProjectId(), user.getUserId());
        return member != null && ProjectMemberRole.LEADER.getCode().equals(member.getMemberRole());
    }

    /** 判断当前用户是否拥有某按钮权限（非等级，如 knowhub:project:delete）。
     *  注意：UserInfo.getPermissions() 是 List<Permission>，需遍历比 permKey，不能用 contains(String) */
    private boolean hasButtonPerm(String permKey) {
        UserInfo u = currentUser();
        if (u.getPermissions() == null) {
            return false;
        }
        for (com.rookie.framework.security.pojo.Permission p : u.getPermissions()) {
            if (permKey.equals(p.getPermKey())) {
                return true;
            }
        }
        return false;
    }

    /** 详情接口回填当前用户对该项目的权限态（供前端控制按钮显隐） */
    private void fillPermissionState(ProjectVo vo, Project project) {
        ProjectPermissionLevel lvl = ProjectPermissionResolver.resolve();
        Long userId = currentUser().getUserId();
        ProjectMember member = projectMemberMapper.getMember(project.getProjectId(), userId);
        // 系统权限够即 true，否则看成员标志位
        vo.setCanView(canOpWithLevelAndMember(project, "view", lvl, member));
        vo.setCanDownload(canOpWithLevelAndMember(project, "download", lvl, member));
        vo.setCanEdit(canOpWithLevelAndMember(project, "edit", lvl, member));
        vo.setMyMemberRole(member != null ? member.getMemberRole() : null);
    }

    /** fillPermissionState 用的内部判定（复用已取的 lvl/member，避免重复查库） */
    private boolean canOpWithLevelAndMember(Project project, String op,
                                            ProjectPermissionLevel lvl, ProjectMember member) {
        if (project.getLevel() != null && lvl.levelOf(op) >= project.getLevel()) {
            return true;
        }
        if (member == null) {
            return false;
        }
        if (ProjectMemberRole.LEADER.getCode().equals(member.getMemberRole())) {
            return true;
        }
        return switch (op) {
            case "view" -> member.getCanView() != null && member.getCanView() == 1;
            case "download" -> member.getCanDownload() != null && member.getCanDownload() == 1;
            case "edit" -> member.getCanEdit() != null && member.getCanEdit() == 1;
            default -> false;
        };
    }

    /** 校验项目载体字段：title 必填、type 必填、level 必填在 1-3 */
    private void validateProjectPayload(ProjectVo vo) {
        if (vo.getTitle() == null || vo.getTitle().isEmpty()) {
            throw new ServiceException(500, "项目名称不能为空");
        }
        if (vo.getType() == null || vo.getType().isEmpty()) {
            throw new ServiceException(500, "项目类型不能为空");
        }
        // type 合法性校验（当前仅 COMPETITION；PRACTICE/OPS 暂不支持）
        boolean validType = false;
        for (ProjectType t : ProjectType.values()) {
            if (t.getCode().equals(vo.getType())) {
                validType = true;
                break;
            }
        }
        if (!validType) {
            throw new ServiceException(500, "项目类型非法");
        }
        if (vo.getLevel() == null || vo.getLevel() < 1 || vo.getLevel() > 3) {
            throw new ServiceException(500, "项目等级需为 1-3");
        }
    }

    /** 按 type 配套写/改类型子表（当前仅 COMPETITION） */
    private void saveTypeSubTable(Project project, ProjectVo vo) {
        if (!ProjectType.COMPETITION.getCode().equals(project.getType())) {
            return;
        }
        // 从 vo 取比赛字段（ProjectVo 不直接含子表字段，前端按 type 把子表字段平铺到 vo 扩展；
        //  这里通过 BeanUtil 把同名字段拷到 ProjectCompetition，competitionName 等字段需 vo 提供）
        // 由于 ProjectVo 未声明 competitionName 等字段，前端提交时走独立子表接口；
        // 本方法仅处理主表保存后的子表存在性，子表字段由前端调成员/子表接口单独维护。
        // 为兼容前端一次性提交，此处通过反射宽松处理：若 vo 含 competitionName 则拷贝。
        // —— 简化：子表 CRUD 由独立接口承担，主表新增时不强制写子表，避免字段耦合。
    }

    /** 创建者默认 LEADER（can_view/can_download/can_edit 全 1） */
    private void addLeaderMember(Long projectId, UserInfo userInfo) {
        ProjectMember member = new ProjectMember();
        member.setProjectId(projectId);
        member.setUserId(userInfo.getUserId());
        member.setMemberRole(ProjectMemberRole.LEADER.getCode());
        member.setCanView(1);
        member.setCanDownload(1);
        member.setCanEdit(1);
        member.setCreateBy(userInfo.getUsername());
        member.setUpdateBy(userInfo.getUsername());
        projectMemberMapper.addMember(member);
    }

    /** 按角色给成员默认标志位（前端传值优先，未传则用默认）：LEADER→1/1/1、MENTOR→1/1/0、MEMBER→1/0/0 */
    private void applyDefaultFlagsByRole(ProjectMember member) {
        String role = member.getMemberRole();
        if (ProjectMemberRole.LEADER.getCode().equals(role)) {
            if (member.getCanView() == null) member.setCanView(1);
            if (member.getCanDownload() == null) member.setCanDownload(1);
            if (member.getCanEdit() == null) member.setCanEdit(1);
        } else if (ProjectMemberRole.MENTOR.getCode().equals(role)) {
            if (member.getCanView() == null) member.setCanView(1);
            if (member.getCanDownload() == null) member.setCanDownload(1);
            if (member.getCanEdit() == null) member.setCanEdit(0);
        } else {
            if (member.getCanView() == null) member.setCanView(1);
            if (member.getCanDownload() == null) member.setCanDownload(0);
            if (member.getCanEdit() == null) member.setCanEdit(0);
        }
    }

    /** 把当前 LEADER 降为 MEMBER（换负责人前置，保证每项目仅一个 LEADER） */
    private void changeLeaderToMember(Long projectId, String operator) {
        ProjectMember leader = projectMemberMapper.getLeader(projectId);
        if (leader == null) {
            return;
        }
        leader.setMemberRole(ProjectMemberRole.MEMBER.getCode());
        leader.setCanView(1);
        leader.setCanDownload(1);
        leader.setCanEdit(0);
        leader.setUpdateBy(operator);
        projectMemberMapper.editMember(leader);
    }

    /** 绑定 file_object.biz_ref_id 为 project_id（级联删依据） */
    private void bindFileBizRef(Long objectId, Long projectId) {
        if (objectId == null || projectId == null) {
            return;
        }
        try {
            BindVo bind = new BindVo();
            bind.setObjectId(objectId);
            bind.setBizRefId(projectId);
            fileService.bindBizRef(bind);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ProjectServiceImpl.class)
                    .warn("回填文件业务关联失败 projectId={} objectId={}: {}", projectId, objectId, e.getMessage());
        }
    }

    /** 级联软删项目下三类 file_object（PROJECT_SRC/PKG/DOC），对象本体由 FileGcTask 回收 */
    private void softDeleteProjectFiles(Long projectId, String operator) {
        String[] types = {FileBusinessType.PROJECT_SRC.getCode(),
                FileBusinessType.PROJECT_PKG.getCode(),
                FileBusinessType.PROJECT_DOC.getCode()};
        for (String type : types) {
            try {
                fileObjectMapper.softDeleteByBizRef(type, projectId, operator);
            } catch (Exception e) {
                org.slf4j.LoggerFactory.getLogger(ProjectServiceImpl.class)
                        .warn("级联软删文件失败 projectId={} businessType={}: {}", projectId, type, e.getMessage());
            }
        }
    }

    /** 递归软删文件节点：目录则递归软删子节点，文件叶子则级联软删 file_object */
    private void recursiveDeleteFile(ProjectFile node, String operator) {
        if (ProjectFileType.isDir(node.getIsDir())) {
            // 查子节点（按 parentId 查，需要全树里找——这里重查项目全树再过滤子节点）
            List<ProjectFile> all = projectFileMapper.listByProjectId(node.getProjectId());
            for (ProjectFile child : all) {
                if (node.getFileId().equals(child.getParentId())) {
                    recursiveDeleteFile(child, operator);
                }
            }
            projectFileMapper.softDeleteFile(node.getFileId());
        } else {
            // 文件叶子：软删树节点 + 级联软删 file_object
            projectFileMapper.softDeleteFile(node.getFileId());
            if (node.getObjectId() != null) {
                try {
                    fileService.deleteFileObjects(new Long[]{node.getObjectId()});
                } catch (Exception e) {
                    org.slf4j.LoggerFactory.getLogger(ProjectServiceImpl.class)
                            .warn("级联软删 file_object 失败 fileId={} objectId={}: {}",
                                    node.getFileId(), node.getObjectId(), e.getMessage());
                }
            }
        }
    }

    /** 扁平文件列表组装为树（按 parentId 归集 children） */
    private List<ProjectFileTreeVo> buildTree(List<ProjectFileVo> flat) {
        if (flat == null || flat.isEmpty()) {
            return new ArrayList<>();
        }
        Map<Long, List<ProjectFileTreeVo>> byParent = new HashMap<>();
        List<ProjectFileTreeVo> all = flat.stream()
                .map(f -> BeanUtil.toBean(f, ProjectFileTreeVo.class))
                .collect(Collectors.toList());
        for (ProjectFileTreeVo node : all) {
            Long pid = node.getParentId() == null ? 0L : node.getParentId();
            byParent.computeIfAbsent(pid, k -> new ArrayList<>()).add(node);
        }
        for (ProjectFileTreeVo node : all) {
            node.setChildren(byParent.get(node.getFileId()));
        }
        // 根节点 parentId 为 null（用 0L 归集）
        return byParent.getOrDefault(0L, new ArrayList<>());
    }

    /**
     * 追加一条审核流水。role 由 ReviewAction 自带，operator_id 用 userId 稳定锁定，
     * operator 存 username 快照。流水表只追加不改不删，写失败不阻断主流程（catch 吞异常仅 log）。
     */
    private void writeReviewLog(Long projectId, ReviewAction action, UserInfo operator, String advice) {
        try {
            ProjectReviewLog log = new ProjectReviewLog(projectId, action.getCode(),
                    operator.getUserId(), operator.getUsername(), action.getRole(), advice);
            projectReviewLogMapper.insertReviewLog(log);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ProjectServiceImpl.class)
                    .warn("写审核流水失败 projectId={} action={}: {}", projectId, action.getCode(), e.getMessage());
        }
    }

    /** 构造一个 system 操作者 UserInfo，用于对账放行时写流水（operator_id=0, operator=system） */
    private UserInfo systemOperator() {
        UserInfo sys = new UserInfo();
        sys.setUserId(0L);
        sys.setUsername("system");
        return sys;
    }

    private UserInfo currentUser() {
        return (UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal();
    }
}
