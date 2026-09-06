package com.knowhub.controller.portal;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.project.quarry.ProjectPortalSearchQuarry;
import com.knowhub.pojo.project.vo.ProjectFileVo;
import com.knowhub.pojo.project.vo.ProjectMemberVo;
import com.knowhub.pojo.project.vo.ProjectPortalDetailVo;
import com.knowhub.pojo.project.vo.ProjectPortalVo;
import com.knowhub.service.project.impl.ProjectPortalService;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 前台项目门户公开接口（/portal/project/*，全部无 @PreAuthorize，走 /portal/** permitAll）。
 * 铁律：所有列表类 SQL 一律 status=PUBLISHED AND deleted=0 AND level&lt;=userViewLevel
 * （分级开关关时恒 1，L2/L3 永不下发前台）；详情越级锁态降级（正文 description 置空 + locked + lockReason）。
 * 项目无标签体系，故无 /tag 相关接口（与博客门户差异点）。
 *
 * @author knowhub
 */
@Tag(name = "项目门户", description = "前台公开搜索/推荐/详情/相关推荐/文件树/文件下载")
@RestController
@RequestMapping("/portal/project")
public class ProjectPortalController {

    @Autowired
    ProjectPortalService projectPortalService;

    @Autowired
    com.knowhub.service.storage.impl.FileService fileService;

    @GetMapping("/search")
    @Operation(summary = "前台项目搜索（标题关键字 + type/level 复合过滤 + 排序，分页）")
    public Result<PageInfo<ProjectPortalVo>> search(ProjectPortalSearchQuarry quarry) {
        PageInfo<ProjectPortalVo> page = projectPortalService.search(quarry);
        return Result.success(page);
    }

    @GetMapping("/recommend")
    @Operation(summary = "前台项目推荐 feed（全局热门兜底 + 排除已浏览；详情页相关推荐排除当前）")
    public Result<List<ProjectPortalVo>> recommend(
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) Long excludeProjectId) {
        List<ProjectPortalVo> list = projectPortalService.recommend(size, excludeProjectId);
        return Result.success(list);
    }

    @GetMapping("/{projectId}")
    @Operation(summary = "前台项目详情（越级锁态降级 + canDownload 权限态回填 + 登录态计浏览量）")
    public Result<ProjectPortalDetailVo> getDetail(@PathVariable Long projectId) {
        ProjectPortalDetailVo vo = projectPortalService.getDetail(projectId);
        return Result.success(vo);
    }

    @GetMapping("/{projectId}/related")
    @Operation(summary = "详情页相关推荐（同 type 排除自身，按热度打分）")
    public Result<List<ProjectPortalVo>> related(@PathVariable Long projectId,
                                                  @RequestParam(defaultValue = "10") int size) {
        List<ProjectPortalVo> list = projectPortalService.related(projectId, size);
        return Result.success(list);
    }

    @GetMapping("/{projectId}/members")
    @Operation(summary = "前台项目成员列表（仅 PUBLISHED 项目下发，参与人员展示用，裁剪内部权限态）")
    public Result<List<ProjectMemberVo>> listMembers(@PathVariable Long projectId) {
        List<ProjectMemberVo> list = projectPortalService.listMembers(projectId);
        return Result.success(list);
    }

    @GetMapping("/{projectId}/file-tree")
    @Operation(summary = "前台项目文件树（扁平带 parentId，前端按 parentId 内存组装树渲染）")
    public Result<List<ProjectFileVo>> listFiles(@PathVariable Long projectId) {
        List<ProjectFileVo> list = projectPortalService.listFiles(projectId);
        return Result.success(list);
    }

    @GetMapping("/file/download/{fileId}")
    @Operation(summary = "前台项目文件下载链接下发（需项目级下载权限，未登录/无权抛 ServiceException）")
    public Result<String> getFileDownloadUrl(@PathVariable Long fileId) {
        String url = projectPortalService.getFileDownloadUrl(fileId);
        return Result.success(url);
    }

    /**
     * 项目整包下载（zip）：service 返回待打包条目列表（含 PUBLISHED+canDownload 校验），controller 逐条用
     * fileService.openRawStream 拉字节流写 ZipEntry 并 close，控制内存峰值。同步写 HttpServletResponse
     * （与 /file/proxy 同口径：异步 dispatch 不传播 SecurityContext，permitAll 区保持一致同步写）。
     * 空文件树打空 zip（浏览器会得到一个合法的空压缩包）。
     */
    @GetMapping("/{projectId}/package")
    @Operation(summary = "前台项目整包下载（zip，需项目级下载权限）")
    public void streamProjectPackage(@PathVariable Long projectId, jakarta.servlet.http.HttpServletResponse response) {
        com.knowhub.pojo.project.vo.ProjectPackageBundle bundle;
        try {
            bundle = projectPortalService.listProjectPackageEntries(projectId);
        } catch (com.rookie.common.exception.ServiceException e) {
            int code = e.getCode() == null ? 500 : e.getCode();
            response.setStatus(switch (code) {
                case 404 -> jakarta.servlet.http.HttpServletResponse.SC_NOT_FOUND;
                case 403 -> jakarta.servlet.http.HttpServletResponse.SC_FORBIDDEN;
                default -> jakarta.servlet.http.HttpServletResponse.SC_INTERNAL_SERVER_ERROR;
            });
            return;
        }
        // zip 文件名用项目标题：去 "\"\\/ : * ? < > | 等文件系统非法字符后落 Content-Disposition。
        // 双段下发：filename*=UTF-8''<enc> 是 RFC 5987/6266 正道，现代浏览器据此显示中文真名；
        // 旧式 filename="" 只能装 ISO-8859-1(0-255) 字节，中文越级会被 Tomcat 10 校验抛 IllegalArgumentException 移除头
        // 致响应中断。故旧段改放同源 ASCII 百分号编码兜底（旧浏览器只见 %E6%B5%8B 但不下错），中文真名仅走 filename*。
        String title = bundle.getTitle();
        String safeName = (title == null || title.trim().isEmpty())
                ? "project-" + projectId
                : title.replace("\"", "").replace("\\", "").replace("/", "").replace(":", "").replace("*", "").replace("?", "").replace("<", "").replace(">", "").replace("|", "").trim();
        String asciiFallback = java.net.URLEncoder.encode(safeName, java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20");
        String disposition = "attachment; filename=\"" + asciiFallback + ".zip\"; filename*=UTF-8''" + asciiFallback + ".zip";
        response.setStatus(jakarta.servlet.http.HttpServletResponse.SC_OK);
        response.setContentType("application/zip");
        response.setHeader("Content-Disposition", disposition);
        try (java.io.OutputStream rawOut = response.getOutputStream();
             java.util.zip.ZipOutputStream zos = new java.util.zip.ZipOutputStream(rawOut)) {
            for (com.knowhub.pojo.project.vo.ProjectPackageEntry entry : bundle.getEntries()) {
                java.util.zip.ZipEntry ze = new java.util.zip.ZipEntry(entry.getZipPath());
                zos.putNextEntry(ze);
                try (var in = fileService.openRawStream(entry.getObjectId()).getStream()) {
                    in.transferTo(zos);
                }
                zos.closeEntry();
            }
        } catch (com.rookie.common.exception.ServiceException e) {
            // openRawStream 抛（对象不存在/未确认 404）：响应头已下发不可改状态，记日志；客户端拿到不完整 zip
            org.slf4j.LoggerFactory.getLogger(ProjectPortalController.class)
                    .warn("项目 {} 整包拉取对象失败 reason={}", projectId, e.getMessage());
        } catch (Exception e) {
            org.slf4j.LoggerFactory.getLogger(ProjectPortalController.class)
                    .warn("项目 {} 整包下载流式中断 reason={}", projectId, e.getMessage());
        }
    }
}