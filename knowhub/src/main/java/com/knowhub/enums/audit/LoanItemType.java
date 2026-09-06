package com.knowhub.enums.audit;

/**
 * 借出物品类型枚举，对应字典 audit_loan_item_type 与 audit_loan.item_type 列。
 * ASSET      资产   有资产编号(asset_no 可填)，按件追溯
 * CONSUMABLE 耗材   无编号，按数量管理
 * 枚举值与字典 dict_data_value 一致，供前端下拉与后端校验共用。
 */
public enum LoanItemType {

    ASSET("ASSET", "资产"),
    CONSUMABLE("CONSUMABLE", "耗材");

    private final String code;

    private final String label;

    LoanItemType(String code, String label) {
        this.code = code;
        this.label = label;
    }

    public String getCode() {
        return code;
    }

    public String getLabel() {
        return label;
    }

    /**
     * 按 code 安全解析枚举，未命中返回 null。
     */
    public static LoanItemType ofCode(String code) {
        if (code == null || code.isEmpty()) {
            return null;
        }
        for (LoanItemType t : values()) {
            if (t.code.equals(code)) {
                return t;
            }
        }
        return null;
    }
}