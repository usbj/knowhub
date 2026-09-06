package com.knowhub.pojo.storage.vo;

/**
 * PRIVATE 下载结果。GET /file/download/{objectId} 鉴权通过后返回短期 GET 预签名 URL，
 * 前端拿 downloadUrl 跳转/拉取。后端只返回 URL、不返回文件字节。
 */
public class DownloadVo {

    /** 预签名 GET URL，带 attachment;filename= 强制下载并指定文件名 */
    private String downloadUrl;

    /** 预签名有效期（秒） */
    private Long expires;

    /** 原始文件名（供前端展示下载提示） */
    private String originalName;

    public DownloadVo() {
    }

    public DownloadVo(String downloadUrl, Long expires, String originalName) {
        this.downloadUrl = downloadUrl;
        this.expires = expires;
        this.originalName = originalName;
    }

    public String getDownloadUrl() {
        return downloadUrl;
    }

    public void setDownloadUrl(String downloadUrl) {
        this.downloadUrl = downloadUrl;
    }

    public Long getExpires() {
        return expires;
    }

    public void setExpires(Long expires) {
        this.expires = expires;
    }

    public String getOriginalName() {
        return originalName;
    }

    public void setOriginalName(String originalName) {
        this.originalName = originalName;
    }

    @Override
    public String toString() {
        return "DownloadVo{" +
                "downloadUrl='" + downloadUrl + '\'' +
                ", expires=" + expires +
                ", originalName='" + originalName + '\'' +
                '}';
    }
}
