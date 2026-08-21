package com.knowhub.controller.common;

import com.knowhub.pojo.storage.vo.MigrationApplyVo;
import com.knowhub.pojo.storage.vo.MigrationProgressVo;
import com.knowhub.service.storage.impl.FileMigrationService;
import com.rookie.common.annotation.Log;
import com.rookie.common.enums.BusinessType;
import com.rookie.common.pojo.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * OSS 数据迁移接口（扩展点2）。路由 /file/migration，权限键 knowhub:file:transfer。
 * <p>
 * 换 OSS 时按源/目标地址完全迁移，目录结构一致。凭证不落库：源/目标 accessKey/secretKey 由管理员表单临时传入，
 * 后端内存用完即弃，file_migration_task 表只存 endpoint/bucket/进度/状态。
 * <p>
 * 流程：POST /file/migration/start 传源/目标连接参数返 taskId → 前端轮询 GET /file/migration/progress/{taskId} 展示进度。
 */
@Tag(name = "文件存储-数据迁移", description = "OSS 数据迁移（源/目标地址迁移，目录结构一致）相关接口")
@RestController
@RequestMapping("/file/migration")
public class FileMigrationController {

    @Autowired
    private FileMigrationService fileMigrationService;

    @PostMapping("/start")
    @Operation(summary = "启动 OSS 数据迁移")
    @Log(title = "文件迁移", businessType = BusinessType.IMPORT)
    @PreAuthorize("hasAuthority('knowhub:file:transfer')")
    public Result<Long> startMigration(@RequestBody MigrationApplyVo vo) {
        Long taskId = fileMigrationService.startMigration(vo);
        return Result.success(taskId);
    }

    @GetMapping("/progress/{taskId}")
    @Operation(summary = "查迁移进度")
    @PreAuthorize("hasAuthority('knowhub:file:transfer')")
    public Result<MigrationProgressVo> getMigrationProgress(@PathVariable Long taskId) {
        MigrationProgressVo vo = fileMigrationService.getMigrationProgress(taskId);
        return Result.success(vo);
    }
}
