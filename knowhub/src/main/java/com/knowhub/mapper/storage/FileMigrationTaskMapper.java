package com.knowhub.mapper.storage;

import com.knowhub.pojo.storage.entity.FileMigrationTask;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 文件迁移任务 Mapper。管理 file_migration_task 表的插入/进度更新/状态变更/查询。
 * 凭证不落库（见 {@link FileMigrationTask} 注释），表只存 endpoint/bucket/进度/状态/错误信息。
 */
@Mapper
public interface FileMigrationTaskMapper {

    /** 新建任务行，回填主键 */
    Boolean addTask(FileMigrationTask task);

    /** 按主键取任务行 */
    FileMigrationTask getTaskById(@Param("taskId") Long taskId);

    /** 更新进度（doneCount/failedCount/status=RUNNING） */
    Boolean updateProgress(@Param("taskId") Long taskId,
                           @Param("doneCount") Long doneCount,
                           @Param("failedCount") Long failedCount);

    /** 置终态（SUCCESS/FAILED/CANCELED）+ 错误信息 + 进度 */
    Boolean updateStatus(@Param("taskId") Long taskId,
                         @Param("status") String status,
                         @Param("totalCount") Long totalCount,
                         @Param("doneCount") Long doneCount,
                         @Param("failedCount") Long failedCount,
                         @Param("errorMessage") String errorMessage);
}
