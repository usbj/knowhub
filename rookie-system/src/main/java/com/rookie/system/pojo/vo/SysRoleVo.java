package com.rookie.system.pojo.vo;

import com.rookie.common.pojo.entity.SysMenu;

import java.util.Date;
import java.util.List;

public class SysRoleVo {

    private Long roleId;

    private String roleName;

    private Integer roleLevel;

    private String roleKey;

    private Integer status;

    private Integer isDefault;

    private Date createTime;

    private List<SysMenu> rolePerm;

    private List<Long> permId;

    public SysRoleVo() {
    }

    public SysRoleVo(Long roleId, String roleName, Integer roleLevel, String roleKey, Integer status, Integer isDefault, Date createTime, List<SysMenu> rolePerm, List<Long> permId) {
        this.roleId = roleId;
        this.roleName = roleName;
        this.roleLevel = roleLevel;
        this.roleKey = roleKey;
        this.status = status;
        this.isDefault = isDefault;
        this.createTime = createTime;
        this.rolePerm = rolePerm;
        this.permId = permId;
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

    public String getRoleKey() {
        return roleKey;
    }

    public void setRoleKey(String roleKey) {
        this.roleKey = roleKey;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public Integer getIsDefault() {
        return isDefault;
    }

    public void setIsDefault(Integer isDefault) {
        this.isDefault = isDefault;
    }

    public Date getCreateTime() {
        return createTime;
    }

    public void setCreateTime(Date createTime) {
        this.createTime = createTime;
    }

    public List<SysMenu> getRolePerm() {
        return rolePerm;
    }

    public void setRolePerm(List<SysMenu> rolePerm) {
        this.rolePerm = rolePerm;
    }

    public List<Long> getPermId() {
        return permId;
    }

    public void setPermId(List<Long> permId) {
        this.permId = permId;
    }

    @Override
    public String toString() {
        return "SysRoleVo{" +
                "roleId=" + roleId +
                ", roleName='" + roleName + '\'' +
                ", roleLevel=" + roleLevel +
                ", roleKey='" + roleKey + '\'' +
                ", status=" + status +
                ", isDefault=" + isDefault +
                ", createTime=" + createTime +
                ", rolePerm=" + rolePerm +
                ", permId=" + permId +
                '}';
    }
}
