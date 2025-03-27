package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

public class SysMenu extends BaseEntity {

    private Long menuId;

    private String menuName;

    private String permKey;

    private Integer menuType;

    private String path;

    private String icon;

    private Integer status;

    private Integer delete;

    public SysMenu() {
    }

    public SysMenu(Date createTime, Date updateTime, String createBy, String updateBy, Long menuId, String menuName, String permKey, Integer menuType, String path, String icon, Integer status, Integer delete) {
        super(createTime, updateTime, createBy, updateBy);
        this.menuId = menuId;
        this.menuName = menuName;
        this.permKey = permKey;
        this.menuType = menuType;
        this.path = path;
        this.icon = icon;
        this.status = status;
        this.delete = delete;
    }

    public Long getMenuId() {
        return menuId;
    }

    public void setMenuId(Long menuId) {
        this.menuId = menuId;
    }

    public String getMenuName() {
        return menuName;
    }

    public void setMenuName(String menuName) {
        this.menuName = menuName;
    }

    public String getPermKey() {
        return permKey;
    }

    public void setPermKey(String permKey) {
        this.permKey = permKey;
    }

    public Integer getMenuType() {
        return menuType;
    }

    public void setMenuType(Integer menuType) {
        this.menuType = menuType;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getDelete() {
        return delete;
    }

    public void setDelete(Integer delete) {
        this.delete = delete;
    }

    @Override
    public String toString() {
        return "MenuEntity{" +
                "menuId=" + menuId +
                ", menuName='" + menuName + '\'' +
                ", permKey='" + permKey + '\'' +
                ", menuType=" + menuType +
                ", path='" + path + '\'' +
                ", icon='" + icon + '\'' +
                ", status=" + status +
                ", createTime=" + getCreateTime() +
                ", updateTime=" + getUpdateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                ", updateBy='" + getUpdateBy() + '\'' +
                ", delete=" + delete +
                '}';
    }
}
