package com.rookie.common.pojo.entity;

import com.rookie.common.pojo.BaseEntity;

import java.util.Date;

public class SysRole extends BaseEntity {

    private Long roleId;

    private String roleName;


    private Integer roleLevel;

    private Integer status;

    private Integer isDefault;

    private Integer delete;

    public SysRole() {
    }

    public SysRole(Date createTime, Date updateTime, String createBy, String updateBy, Long roleId, String roleName, Integer roleLevel, Integer status, Integer isDefault, Integer delete) {
        super(createTime, updateTime, createBy, updateBy);
        this.roleId = roleId;
        this.roleName = roleName;
        this.roleLevel = roleLevel;
        this.status = status;
        this.isDefault = isDefault;
        this.delete = delete;
    }

    public Long getRoleId() {
        return roleId;
    }

    public void setRoleId(Long roleId) {
        this.roleId = roleId;
    }

    public String getRoleName() {
        return roleName;
    }

    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }


    public Integer getRoleLevel() {
        return roleLevel;
    }

    public void setRoleLevel(Integer roleLevel) {
        this.roleLevel = roleLevel;
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
        return "RoleEntity{" +
                "roleId=" + roleId +
                ", roleName='" + roleName + '\'' +
                ", roleLevel=" + roleLevel +
                ", status=" + status +
                ", isDefault=" + isDefault +
                ", createTime=" + getCreateTime() +
                ", updateTime=" + getUpdateTime() +
                ", createBy='" + getCreateBy() + '\'' +
                ", updateBy='" + getUpdateBy() + '\'' +
                ", delete=" + delete +
                '}';
    }
}
