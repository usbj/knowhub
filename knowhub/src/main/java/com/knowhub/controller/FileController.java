package com.knowhub.controller;

import com.github.pagehelper.PageInfo;
import com.knowhub.pojo.quarry.FileQuarry;
import com.knowhub.pojo.vo.BindVo;
import com.knowhub.pojo.vo.DownloadVo;
import com.knowhub.pojo.vo.FileObjectVo;
import com.knowhub.pojo.vo.UploadApplyVo;
import com.knowhub.pojo.vo.UploadTokenVo;
import com.knowhub.service.FileService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 文件存储接口。路由 /file，权限键三段式 knowhub:file:*。
 * 预签名直传：POST /file/upload-token 签发令牌 → 前端直传 RustFS → POST /file/confirm/{id} 确认。
 * PUBLIC 回显走 GET /file/public/{id} 302 重定向（供 Markdown <img> 直接引用）。
 * PRIVATE 下载走 GET /file/download/{id} 返回预签名 URL（带 attachment;filename）。
 */
@Tag(name = "文件存储", description = "文件上传令牌/确认/回显/下载/管理相关接口")
@RestController
@RequestMapping("/file")
public class FileController {

    @Autowired
    private FileService fileService;

    @PostMapping("/upload-token")
    @Operation(summary = "签发上传令牌")
    @Log(title = "文件对象", businessType = BusinessType.INSERT)
    @PreAuthorize("hasAuthority('knowhub:file:upload')")
    public Result<UploadTokenVo> applyUploadToken(@RequestBody UploadApplyVo vo) {
        UploadTokenVo token = fileService.applyUploadToken(vo);
        return Result.success(token);
    }

    @PostMapping("/confirm/{objectId}")
    @Operation(summary = "上传确认")
    @Log(title = "文件对象", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:file:upload')")
    public Result<Boolean> confirmUpload(@PathVariable Long objectId,
                                         @RequestParam(required = false) Long bizRefId) {
        Boolean b = fileService.confirmUpload(objectId, bizRefId);
        return Result.success(b);
    }

    @GetMapping("/public/{objectId}")
    @Operation(summary = "PUBLIC 对象回显（302 重定向到 RustFS）")
    public ResponseEntity<Void> getPublic(@PathVariable Long objectId) {
        String url = fileService.getPublicUrl(objectId);
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, url)
                .build();
    }

    @GetMapping("/download/{objectId}")
    @Operation(summary = "获取下载预签名 URL（PRIVATE 鉴权）")
    @PreAuthorize("hasAuthority('knowhub:file:download')")
    public Result<DownloadVo> getDownloadUrl(@PathVariable Long objectId) {
        DownloadVo vo = fileService.getDownloadUrl(objectId);
        return Result.success(vo);
    }

    @GetMapping("/list")
    @Operation(summary = "获取文件对象列表")
    @PreAuthorize("hasAuthority('knowhub:file:quarry')")
    public Result<PageInfo<FileObjectVo>> quarryFile(FileQuarry quarry) {
        PageInfo<FileObjectVo> page = fileService.quarryFile(quarry);
        return Result.success(page);
    }

    @GetMapping("/{objectId}")
    @Operation(summary = "获取文件对象详情")
    @PreAuthorize("hasAuthority('knowhub:file:info')")
    public Result<FileObjectVo> getFileObjectInfo(@PathVariable Long objectId) {
        FileObjectVo vo = fileService.getFileObjectInfo(objectId);
        return Result.success(vo);
    }

    @PutMapping("/bind")
    @Operation(summary = "绑定业务关联")
    @Log(title = "文件对象", businessType = BusinessType.UPDATE)
    @PreAuthorize("hasAuthority('knowhub:file:upload')")
    public Result<Boolean> bindBizRef(@RequestBody BindVo vo) {
        Boolean b = fileService.bindBizRef(vo);
        return Result.success(b);
    }

    @DeleteMapping("/{objectIds}")
    @Operation(summary = "批量删除文件对象")
    @Log(title = "文件对象", businessType = BusinessType.DELETE)
    @PreAuthorize("hasAuthority('knowhub:file:delete')")
    public Result<Boolean> deleteFileObjects(@PathVariable Long[] objectIds) {
        Boolean b = fileService.deleteFileObjects(objectIds);
        return Result.success(b);
    }
}
