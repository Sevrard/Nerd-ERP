package com.nerd.erp.domain.tenancy;

public enum TenantRole {
    OWNER,
    ADMIN,
    USER,
    READONLY;

    public boolean canWrite() {
        return this != READONLY;
    }

    public boolean canAdminister() {
        return this == OWNER || this == ADMIN;
    }
}
