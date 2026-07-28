package com.knowhub.controller;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.quarry.ProjectQuarry;
import com.knowhub.pojo.vo.ProjectFileTreeVo;
import com.knowhub.pojo.vo.ProjectFileVo;
import com.knowhub.pojo.vo.ProjectMemberVo;
import com.knowhub.pojo.vo.ProjectReviewLogVo;
import com.knowhub.pojo.vo.ProjectReviewVo;
import com.knowhub.pojo.vo.ProjectVo;
import com.knowhub.service.ProjectService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 项目管理接口。
 * 后台菜单名"项目管理"，前台展示端复用同一套接口（GitHub 式文件树侧边栏）。
 * 权限键：按钮(非等级) knowhub:project:动作 走 @PreAuthorize；
 * 等级(view/download/edit:lN) 走 service 层 ProjectPermissionResolver 扫 perms 取最高等级判定，
 * 列表 quarry 接口 @PreAuthorize 用 knowhub:project:quarry（进页面门槛），具体可见性由 SQL 过滤。
 * 审核流程复用博客/资源范式（状态机+回避+流水表+对账任务），ReviewAction 枚举复用。
 */
@Tag(name = "项目管理", description = "项目 CRUD / 发布审核 / 成员管理 / 文件树 / 下载相关接口")
@RestController
@RequestMapping("/project")
public class ProjectController {

    @Autowired
    private ProjectService projectService;

    @GetMapping("/list")
    @Operation(summary = "获取项目列表")
    @PreAuthorize("hasAuthority('knowhub:project:quarry')")
    public Result<PageInfo<ProjectVo>> quarryProject(ProjectQuarry quarry) {
        PageInfo<ProjectVo> pageInfo = projectService.quarryProject(quarry);
        return Result.success(pageInfo);
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "获取项目详情")
    @PreAuthorize("hasAuthority('knowhub:project:info')")
    public Result<ProjectVo> getProjectInfo(@PathVariable Long projectId) {
        ProjectVo vo = projectService.getProjectInfo(projectId);
        return Result.success(vo);
    }

    @PostMapping()
    @Operation(summary = "新增项目")
    @Log(title = "项目管理", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:project:add')")
    public Result<Boolean> addProject(@RequestBody ProjectVo vo) {
        Boolean b = projectService.addProjectInfo(vo);
        return Result.success(b);
    }

    @PutMapping()
    @Operation(summary = "编辑项目")
    @Log(title = "项目管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:project:add')")
    public Result<Boolean> editProject(@RequestBody ProjectVo vo) {
        Boolean b = projectService.editProjectInfo(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{projectIds}")
    @Operation(summary = "批量删除项目")
    @Log(title = "项目管理", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:project:delete')")
    public Result<Boolean> deleteProject(@PathVariable Long[] projectIds) {
        Boolean b = projectService.deleteProjectInfo(projectIds);
        return Result.success(b);
    }

    @PutMapping("/publish/{projectId}")
    @Operation(summary = "发布项目")
    @Log(title = "项目管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:project:publish')")
    public Result<Boolean> publishProject(@PathVariable Long projectId) {
        Boolean b = projectService.publishProject(projectId);
        return Result.success(b);
    }

    @PutMapping("/revoke/{projectId}")
    @Operation(summary = "撤回项目")
    @Log(title = "项目管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:project:revoke')")
    public Result<Boolean> revokeProject(@PathVariable Long projectId) {
        Boolean b = projectService.revokeProject(projectId);
        return Result.success(b);
    }

    @PutMapping("/review")
    @Operation(summary = "审核项目")
    @Log(title = "项目管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:project:review')")
    public Result<Boolean> reviewProject(@RequestBody ProjectReviewVo vo) {
        Boolean b = projectService.reviewProject(vo);
        return Result.success(b);
    }

    @GetMapping("/review-log/{projectId}")
    @Operation(summary = "获取项目审核历史")
    @PreAuthorize("hasAuthority('knowhub:project:reviewLog')")
    public Result<List<ProjectReviewLogVo>> listReviewLog(@PathVariable Long projectId) {
        List<ProjectReviewLogVo> list = projectService.listReviewLog(projectId);
        return Result.success(list);
    }

    // ---- 成员管理 ----

    @GetMapping("/member/{projectId}")
    @Operation(summary = "获取项目成员列表")
    @PreAuthorize("hasAuthority('knowhub:project:member')")
    public Result<List<ProjectMemberVo>> listMembers(@PathVariable Long projectId) {
        List<ProjectMemberVo> list = projectService.listMembers(projectId);
        return Result.success(list);
    }

    @PostMapping("/member")
    @Operation(summary = "新增项目成员")
    @Log(title = "项目成员管理", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:project:member')")
    public Result<Boolean> addMember(@RequestBody ProjectMemberVo vo) {
        Boolean b = projectService.addMember(vo);
        return Result.success(b);
    }

    @PostMapping("/member/batch/{projectId}")
    @Operation(summary = "批量新增项目成员(默认 MEMBER,参考通知分组)")
    @Log(title = "项目成员管理", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:project:member')")
    public Result<Boolean> addMembersBatch(@PathVariable Long projectId, @RequestBody List<Long> userIds) {
        Boolean b = projectService.addMembersBatch(projectId, userIds);
        return Result.success(b);
    }

    @PutMapping("/member")
    @Operation(summary = "编辑项目成员")
    @Log(title = "项目成员管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:project:member')")
    public Result<Boolean> editMember(@RequestBody ProjectMemberVo vo) {
        Boolean b = projectService.editMember(vo);
        return Result.success(b);
    }

    @DeleteMapping("/member/{memberId}")
    @Operation(summary = "删除项目成员")
    @Log(title = "项目成员管理", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:project:member')")
    public Result<Boolean> deleteMember(@PathVariable Long memberId) {
        Boolean b = projectService.deleteMember(memberId);
        return Result.success(b);
    }

    // ---- 文件树管理 ----

    @GetMapping("/file/tree/{projectId}")
    @Operation(summary = "获取项目文件树(树形,GitHub式侧边栏)")
    @PreAuthorize("hasAuthority('knowhub:project:info')")
    public Result<List<ProjectFileTreeVo>> listFileTree(@PathVariable Long projectId) {
        List<ProjectFileTreeVo> tree = projectService.listFileTree(projectId);
        return Result.success(tree);
    }

    @GetMapping("/file/list/{projectId}")
    @Operation(summary = "获取项目文件列表(扁平)")
    @PreAuthorize("hasAuthority('knowhub:project:info')")
    public Result<List<ProjectFileVo>> listFiles(@PathVariable Long projectId) {
        List<ProjectFileVo> list = projectService.listFiles(projectId);
        return Result.success(list);
    }

    @PostMapping("/file/folder")
    @Operation(summary = "新建文件夹")
    @Log(title = "项目文件管理", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:project:add')")
    public Result<Boolean> addFolder(@RequestBody ProjectFileVo vo) {
        Boolean b = projectService.addFolder(vo);
        return Result.success(b);
    }

    @PostMapping("/file/node")
    @Operation(summary = "新增文件节点(关联已上传 file_object)")
    @Log(title = "项目文件管理", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:project:add')")
    public Result<Boolean> addFileNode(@RequestBody ProjectFileVo vo) {
        Boolean b = projectService.addFileNode(vo);
        return Result.success(b);
    }

    @PutMapping("/file/node")
    @Operation(summary = "编辑文件节点(改名/移动/排序)")
    @Log(title = "项目文件管理", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:project:add')")
    public Result<Boolean> editFileNode(@RequestBody ProjectFileVo vo) {
        Boolean b = projectService.editFileNode(vo);
        return Result.success(b);
    }

    @DeleteMapping("/file/{fileId}")
    @Operation(summary = "删除文件节点(目录级联删子节点,文件叶子级联删 file_object)")
    @Log(title = "项目文件管理", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:project:add')")
    public Result<Boolean> deleteFileNode(@PathVariable Long fileId) {
        Boolean b = projectService.deleteFileNode(fileId);
        return Result.success(b);
    }

    @GetMapping("/file/download/{fileId}")
    @Operation(summary = "获取文件下载链接(中转/预签名)")
    @PreAuthorize("hasAuthority('knowhub:project:info')")
    public Result<String> downloadFile(@PathVariable Long fileId) {
        String url = projectService.downloadFile(fileId);
        return Result.success(url);
    }
}
