package com.knowhub.service.project.impl;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.project.quarry.ProjectPortalSearchQuarry;
import com.knowhub.pojo.project.vo.ProjectFileVo;
import com.knowhub.pojo.project.vo.ProjectMemberVo;
import com.knowhub.pojo.project.vo.ProjectPortalDetailVo;
import com.knowhub.pojo.project.vo.ProjectPortalVo;

import java.util.List;

/**
 * 前台项目门户 Service（/portal/project/* 项目展示相关业务逻辑集中在此）。
 * <p>
 * 接口只暴露 DTO，不暴露实体。前台无 @PreAuthorize，登录态在 service 内防御性获取
 * （principal 非 UserInfo 视为未登录，照博客 {@code BlogPortalServiceImpl.currentUserOrNull}）。
 * 分级开关 {@code knowhub.portal.hierarchical.enabled} 关→userViewLevel 恒 1，开→max(1, resolver.view())。
 * <p>
 * 项目无标签体系，故无 hotTags/listEnabledTags（与博客门户差异点）——前台标签热度在博客门户。
 *
 * @author knowhub
 */
public interface ProjectPortalService {

    /** 搜索：标题 keyword + type/level 复合过滤 + 排序，分页 */
    PageInfo<ProjectPortalVo> search(ProjectPortalSearchQuarry quarry);

    /**
     * 项目推荐 feed（全局热门兜底，登录用户排除已浏览项目，详情页相关推荐时排除 excludeProjectId）。
     * <p>
     * 项目无标签，不走"偏好 tag 召回"那步；推荐打分用 download*3+like*2+collect*1+view*1 时间衰减。
     * @param size 召回条数
     * @param excludeProjectId 排除的项目ID（详情页相关推荐时排除当前；feed 可为 null）
     */
    List<ProjectPortalVo> recommend(int size, Long excludeProjectId);

    /** 前台公开详情（含 description/越级锁态降级 + canDownload 权限态回填 + 浏览量计数） */
    ProjectPortalDetailVo getDetail(Long projectId);

    /** 详情页相关推荐（同 type 排除自身） */
    List<ProjectPortalVo> related(Long projectId, int size);

    /**
     * 前台项目文件树（扁平带 parentId，前端按 parentId 内存组装树渲染）。
     * 权限：先需"可见"该项目（达 userViewLevel OR member.can_view=1 OR LEADER），否则空列表（不暴露结构）。
     * 不下发敏感字段（private sign-url 等下载链接走 download 接口）。
     */
    List<ProjectFileVo> listFiles(Long projectId);

    /**
     * 前台项目成员列表（公开详情页右栏"参与人员"展示用，仅 PUBLISHED 项目下发，
     * 仅返回成员昵称/用户名/角色（不回标权位 canView/canDownload/canEdit 等内部权限态，不暴露给访客））。
     */
    List<ProjectMemberVo> listMembers(Long projectId);

    /**
     * 前台文件下载链接下发（中转或预签名，由 FileService 按访问模式决定）。
     * 权限：项目级 canDownload（系统 download:lN 够 OR 成员 can_download=1 OR LEADER，
     * 复用 admin 下载权限范式但适配未登录 null userId）。无权抛 ServiceException(500)。
     */
    String getFileDownloadUrl(Long fileId);

    /**
     * 前台项目整包下载（zip 字节流，headers 在 controller；history/计数同 getFileDownloadUrl 口径）。
     * <p>
     * 将项目下所有文件叶子按其文件树相对路径（祖先文件夹名拼成 "a/b/c.ext"）写入 zip 的 ZipEntry，
     * 目录节点不入 zip（zip 由叶子 entry 自带路径还原层级）；根目录文件直接以 name 入 zip。
     * 权限：项目级 canDownload（与 getFileDownloadUrl 同口径）；非 PUBLISHED 项目不下发。
     * 返回 {@link com.knowhub.pojo.project.vo.ProjectPackageBundle}（含项目标题 + 条目列表），
     * controller 用 title 拼 zip 文件名（project-{标题}.zip），用 entries 调 fileService.openRawStream 拉流写 ZipEntry。
     * 不预先全部读入内存：service 只返清单，controller 流式逐文件拉/写，控制内存峰值。
     * 空文件树返回 entries 为空列表（controller 打空 zip）。
     */
    com.knowhub.pojo.project.vo.ProjectPackageBundle listProjectPackageEntries(Long projectId);

    /**
     * 我的项目收藏列表（按收藏时间倒序，仅前台可见口径已发布项目）。
     * 取"收藏 ∩ 前台可见"的 VO 再按收藏顺序排。照 ArticlePortalService.listMyCollected 范式。
     * 未登录防御返空（authoring controller 已 isAuthenticated 兜底，此处双保险）。
     */
    PageInfo<ProjectPortalVo> listMyCollected(int pageNum, int pageSize);
}