package com.knowhub.pojo.project.vo;

/**
 * 项目整包下载 zip 内单文件条目（service listProjectPackageEntries 出参）。
 * <p>
 * 仅含文件叶子（目录不入 zip，由叶子 zipPath 自带 "/" 还原层级），controller 用本条目的 objectId +
 * FileService.openRawStream 拉字节流写 ZipEntry，path 用作 ZipEntry 名（相对项目根目录路径，含祖先文件夹名）。
 *
 * @author knowhub
 */
public class ProjectPackageEntry {

    /** 项目文件节点主键（fileId） */
    private Long fileId;

    /** 关联 file_object 主键（s3Client.getObject 用） */
    private Long objectId;

    /** zip 内相对路径（如 "src/readme.md" 或 "doc.pdf"，根目录文件直接为 name） */
    private String zipPath;

    public ProjectPackageEntry() {
    }

    public ProjectPackageEntry(Long fileId, Long objectId, String zipPath) {
        this.fileId = fileId;
        this.objectId = objectId;
        this.zipPath = zipPath;
    }

    public Long getFileId() {
        return fileId;
    }

    public void setFileId(Long fileId) {
        this.fileId = fileId;
    }

    public Long getObjectId() {
        return objectId;
    }

    public void setObjectId(Long objectId) {
        this.objectId = objectId;
    }

    public String getZipPath() {
        return zipPath;
    }

    public void setZipPath(String zipPath) {
        this.zipPath = zipPath;
    }
}