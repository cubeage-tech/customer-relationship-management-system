package com.company.crm.common.enums;

public enum RoleType {
    SUPER_ADMIN("super_admin"),
    ADMIN("admin"),
    SALES_MANAGER("sales_manager"),
    SALES_EXECUTIVE("sales_executive"),
    MARKETING_EXECUTIVE("marketing_executive"),
    SERVICE_AGENT("service_agent"),
    FINANCE_APPROVER("finance_approver"),
    EXECUTIVE_OWNER("executive_owner");

    private final String dbValue;

    RoleType(String dbValue) {
        this.dbValue = dbValue;
    }

    public String getDbValue() {
        return dbValue;
    }

    public static RoleType fromDbValue(String dbValue) {
        for (RoleType role : values()) {
            if (role.dbValue.equals(dbValue)) {
                return role;
            }
        }
        throw new IllegalArgumentException("Unknown role_type value: " + dbValue);
    }
}
