package com.knowhub.service.project;

import cn.hutool.core.bean.BeanUtil;
import com.github.pagehelper.PageInfo;
import com.knowhub.config.PortalConfigReader;
import com.knowhub.enums.history.ViewBizType;
import com.knowhub.enums.project.ProjectFileType;
import com.knowhub.mapper.project.ProjectFileMapper;
import com.knowhub.mapper.project.ProjectCollectMapper;
import com.knowhub.mapper.project.ProjectMemberMapper;
import com.knowhub.mapper.project.ProjectPortalMapper;
import com.knowhub.pojo.project.entity.ProjectFile;
import com.knowhub.pojo.project.entity.ProjectMember;
import com.knowhub.pojo.project.entity.ProjectCollect;
import com.knowhub.enums.project.ProjectMemberRole;
import com.knowhub.pojo.project.quarry.ProjectPortalSearchQuarry;
import com.knowhub.pojo.project.vo.ProjectFileVo;
import com.knowhub.pojo.project.vo.ProjectMemberVo;
import com.knowhub.pojo.project.vo.ProjectPortalDetailVo;
import com.knowhub.pojo.project.vo.ProjectPortalVo;
import com.knowhub.service.history.impl.ViewHistoryService;
import com.knowhub.service.project.impl.ProjectPortalService;
import com.knowhub.service.storage.impl.FileService;
import com.knowhub.support.ProjectPermissionResolver;
import com.rookie.common.exception.ServiceException;
import com.rookie.common.util.PageUtil;
import com.rookie.framework.security.pojo.UserInfo;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 前台项目门户 Service 实现。
 * <p>
 * 照博客 {@code BlogPortalServiceImpl} 同构：前台无 @PreAuthorize、走 /portal/** permitAll，
 * 登录态防御性获取（principal 非 UserInfo 视为未登录 null）。
 * 分级开关 {@code knowhub.portal.hierarchical.enabled} 关→userViewLevel 恒 1（二元闸，L1+L2 带 locked）；
 * 开→max(1, ProjectPermissionResolver.level())（单键 knowhub:project:lN，未登录/无 perm level=0→1 看 L1；有等级者看 L1~LN）。
 * 2026-08-18 权限大修搜索范围 +1：列表 where 片段 level<=userViewLevel+1，越级作品进列表带 locked=true、summary 可见；
 * 详情 meta 不带 level 过滤由 service 判越级 → 锁态 VO（**description 仍下发**决策#5，只锁下载 canDownload=false +
 * locked + lockReason），不抛 403。
 * canDownload：作者 OR admin OR LEADER OR 系统等级够(level>=P.level) OR 成员 can_download=1（单键后用 level 比较）。
 *
 * @author knowhub
 */
@Service
public class ProjectPortalServiceImpl implements ProjectPortalService {

    @Autowired
    ProjectPortalMapper projectPortalMapper;

    @Autowired
    PortalConfigReader portalConfigReader;

    @Autowired
    ViewHistoryService viewHistoryService;

    @Autowired
    ProjectMemberMapper projectMemberMapper;

    @Autowired
    ProjectCollectMapper projectCollectMapper;

    @Autowired
    ProjectFileMapper projectFileMapper;

    @Autowired
    FileService fileService;

    @Override
    public PageInfo<ProjectPortalVo> search(ProjectPortalSearchQuarry quarry) {
        quarry.setUserViewLevel(resolveUserViewLevel());
        PageUtil.startPage();
        List<ProjectPortalVo> list = projectPortalMapper.searchProjects(quarry);
        fillLockedForList(list, quarry.getUserViewLevel());
        return new PageInfo<>(list);
    }

    @Override
    public List<ProjectPortalVo> recommend(int size, Long excludeProjectId) {
        Integer userViewLevel = resolveUserViewLevel();
        UserInfo user = currentUserOrNull();
        List<Long> viewedProjectIds = Collections.emptyList();
        if (user != null) {
            // 登录用户：排除已浏览过的项目，防看点重复
            viewedProjectIds = projectPortalMapper.viewedProjectIdsByUser(user.getUserId());
        }
        List<ProjectPortalVo> list = projectPortalMapper.recommendHot(
                userViewLevel, excludeProjectId, viewedProjectIds, size);
        fillLockedForList(list, userViewLevel);
        return list;
    }

    @Override
    public PageInfo<ProjectPortalVo> listMyCollected(int pageNum, int pageSize) {
        UserInfo user = currentUserOrNull();
        if (user == null) {
            // authoring controller 已 isAuthenticated 兜底，此处双保险
            return new PageInfo<>(Collections.emptyList());
        }
        List<Long> projectIds = projectCollectMapper.listCollectedProjectIds(user.getUserId());
        if (projectIds == null || projectIds.isEmpty()) {
            return new PageInfo<>(Collections.emptyList());
        }
        // listByIds 取"收藏 ID 集 ∩ 前台可见"全量 VO（前台铁律过滤未发布/越级），不排序——
        // service 按收藏时间倒序的 projectIds 顺序拼装，还原"最近收藏在前"语义。
        // 2026-08-12 修正：原实现误调 recommendHot(全局热门 topN, size=收藏数) 再求交集——收藏项目不在
        // 全局热门 topN 里即被丢，收藏列表为空。改为 listByIds 精确召回。
        Integer userViewLevel = resolveUserViewLevel();
        List<ProjectPortalVo> all = projectPortalMapper.listByIds(userViewLevel, projectIds);
        Map<Long, ProjectPortalVo> voMap = new HashMap<>();
        for (ProjectPortalVo vo : all) {
            voMap.put(vo.getProjectId(), vo);
        }
        List<ProjectPortalVo> ordered = new ArrayList<>();
        for (Long pid : projectIds) {
            ProjectPortalVo vo = voMap.get(pid);
            if (vo != null) {
                ordered.add(vo);
            }
        }
        fillLockedForList(ordered, userViewLevel);
        // 项目无标签体系，无需 fillTagsForList
        return new PageInfo<>(ordered);
    }

    @Override
    public ProjectPortalDetailVo getDetail(Long projectId) {
        Integer userViewLevel = resolveUserViewLevel();
        ProjectPortalDetailVo meta = projectPortalMapper.getPortalProjectMeta(projectId);
        if (meta == null) {
            // 不存在或非 PUBLISHED（草稿/审核中/撤回），前台 404 语义由 controller 处理
            return null;
        }
        Integer level = meta.getLevel();
        // canDownload 权限态回填（前端据此控制文件树下载按钮显隐，传 meta 避免重复查库）
        meta.setCanDownload(canDownload(projectId, level, meta));
        fillCurrentUserCollect(meta, projectId);
        // 越级锁态：level > userViewLevel → 仍下发 description（决策#5，项目越级只锁下载、description 可见），
        // 只置 locked + lockReason + canDownload 已回填为 false（锁下载按钮）。越级不计浏览量。
        if (level != null && level > userViewLevel) {
            meta.setLocked(true);
            meta.setLockReason("需 L" + level + " 权限查看/下载完整内容");
            meta.setDescription(projectPortalMapper.getProjectDescription(projectId));
            return meta;
        }
        // 达权：取 description 正文 + 计浏览量（仅登录态计，未登录不计，对应决策#3）
        meta.setLocked(false);
        meta.setDescription(projectPortalMapper.getProjectDescription(projectId));
        meta.setLockReason(null);
        UserInfo user = currentUserOrNull();
        if (user != null) {
            viewHistoryService.recordView(user.getUserId(), ViewBizType.PROJECT.getCode(), projectId);
        }
        return meta;
    }

    @Override
    public List<ProjectPortalVo> related(Long projectId, int size) {
        Integer userViewLevel = resolveUserViewLevel();
        List<ProjectPortalVo> list = projectPortalMapper.relatedProjects(projectId, userViewLevel, size);
        fillLockedForList(list, userViewLevel);
        return list;
    }

    @Override
    public List<ProjectMemberVo> listMembers(Long projectId) {
        // 项目存在性走 meta 做 PUBLISHED 校验（非 PUBLISHED 不下发公开参与人员）
        ProjectPortalDetailVo meta = projectPortalMapper.getPortalProjectMeta(projectId);
        if (meta == null) {
            return Collections.emptyList();
        }
        List<ProjectMember> members = projectMemberMapper.listByProjectId(projectId);
        if (members == null || members.isEmpty()) {
            return new ArrayList<>();
        }
        // 公开访问清理内部权限标志位（canView/canDownload/canEdit 不暴露给访客，
        // 仅留昵称/用户名/角色，参与人员纯展示用途，照 admin listMembers 出参但裁剪敏感字段）
        return members.stream()
                .map(m -> {
                    ProjectMemberVo vo = cn.hutool.core.bean.BeanUtil.toBean(m, ProjectMemberVo.class);
                    vo.setCanView(null);
                    vo.setCanDownload(null);
                    vo.setCanEdit(null);
                    return vo;
                })
                .collect(Collectors.toList());
    }

    @Override
    public List<ProjectFileVo> listFiles(Long projectId) {
        // 项目存在性走 meta 做 PUBLISHED 校验（非 PUBLISHED 项目文件树不下发前台）
        ProjectPortalDetailVo meta = projectPortalMapper.getPortalProjectMeta(projectId);
        if (meta == null) {
            return Collections.emptyList();
        }
        // 可见性校验：达 userViewLevel OR 成员 can_view=1 OR LEADER，否则空列表（不暴露文件结构给无权者）
        if (!canViewProject(projectId, meta.getLevel())) {
            return Collections.emptyList();
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
    public String getFileDownloadUrl(Long fileId) {
        ProjectFile exist = projectFileMapper.getFileById(fileId);
        if (exist == null) {
            throw new ServiceException(500, "文件节点不存在");
        }
        if (ProjectFileType.isDir(exist.getIsDir()) || exist.getObjectId() == null) {
            throw new ServiceException(500, "目录不可下载");
        }
        ProjectPortalDetailVo meta = projectPortalMapper.getPortalProjectMeta(exist.getProjectId());
        if (meta == null) {
            throw new ServiceException(500, "项目不存在或未发布");
        }
        // 下载权限：canDownload（系统 download:lN 够 OR 成员 can_download=1 OR LEADER），未登录 null userId 返回 false
        if (!canDownload(exist.getProjectId(), meta.getLevel())) {
            throw new ServiceException(500, "无权下载该文件");
        }
        // 取下载链接：项目层已 canDownload 鉴权（业务可见性闸），传 bizAuthorized=true 跳过文件底座 owner 闸。
        // 否则登录非上传人（如成员 can_download=1）下不了项目文件（owner 闸只认项目文件上传人/文件管理员）。
        com.knowhub.pojo.storage.vo.DownloadVo downloadVo = fileService.getDownloadUrl(exist.getObjectId(), true);
        // 下载计数 +1（与 admin downloadFile 同口径；前台独立路径不再走 admin service，故此自增；
        // 计数失败不阻断下载，仅记日志，推荐打分权重最高需依赖此计数）
        try {
            projectPortalMapper.incrDownloadCount(exist.getProjectId(), 1);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ProjectPortalServiceImpl.class)
                    .warn("项目 {} 下载计数 +1 失败，不影响下载链接下发", exist.getProjectId(), e);
        }
        return downloadVo != null ? downloadVo.getDownloadUrl() : null;
    }

    @Override
    public com.knowhub.pojo.project.vo.ProjectPackageBundle listProjectPackageEntries(Long projectId) {
        // 判 PUBLISHED + canDownload，与 getFileDownloadUrl 同口径但维度为"项目级"
        ProjectPortalDetailVo meta = projectPortalMapper.getPortalProjectMeta(projectId);
        if (meta == null) {
            throw new ServiceException(500, "项目不存在或未发布");
        }
        if (!canDownload(projectId, meta.getLevel())) {
            throw new ServiceException(500, "无权下载该文件");
        }
        com.knowhub.pojo.project.vo.ProjectPackageBundle bundle =
                new com.knowhub.pojo.project.vo.ProjectPackageBundle(meta.getTitle(), new java.util.ArrayList<>());
        java.util.List<ProjectFile> files = projectFileMapper.listByProjectId(projectId);
        if (files == null || files.isEmpty()) {
            return bundle;
        }
        // 建 fileId→ProjectFile 反查表用于沿 parentId 上溯拼相对路径
        java.util.Map<Long, ProjectFile> byId = new java.util.HashMap<>();
        for (ProjectFile f : files) {
            if (f.getFileId() != null) {
                byId.put(f.getFileId(), f);
            }
        }
        java.util.List<com.knowhub.pojo.project.vo.ProjectPackageEntry> entries = new java.util.ArrayList<>();
        java.util.Set<String> usedPaths = new java.util.HashSet<>();
        for (ProjectFile f : files) {
            // 仅文件叶子入 zip（目录由叶子路径的 "/" 段还原）
            if (ProjectFileType.isDir(f.getIsDir()) || f.getObjectId() == null) {
                continue;
            }
            // 沿 parentId 上溯拼 "祖先/.../name"，根节点 parent=null 直接用 name
            java.util.LinkedList<String> parts = new java.util.LinkedList<>();
            parts.addFirst(f.getName());
            Long pid = f.getParentId();
            int guard = 0; // 防御环（理论上文件树无环，限深 64）
            while (pid != null && guard++ < 64) {
                ProjectFile parent = byId.get(pid);
                if (parent == null) break;
                parts.addFirst(parent.getName());
                pid = parent.getParentId();
            }
            String zipPath = String.join("/", parts);
            // 同名路径兜底（不同文件夹同名文件不影响，同文件夹同名极少见，加序号避免 ZipEntry 重复）
            if (usedPaths.contains(zipPath)) {
                int seq = 2;
                while (usedPaths.contains(zipPath + "_" + seq)) seq++;
                zipPath = zipPath + "_" + seq;
            }
            usedPaths.add(zipPath);
            entries.add(new com.knowhub.pojo.project.vo.ProjectPackageEntry(f.getFileId(), f.getObjectId(), zipPath));
        }
        // 下载计数 +1（一次整包下载算一次项目下载，与单文件下载同口径；计数失败不阻断）
        try {
            projectPortalMapper.incrDownloadCount(projectId, 1);
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ProjectPortalServiceImpl.class)
                    .warn("项目 {} 整包下载计数 +1 失败", projectId, e);
        }
        bundle.setEntries(entries);
        return bundle;
    }

    // ============================ 私有辅助 ============================

    /**
     * 解析前台 userViewLevel：
     * 分级开关关 → 恒 1（二元闸，所有人只看 L1+L2 带 locked）；
     * 开 → max(1, ProjectPermissionResolver.level())（单键 knowhub:project:lN，未登录/无 perm level=0→1 看 L1；有等级者看 L1~LN）。
     * 2026-08-18 权限大修单键化 + 搜索范围 +1：where 片段 level<=userViewLevel+1，越级作品进列表带 locked。
     * 与博客 {@code BlogPortalServiceImpl.resolveUserViewLevel} 同构。
     * <p>
     * admin 短路兜底：admin 默认拥有所有权限、能看任何级别作品，**在分级开关判定之前**直接返回 3。
     * 不受 hierarchical 开关（关时普通用户恒 1）与 sys_role_menu 绑定（admin 角色未绑 l3 菜单也能得 3）影响。
     * UserInfo.isAdmin() 由 rookie 框架 UserDetailServiceImpl 依 roleKey="admin" 装载（本类 canDownload/canViewProject 已用同口径）。
     */
    private Integer resolveUserViewLevel() {
        // admin 短路：admin 看任何级别，不受分级开关与角色菜单绑定影响
        UserInfo user = currentUserOrNull();
        if (user != null && user.isAdmin()) {
            return 3;
        }
        if (!portalConfigReader.isHierarchicalEnabled()) {
            return 1;
        }
        int level = ProjectPermissionResolver.resolve().level();
        return Math.max(1, level);
    }

    /**
     * 当前用户对项目 P 的下载权限判定（照 admin {@code ProjectServiceImpl.canOp(download)} 范式，2026-08-18 单键化）：
     * 作者 OR admin OR LEADER OR 系统等级够(level>=P.level) OR 成员 can_download=1。
     * 单键 knowhub:project:lN 后 resolver 只提供 level()，下载闸用 level 与项目 level 比较（不再有独立 download:lN）。
     * 未登录/匿名返回 false（未登录看不到非 L1 项目，且 L1 项目下载仍需下载权限——L1 level=1，未登录 level=0 不够，返 false）。
     * 复用调用方已查的 meta（避免 getDetail 重复查库）；调用方未查时走重载 canDownload(projectId, level)。
     */
    private boolean canDownload(Long projectId, Integer projectLevel) {
        return canDownload(projectId, projectLevel, null);
    }

    private boolean canDownload(Long projectId, Integer projectLevel, ProjectPortalDetailVo meta) {
        UserInfo user = currentUserOrNull();
        // 作者全权（创建者=LEADER，能下载自己项目不论等级）
        Long authorId = meta != null ? meta.getAuthorId() : null;
        if (authorId == null) {
            // 调用方未传 meta，反查一次（仅 getFileDownloadUrl/listProjectPackageEntries 走此分支）
            ProjectPortalDetailVo m = projectPortalMapper.getPortalProjectMeta(projectId);
            authorId = m != null ? m.getAuthorId() : null;
        }
        if (authorId != null && user != null && authorId.equals(user.getUserId())) {
            return true;
        }
        // admin 全权
        if (user != null && user.isAdmin()) {
            return true;
        }
        // 系统权限等级够 → 通过
        ProjectPermissionResolver.ProjectPermissionLevel lvl = ProjectPermissionResolver.resolve();
        if (projectLevel != null && lvl.level() >= projectLevel) {
            return true;
        }
        if (user == null) {
            return false;
        }
        // 项目内权限：查成员记录
        ProjectMember member = projectMemberMapper.getMember(projectId, user.getUserId());
        if (member == null) {
            return false;
        }
        // LEADER 全权
        if (ProjectMemberRole.LEADER.getCode().equals(member.getMemberRole())) {
            return true;
        }
        return member.getCanDownload() != null && member.getCanDownload() == 1;
    }

    /**
     * 当前用户对项目 P 的可见性判定（listFiles 用，照博客 listFiles 范式，2026-08-18 单键化）：
     * 作者 OR admin OR LEADER OR 系统等级够(level>=P.level) OR 成员 can_view=1。未登录仅能看 L1（由 userViewLevel 兜底）。
     */
    private boolean canViewProject(Long projectId, Integer projectLevel) {
        UserInfo user = currentUserOrNull();
        // 作者/admin 全权
        if (user != null && user.isAdmin()) {
            return true;
        }
        ProjectPermissionResolver.ProjectPermissionLevel lvl = ProjectPermissionResolver.resolve();
        if (projectLevel != null && lvl.level() >= projectLevel) {
            return true;
        }
        if (user == null) {
            return false;
        }
        ProjectMember member = projectMemberMapper.getMember(projectId, user.getUserId());
        if (member == null) {
            return false;
        }
        if (ProjectMemberRole.LEADER.getCode().equals(member.getMemberRole())) {
            return true;
        }
        return member.getCanView() != null && member.getCanView() == 1;
    }

    /**
     * 防御性取当前登录用户：principal 是 UserInfo 才返回，否则 null（未登录/匿名）。
     * 前台 permitAll 区不能像后台那样强转（会 NPE），照博客 {@code BlogPortalServiceImpl.currentUserOrNull}。
     */
    private UserInfo currentUserOrNull() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return null;
        }
        Object p = auth.getPrincipal();
        return (p instanceof UserInfo) ? (UserInfo) p : null;
    }

    /**
     * 详情回填当前用户的收藏态（登录态查 project_collect 事实表，未登录置 null 不查库）。
     * 项目无点赞链路（无 ProjectLike 实体/事实表/toggle 端点），故只回填 hasCollected。
     * 照 ResourcePortalServiceImpl.fillCurrentUserInteract 同范式：Boolean 包装类型，未登录留 null。
     */
    private void fillCurrentUserCollect(ProjectPortalDetailVo vo, Long projectId) {
        UserInfo user = currentUserOrNull();
        if (user == null) {
            return; // 未登录：hasCollected 留 null
        }
        vo.setHasCollected(projectCollectMapper.getProjectCollect(
                new ProjectCollect(projectId, user.getUserId())) != null);
    }

    /**
     * 批量回填越级锁标记：vo.level > userViewLevel → locked=true（2026-08-18 搜索范围 +1 落地）。
     * 越级作品进列表带锁标记、summary 可见，前端据此渲染锁图标；达权 locked=false。O(n) n=页大小，开销可忽略。
     * 项目越级只锁下载（description 在详情仍下发），列表层仅标 locked 不置空字段。
     */
    private void fillLockedForList(List<ProjectPortalVo> list, Integer userViewLevel) {
        if (list == null || list.isEmpty()) return;
        for (ProjectPortalVo vo : list) {
            Integer level = vo.getLevel();
            vo.setLocked(level != null && level > userViewLevel);
        }
    }
}