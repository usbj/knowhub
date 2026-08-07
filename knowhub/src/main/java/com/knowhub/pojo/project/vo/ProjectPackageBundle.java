package com.knowhub.pojo.project.vo;

import java.util.List;

/**
 * 项目整包下载打包载体（service listProjectPackageEntries 出参）。
 * <p>
 * 含项目标题（controller 据此拼 zip 文件名）+ 待打包文件条目列表（fileId/objectId/zipPath），
 * controller 遍历 entries 用 FileService.openRawStream 拉字节流写 ZipEntry；title 用于
 * 生成 Content-Disposition 文件名（项目名 + .zip，对非法文件名字符做 sanitize 兜底）。
 *
 * @author knowhub
 */
public class ProjectPackageBundle {

    /** 项目标题（供 controller 拼 zip 文件名用，sanitize 后入 Content-Disposition） */
    private String title;

    /** 待打包文件条目列表（仅文件叶子，目录由叶子 zipPath 自带 "/" 还原层级） */
    private List<ProjectPackageEntry> entries;

    public ProjectPackageBundle() {
    }

    public ProjectPackageBundle(String title, List<ProjectPackageEntry> entries) {
        this.title = title;
        this.entries = entries;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public List<ProjectPackageEntry> getEntries() {
        return entries;
    }

    public void setEntries(List<ProjectPackageEntry> entries) {
        this.entries = entries;
    }
}