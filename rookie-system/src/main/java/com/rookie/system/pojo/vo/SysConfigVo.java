package com.rookie.system.pojo.vo;

/**
 * 系统设置 VO（对前后端交互结构）。
 * <p>
 * 与 {@code SysConfig} 实体字段一致，作为 Controller 出参 / 入参载体，
 * 通过 BeanUtil 与实体互转，对齐 {@code SysDictDataVo} 的使用风格。
 */
public class SysConfigVo {

    private Long configId;

    private String configKey;

    private String configName;

    private String configValue;

    private String valueType;

    private Integer isSystem;

    private String remark;

    private Integer status;

    private String createTime;

    private String updateTime;

    public SysConfigVo() {
    }

    public SysConfigVo(Long configId, String configKey, String configName, String configValue,
                       String valueType, Integer isSystem, String remark, Integer status,
                       String createTime, String updateTime) {
        this.configId = configId;
        this.configKey = configKey;
        this.configName = configName;
        this.configValue = configValue;
        this.valueType = valueType;
        this.isSystem = isSystem;
        this.remark = remark;
        this.status = status;
        this.createTime = createTime;
        this.updateTime = updateTime;
    }

    public Long getConfigId() {
        return configId;
    }

    public void setConfigId(Long configId) {
        this.configId = configId;
    }

    public String getConfigKey() {
        return configKey;
    }

    public void setConfigKey(String configKey) {
        this.configKey = configKey;
    }

    public String getConfigName() {
        return configName;
    }

    public void setConfigName(String configName) {
        this.configName = configName;
    }

    public String getConfigValue() {
        return configValue;
    }

    public void setConfigValue(String configValue) {
        this.configValue = configValue;
    }

    public String getValueType() {
        return valueType;
    }

    public void setValueType(String valueType) {
        this.valueType = valueType;
    }

    public Integer getIsSystem() {
        return isSystem;
    }

    public void setIsSystem(Integer isSystem) {
        this.isSystem = isSystem;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
    }

    public Integer getStatus() {
        return status;
    }

    public void setStatus(Integer status) {
        this.status = status;
    }

    public String getCreateTime() {
        return createTime;
    }

    public void setCreateTime(String createTime) {
        this.createTime = createTime;
    }

    public String getUpdateTime() {
        return updateTime;
    }

    public void setUpdateTime(String updateTime) {
        this.updateTime = updateTime;
    }
}
