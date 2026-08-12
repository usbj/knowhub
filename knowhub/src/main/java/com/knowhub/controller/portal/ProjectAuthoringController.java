package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.project.quarry.ProjectQuarry;
import com.knowhub.pojo.project.vo.ProjectFileTreeVo;
import com.knowhub.pojo.project.vo.ProjectFileVo;
import com.knowhub.pojo.project.vo.ProjectMemberVo;
import com.knowhub.pojo.project.vo.ProjectPortalVo;
import com.knowhub.pojo.project.vo.ProjectVo;
import com.knowhub.service.project.impl.ProjectPortalService;
import com.knowhub.service.project.impl.ProjectService;
import com.knowhub.support.ProjectPermissionResolver;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import com.rookie.framework.security.pojo.UserInfo;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台项目创作接口（/authoring/project/**，走 /authoring/** authenticated 兜底，无按钮权限键）。
 * <p>
 * 薄封装复用后台 {@link ProjectService}（addProjectInfo/editProjectInfo/publishProject/revokeProject/getProjectInfo/
 * quarryProject + 成员/文件树全量业务：状态机、回避、LEADER 唯一性、事务、分级创作闸、canOp(edit/view/download)）。
 * 与博客 {@code BlogAuthoringController} 同构：登录即可创作自己的项目，无需后台按钮权限键。
 * <p>
 * member/file 接口另挂一组 /authoring/project/{projectId}/member|file/**：admin ProjectController 的成员/文件接口
 * 带 knowhub:project:member|add 按钮权限，前台创作者（仅项目 LEADER/作者）未必有；在此以 authenticated 兜底 +
 * service 层 canOp(view|edit) 校验同口径防越权（与 admin service 一致），不再依赖后台按钮权限。
 *
 * @author knowhub
 */
@Tag(name = "项目创作", description = "前台作者创建项目：草稿/发布/编辑/撤回/成员管理/文件树")
@RestController
@RequestMapping("/authoring/project")
public class ProjectAuthoringController {

    @Autowired
    private ProjectService projectService;

    @Autowired
    private ProjectPortalService projectPortalService;

    @GetMapping("/level")
    @Operation(summary = "当前用户项目 view 等级（创作页等级选择器权限感知，0/1/2/3）")
    @PreAuthorize("isAuthenticated()")
    public Result<Integer> myLevel() {
        // 纯内存计算：扫描当前登录用户 perms 取 view 最高等级（admin 自然 3，未授权但已登录=0）。
        // 前端据此禁用不可选等级，后端 addProjectInfo 分级创作闸兜底。
        return Result.success(ProjectPermissionResolver.resolve().view());
    }

    @GetMapping("/list")
    @Operation(summary = "前台我的项目列表（薄封装 quarryProject，controller 注入 authorId=当前用户 userId 收紧到本人创建）")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ProjectVo>> myList(ProjectQuarry quarry) {
        // 强制只召回本人创建的项目：quarry.authorId = 当前用户 userId。
        // service 内 quarryProject 回填 userViewLevel/userId 走 "level<=userViewLevel OR project_id IN (member 子查询)"
        // OR 分支，叠加此 AND 后集合被 author_id=? 收紧到本人，OR 分支恒真叠加不放大，非本人创建（仅作为成员参与）即被排除。
        // 不改 service（admin 共用 quarryProject 保持原"参与/有权看"召回口径，不受影响）。
        // 注意：项目"多人协作"语义由成员表承载，本接口语义为"我创建的项目"，非本人作为成员参与的将不出现在此列表。
        quarry.setAuthorId(currentUserId());
        PageInfo<ProjectVo> page = projectService.quarryProject(quarry);
        return Result.success(page);
    }

    /** 当前登录用户 userId（principal 是 UserInfo，/authoring/** 已 authenticated 兜底）。 */
    private Long currentUserId() {
        return ((UserInfo) SecurityContextHolder.getContext().getAuthentication().getPrincipal()).getUserId();
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "前台编辑回填（复用 getProjectInfo，内 canOp(view) 防越权：作者看自己全态、别人草稿拒）")
    @PreAuthorize("isAuthenticated()")
    public Result<ProjectVo> getForEdit(@PathVariable Long projectId) {
        ProjectVo vo = projectService.getProjectInfo(projectId);
        return Result.success(vo);
    }

    @PostMapping("/draft")
    @Operation(summary = "前台新建项目草稿（复用 addProjectInfo，创建者默认 LEADER + 分级创作闸）")
    @Log(title = "项目创作", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> draft(@RequestBody ProjectVo vo) {
        Boolean b = projectService.addProjectInfo(vo);
        return Result.success(b);
    }

    @PutMapping
    @Operation(summary = "前台编辑项目（复用 editProjectInfo，canOp(edit) + 改 level 校验自身 edit 等级）")
    @Log(title = "项目创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> edit(@RequestBody ProjectVo vo) {
        Boolean b = projectService.editProjectInfo(vo);
        return Result.success(b);
    }

    @PutMapping("/{projectId}/publish")
    @Operation(summary = "前台发布项目（复用 publishProject，走审核开关 knowhub.project.review_enabled）")
    @Log(title = "项目创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> publish(@PathVariable Long projectId) {
        Boolean b = projectService.publishProject(projectId);
        return Result.success(b);
    }

    @PutMapping("/{projectId}/revoke")
    @Operation(summary = "前台撤回项目（复用 revokeProject，仅 PUBLISHED 可撤回）")
    @Log(title = "项目创作", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> revoke(@PathVariable Long projectId) {
        Boolean b = projectService.revokeProject(projectId);
        return Result.success(b);
    }

    // ---- 成员管理（前台创作者管理自己的项目成员，不依赖后台 knowhub:project:member 按钮权限） ----

    @GetMapping("/{projectId}/member")
    @Operation(summary = "前台项目成员列表（薄封装 listMembers，service 内 canOp(view) 校验防越权）")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ProjectMemberVo>> listMembers(@PathVariable Long projectId) {
        List<ProjectMemberVo> list = projectService.listMembers(projectId);
        return Result.success(list);
    }

    @PostMapping("/{projectId}/member")
    @Operation(summary = "前台新增项目成员（薄封装 addMember，service 校验操作者 LEADER/canOp）")
    @Log(title = "项目成员管理", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> addMember(@PathVariable Long projectId, @RequestBody ProjectMemberVo vo) {
        vo.setProjectId(projectId);
        Boolean b = projectService.addMember(vo);
        return Result.success(b);
    }

    @PostMapping("/{projectId}/member/batch")
    @Operation(summary = "前台批量新增项目成员（默认 MEMBER；薄封装 addMembersBatch）")
    @Log(title = "项目成员管理", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> addMembersBatch(@PathVariable Long projectId, @RequestBody List<Long> userIds) {
        Boolean b = projectService.addMembersBatch(projectId, userIds);
        return Result.success(b);
    }

    @PutMapping("/member")
    @Operation(summary = "前台编辑项目成员（薄封装 editMember，LEADER 唯一性 + 换负责人同步 author_id）")
    @Log(title = "项目成员管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> editMember(@RequestBody ProjectMemberVo vo) {
        Boolean b = projectService.editMember(vo);
        return Result.success(b);
    }

    @DeleteMapping("/member/{memberId}")
    @Operation(summary = "前台删除项目成员（LEADER 不可删，需先换负责人；薄封装 deleteMember）")
    @Log(title = "项目成员管理", businessType = BusinessType.DELETE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> deleteMember(@PathVariable Long memberId) {
        Boolean b = projectService.deleteMember(memberId);
        return Result.success(b);
    }

    // ---- 互动（前台作者对任意已发布项目的收藏/取消收藏 + 我的收藏列表，不依赖后台按钮权限） ----

    @PutMapping("/{projectId}/collect")
    @Operation(summary = "收藏/取消收藏项目（collected=true 收藏,false 取消,主表 collect_count 同步）")
    @Log(title = "项目收藏", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> toggleCollect(@PathVariable Long projectId,
                                           @RequestParam(required = false, defaultValue = "true") Boolean collected) {
        Boolean b = projectService.toggleCollect(projectId, collected);
        return Result.success(b);
    }

    @GetMapping("/collect/list")
    @Operation(summary = "我的项目收藏列表（按收藏时间倒序,仅前台可见口径已发布项目）")
    @PreAuthorize("isAuthenticated()")
    public Result<PageInfo<ProjectPortalVo>> myCollected(
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        PageInfo<ProjectPortalVo> page = projectPortalService.listMyCollected(pageNum, pageSize);
        return Result.success(page);
    }

    // ---- 文件树管理（前台创作者管理自己项目文件，不依赖后台 knowhub:project:add 按钮） ----

    @GetMapping("/{projectId}/file/tree")
    @Operation(summary = "前台项目文件树（树形，薄封装 listFileTree，service canOp(view) 校验）")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ProjectFileTreeVo>> listFileTree(@PathVariable Long projectId) {
        List<ProjectFileTreeVo> tree = projectService.listFileTree(projectId);
        return Result.success(tree);
    }

    @GetMapping("/{projectId}/file/list")
    @Operation(summary = "前台项目文件扁平列表（薄封装 listFiles）")
    @PreAuthorize("isAuthenticated()")
    public Result<List<ProjectFileVo>> listFiles(@PathVariable Long projectId) {
        List<ProjectFileVo> list = projectService.listFiles(projectId);
        return Result.success(list);
    }

    @PostMapping("/file/folder")
    @Operation(summary = "前台新建文件夹（薄封装 addFolder）")
    @Log(title = "项目文件管理", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> addFolder(@RequestBody ProjectFileVo vo) {
        Boolean b = projectService.addFolder(vo);
        return Result.success(b);
    }

    @PostMapping("/file/node")
    @Operation(summary = "前台新增文件节点（关联已上传 file_object，薄封装 addFileNode）")
    @Log(title = "项目文件管理", businessType = BusinessType.INSERT)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> addFileNode(@RequestBody ProjectFileVo vo) {
        Boolean b = projectService.addFileNode(vo);
        return Result.success(b);
    }

    @PutMapping("/file/node")
    @Operation(summary = "前台编辑文件节点（改名/移动/排序，薄封装 editFileNode）")
    @Log(title = "项目文件管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> editFileNode(@RequestBody ProjectFileVo vo) {
        Boolean b = projectService.editFileNode(vo);
        return Result.success(b);
    }

    @DeleteMapping("/file/{fileId}")
    @Operation(summary = "前台删除文件节点（级联软删子节点/file_object，薄封装 deleteFileNode）")
    @Log(title = "项目文件管理", businessType = BusinessType.DELETE)
    @PreAuthorize("isAuthenticated()")
    public Result<Boolean> deleteFileNode(@PathVariable Long fileId) {
        Boolean b = projectService.deleteFileNode(fileId);
        return Result.success(b);
    }

    @GetMapping("/file/download/{fileId}")
    @Operation(summary = "前台获取文件下载链接（薄封装 downloadFile，canOp(download) 校验）")
    @PreAuthorize("isAuthenticated()")
    public Result<String> downloadFile(@PathVariable Long fileId) {
        String url = projectService.downloadFile(fileId);
        return Result.success(url);
    }
}