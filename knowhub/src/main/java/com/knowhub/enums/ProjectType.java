package com.knowhub.enums;

/**
 * 项目类型，对应字典 project_type 与 project.type 列。
 * COMPETITION 比赛项目（特有字段走 project_competition 子表）/
 * PRACTICE 练习项目 / OPS 运维项目（当前无独立子表，所需属性由主表
 * description/summary/项目文件覆盖；后续若有特有字段需求，加子表 + 配套字典/前端表单，主表不动）。
 */
public enum ProjectType {

    COMPETITION("COMPETITION", "比赛项目"),
    PRACTICE("PRACTICE", "练习项目"),
    OPS("OPS", "运维项目"),
    ;

    private final String code;

    private final String desc;

    ProjectType(String code, String desc) {
        this.code = code;
        this.desc = desc;
    }

    public String getCode() {
        return code;
    }

    public String getDesc() {
        return desc;
    }
}
