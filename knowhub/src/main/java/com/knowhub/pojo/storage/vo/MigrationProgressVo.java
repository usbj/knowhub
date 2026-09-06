package com.knowhub.pojo.storage.vo;

/**
 * OSS 迁移进度 VO。前端轮询 GET /file/migration/progress/{taskId} 拿此对象展示进度条 + 状态。
 * <p>
 * status：PENDING 建任务待跑 / RUNNING 拷贝中 / SUCCESS 全部成功 / FAILED 中途异常 / CANCELED 取消。
 * totalCount 源桶对象总数；doneCount 已成功拷贝数；failedCount 拷贝失败数。
 * errorMessage 仅 FAILED 时填。
 */
public class MigrationProgressVo {

    private Long taskId;
    private String status;
    private Long totalCount;
    private Long doneCount;
    private Long failedCount;
    private String errorMessage;

    public Long getTaskId() {
        return taskId;
    }

    public void setTaskId(Long taskId) {
        this.taskId = taskId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getTotalCount() {
        return totalCount;
    }

    public void setTotalCount(Long totalCount) {
        this.totalCount = totalCount;
    }

    public Long getDoneCount() {
        return doneCount;
    }

    public void setDoneCount(Long doneCount) {
        this.doneCount = doneCount;
    }

    public Long getFailedCount() {
        return failedCount;
    }

    public void setFailedCount(Long failedCount) {
        this.failedCount = failedCount;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public void setErrorMessage(String errorMessage) {
        this.errorMessage = errorMessage;
    }
}
